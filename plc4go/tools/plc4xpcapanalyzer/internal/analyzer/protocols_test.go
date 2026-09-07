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
