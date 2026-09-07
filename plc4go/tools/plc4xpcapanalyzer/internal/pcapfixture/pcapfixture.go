/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

// Package pcapfixture builds small, deterministic pcap captures containing real protocol
// traffic.
//
// It has two consumers. Tests use it so the analyzers can be exercised against genuine
// protocol bytes without shipping a binary capture or needing a device, and demo mode uses it
// so the tool has something real to analyze when the user has no capture of their own.
//
// The payloads in this package are not invented. Each one was taken from plc4x's own protocol
// tests, or is a well-known BACnet/IP service request, and each was verified to parse and then
// re-serialize to byte-identical output through the real plc4x codec. That property is what
// makes them useful: the analyzer's whole job is a parse, re-serialize and byte-compare loop,
// so a fixture that round-trips exercises the success path, and the deliberately broken
// payloads below exercise the failure counters.
//
// Captures are written with the pure-Go pcapgo writer rather than the libpcap binding, so
// building a fixture needs no cgo even though reading one back through the analyzer does.
package pcapfixture

import (
	"encoding/hex"
	"net"
	"os"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/gopacket/gopacket"
	"github.com/gopacket/gopacket/layers"
	"github.com/gopacket/gopacket/pcapgo"
)

// The synthetic endpoints. The c-bus analyzer decides whether a packet is a request or a
// response by comparing its source address against the configured client address, so a
// fixture and the configuration that reads it have to agree on these.
const (
	// ClientIP is the address that originates requests.
	ClientIP = "192.168.178.101"
	// ServerIP is the address that originates responses.
	ServerIP = "192.168.178.10"

	// CBusPort is the TCP port C-Bus runs on.
	CBusPort = 10001
	// BacnetPort is the UDP port BACnet/IP runs on.
	BacnetPort = 47808
	// The registered ports of the other protocols, which are also the ports their default
	// filters select. A fixture has to be written on the port the analyzer will look for.
	ModbusPort = 502
	S7Port     = 102
	EipPort    = 44818
	KnxPort    = 3671
	AdsPort    = 48898
	AbEthPort  = 2222
	SlmpPort   = 5007
	// FirmataPort is not registered: Firmata is a serial protocol, so it only reaches a capture
	// through a gateway. This is the port the fixtures use and the default filter looks for; a
	// real tunnel needs the user's own --filter.
	FirmataPort = 3030
	Iec104Port  = 2404
	OpcuaPort   = 4840
	UmasPort    = 502
	ClientPort  = 40000
)

// Transport is the transport a protocol runs over.
type Transport int

const (
	// TCP is a stream protocol: the client connects from ClientPort to the server's port.
	TCP Transport = iota
	// UDP is a datagram protocol, where both ends use the protocol's own port.
	UDP
)

// Wire is where a protocol lives, which is all a capture needs to know about it.
type Wire struct {
	Transport Transport
	Port      int
}

// BaseTimestamp is the capture time of the first packet. It is fixed, not time.Now(), so that
// a generated capture is byte-identical from run to run and golden files stay stable.
var BaseTimestamp = time.Unix(1700000000, 0).UTC()

// PacketInterval is added to BaseTimestamp for each successive packet. The analyzer indexes
// packets by timestamp, so two packets must never share one.
const PacketInterval = time.Millisecond

// Direction says which endpoint sent a packet.
type Direction int

const (
	// FromClient is a request: ClientIP -> ServerIP.
	FromClient Direction = iota
	// FromServer is a response: ServerIP -> ClientIP.
	FromServer
)

// Packet is one application payload and the direction it travelled.
type Packet struct {
	// Payload is the application-layer bytes, which is what the analyzer parses.
	Payload []byte
	// Direction selects the source and destination addresses.
	Direction Direction
	// Comment describes the packet for humans reading a fixture definition.
	Comment string
}

