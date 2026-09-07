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

package analyzer_test

import (
	"bytes"
	"context"
	"io"
	"path/filepath"
	"slices"
	"testing"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/codec"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Every protocol, against its own reference vectors.
//
// The payloads come from each protocol's ParserSerializerTestsuite in the Apache PLC4X repo,
// which plc4x asserts round-trips. So the analyzer -- whose entire job is to measure that
// property -- has to report a capture built from them as completely clean. Anything else means
// the adapter here is wrong, or plc4x has regressed, and both are worth failing a build over.
//
// This is what makes the support real rather than merely compiled. It is also why the byte
// order is in the codec: parsed through the default big-endian buffer, not one of EtherNet/IP's
// twenty-seven vectors survives, which would have shipped as "the codec cannot parse its own
// protocol".

// protocolCase is one protocol's fixture.
type protocolCase struct {
	protocol string
	wire     pcapfixture.Wire
	packets  []pcapfixture.Packet
}

func protocolCases() []protocolCase {
	return []protocolCase{
		{protocol.AbEth.Name, pcapfixture.AbEthWire, pcapfixture.AbEthSession()},
		{protocol.Ads.Name, pcapfixture.AdsWire, pcapfixture.AdsSession()},
		{protocol.Eip.Name, pcapfixture.EipWire, pcapfixture.EipSession()},
		{protocol.Firmata.Name, pcapfixture.FirmataWire, pcapfixture.FirmataSession()},
		{protocol.KnxNetIp.Name, pcapfixture.KnxWire, pcapfixture.KnxSession()},
		{protocol.ModbusTcp.Name, pcapfixture.ModbusWire, pcapfixture.ModbusSession()},
		{protocol.ModbusAscii.Name, pcapfixture.ModbusAsciiWire, pcapfixture.ModbusAsciiSession()},
		{protocol.ModbusRtu.Name, pcapfixture.ModbusRtuWire, pcapfixture.ModbusRtuSession()},
		{protocol.S7.Name, pcapfixture.S7Wire, pcapfixture.S7Session()},
		{protocol.Slmp.Name, pcapfixture.SlmpWire, pcapfixture.SlmpSession()},
	}
}

// TestEveryProtocolRoundTripsItsOwnReferenceVectors is the test that makes the support real.
func TestEveryProtocolRoundTripsItsOwnReferenceVectors(t *testing.T) {
	for _, test := range protocolCases() {
		t.Run(test.protocol, func(t *testing.T) {
			require.NotEmpty(t, test.packets, "a protocol with no vectors proves nothing")

			capture := filepath.Join(t.TempDir(), test.protocol+".pcap")
			require.NoError(t, pcapfixture.Write(capture, test.wire, test.packets))

			findings, counters := analyseFixture(t, capture, test.protocol)

			require.Len(t, findings, len(test.packets),
				"every packet in the capture has to be examined; the default filter may be dropping them")
			assert.Equal(t, len(test.packets), counters.Walked)
			assert.Zero(t, counters.Issues(),
				"these payloads round-trip by construction, so any finding is a defect here or in plc4x")

			for i, found := range findings {
				assert.Equal(t, finding.VerdictOK, found.Verdict,
					"%s packet %d (%s): %s", test.protocol, i+1, test.packets[i].Comment, found.Reason)
				assert.NotEmpty(t, found.Summary, "a parsed message has to name its type")
				assert.Equal(t, test.packets[i].Payload, found.Original)
				assert.Equal(t, test.packets[i].Payload, found.Reserialized,
					"the whole point: what the codec wrote back has to be what it read")
			}
		})
	}
}

// TestEveryCodecIsRegisteredUnderAKnownName holds the two registries to each other. A codec
// under a name the CLI does not accept is unreachable; a name with no codec is a promise the
// analyzer cannot keep, and the switch's default says so at runtime rather than at build time.
func TestEveryCodecIsRegisteredUnderAKnownName(t *testing.T) {
	for _, name := range codec.Names() {
		resolved, err := protocol.Resolve(name)
		require.NoError(t, err, "codec %q is not a protocol the command line accepts", name)
		assert.Equal(t, name, resolved.Name, "a codec has to be keyed by the canonical name")
	}

	// And the other direction: every name the CLI offers is analysable, by a codec or by one of
	// the two adapters that predate this package.
	adapters := []string{protocol.BacnetIP.Name, protocol.CBus.Name}
	for _, known := range protocol.All() {
		_, hasCodec := codec.For(known.Name)
		assert.True(t, hasCodec || slices.Contains(adapters, known.Name),
			"%s is offered by the command line but nothing can analyse it", known.Name)
	}
}

// TestEveryProtocolCaseIsCovered guards the fixture list against drifting from the registry --
// a protocol added to the codec registry without vectors would be untested support, which is
// the thing this whole exercise exists to avoid.
func TestEveryProtocolCaseIsCovered(t *testing.T) {
	covered := make([]string, 0, len(protocolCases()))
	for _, test := range protocolCases() {
		covered = append(covered, test.protocol)
	}
	for _, name := range codec.Names() {
		assert.Contains(t, covered, name,
			"%s has a codec but no reference vectors, so nothing shows it works", name)
	}
}

// analyseFixture runs the analyzer over a capture and returns what it found.
func analyseFixture(t *testing.T, capture, protocolName string) ([]finding.Finding, finding.Counters) {
	t.Helper()

	savedRoot, savedPcap := config.RootConfigInstance, config.PcapConfigInstance
	savedAnalyze := config.AnalyzeConfigInstance
	savedLogger, savedLevel := log.Logger, zerolog.GlobalLevel()
	t.Cleanup(func() {
		config.RootConfigInstance, config.PcapConfigInstance = savedRoot, savedPcap
		config.AnalyzeConfigInstance = savedAnalyze
		log.Logger = savedLogger
		zerolog.SetGlobalLevel(savedLevel)
	})

	config.RootConfigInstance.HideProgressBar = true
	// The vectors' own client address, so the protocols that encode the two directions
	// differently are read the right way round.
	config.AnalyzeConfigInstance.Client = pcapfixture.ClientIP
	// No flags are parsed here, so the limit is its zero value and the loop would stop after
	// the first packet.
	config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	log.Logger = zerolog.New(io.Discard)
	zerolog.SetGlobalLevel(zerolog.Disabled)

	var findings []finding.Finding
	var counters finding.Counters
	err := analyzer.AnalyzeWithOptions(context.Background(), capture, protocolName, analyzer.Options{
		OnFinding:  func(one finding.Finding) { findings = append(findings, one) },
		OnCounters: func(all finding.Counters) { counters = all },
	})
	require.NoError(t, err)
	return findings, counters
}

// TestTheUsualPortNeedsNoClientAddress is the headline of how direction is decided. A packet
// leaving the protocol's registered port came from the device; one arriving at it came from the
// client. That is how Wireshark decides, it is right per packet, and it asks the user nothing.
//
// It replaced being told the client's address, which was a footgun: pointed at a real Modbus
// capture without -c, the tool read every response as a request and reported half the file as
// parse failures.
func TestTheUsualPortNeedsNoClientAddress(t *testing.T) {
	capture := filepath.Join(t.TempDir(), "modbus.pcap")
	require.NoError(t, pcapfixture.Write(capture, pcapfixture.ModbusWire, pcapfixture.ModbusSession()))

	stderr := &bytes.Buffer{}
	findings, counters := analyseWithStderr(t, capture, protocol.ModbusTcp.Name, "", stderr, false)

	assert.Empty(t, stderr.String(), "nothing to advise: the port settled every packet")
	assert.Zero(t, counters.Issues(),
		"the responses have to have been read as responses, or they would all have failed")
	require.Len(t, findings, len(pcapfixture.ModbusSession()))
	for i, found := range findings {
		assert.Equal(t, finding.VerdictOK, found.Verdict, "packet %d: %s", i+1, found.Reason)
	}
}

// TestAnUnusualPortFallsBackAndSaysSo is the other half. A capture taken somewhere the protocol
// does not normally live -- a tunnel, a gateway -- tells the port nothing, so the client address
// is needed and its absence has to be announced rather than logged: the default log level is
// "error", so a log line was invisible in exactly the situation it exists for.
func TestAnUnusualPortFallsBackAndSaysSo(t *testing.T) {
	// The same payloads, somewhere Modbus does not live.
	tunnelled := pcapfixture.Wire{Transport: pcapfixture.TCP, Port: 15020}
	capture := filepath.Join(t.TempDir(), "tunnelled.pcap")
	require.NoError(t, pcapfixture.Write(capture, tunnelled, pcapfixture.ModbusSession()))

	t.Run("without a client address", func(t *testing.T) {
		stderr := &bytes.Buffer{}
		_, counters := analyseWithStderr(t, capture, protocol.ModbusTcp.Name, "", stderr, true)

		assert.Contains(t, stderr.String(), "encodes requests and responses differently")
		assert.Contains(t, stderr.String(), "-c <client ip>", "it has to say what to do")
		assert.Positive(t, counters.Issues(),
			"and the failures it is explaining have to actually be there")
	})

	t.Run("with a client address", func(t *testing.T) {
		stderr := &bytes.Buffer{}
		_, counters := analyseWithStderr(t, capture, protocol.ModbusTcp.Name, pcapfixture.ClientIP, stderr, true)

		assert.Empty(t, stderr.String(), "the address answered the question, so there is nothing to say")
		assert.Zero(t, counters.Issues(), "and it answered it correctly")
	})
}

// TestADirectionlessProtocolSaysNothing keeps the notice from becoming noise: KNXNet/IP and the
// rest do not care which way a packet went, so telling their users about -c would be wrong.
func TestADirectionlessProtocolSaysNothing(t *testing.T) {
	tunnelled := pcapfixture.Wire{Transport: pcapfixture.UDP, Port: 13671}
	capture := filepath.Join(t.TempDir(), "knx.pcap")
	require.NoError(t, pcapfixture.Write(capture, tunnelled, pcapfixture.KnxSession()))

	stderr := &bytes.Buffer{}
	analyseWithStderr(t, capture, protocol.KnxNetIp.Name, "", stderr, true)
	assert.Empty(t, stderr.String(), "KNXNet/IP needs no direction, so there is nothing to say")
}

// analyseWithStderr runs the analyzer with the given client address and captures its stderr.
func analyseWithStderr(t *testing.T, capture, protocolName, client string, stderr io.Writer,
	noFilter bool) ([]finding.Finding, finding.Counters) {
	t.Helper()

	savedRoot, savedPcap := config.RootConfigInstance, config.PcapConfigInstance
	savedAnalyze := config.AnalyzeConfigInstance
	savedLogger, savedLevel := log.Logger, zerolog.GlobalLevel()
	t.Cleanup(func() {
		config.RootConfigInstance, config.PcapConfigInstance = savedRoot, savedPcap
		config.AnalyzeConfigInstance = savedAnalyze
		log.Logger = savedLogger
		zerolog.SetGlobalLevel(savedLevel)
	})
	config.RootConfigInstance.HideProgressBar = true
	config.AnalyzeConfigInstance.Client = client
	config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	// A capture written somewhere the protocol does not live would be thrown away by the
	// protocol's own default filter, which is not what these tests are about.
	config.AnalyzeConfigInstance.NoFilter = noFilter
	log.Logger = zerolog.New(io.Discard)
	zerolog.SetGlobalLevel(zerolog.Disabled)

	var findings []finding.Finding
	var counters finding.Counters
	require.NoError(t, analyzer.AnalyzeWithOptions(context.Background(), capture, protocolName,
		analyzer.Options{
			Stderr:     stderr,
			OnFinding:  func(one finding.Finding) { findings = append(findings, one) },
			OnCounters: func(all finding.Counters) { counters = all },
		}))
	return findings, counters
}

// TestModbusExceptionResponsesRoundTrip pins a plc4x fix that this test used to record as a defect.
//
// Modbus flags an error by setting the top bit of the original function code, so 0x03 becomes 0x83
// and 0x08 becomes 0x88. plc4x used to write 0x80 back whatever went in: the typeSwitch case for
// ModbusPDUError matches on the error bit alone, which left the code generator with no constant for
// the function code, so it emitted a zero. Its own parser read the function correctly and its own
// serializer then discarded it. Fixed upstream in the generator, which now retains a discriminator
// value that no sub type pins.
//
// The exchange stays here as a regression vector rather than being deleted with the defect, because
// these are real exception responses and nothing else in the suite covers them. A failure here means
// either the fix was lost in a plc4go bump or the analyzer has started reporting clean traffic.
func TestModbusExceptionResponsesRoundTrip(t *testing.T) {
	packets := pcapfixture.ModbusExceptionSession()
	capture := filepath.Join(t.TempDir(), "exceptions.pcap")
	require.NoError(t, pcapfixture.Write(capture, pcapfixture.ModbusWire, packets))

	findings, counters := analyseFixture(t, capture, protocol.ModbusTcp.Name)
	require.Len(t, findings, len(packets))
	assert.Equal(t, 0, counters.Issues())

	exceptions := 0
	for _, found := range findings {
		assert.Equal(t, finding.VerdictOK, found.Verdict,
			"packet %d: %s", found.Number, found.Reason)
		require.Equal(t, found.Original, found.Reserialized, "packet %d", found.Number)

		// The two responses are the point: the error bit and the function code both survive,
		// where the function code used to be replaced by a zero.
		if len(found.Original) > 7 && found.Original[7]&0x80 != 0 {
			exceptions++
			assert.NotZero(t, found.Original[7]&0x7f, "the vector has to carry a function code")
			assert.Equal(t, found.Original[7], found.Reserialized[7],
				"the function code that failed has to survive re-serialization")
		}
	}
	require.Equal(t, 2, exceptions, "the fixture has to still contain both exception responses")
}
