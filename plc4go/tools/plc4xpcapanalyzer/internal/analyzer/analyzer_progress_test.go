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
	"io"
	"path/filepath"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// These tests exist because the progress wiring had no regression protection: deleting the
// reporter.Start and reporter.Advance calls from the analysis loop left every other test in
// the tree green. The reporter is the whole point of the Options.Progress seam, so it needs a
// test that fails when the analyser stops driving it.

// recordingReporter records the calls an analysis makes, so a test can assert on them.
//
// It is mutex-guarded because a reporter is fair game to call from whichever goroutine is
// doing the work, and the race detector runs over this package.
type recordingReporter struct {
	mu sync.Mutex

	starts       []recordedStart
	advanced     int
	advanceCalls int
	descriptions []string
	doneCalls    int
}

type recordedStart struct {
	total       int
	description string
}

func (r *recordingReporter) Start(total int, description string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.starts = append(r.starts, recordedStart{total: total, description: description})
}

func (r *recordingReporter) Advance(delta int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.advanced += delta
	r.advanceCalls++
}

func (r *recordingReporter) SetDescription(description string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.descriptions = append(r.descriptions, description)
}

func (r *recordingReporter) Done() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.doneCalls++
}

func (r *recordingReporter) snapshot() recordingReporter {
	r.mu.Lock()
	defer r.mu.Unlock()
	return recordingReporter{
		starts:       append([]recordedStart(nil), r.starts...),
		advanced:     r.advanced,
		advanceCalls: r.advanceCalls,
		descriptions: append([]string(nil), r.descriptions...),
		doneCalls:    r.doneCalls,
	}
}

// runWithReporter analyses a fixture with a recording reporter attached, restoring the
// package-level configuration the analyser still reads from.
func runWithReporter(t *testing.T, pcapFile, protocolName string, configure func()) (recordingReporter, error) {
	t.Helper()

	savedPcap := config.PcapConfigInstance
	savedAnalyze := config.AnalyzeConfigInstance
	savedRoot := config.RootConfigInstance
	t.Cleanup(func() {
		config.PcapConfigInstance = savedPcap
		config.AnalyzeConfigInstance = savedAnalyze
		config.RootConfigInstance = savedRoot
	})
	config.RootConfigInstance.HideProgressBar = true
	configure()

	reporter := &recordingReporter{}
	err := analyzer.AnalyzeWithOptions(t.Context(), pcapFile, protocolName, analyzer.Options{
		Stdout:   io.Discard,
		Stderr:   io.Discard,
		Progress: reporter,
	})
	return reporter.snapshot(), err
}

// TestAnalyzeDrivesTheProgressReporter is the guard the reviewer asked for: it fails if the
// analysis loop stops announcing its size or stops advancing.
func TestAnalyzeDrivesTheProgressReporter(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "cbus.pcap")
	session := pcapfixture.CBusSession()
	require.NoError(t, pcapfixture.WriteCBus(pcapFile, session))

	recorded, err := runWithReporter(t, pcapFile, protocol.CBus.Name, func() {
		config.PcapConfigInstance.Client = pcapfixture.ClientIP
		config.AnalyzeConfigInstance.NoFilter = true
		config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	})
	require.NoError(t, err)

	require.NotEmpty(t, recorded.starts, "the analyser must announce the work it is about to do")
	assert.Equal(t, len(session), recorded.starts[0].total,
		"Start must carry the packet count, which is what sizes the bar")
	assert.NotEmpty(t, recorded.starts[0].description, "a bar with no description says nothing")

	assert.Equal(t, len(session), recorded.advanced,
		"every packet walked must advance the reporter exactly once")
	assert.Equal(t, len(session), recorded.advanceCalls)
	assert.Positive(t, recorded.doneCalls, "the analyser must finish the bar rather than abandon it")
}

func TestAnalyzeDrivesTheProgressReporterForBacnetToo(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "bacnet.pcap")
	session := pcapfixture.BacnetSession()
	require.NoError(t, pcapfixture.WriteBacnet(pcapFile, session))

	recorded, err := runWithReporter(t, pcapFile, protocol.BacnetIP.Name, func() {
		config.AnalyzeConfigInstance.NoFilter = true
		config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	})
	require.NoError(t, err)

	require.NotEmpty(t, recorded.starts)
	assert.Equal(t, len(session), recorded.starts[0].total)
	assert.Equal(t, len(session), recorded.advanced)
	assert.Positive(t, recorded.doneCalls)
}

// TestAnalyzeFinishesTheReporterEvenWhenItStopsEarly matters because the package-number limit
// breaks out of the loop. A bar left unfinished is exactly the "stuck at 60%" artefact the
// reporter is supposed to avoid.
func TestAnalyzeFinishesTheReporterEvenWhenItStopsEarly(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "cbus.pcap")
	session := pcapfixture.CBusSession()
	require.NoError(t, pcapfixture.WriteCBus(pcapFile, session))

	const limit = 3
	recorded, err := runWithReporter(t, pcapFile, protocol.CBus.Name, func() {
		config.PcapConfigInstance.Client = pcapfixture.ClientIP
		config.AnalyzeConfigInstance.NoFilter = true
		config.PcapConfigInstance.PackageNumberLimit = limit
	})
	require.NoError(t, err)

	assert.Positive(t, recorded.doneCalls, "an early exit must still finish the bar")
	assert.Less(t, recorded.advanced, len(session), "the limit must actually cut the run short")
	assert.LessOrEqual(t, recorded.advanced, limit+1,
		"the loop advances before testing the limit, so it may overshoot by one")
}

// TestAnalyzeToleratesTheNopReporter pins that progress really is optional: the default path
// must not depend on a reporter being attached.
func TestAnalyzeToleratesTheNopReporter(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, pcapfixture.WriteCBus(pcapFile, pcapfixture.CBusSession()))

	savedPcap := config.PcapConfigInstance
	savedAnalyze := config.AnalyzeConfigInstance
	savedRoot := config.RootConfigInstance
	t.Cleanup(func() {
		config.PcapConfigInstance = savedPcap
		config.AnalyzeConfigInstance = savedAnalyze
		config.RootConfigInstance = savedRoot
	})
	config.RootConfigInstance.HideProgressBar = true
	config.PcapConfigInstance.Client = pcapfixture.ClientIP
	config.AnalyzeConfigInstance.NoFilter = true
	config.PcapConfigInstance.PackageNumberLimit = ^uint(0)

	assert.NoError(t, analyzer.AnalyzeWithOptions(t.Context(), pcapFile, protocol.CBus.Name, analyzer.Options{
		Stdout:   io.Discard,
		Stderr:   io.Discard,
		Progress: progress.Nop(),
	}))
}
