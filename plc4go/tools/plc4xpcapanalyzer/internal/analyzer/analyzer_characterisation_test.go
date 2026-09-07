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
	"encoding/json"
	"io"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// These are characterisation tests. They describe what the analyzer does TODAY, driven by real
// protocol bytes, so that the Bubble Tea port and the refactor that gives Analyze a real return
// value can be checked against the behaviour they are supposed to preserve.
//
// The counters are currently only observable as fields on one zerolog line, so that is what
// these tests read. When Analyze grows a Stats return value, these assertions should be
// re-pointed at it and must produce the same numbers.

// syncBuffer is a mutex-guarded log sink.
//
// It is guarded for a specific reason: the C-Bus analyzer's MapPackets starts a producer
// goroutine and leaks it whenever the consumer stops early, which is exactly what the
// package-number-limit test does. That orphaned goroutine keeps logging through the global
// logger, so it can write into a later test's buffer. Locking keeps that from being a data
// race; the stray lines themselves are harmless here because we select the summary line by
// content. Removing the leak is part of the analyzer refactor.
type syncBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (b *syncBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.Write(p)
}

func (b *syncBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.String()
}

// summary is the tail of the analyzer's final log line.
type summary struct {
	CurrentPackageNum int `json:"currentPackageNum"`
	NumberOfPackage   int `json:"numberOfPackage"`
	ParseFails        int `json:"parseFails"`
	SerializeFails    int `json:"serializeFails"`
	CompareFails      int `json:"compareFails"`
}

// runAnalyze runs the analyzer against a capture and returns its final counters.
//
// It has to save and restore the package-level config singletons and the global logger,
// because the analyzer reads its entire configuration from them. That is precisely the
// coupling the port is meant to remove; until then, tests have to work around it.
func runAnalyze(t *testing.T, pcapFile, protocolName string, configure func()) (summary, error) {
	got, found, err := runAnalyzeRaw(t, pcapFile, protocolName, configure)
	if err == nil {
		require.True(t, found, "a successful analysis must report a summary line")
	}
	return got, err
}

// runAnalyzeRaw is runAnalyze without the expectation that a summary was produced, for the
// cases that fail before the analyzer reaches its summary.
func runAnalyzeRaw(t *testing.T, pcapFile, protocolName string, configure func()) (summary, bool, error) {
	t.Helper()

	savedPcap := config.PcapConfigInstance
	savedAnalyze := config.AnalyzeConfigInstance
	savedRoot := config.RootConfigInstance
	savedCBus := config.CBusConfigInstance
	savedLogger := log.Logger
	savedLevel := zerolog.GlobalLevel()
	t.Cleanup(func() {
		config.PcapConfigInstance = savedPcap
		config.AnalyzeConfigInstance = savedAnalyze
		config.RootConfigInstance = savedRoot
		config.CBusConfigInstance = savedCBus
		log.Logger = savedLogger
		zerolog.SetGlobalLevel(savedLevel)
	})

	// A progress bar would write escape codes to the real stderr during the test run.
	config.RootConfigInstance.HideProgressBar = true
	configure()

	logs := &syncBuffer{}
	log.Logger = zerolog.New(logs).Level(zerolog.InfoLevel)
	zerolog.SetGlobalLevel(zerolog.InfoLevel)

	err := analyzer.AnalyzeWithOutput(t.Context(), pcapFile, protocolName, io.Discard, io.Discard)

	got, found := lastSummary(t, logs.String())
	return got, found, err
}

// lastSummary finds the analyzer's closing log line and decodes its counters. It reports
// whether such a line was present, since a run that fails early never emits one.
func lastSummary(t *testing.T, logOutput string) (summary, bool) {
	t.Helper()
	var got summary
	var found bool
	// Take the LAST matching line: a leaked goroutine from an earlier test may have written an
	// older summary into this buffer.
	for line := range strings.SplitSeq(logOutput, "\n") {
		if !strings.Contains(line, "Done evaluating") {
			continue
		}
		var candidate summary
		require.NoError(t, json.Unmarshal([]byte(line), &candidate), "summary line should be JSON: %s", line)
		got, found = candidate, true
	}
	return got, found
}

