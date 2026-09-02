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

package pcapfixture

import (
	"bytes"
	"context"
	"os"
	"path/filepath"
	"testing"

	bacnetModel "github.com/apache/plc4x/plc4go/protocols/bacnetip/readwrite/model"
	cbusModel "github.com/apache/plc4x/plc4go/protocols/cbus/readwrite/model"
	"github.com/gopacket/gopacket"
	"github.com/gopacket/gopacket/layers"
	"github.com/gopacket/gopacket/pcapgo"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// readBack reads a written capture with the pure-Go reader, so this test needs no cgo, and
// returns the application payloads in capture order.
func readBack(t *testing.T, path string) []gopacket.Packet {
	t.Helper()
	file, err := os.Open(path)
	require.NoError(t, err)
	t.Cleanup(func() { _ = file.Close() })

	reader, err := pcapgo.NewReader(file)
	require.NoError(t, err)

	var out []gopacket.Packet
	for packet := range gopacket.NewPacketSource(reader, layers.LinkTypeEthernet).Packets() {
		out = append(out, packet)
	}
	return out
}

// TestCBusFixturePayloadsSurviveTheCapture is the guard that the capture writer does not
// corrupt or reorder what the analyzer will later parse.
func TestCBusFixturePayloadsSurviveTheCapture(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cbus.pcap")
	want := CBusSession()
	require.NoError(t, WriteCBus(path, want))

	got := readBack(t, path)
	require.Len(t, got, len(want), "every fixture packet must reach the capture")

	for i, packet := range got {
		app := packet.ApplicationLayer()
		require.NotNil(t, app, "packet %d (%s) must carry an application layer", i+1, want[i].Comment)
		assert.Equal(t, want[i].Payload, app.Payload(),
			"packet %d (%s) payload must round-trip through the capture", i+1, want[i].Comment)
	}
}

func TestBacnetFixturePayloadsSurviveTheCapture(t *testing.T) {
	path := filepath.Join(t.TempDir(), "bacnet.pcap")
	want := BacnetSession()
	require.NoError(t, WriteBacnet(path, want))

	got := readBack(t, path)
	require.Len(t, got, len(want))
	for i, packet := range got {
		app := packet.ApplicationLayer()
		require.NotNil(t, app, "packet %d (%s)", i+1, want[i].Comment)
		assert.Equal(t, want[i].Payload, app.Payload(), "packet %d (%s)", i+1, want[i].Comment)
	}
}

// TestCBusFixtureDirectionsMapToAddresses pins the property the c-bus analyzer depends on to
// tell a request from a response: it compares the packet's source IP against the configured
// client address.
func TestCBusFixtureDirectionsMapToAddresses(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cbus.pcap")
	want := CBusSession()
	require.NoError(t, WriteCBus(path, want))

	for i, packet := range readBack(t, path) {
		ip, ok := packet.NetworkLayer().(*layers.IPv4)
		require.True(t, ok, "packet %d must have an IPv4 layer", i+1)
		if want[i].Direction == FromClient {
			assert.Equal(t, ClientIP, ip.SrcIP.String(), "packet %d is a request", i+1)
			assert.Equal(t, ServerIP, ip.DstIP.String(), "packet %d is a request", i+1)
		} else {
			assert.Equal(t, ServerIP, ip.SrcIP.String(), "packet %d is a response", i+1)
			assert.Equal(t, ClientIP, ip.DstIP.String(), "packet %d is a response", i+1)
		}
	}
}

func TestCBusFixtureUsesTcpOnTheCBusPort(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, WriteCBus(path, CBusSession()))
	for i, packet := range readBack(t, path) {
		tcp, ok := packet.TransportLayer().(*layers.TCP)
		require.True(t, ok, "packet %d must be TCP", i+1)
		assert.True(t, tcp.SrcPort == CBusPort || tcp.DstPort == CBusPort,
			"packet %d must involve the c-bus port, got %v->%v", i+1, tcp.SrcPort, tcp.DstPort)
	}
}

func TestBacnetFixtureUsesUdpOnTheBacnetPort(t *testing.T) {
	path := filepath.Join(t.TempDir(), "bacnet.pcap")
	require.NoError(t, WriteBacnet(path, BacnetSession()))
	for i, packet := range readBack(t, path) {
		udp, ok := packet.TransportLayer().(*layers.UDP)
		require.True(t, ok, "packet %d must be UDP", i+1)
		assert.EqualValues(t, BacnetPort, uint16(udp.DstPort), "packet %d", i+1)
	}
}

