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

package codec

import (
	"net"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
)

// How a packet's direction is decided. It matters more than it sounds: a protocol that encodes
// requests and responses differently, read in the wrong direction, is not read at all -- every
// response comes back as a parse failure, which is what a real Modbus capture did before this.

// TestThePortDecidesFirst is the mechanism, and the reason nothing has to be configured. A
// packet leaving the protocol's registered port came from the device; one arriving at it came
// from the client. That is per-packet and needs nothing from the user.
func TestThePortDecidesFirst(t *testing.T) {
	modbus, ok := For("modbus-tcp")
	require.True(t, ok)
	require.Equal(t, 502, modbus.ServerPort)

	device := net.ParseIP("10.0.0.2")
	client := net.ParseIP("10.0.0.1")

	toDevice := common.PacketInformation{SrcIp: client, DstIp: device, SrcPort: 40000, DstPort: 502}
	fromDevice := common.PacketInformation{SrcIp: device, DstIp: client, SrcPort: 502, DstPort: 40000}

	response, known := modbus.IsResponse(toDevice, nil)
	assert.False(t, response, "a packet arriving at the device's port is a request")
	assert.True(t, known)

	response, known = modbus.IsResponse(fromDevice, nil)
	assert.True(t, response, "a packet leaving the device's port is a response")
	assert.True(t, known)

	// And the port wins over a client address that says otherwise: the port is a fact about
	// the packet, an address given on a command line is a claim about the capture.
	response, known = modbus.IsResponse(fromDevice, device)
	assert.True(t, response, "the port is the better evidence")
	assert.True(t, known)
}

// TestTheClientAddressIsTheFallback covers a capture taken somewhere the protocol does not
// normally live -- a tunnel, a gateway -- where the port says nothing.
func TestTheClientAddressIsTheFallback(t *testing.T) {
	modbus, ok := For("modbus-tcp")
	require.True(t, ok)

	client := net.ParseIP("10.0.0.1")
	device := net.ParseIP("10.0.0.2")
	// Neither end is on 502.
	tunnelled := common.PacketInformation{SrcIp: device, DstIp: client, SrcPort: 15020, DstPort: 40000}

	response, known := modbus.IsResponse(tunnelled, client)
	assert.True(t, response, "from anyone but the client is a response")
	assert.True(t, known)

	fromClient := common.PacketInformation{SrcIp: client, DstIp: device, SrcPort: 40000, DstPort: 15020}
	response, known = modbus.IsResponse(fromClient, client)
	assert.False(t, response)
	assert.True(t, known)
}

// TestNeitherIsAdmittedRatherThanGuessedSilently is the honesty of the thing. With no port and
// no address the answer is "request", because that is what most of a capture is -- but the
// caller is told it was a guess, so it can say so out loud instead of reporting failures it
// could have explained.
func TestNeitherIsAdmittedRatherThanGuessedSilently(t *testing.T) {
	modbus, ok := For("modbus-tcp")
	require.True(t, ok)

	unknown := common.PacketInformation{SrcPort: 15020, DstPort: 40000}
	response, known := modbus.IsResponse(unknown, nil)
	assert.False(t, response, "a request is the honest default")
	assert.False(t, known, "and the caller has to be able to tell that it was a guess")
}

// TestTheFilterComesFromThePort is why the two cannot disagree. They were separate fields, and
// a filter naming one port while the direction logic used another would drop the packets it was
// meant to classify.
func TestTheFilterComesFromThePort(t *testing.T) {
	for name, want := range map[string]string{
		"modbus-tcp": "tcp port 502",
		"s7":         "tcp port 102",
		"eip":        "tcp port 44818",
		"knxnet-ip":  "udp port 3671",
		"ads":        "tcp port 48898",
		"ab-eth":     "tcp port 2222",
		"slmp":       "tcp port 5007",
	} {
		found, ok := For(name)
		require.True(t, ok, name)
		assert.Equal(t, want, found.DefaultFilter(), name)
	}

	// A codec with nowhere to live has no filter rather than a malformed one.
	assert.Empty(t, Codec{}.DefaultFilter())
}

// TestEveryCodecCanBeFilteredAndDirected guards the registry: a codec with no port has no
// default filter and no way to decide direction, which would make it quietly worse than the
// others rather than visibly unfinished.
func TestEveryCodecCanBeFilteredAndDirected(t *testing.T) {
	for _, name := range Names() {
		found, ok := For(name)
		require.True(t, ok, name)
		assert.NotZero(t, found.ServerPort, "%s needs a port, for its filter and its direction", name)
		assert.Contains(t, []string{"tcp", "udp"}, found.Transport, "%s", name)
		assert.NotEmpty(t, found.DefaultFilter(), "%s", name)
		assert.NotNil(t, found.ByteOrder, "%s needs a byte order; EtherNet/IP proves it matters", name)
	}
}
