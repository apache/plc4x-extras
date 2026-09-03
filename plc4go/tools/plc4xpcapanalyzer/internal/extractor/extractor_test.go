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

package extractor_test

import (
	"bytes"
	"io"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/extractor"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// The extractor had no tests at all, and its colour handling, direction markers and writer
// wiring were all rewritten when the progress reporter replaced the fatih/color writers. These
// pin the behaviour that rewrite had to preserve.

// countingReporter records what the extractor tells it.
type countingReporter struct {
	mu       sync.Mutex
	total    int
	starts   int
	advanced int
	dones    int
}

func (c *countingReporter) Start(total int, _ string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.starts++
	c.total = total
}

func (c *countingReporter) Advance(delta int) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.advanced += delta
}

func (c *countingReporter) SetDescription(string) {}

func (c *countingReporter) Done() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.dones++
}

func (c *countingReporter) read() (total, starts, advanced, dones int) {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.total, c.starts, c.advanced, c.dones
}

// runExtract extracts a fixture, restoring the package-level configuration afterwards.
func runExtract(t *testing.T, pcapFile, protocolName string, theme *tui.Theme, configure func()) (stdout, stderr string, reporter *countingReporter, err error) {
	t.Helper()

	savedPcap := config.PcapConfigInstance
	savedExtract := config.ExtractConfigInstance
	savedRoot := config.RootConfigInstance
	savedLogger := log.Logger
	savedLevel := zerolog.GlobalLevel()
	t.Cleanup(func() {
		config.PcapConfigInstance = savedPcap
		config.ExtractConfigInstance = savedExtract
		config.RootConfigInstance = savedRoot
		log.Logger = savedLogger
		zerolog.SetGlobalLevel(savedLevel)
	})
	// The extractor logs at info on every packet; a test run is not the place for it.
	log.Logger = zerolog.New(io.Discard)
	zerolog.SetGlobalLevel(zerolog.Disabled)
	config.RootConfigInstance.HideProgressBar = true
	// The analysis loop treats a zero limit as "stop after the first packet". Nothing gives it
	// a sane default: the CLI only avoids that because its flag default is math.MaxUint. A test
	// driving the package directly has to say so itself.
	config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	// Payload output is gated behind verbosity: extractor.go:234 requires Verbosity > 1 before
	// a payload is printed at all, and extractor.go:135 requires > 2 for the extra annotation.
	// A test that leaves it at zero silently exercises an extractor that prints nothing.
	config.ExtractConfigInstance.Verbosity = 2
	if configure != nil {
		configure()
	}

	var out, errOut bytes.Buffer
	reporter = &countingReporter{}
	err = extractor.ExtractWithOptions(t.Context(), pcapFile, protocolName, extractor.Options{
		Stdout:   &out,
		Stderr:   &errOut,
		Progress: reporter,
		Theme:    theme,
	})
	return out.String(), errOut.String(), reporter, err
}

func cbusFixture(t *testing.T) (path string, packets int) {
	t.Helper()
	path = filepath.Join(t.TempDir(), "cbus.pcap")
	session := pcapfixture.CBusSession()
	require.NoError(t, pcapfixture.WriteCBus(path, session))
	return path, len(session)
}

// TestExtractWritesPayloadsToTheWriterItIsGiven is the core of the rewrite: the extractor used
// to build its own writers bound to the process's stdout and stderr, ignoring the ones it was
// handed.
func TestExtractWritesPayloadsToTheWriterItIsGiven(t *testing.T) {
	pcapFile, _ := cbusFixture(t)
	noColour := tui.NewTheme(tui.Options{Dark: true, NoColor: true})

	stdout, _, _, err := runExtract(t, pcapFile, protocol.CBus.Name, &noColour, func() {
		config.ExtractConfigInstance.Client = pcapfixture.ClientIP
	})
	require.NoError(t, err)

	// Assert on real payload content, not merely on the stream being non-empty: the extractor
	// emits a trailing newline even when it extracts nothing, so "not empty" would pass
	// against an extractor that had stopped working entirely.
	assert.Contains(t, stdout, "~~~", "the reset request from the fixture should appear in the output")
	assert.Greater(t, len(strings.TrimSpace(stdout)), 10, "the payload stream should carry real content")
}