// CBusSession returns a C-Bus exchange whose every packet parses and re-serializes to
// identical bytes. Requests and responses are interleaved the way a real session runs.
func CBusSession() []Packet {
	return []Packet{
		{Payload: []byte("~~~\r"), Direction: FromClient, Comment: "reset request"},
		{Payload: []byte("322100AD\r\n"), Direction: FromServer, Comment: "reply"},
		{Payload: []byte("@A32100FF\r"), Direction: FromClient, Comment: "direct command"},
		{Payload: []byte("3230009E\r\n"), Direction: FromServer, Comment: "reply"},
		{Payload: []byte("@A3300079\r"), Direction: FromClient, Comment: "direct command"},
		{Payload: []byte("0531AC0079042F0401430316000011\r\n"), Direction: FromServer, Comment: "monitored SAL"},
		{Payload: []byte("86FD0201078900434C495053414C20C2\r\n"), Direction: FromServer, Comment: "identify reply"},
		{Payload: []byte("g.890050435F434E49454422\r\n"), Direction: FromServer, Comment: "MMI"},
	}
}

// CBusSessionWithFailures returns the C-Bus session plus payloads the codec will not accept.
// It exists so tests can assert the analyzer's parse-failure counter, which is otherwise only
// reachable with a real broken capture.
func CBusSessionWithFailures() []Packet {
	return append(CBusSession(),
		// Two payloads the codec will not accept, and deliberately not in the same way: the
		// first is a parse failure, which is a defect and therefore a finding, and the second
		// the codec rejects as not being a whole message at all, which is a skip and not a
		// finding. A fixture with only one of the two cannot exercise the difference, and the
		// difference is what the verdict column is for.
		Packet{Payload: []byte("AFFE!!!\r"), Direction: FromClient, Comment: "parse failure"},
		Packet{Payload: []byte("@A62120\r"), Direction: FromClient, Comment: "skipped: not a whole message"},
	)
}

// BacnetSession returns a BACnet/IP exchange whose every packet parses and re-serializes to
// identical bytes.
//
// Note on filtering: the CLI's default BACnet filter is "udp port 47808 and udp[4:2] > 29",
// which drops packets whose UDP length is 29 bytes or less. A bare Who-Is is 12 bytes of BVLC
// and is therefore dropped by that filter. The longer payloads here survive it, and a test
// that wants the short ones has to disable filtering.
func BacnetSession() []Packet {
	return []Packet{
		{Payload: mustHex("810b00180120ffff00ff1000c40200271f2201e09100210f"), Direction: FromServer, Comment: "I-Am broadcast"},
		{Payload: mustHex("810a001101040275010c0c020000271955"), Direction: FromClient, Comment: "ReadProperty request"},
		{Payload: mustHex("810b000c0120ffff00ff1008"), Direction: FromClient, Comment: "Who-Is broadcast (short: needs no filter)"},
		{Payload: mustHex("810a000801001008"), Direction: FromClient, Comment: "Who-Is unicast (short: needs no filter)"},
	}
}

// WriteCBus writes packets to path as a C-Bus capture: Ethernet/IPv4/TCP on CBusPort.
func WriteCBus(path string, packets []Packet) error {
	return write(path, packets, func(p Packet, ip *layers.IPv4) (gopacket.SerializableLayer, error) {
		src, dst := layers.TCPPort(40000), layers.TCPPort(CBusPort)
		if p.Direction == FromServer {
			src, dst = dst, src
		}
		tcp := &layers.TCP{SrcPort: src, DstPort: dst, Seq: 1, Window: 4096, PSH: true, ACK: true}
		if err := tcp.SetNetworkLayerForChecksum(ip); err != nil {
			return nil, errors.Wrap(err, "error preparing tcp checksum")
		}
		return tcp, nil
	})
}

// WriteBacnet writes packets to path as a BACnet/IP capture: Ethernet/IPv4/UDP on BacnetPort.
func WriteBacnet(path string, packets []Packet) error {
	return write(path, packets, func(p Packet, ip *layers.IPv4) (gopacket.SerializableLayer, error) {
		udp := &layers.UDP{SrcPort: layers.UDPPort(BacnetPort), DstPort: layers.UDPPort(BacnetPort)}
		if err := udp.SetNetworkLayerForChecksum(ip); err != nil {
			return nil, errors.Wrap(err, "error preparing udp checksum")
		}
		return udp, nil
	})
}

