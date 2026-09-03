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

package ui

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	tea "charm.land/bubbletea/v2"
	"github.com/charmbracelet/x/exp/teatest/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Demo mode, the session configuration, and one end-to-end run of the real program.

func TestDemoWritesACaptureOfRealTraffic(t *testing.T) {
	demo, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)

	assert.Equal(t, DemoCaptureName, filepath.Base(demo.Path))
	assert.Equal(t, protocol.CBus.Name, demo.Protocol.Name)
	assert.NotEmpty(t, demo.Client, "C-Bus cannot tell a request from a response without a client address")

	stat, err := os.Stat(demo.Path)
	require.NoError(t, err)
	assert.Positive(t, stat.Size())
}

// TestDemoIsByteIdenticalEveryTime is what makes the demo usable as a fixture as well as a
// demonstration: the capture is built from fixed payloads and a fixed timestamp, never
// time.Now.
func TestDemoIsByteIdenticalEveryTime(t *testing.T) {
	first, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)
	second, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)

	firstBytes, err := os.ReadFile(first.Path)
	require.NoError(t, err)
	secondBytes, err := os.ReadFile(second.Path)
	require.NoError(t, err)
	assert.Equal(t, firstBytes, secondBytes)
}

// TestDemoProducesFindings is the point of using the failure fixture. A demo where everything
// succeeds shows none of what the tool is for.
func TestDemoProducesFindings(t *testing.T) {
	pinGlobals(t)
	demo, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)

	state := NewState(demo.Dir, NewConfig())
	state.HostIP = demo.Client
	_, err = state.Open(demo.Path)
	require.NoError(t, err)

	result := Analyze(t.Context(), state.RequestFor(demo.Protocol, demo.Path), Sink{})
	require.NoError(t, result.Err)
	assert.Positive(t, result.Counters.Walked, "the demo has to analyse something")
	assert.Positive(t, result.Counters.Parsed, "most of the demo traffic has to parse, or it is not a demonstration")
	assert.Positive(t, result.Counters.Issues(), "and some of it has to fail, or the findings tab is empty")
}

func TestDemoCleanupRemovesTheCapture(t *testing.T) {
	demo, err := NewDemo()
	require.NoError(t, err)
	require.FileExists(t, demo.Path)

	require.NoError(t, demo.Cleanup())
	assert.NoFileExists(t, demo.Path)
	assert.NoDirExists(t, demo.Dir)

	assert.NoError(t, demo.Cleanup(), "cleanup has to be safe to run twice, because it runs from a defer")
	assert.NoError(t, Demo{}.Cleanup(), "and safe on a demo that was never created")
}

func TestDemoModeAnalysesItsCaptureAtStartup(t *testing.T) {
	pinGlobals(t)
	demo, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)
	state := NewState(demo.Dir, NewConfig())
	state.Demo = true
	state.Protocol = demo.Protocol
	state.HostIP = demo.Client
	_, err = state.Open(demo.Path)
	require.NoError(t, err)

	request := state.RequestFor(demo.Protocol, demo.Path)
	model := NewModel(Options{Theme: testTheme(), State: state, Clock: fixedClock(), AutoRun: &request})
	model = send(model, tea.WindowSizeMsg{Width: 100, Height: 30})

	// Init queues the run; running it is Update's job, so that one code path starts every run.
	require.NotNil(t, model.Init())
	model = send(model, startAnalysisMsg{Request: request})
	require.True(t, model.run.active)

	result := Analyze(t.Context(), request, Sink{})
	model = send(model, analysisDoneMsg{Result: result})

	screen := model.Render()
	assert.Contains(t, screen, "DEMO")
	assert.Contains(t, screen, DemoCaptureName)
	assert.Contains(t, screen, "c-bus")
	assert.NotEmpty(t, model.packets)
	assert.Positive(t, len(model.findings()), "the demo's findings have to reach the table")
}