// TestAnalyzeCBusFixtureCharacterisation records the counters for a capture in which every
// payload is known to parse and re-serialize identically.
func TestAnalyzeCBusFixtureCharacterisation(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "cbus.pcap")
	session := pcapfixture.CBusSession()
	require.NoError(t, pcapfixture.WriteCBus(pcapFile, session))

	got, err := runAnalyze(t, pcapFile, protocol.CBus.Name, func() {
		config.PcapConfigInstance.Client = pcapfixture.ClientIP
		config.AnalyzeConfigInstance.NoFilter = true
		config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	})
	require.NoError(t, err)

	assert.Equal(t, len(session), got.NumberOfPackage, "every fixture packet should be seen")
	assert.Equal(t, len(session), got.CurrentPackageNum, "every packet should be walked")
	assert.Zero(t, got.ParseFails, "every fixture payload is known to parse")
	assert.Zero(t, got.SerializeFails, "every fixture payload is known to serialize")
	assert.Zero(t, got.CompareFails, "every fixture payload is known to round-trip byte-identically")
}

// TestAnalyzeCBusCountsParseFailures proves the parse-failure counter actually counts, using
// payloads verified not to parse. Without this, a refactor could zero the counter unnoticed.
func TestAnalyzeCBusCountsParseFailures(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "cbus-broken.pcap")
	session := pcapfixture.CBusSessionWithFailures()
	require.NoError(t, pcapfixture.WriteCBus(pcapFile, session))

	got, err := runAnalyze(t, pcapFile, protocol.CBus.Name, func() {
		config.PcapConfigInstance.Client = pcapfixture.ClientIP
		config.AnalyzeConfigInstance.NoFilter = true
		config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	})
	require.NoError(t, err)

	assert.Equal(t, len(session), got.NumberOfPackage)
	assert.Positive(t, got.ParseFails, "the two unparseable payloads must be counted")
}

// TestAnalyzeBacnetFixtureCharacterisation is the regression test for the defect that made
// every BACnet analysis fail. Both the canonical name and the alias must now analyse the
// capture instead of returning an unsupported-protocol error.
func TestAnalyzeBacnetFixtureCharacterisation(t *testing.T) {
	for _, name := range []string{"bacnetip", "bacnet"} {
		t.Run(name, func(t *testing.T) {
			pcapFile := filepath.Join(t.TempDir(), "bacnet.pcap")
			session := pcapfixture.BacnetSession()
			require.NoError(t, pcapfixture.WriteBacnet(pcapFile, session))

			got, err := runAnalyze(t, pcapFile, name, func() {
				// Filtering off: the default BACnet filter drops packets whose UDP length is
				// 29 bytes or less, which would silently discard the Who-Is fixtures.
				config.AnalyzeConfigInstance.NoFilter = true
				config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
			})
			require.NoError(t, err, "%q must be analysable; it used to fail with an unsupported-protocol error", name)

			assert.Equal(t, len(session), got.NumberOfPackage)
			assert.Zero(t, got.ParseFails, "every BACnet fixture payload is known to parse")
			assert.Zero(t, got.CompareFails, "every BACnet fixture payload round-trips byte-identically")
		})
	}
}

// TestAnalyzeRejectsUnknownProtocolWithoutTouchingTheCapture documents that protocol
// resolution now happens before any file access, so a typo cannot look like a capture problem.
func TestAnalyzeRejectsUnknownProtocolWithoutTouchingTheCapture(t *testing.T) {
	_, _, err := runAnalyzeRaw(t, filepath.Join(t.TempDir(), "does-not-exist.pcap"), "modbus", func() {})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "unsupported protocol")
	assert.NotContains(t, err.Error(), "open offline", "the protocol should be rejected before the capture is opened")
}