// Write writes packets to path as a capture of a protocol living on the given wire.
//
// One writer for every protocol, because the transport and the port are the only things that
// differ between them -- and getting the port wrong makes a fixture the analyzer's own default
// filter throws away, which reads as "the codec parsed nothing" rather than as a broken test.
func Write(path string, wire Wire, packets []Packet) error {
	return write(path, packets, func(p Packet, ip *layers.IPv4) (gopacket.SerializableLayer, error) {
		if wire.Transport == UDP {
			udp := &layers.UDP{
				SrcPort: layers.UDPPort(wire.Port),
				DstPort: layers.UDPPort(wire.Port),
			}
			if err := udp.SetNetworkLayerForChecksum(ip); err != nil {
				return nil, errors.Wrap(err, "error preparing udp checksum")
			}
			return udp, nil
		}
		src, dst := layers.TCPPort(ClientPort), layers.TCPPort(wire.Port)
		if p.Direction == FromServer {
			src, dst = dst, src
		}
		tcp := &layers.TCP{SrcPort: src, DstPort: dst, Seq: 1, Window: 4096, PSH: true, ACK: true}
		if err := tcp.SetNetworkLayerForChecksum(ip); err != nil {
			return nil, errors.Wrap(err, "error preparing tcp checksum")
		}
		return tcp, nil
	})
}

// write assembles each packet and writes the capture. transportFor supplies the transport
// layer, which is the only part that differs between the protocols.
func write(path string, packets []Packet, transportFor func(Packet, *layers.IPv4) (gopacket.SerializableLayer, error)) error {
	if len(packets) == 0 {
		return errors.New("refusing to write a capture with no packets")
	}
	file, err := os.Create(path)
	if err != nil {
		return errors.Wrapf(err, "error creating capture %s", path)
	}
	defer func() { _ = file.Close() }()

	writer := pcapgo.NewWriter(file)
	// snaplen 65536 matches what a default libpcap capture would record.
	if err := writer.WriteFileHeader(65536, layers.LinkTypeEthernet); err != nil {
		return errors.Wrap(err, "error writing pcap file header")
	}

	for i, packet := range packets {
		raw, err := serialize(packet, transportFor)
		if err != nil {
			return errors.Wrapf(err, "error serializing packet %d (%s)", i+1, packet.Comment)
		}
		info := gopacket.CaptureInfo{
			Timestamp:     BaseTimestamp.Add(time.Duration(i) * PacketInterval),
			CaptureLength: len(raw),
			Length:        len(raw),
		}
		if err := writer.WritePacket(info, raw); err != nil {
			return errors.Wrapf(err, "error writing packet %d", i+1)
		}
	}
	return nil
}

// serialize builds the full Ethernet frame for one packet.
func serialize(packet Packet, transportFor func(Packet, *layers.IPv4) (gopacket.SerializableLayer, error)) ([]byte, error) {
	clientMAC := net.HardwareAddr{0x00, 0x11, 0x22, 0x33, 0x44, 0x55}
	serverMAC := net.HardwareAddr{0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb}
	srcMAC, dstMAC := clientMAC, serverMAC
	srcIP, dstIP := net.ParseIP(ClientIP), net.ParseIP(ServerIP)
	if packet.Direction == FromServer {
		srcMAC, dstMAC = serverMAC, clientMAC
		srcIP, dstIP = dstIP, srcIP
	}

	ethernet := &layers.Ethernet{SrcMAC: srcMAC, DstMAC: dstMAC, EthernetType: layers.EthernetTypeIPv4}
	ip := &layers.IPv4{
		Version:  4,
		IHL:      5,
		TTL:      64,
		SrcIP:    srcIP,
		DstIP:    dstIP,
		Protocol: layers.IPProtocolTCP,
	}
	transport, err := transportFor(packet, ip)
	if err != nil {
		return nil, err
	}
	if _, isUDP := transport.(*layers.UDP); isUDP {
		ip.Protocol = layers.IPProtocolUDP
	}

	buffer := gopacket.NewSerializeBuffer()
	options := gopacket.SerializeOptions{FixLengths: true, ComputeChecksums: true}
	if err := gopacket.SerializeLayers(buffer, options, ethernet, ip, transport, gopacket.Payload(packet.Payload)); err != nil {
		return nil, errors.Wrap(err, "error serializing layers")
	}
	return buffer.Bytes(), nil
}

// mustHex decodes a hex payload defined in this file. The inputs are compile-time constants
// that are covered by this package's tests, so a decode failure is a programming error.
func mustHex(s string) []byte {
	decoded, err := hex.DecodeString(s)
	if err != nil {
		panic("pcapfixture: invalid hex literal " + s + ": " + err.Error())
	}
	return decoded
}