// TestEndToEndInDemoMode runs the real program.
//
// It is the only test here that starts a Bubble Tea program, and it exists to prove the parts
// are wired together: Init arms the drains, the queued demo run reaches Update, the analysis
// runs on its own goroutine, its progress and its records come back as messages, and the quit
// binding ends the program.
func TestEndToEndInDemoMode(t *testing.T) {
	pinGlobals(t)
	demo, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)

	state := NewState(demo.Dir, NewConfig())
	state.Demo = true
	state.Protocol = demo.Protocol
	state.HostIP = demo.Client
	_, err = state.Open(demo.Path)
	require.NoError(t, err)

	request := state.RequestFor(demo.Protocol, demo.Path)
	model := NewModel(Options{Theme: testTheme(), State: state, Clock: fixedClock(), AutoRun: &request})

	program := teatest.NewTestModel(t, model, teatest.WithInitialTermSize(100, 30))
	// Stop the program before pinGlobals puts the configuration singletons back. Cleanups run
	// last-registered-first, and pinGlobals registered its restore before this, so this one
	// runs first. Without it a failing assertion anywhere below returns through t.Fatal while
	// the program is still rendering, and the restore races the render's read of those
	// singletons -- which reports as a data race in whatever assertion happened to fail.
	t.Cleanup(func() { _ = program.Quit() })

	// The frame that proves the run reached Update: the counters row of the run panel, and a
	// packet the codec actually named.
	teatest.WaitFor(t, program.Output(), func(out []byte) bool {
		return bytes.Contains(out, []byte("cbus-demo.pcap")) &&
			bytes.Contains(out, []byte("parse-fail")) &&
			bytes.Contains(out, []byte("CBusMessage"))
	}, teatest.WithDuration(10*time.Second), teatest.WithCheckInterval(20*time.Millisecond))

	// Quitting is confirmed, so it takes the question and then the answer. A second ctrl+c is
	// the answer, which is also the end-to-end proof that the confirmation is reachable
	// through a real program and not only through Update.
	// Quitting is confirmed, and ctrl+c means "stop the run" while one is in flight, so how
	// many presses it takes depends on whether this machine has finished the run yet:
	//
	//   nothing running   press 1 asks,   press 2 confirms
	//   run in flight     press 1 aborts, press 2 asks,     press 3 confirms
	//
	// Three presses end the session either way, and a press delivered after the program has
	// already stopped is dropped. Waiting for the run to finish first and sending exactly two
	// was the flaky version of this: the records stream in while the run is still going, so
	// there is no frame that reliably means "and now nothing is running".
	//
	// That the question is reachable at all, and that only a yes answers it, is pinned by
	// TestQuitBindingsQuit and TestAnyOtherKeyKeepsTheSession against the real Update path.
	for range 3 {
		program.Send(tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	}
	program.WaitFinished(t, teatest.WithFinalTimeout(10*time.Second))

	final, ok := program.FinalModel(t, teatest.WithFinalTimeout(10*time.Second)).(Model)
	require.True(t, ok)
	assert.True(t, final.quitting)
	assert.NotEmpty(t, final.packets, "the analysis has to have populated the table before the program exited")
}

func TestConfigRoundTrips(t *testing.T) {
	pinGlobals(t)
	path := filepath.Join(t.TempDir(), "nested", ConfigFileName)

	config := NewConfig()
	config.HostIp = "10.1.2.3"
	config.LogLevel = "debug"
	config.MaxConsoleLines = 42
	config.RememberFile("/captures/a.pcap")
	config.RememberCommand("analyze c-bus a.pcap")
	require.NoError(t, config.EnableAutoRegister("c-bus"))

	require.NoError(t, SaveConfigTo(path, config, time.Unix(0, 0)))

	loaded, err := LoadConfigFrom(path)
	require.NoError(t, err)
	assert.Equal(t, "10.1.2.3", loaded.HostIp)
	assert.Equal(t, "debug", loaded.LogLevel)
	assert.Equal(t, 42, loaded.MaxConsoleLines)
	assert.Equal(t, []string{"/captures/a.pcap"}, loaded.History.Last10Files)
	assert.Equal(t, []string{"analyze c-bus a.pcap"}, loaded.History.Last10Commands)
	assert.Equal(t, []string{"c-bus"}, loaded.AutoRegisterDrivers)
	assert.Same(t, CliConfigInstances().RootConfig, loaded.CliConfigs.RootConfig,
		"a loaded config has to point back at the live singletons, or conf set edits a copy")
}