// TestAnalyzePackageNumberLimitStopsEarly characterises the limit flag, which the TUI sets on
// every run and which the port must keep honouring.
func TestAnalyzePackageNumberLimitStopsEarly(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "cbus.pcap")
	session := pcapfixture.CBusSession()
	require.NoError(t, pcapfixture.WriteCBus(pcapFile, session))

	const limit = 3
	got, err := runAnalyze(t, pcapFile, protocol.CBus.Name, func() {
		config.PcapConfigInstance.Client = pcapfixture.ClientIP
		config.AnalyzeConfigInstance.NoFilter = true
		config.PcapConfigInstance.PackageNumberLimit = limit
	})
	require.NoError(t, err)

	assert.Equal(t, limit+1, got.CurrentPackageNum,
		"the loop increments before testing the limit, so it stops one past it; pinned deliberately")
	assert.Less(t, got.CurrentPackageNum, len(session), "the limit must actually cut the run short")
}

// TestACancelledContextStopsTheAnalysis is the half of the abort that lives here. The check
// between packets has always been in the loop; what was missing was any way to reach it, because
// the entry points passed context.TODO. The evidence is the analyzer's own summary: a cancelled
// run reports a capture of eight packets and none walked, where an unhonoured cancellation
// would walk all eight.
//
// It runs in this package rather than through the command, because cobra.OnInitialize replaces
// the global logger before a command's body runs and takes the summary with it.
func TestACancelledContextStopsTheAnalysis(t *testing.T) {
	capture := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, pcapfixture.WriteCBus(capture, pcapfixture.CBusSession()))

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	got, found, err := runAnalyzeWith(t, ctx, capture, "c-bus")

	assert.NoError(t, err, "an interrupt is not a failure")
	require.True(t, found, "the analyzer still reports what it managed to do")
	assert.Positive(t, got.NumberOfPackage, "the capture has packets in it to walk")
	assert.Zero(t, got.CurrentPackageNum,
		"a run cancelled before it started must not walk the capture")
}

// TestALiveContextRunsToTheEnd is the control. Without it the test above would pass just as well
// against an analyzer that had stopped working altogether.
func TestALiveContextRunsToTheEnd(t *testing.T) {
	capture := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, pcapfixture.WriteCBus(capture, pcapfixture.CBusSession()))

	got, found, err := runAnalyzeWith(t, context.Background(), capture, "c-bus")

	require.NoError(t, err)
	require.True(t, found)
	assert.Equal(t, got.NumberOfPackage, got.CurrentPackageNum,
		"a live context has to let the whole capture through")
}

// runAnalyzeWith is runAnalyzeRaw with the context chosen by the caller, which is the one thing
// the other helpers cannot vary.
func runAnalyzeWith(t *testing.T, ctx context.Context, pcapFile, protocolName string) (summary, bool, error) {
	t.Helper()

	savedRoot, savedCBus := config.RootConfigInstance, config.CBusConfigInstance
	savedPcap, savedAnalyze := config.PcapConfigInstance, config.AnalyzeConfigInstance
	savedLogger, savedLevel := log.Logger, zerolog.GlobalLevel()
	t.Cleanup(func() {
		config.RootConfigInstance, config.CBusConfigInstance = savedRoot, savedCBus
		config.PcapConfigInstance, config.AnalyzeConfigInstance = savedPcap, savedAnalyze
		log.Logger = savedLogger
		zerolog.SetGlobalLevel(savedLevel)
	})
	config.RootConfigInstance.HideProgressBar = true
	// No flags are parsed here, so the limit is its zero value and the loop would stop after
	// the first packet -- which would make the control below indistinguishable from the abort.
	config.PcapConfigInstance.PackageNumberLimit = ^uint(0)

	logs := &syncBuffer{}
	log.Logger = zerolog.New(logs).Level(zerolog.InfoLevel)
	zerolog.SetGlobalLevel(zerolog.InfoLevel)

	err := analyzer.AnalyzeWithOutput(ctx, pcapFile, protocolName, io.Discard, io.Discard)
	got, found := lastSummary(t, logs.String())
	return got, found, err
}
