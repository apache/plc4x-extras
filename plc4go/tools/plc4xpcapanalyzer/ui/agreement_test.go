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

package ui_test

import (
	"context"
	"io"
	"path/filepath"
	"testing"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	cliConfig "github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/ui"
)

// There are two implementations of the same analysis: internal/analyzer walks a capture once
// and the terminal interface walks it in three phases so it can report progress and stream
// records. They exist separately for a reason the interface documents -- bytes cannot be
// recovered from a log line -- and they are now expected to reach the SAME verdicts, because
// they classify, compare and count through internal/finding rather than each deciding for
// itself.
//
// This is the test that expectation earns. Before the shared package there were two taxonomies
// and nothing could compare them, which is how the documented description of the demo came to
// disagree with what the tool actually reported.

// TestBothAnalysisPathsAgree runs the same capture through both and compares verdict by verdict.
func TestBothAnalysisPathsAgree(t *testing.T) {
	capture := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, pcapfixture.WriteCBus(capture, pcapfixture.CBusSessionWithFailures()))

	fromAnalyzer, analyzerCounters := analyseWithTheAnalyzer(t, capture)
	fromInterface := analyseWithTheInterface(t, capture)

	require.NotEmpty(t, fromAnalyzer, "the analyzer has to report something to compare")
	require.Equal(t, len(fromAnalyzer), len(fromInterface),
		"the two paths have to examine the same number of packets")

	for i := range fromAnalyzer {
		mine, theirs := fromAnalyzer[i], fromInterface[i]
		assert.Equal(t, mine.Number, theirs.Number, "packet %d: numbering", i)
		assert.Equal(t, mine.Verdict, theirs.Verdict,
			"packet %d (no. %d): the analyzer says %s and the interface says %s",
			i, mine.Number, mine.Verdict, theirs.Verdict)
		assert.Equal(t, mine.Summary, theirs.Summary, "packet %d: message name", i)
		assert.Equal(t, mine.DiffOffset, theirs.DiffOffset, "packet %d: first difference", i)
		assert.Equal(t, mine.Original, theirs.Original, "packet %d: captured payload", i)
		assert.Equal(t, mine.Reserialized, theirs.Reserialized, "packet %d: reserialized payload", i)
	}

	// And the totals, which used to be reached by two different rules.
	interfaceCounters := finding.Counters{}
	for _, found := range fromInterface {
		interfaceCounters.Count(found.Verdict)
	}
	assert.Equal(t, analyzerCounters.Walked, interfaceCounters.Walked)
	assert.Equal(t, analyzerCounters.Parsed, interfaceCounters.Parsed)
	assert.Equal(t, analyzerCounters.ParseFail, interfaceCounters.ParseFail)
	assert.Equal(t, analyzerCounters.SerializeFail, interfaceCounters.SerializeFail)
	assert.Equal(t, analyzerCounters.CompareFail, interfaceCounters.CompareFail)
	assert.Equal(t, analyzerCounters.Skipped, interfaceCounters.Skipped)
	assert.Equal(t, analyzerCounters.Issues(), interfaceCounters.Issues())
}

// analyseWithTheAnalyzer runs the command line's path and collects what it found.
func analyseWithTheAnalyzer(t *testing.T, capture string) ([]finding.Finding, finding.Counters) {
	t.Helper()
	pinAnalyzerGlobals(t)
	cliConfig.AnalyzeConfigInstance.Client = pcapfixture.ClientIP

	var found []finding.Finding
	var counters finding.Counters
	err := analyzer.AnalyzeWithOptions(context.Background(), capture, protocol.CBus.Name, analyzer.Options{
		OnFinding:  func(one finding.Finding) { found = append(found, one) },
		OnCounters: func(all finding.Counters) { counters = all },
	})
	require.NoError(t, err)
	return found, counters
}

// analyseWithTheInterface runs the terminal interface's path over the same capture, configured
// the same way, and reduces its records to the shared facts.
func analyseWithTheInterface(t *testing.T, capture string) []finding.Finding {
	t.Helper()
	pinAnalyzerGlobals(t)

	result := ui.Analyze(context.Background(), ui.Request{
		Protocol:    protocol.CBus,
		PcapFile:    capture,
		Client:      pcapfixture.ClientIP,
		PacketLimit: ^uint(0),
	}, ui.Sink{})
	require.NoError(t, result.Err)

	found := make([]finding.Finding, 0, len(result.Records))
	for _, record := range result.Records {
		found = append(found, record.Finding)
	}
	return found
}

// pinAnalyzerGlobals saves and restores the configuration singletons both paths read, and
// quiets the logger the analyzer reports through.
func pinAnalyzerGlobals(t *testing.T) {
	t.Helper()
	saved := struct {
		root    cliConfig.RootConfig
		pcap    cliConfig.PcapConfig
		analyze cliConfig.AnalyzeConfig
		cbus    cliConfig.CBusConfig
		logger  zerolog.Logger
		level   zerolog.Level
	}{
		cliConfig.RootConfigInstance, cliConfig.PcapConfigInstance,
		cliConfig.AnalyzeConfigInstance, cliConfig.CBusConfigInstance,
		log.Logger, zerolog.GlobalLevel(),
	}
	t.Cleanup(func() {
		cliConfig.RootConfigInstance = saved.root
		cliConfig.PcapConfigInstance = saved.pcap
		cliConfig.AnalyzeConfigInstance = saved.analyze
		cliConfig.CBusConfigInstance = saved.cbus
		log.Logger = saved.logger
		zerolog.SetGlobalLevel(saved.level)
	})

	cliConfig.RootConfigInstance.HideProgressBar = true
	// No flags are parsed here, so the limit is its zero value and the analyzer would stop
	// after the first packet.
	cliConfig.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	log.Logger = zerolog.New(io.Discard)
	zerolog.SetGlobalLevel(zerolog.Disabled)
}
