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

// Package codec is the parse-and-re-serialize pair for each protocol the analyzer understands.
//
// It exists so that supporting a protocol is a data change rather than another branch. The
// analyzer used to name its two protocols in a switch, and the comment on that switch's default
// said as much: adding one to the name registry without adding a branch had to fail loudly,
// because there was nowhere else for the knowledge to live.
//
// Three things vary between protocols and all three are mechanical, which is why this works:
// the parse function, the byte order its buffers use, and the BPF filter that selects its
// packets. Two protocols need more than that -- C-Bus tracks request context across a session
// and BACnet has an adapter of its own -- and those stay where they are rather than being bent
// into this shape.
package codec

import (
	"context"
	"encoding/binary"
	"net"
	"strconv"

	abethModel "github.com/apache/plc4x/plc4go/protocols/abeth/readwrite/model"
	adsModel "github.com/apache/plc4x/plc4go/protocols/ads/readwrite/model"
	eipModel "github.com/apache/plc4x/plc4go/protocols/eip/readwrite/model"
	firmataModel "github.com/apache/plc4x/plc4go/protocols/firmata/readwrite/model"
	knxModel "github.com/apache/plc4x/plc4go/protocols/knxnetip/readwrite/model"
	modbusModel "github.com/apache/plc4x/plc4go/protocols/modbus/readwrite/model"
	s7Model "github.com/apache/plc4x/plc4go/protocols/s7/readwrite/model"
	slmpModel "github.com/apache/plc4x/plc4go/protocols/slmp/readwrite/model"
	"github.com/apache/plc4x/plc4go/spi"
	"github.com/apache/plc4x/plc4go/spi/utils"
	"github.com/pkg/errors"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
)

// parseFunc reads one message from a buffer. response says the payload travelled from the
// device to the client, which several protocols need because they encode the two directions
// differently -- and which is why the analyzer wants a client address.
type parseFunc func(ctx context.Context, buffer utils.ReadBufferByteBased, response bool) (spi.Message, error)

// Codec is what the analyzer needs in order to examine one protocol.
type Codec struct {
	// ByteOrder is the order this protocol's buffers read and write in.
	//
	// It is not decoration. EtherNet/IP is little-endian, and parsed through the default
	// big-endian buffer not one of Apache's twenty-seven reference vectors survives the round
	// trip -- they fail at the first field, which reads as a codec that cannot parse its own
	// protocol rather than as a buffer configured wrongly.
	ByteOrder binary.ByteOrder
	// Transport and ServerPort are where the protocol lives: the device listens on ServerPort.
	//
	// They do two jobs. They generate the default filter, so the filter and the port cannot
	// disagree. And they settle the direction of a packet without asking anyone: one arriving
	// at ServerPort is a request and one leaving it is a response, which is how Wireshark
	// decides. Being told the client's address is the fallback, not the mechanism.
	Transport  string
	ServerPort int
	// NeedsDirection says the parse depends on which way the packet travelled. Such a protocol
	// read in the wrong direction is not read at all: every response looks like a parse failure.
	NeedsDirection bool

	parse parseFunc
}

// DefaultFilter selects this protocol's packets when the user gives no filter of their own.
func (c Codec) DefaultFilter() string {
	if c.Transport == "" || c.ServerPort == 0 {
		return ""
	}
	return c.Transport + " port " + strconv.Itoa(c.ServerPort)
}

// IsResponse says whether a packet travelled from the device to the client, and whether that
// could be established at all.
//
// The port first, because it needs nothing from the user and is right per packet: a packet
// leaving the protocol's registered port came from the device. The client address second, for a
// capture taken somewhere else -- a tunnel, a non-standard port -- where the port says nothing.
// When neither settles it the answer is "request", because that is what most of a capture is and
// what the protocols' own reference vectors mostly are, and the caller is told it was a guess so
// it can say so out loud.
//
// Deliberately not attempted: parsing both ways and keeping whichever succeeds. A payload can
// parse validly as both a request and a response, so that would silently pick one and report a
// confident round trip for a message it had read wrongly. In a tool whose only output is whether
// something round-trips, a plausible wrong answer is worse than a failure.
func (c Codec) IsResponse(info common.PacketInformation, client net.IP) (response, known bool) {
	if c.ServerPort != 0 {
		switch {
		case info.SrcPort == c.ServerPort:
			return true, true
		case info.DstPort == c.ServerPort:
			return false, true
		}
	}
	if client != nil && info.SrcIp != nil {
		return !info.SrcIp.Equal(client), true
	}
	return false, false
}

// Parse reads one message from a payload.
func (c Codec) Parse(ctx context.Context, payload []byte, response bool) (spi.Message, error) {
	buffer := utils.NewReadBufferByteBased(payload, utils.WithByteOrderForReadBufferByteBased(c.ByteOrder))
	message, err := c.parse(ctx, buffer, response)
	if err != nil {
		return nil, err
	}
	return message, nil
}