// TestCBusSessionPayloadsRoundTripThroughTheRealCodec is the heart of the fixture's value: it
// drives plc4x's actual C-Bus codec with the fixture bytes and asserts every payload parses
// and re-serializes byte-identically. If a plc4x upgrade changes C-Bus encoding, this fails.
func TestCBusSessionPayloadsRoundTripThroughTheRealCodec(t *testing.T) {
	for i, packet := range CBusSession() {
		t.Run(packet.Comment, func(t *testing.T) {
			isResponse := packet.Direction == FromServer
			// Responses carry a checksum, which is what the srchk option selects.
			options := cbusModel.NewCBusOptions(false, false, false, false, false, false, false, false, isResponse)
			parsed, err := cbusModel.CBusMessageParse[cbusModel.CBusMessage](
				context.Background(), packet.Payload, isResponse, cbusModel.NewRequestContext(false), options)
			require.NoError(t, err, "fixture packet %d (%q) must parse", i+1, packet.Payload)

			serialized, err := parsed.Serialize()
			require.NoError(t, err)
			assert.True(t, bytes.Equal(packet.Payload, serialized),
				"fixture packet %d must re-serialize identically\n got: %q\nwant: %q", i+1, serialized, packet.Payload)
		})
	}
}

// TestBacnetSessionPayloadsRoundTripThroughTheRealCodec is the BACnet counterpart.
func TestBacnetSessionPayloadsRoundTripThroughTheRealCodec(t *testing.T) {
	for i, packet := range BacnetSession() {
		t.Run(packet.Comment, func(t *testing.T) {
			parsed, err := bacnetModel.BVLCParse[bacnetModel.BVLC](context.Background(), packet.Payload)
			require.NoError(t, err, "fixture packet %d (%x) must parse", i+1, packet.Payload)

			serialized, err := parsed.Serialize()
			require.NoError(t, err)
			assert.True(t, bytes.Equal(packet.Payload, serialized),
				"fixture packet %d must re-serialize identically\n got: %x\nwant: %x", i+1, serialized, packet.Payload)
		})
	}
}

// TestCBusFailurePayloadsDoNotParse pins the negatives. The analyzer's parse-failure counter is
// only meaningful if these really do fail, so if a plc4x upgrade starts accepting them the
// failure-path tests built on this fixture would silently stop testing anything.
func TestCBusFailurePayloadsDoNotParse(t *testing.T) {
	session := CBusSession()
	withFailures := CBusSessionWithFailures()
	require.Greater(t, len(withFailures), len(session), "the failure fixture must add packets")

	for _, packet := range withFailures[len(session):] {
		t.Run(string(packet.Payload), func(t *testing.T) {
			options := cbusModel.NewCBusOptions(false, false, false, false, false, false, false, false, false)
			_, err := cbusModel.CBusMessageParse[cbusModel.CBusMessage](
				context.Background(), packet.Payload, false, cbusModel.NewRequestContext(false), options)
			assert.Error(t, err, "payload %q is a fixture negative and must not parse", packet.Payload)
		})
	}
}

// TestCapturesAreByteIdenticalAcrossRuns is what makes golden-file testing viable: the writer
// must not embed a wall-clock timestamp.
func TestCapturesAreByteIdenticalAcrossRuns(t *testing.T) {
	dir := t.TempDir()
	first, second := filepath.Join(dir, "a.pcap"), filepath.Join(dir, "b.pcap")
	require.NoError(t, WriteCBus(first, CBusSession()))
	require.NoError(t, WriteCBus(second, CBusSession()))

	a, err := os.ReadFile(first)
	require.NoError(t, err)
	b, err := os.ReadFile(second)
	require.NoError(t, err)
	assert.True(t, bytes.Equal(a, b), "two writes of the same fixture must produce identical bytes")
}

// TestEveryPacketHasADistinctTimestamp matters because the analyzer indexes packets by
// timestamp; duplicates would collapse its packet numbering.
func TestEveryPacketHasADistinctTimestamp(t *testing.T) {
	path := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, WriteCBus(path, CBusSession()))

	seen := map[int64]bool{}
	for i, packet := range readBack(t, path) {
		ts := packet.Metadata().Timestamp.UnixNano()
		assert.False(t, seen[ts], "packet %d reuses timestamp %d", i+1, ts)
		seen[ts] = true
	}
}

func TestWriteRejectsAnEmptyCapture(t *testing.T) {
	err := WriteCBus(filepath.Join(t.TempDir(), "empty.pcap"), nil)
	assert.Error(t, err, "an empty capture is a programming mistake, not a valid fixture")
}