// TestLoadingAMissingConfigIsAFirstRunRatherThanAFailure keeps the tool starting on a fresh
// machine.
func TestLoadingAMissingConfigIsAFirstRunRatherThanAFailure(t *testing.T) {
	loaded, err := LoadConfigFrom(filepath.Join(t.TempDir(), "absent.yml"))
	require.NoError(t, err)
	assert.Equal(t, NewConfig().MaxConsoleLines, loaded.MaxConsoleLines)
}

// TestACorruptConfigStillYieldsUsableDefaults is what stops a bad file bricking the tool. The
// version this replaces panicked out of an init function on a configuration problem.
func TestACorruptConfigStillYieldsUsableDefaults(t *testing.T) {
	path := filepath.Join(t.TempDir(), ConfigFileName)
	require.NoError(t, os.WriteFile(path, []byte("this: [is not: yaml"), 0o600))

	loaded, err := LoadConfigFrom(path)
	require.Error(t, err)
	assert.Equal(t, NewConfig().MaxConsoleLines, loaded.MaxConsoleLines)
}

func TestRememberKeepsTheMostRecentAndDropsDuplicates(t *testing.T) {
	config := NewConfig()
	for i := range HistoryLimit + 5 {
		config.RememberCommand("command " + itoa(i))
	}
	assert.Len(t, config.History.Last10Commands, HistoryLimit)
	assert.Equal(t, "command 14", config.History.Last10Commands[HistoryLimit-1])

	config.RememberCommand("command 10")
	assert.Equal(t, "command 10", config.History.Last10Commands[HistoryLimit-1],
		"re-running a command moves it to the front rather than adding a second entry")
	assert.Len(t, config.History.Last10Commands, HistoryLimit)
}

// TestClearAndHistoryAreNotRemembered keeps the two commands whose whole purpose is to inspect
// the session out of the list of commands to recall.
func TestClearAndHistoryAreNotRemembered(t *testing.T) {
	config := NewConfig()
	config.RememberCommand("clear")
	config.RememberCommand("history")
	assert.Empty(t, config.History.Last10Commands)
}

func TestConfigPathLivesUnderTheToolsOwnDirectory(t *testing.T) {
	path, err := ConfigPath()
	if err != nil {
		t.Skip("no user configuration directory on this machine")
	}
	assert.True(t, strings.HasSuffix(path, filepath.Join("plc4xpcapanalyzer", ConfigFileName)), "got %q", path)
}

func TestLineWriterEmitsWholeLinesOnly(t *testing.T) {
	var got []string
	writer := newLineWriter(func(line string) { got = append(got, line) })

	_, err := writer.Write([]byte("first\nsec"))
	require.NoError(t, err)
	assert.Equal(t, []string{"first"}, got, "a partial line has to be held back, not shown as a fragment")

	_, err = writer.Write([]byte("ond\nthird\n"))
	require.NoError(t, err)
	assert.Equal(t, []string{"first", "second", "third"}, got)

	_, err = writer.Write([]byte("tail"))
	require.NoError(t, err)
	writer.Flush()
	assert.Equal(t, []string{"first", "second", "third", "tail"}, got)
}

func TestChannelSinkNeverBlocks(t *testing.T) {
	ch := make(chan string, 2)
	sink := channelSink(ch)
	for range 100 {
		sink("line")
	}
	assert.Len(t, ch, 2, "a full channel drops rather than throttling whatever is logging")
}

func TestTheModelsLogWriterReachesTheLogPane(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	_, err := model.LogWriter().Write([]byte("hello from a goroutine\n"))
	require.NoError(t, err)

	// The line is on the channel; the drain turns it into a message.
	msg := waitForLog(model.logCh)()
	model = send(model, msg)
	assert.Contains(t, strings.Join(model.logLines, "\n"), "hello from a goroutine")
}