// Serialize writes a message back out, in the same byte order it was read in.
func (c Codec) Serialize(ctx context.Context, message spi.Message) ([]byte, error) {
	buffer := utils.NewWriteBufferByteBased(utils.WithByteOrderForByteBasedBuffer(c.ByteOrder))
	if err := message.SerializeWithWriteBuffer(ctx, buffer); err != nil {
		return nil, errors.Wrap(err, "error serializing")
	}
	return buffer.GetBytes(), nil
}

// codecs are the protocols this package can examine, by canonical protocol name.
//
// The filters are the protocols' registered ports. A protocol carried somewhere else -- Modbus
// RTU tunnelled over TCP, say -- needs the user's own --filter, which is why the serial ones
// are here at all: their transport is not IP, so they only ever reach a capture through a
// gateway, and only the user knows which port that gateway uses.
var codecs = map[string]Codec{
	"modbus-tcp": {
		ByteOrder: binary.BigEndian, Transport: "tcp", ServerPort: 502, NeedsDirection: true,
		parse: modbusParse(modbusModel.DriverType_MODBUS_TCP),
	},
	"modbus-rtu": {
		ByteOrder: binary.BigEndian, Transport: "tcp", ServerPort: 502, NeedsDirection: true,
		parse: modbusParse(modbusModel.DriverType_MODBUS_RTU),
	},
	"modbus-ascii": {
		ByteOrder: binary.BigEndian, Transport: "tcp", ServerPort: 502, NeedsDirection: true,
		parse: modbusParse(modbusModel.DriverType_MODBUS_ASCII),
	},
	"s7": {
		ByteOrder: binary.BigEndian, Transport: "tcp", ServerPort: 102,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, _ bool) (spi.Message, error) {
			return s7Model.TPKTPacketParseWithBuffer(ctx, buffer)
		},
	},
	"eip": {
		ByteOrder: binary.LittleEndian, Transport: "tcp", ServerPort: 44818, NeedsDirection: true,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, response bool) (spi.Message, error) {
			return eipModel.EipPacketParseWithBuffer[eipModel.EipPacket](ctx, buffer, response)
		},
	},
	"knxnet-ip": {
		ByteOrder: binary.BigEndian, Transport: "udp", ServerPort: 3671,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, _ bool) (spi.Message, error) {
			return knxModel.KnxNetIpMessageParseWithBuffer[knxModel.KnxNetIpMessage](ctx, buffer)
		},
	},
	"ads": {
		ByteOrder: binary.LittleEndian, Transport: "tcp", ServerPort: 48898,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, _ bool) (spi.Message, error) {
			return adsModel.AmsTCPPacketParseWithBuffer(ctx, buffer)
		},
	},
	"ab-eth": {
		ByteOrder: binary.BigEndian, Transport: "tcp", ServerPort: 2222,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, _ bool) (spi.Message, error) {
			return abethModel.CIPEncapsulationPacketParseWithBuffer[abethModel.CIPEncapsulationPacket](ctx, buffer)
		},
	},
	"slmp": {
		ByteOrder: binary.LittleEndian, Transport: "tcp", ServerPort: 5007,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, _ bool) (spi.Message, error) {
			return slmpModel.SlmpMessageParseWithBuffer[slmpModel.SlmpMessage](ctx, buffer)
		},
	},
	"firmata": {
		ByteOrder: binary.BigEndian, Transport: "tcp", ServerPort: 3030, NeedsDirection: true,
		parse: func(ctx context.Context, buffer utils.ReadBufferByteBased, response bool) (spi.Message, error) {
			return firmataModel.FirmataMessageParseWithBuffer[firmataModel.FirmataMessage](ctx, buffer, response)
		},
	},
}

// modbusParse is the Modbus parse for one of its three framings. The three differ only in the
// driver type they are told, which is what makes one entry serve TCP, RTU and ASCII.
func modbusParse(driver modbusModel.DriverType) parseFunc {
	return func(ctx context.Context, buffer utils.ReadBufferByteBased, response bool) (spi.Message, error) {
		return modbusModel.ModbusADUParseWithBuffer[modbusModel.ModbusADU](ctx, buffer, driver, response)
	}
}

// For returns the codec for a canonical protocol name.
func For(name string) (Codec, bool) {
	found, ok := codecs[name]
	return found, ok
}

// Names lists the protocols this package can examine, for a test that wants to hold the
// registry and the name registry to each other.
func Names() []string {
	names := make([]string, 0, len(codecs))
	for name := range codecs {
		names = append(names, name)
	}
	return names
}