// TestExtractDrivesTheProgressReporter is the wiring guard: deleting the Start or Advance call
// must fail a test.
func TestExtractDrivesTheProgressReporter(t *testing.T) {
	pcapFile, packets := cbusFixture(t)
	noColour := tui.NewTheme(tui.Options{Dark: true, NoColor: true})

	_, _, reporter, err := runExtract(t, pcapFile, protocol.CBus.Name, &noColour, func() {
		config.ExtractConfigInstance.Client = pcapfixture.ClientIP
	})
	require.NoError(t, err)

	total, starts, advanced, dones := reporter.read()
	assert.Positive(t, starts, "the extractor must announce the work it is about to do")
	assert.Equal(t, packets, total, "Start must carry the packet count")
	assert.Equal(t, packets, advanced, "every packet must advance the reporter once")
	assert.Positive(t, dones, "the extractor must finish the bar rather than abandon it")
}

// TestExtractEmitsNoEscapeSequencesWithAColourlessTheme pins the property the fatih/color
// writers provided: extract's output is routinely piped into a file, and escape sequences in
// it corrupt the transcript.
func TestExtractEmitsNoEscapeSequencesWithAColourlessTheme(t *testing.T) {
	pcapFile, _ := cbusFixture(t)
	noColour := tui.NewTheme(tui.Options{Dark: true, NoColor: true})

	stdout, stderr, _, err := runExtract(t, pcapFile, protocol.CBus.Name, &noColour, func() {
		config.ExtractConfigInstance.Client = pcapfixture.ClientIP
		config.ExtractConfigInstance.ShowDirectionalIndicators = true
	})
	require.NoError(t, err)
	assert.NotContains(t, stdout, "\x1b[", "a colourless theme must not write escape sequences into the payload stream")
	assert.NotContains(t, stderr, "\x1b[", "nor into the marker stream")
}

// TestExtractDirectionalIndicatorsAreOptional covers the flag that gates the markers, which
// moved from a fatih/color writer to a themed one.
func TestExtractDirectionalIndicatorsAreOptional(t *testing.T) {
	pcapFile, _ := cbusFixture(t)
	noColour := tui.NewTheme(tui.Options{Dark: true, NoColor: true})

	_, withMarkers, _, err := runExtract(t, pcapFile, protocol.CBus.Name, &noColour, func() {
		config.ExtractConfigInstance.Client = pcapfixture.ClientIP
		config.ExtractConfigInstance.ShowDirectionalIndicators = true
	})
	require.NoError(t, err)

	_, withoutMarkers, _, err := runExtract(t, pcapFile, protocol.CBus.Name, &noColour, func() {
		config.ExtractConfigInstance.Client = pcapfixture.ClientIP
		config.ExtractConfigInstance.ShowDirectionalIndicators = false
	})
	require.NoError(t, err)

	assert.NotEqual(t, withMarkers, withoutMarkers,
		"the directional-indicator flag must actually change what is written")
	assert.Greater(t, len(withMarkers), len(withoutMarkers),
		"enabling the markers should add output, not replace it")
}

// TestExtractRejectsAnUnknownProtocolBeforeOpeningTheCapture matches the analyser's behaviour,
// so a typo reads as a typo rather than as a capture problem.
func TestExtractRejectsAnUnknownProtocolBeforeOpeningTheCapture(t *testing.T) {
	_, _, _, err := runExtract(t, filepath.Join(t.TempDir(), "absent.pcap"), "modbus", nil, nil)
	require.Error(t, err)
	assert.Contains(t, strings.ToLower(err.Error()), "unsupported protocol")
}

// TestExtractAcceptsBothBacnetSpellings is the extractor's half of the protocol-alias fix: it
// used to match only "bacnet", disagreeing with the analyser, so "bacnetip" was rejected here.
func TestExtractAcceptsBothBacnetSpellings(t *testing.T) {
	pcapFile := filepath.Join(t.TempDir(), "bacnet.pcap")
	require.NoError(t, pcapfixture.WriteBacnet(pcapFile, pcapfixture.BacnetSession()))
	noColour := tui.NewTheme(tui.Options{Dark: true, NoColor: true})

	for _, name := range []string{"bacnet", "bacnetip"} {
		t.Run(name, func(t *testing.T) {
			_, _, _, err := runExtract(t, pcapFile, name, &noColour, nil)
			assert.NoError(t, err, "%q must be extractable", name)
		})
	}
}
