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
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	cliConfig "github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// run executes one command line against a session and returns what it produced.
func run(t *testing.T, state *State, line string) Outcome {
	t.Helper()
	return Root().Execute(t.Context(), state, line)
}

// TestEveryCommandTheOldUiOfferedIsStillHere is the inventory check.
//
// The list is transcribed from the tview version's command tree. It is spelled out rather than
// derived so that removing a command from the tree fails here instead of quietly shipping.
func TestEveryCommandTheOldUiOfferedIsStillHere(t *testing.T) {
	inventory := []string{
		"ls", "cd", "pwd", "open", "analyze", "extract",
		"host set", "host get",
		"register", "quit",
		"log get", "log set",
		"conf list",
		"conf plc4xpcapanalyzer-debug on", "conf plc4xpcapanalyzer-debug off",
		"conf auto-register list", "conf auto-register enable", "conf auto-register disable",
		"plc4x-conf TraceTransactionManagerWorkers on", "plc4x-conf TraceTransactionManagerWorkers off",
		"plc4x-conf TraceTransactionManagerTransactions on", "plc4x-conf TraceTransactionManagerTransactions off",
		"plc4x-conf TraceDefaultMessageCodecWorker on", "plc4x-conf TraceDefaultMessageCodecWorker off",
		"history",
		"clear", "clear message", "clear console", "clear command",
		"abort", "help",
	}
	root := Root()
	for _, path := range inventory {
		t.Run(path, func(t *testing.T) {
			node, _, ok := root.resolve(path)
			require.True(t, ok, "%q no longer resolves to a command", path)
			last := path[strings.LastIndex(path, " ")+1:]
			assert.Equal(t, last, node.Name, "%q resolved to %q instead", path, node.Name)
			assert.NotNil(t, node.Run, "%q resolves but cannot be run", path)
		})
	}
}

func TestConfSetCoversEveryConfigurationStruct(t *testing.T) {
	root := Root()
	set, _, ok := root.resolve("conf set")
	require.True(t, ok, "conf set is missing")
	assert.NotEmpty(t, set.Sub, "conf set is a dispatcher; with no groups it can set nothing")
	for _, group := range []string{"RootConfig", "AnalyzeConfig", "ExtractConfig", "BacnetConfig", "CBusConfig", "PcapConfig"} {
		node, _, ok := root.resolve("conf set " + group)
		require.True(t, ok, "conf set %s is missing", group)
		assert.NotEmpty(t, node.Sub, "conf set %s offers no fields", group)
	}
}

func TestUnknownCommandNamesTheWayOut(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	outcome := run(t, state, "frobnicate the widget")
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "frobnicate")
	assert.Contains(t, outcome.Err.Error(), "help")
}

func TestACommandThatNeedsASubcommandSaysWhichOnes(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	outcome := run(t, state, "host")
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "set")
	assert.Contains(t, outcome.Err.Error(), "get")
}

func TestPwdAndCd(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.Mkdir(filepath.Join(dir, "captures"), 0o755))
	state := NewState(dir, NewConfig())

	assert.Contains(t, run(t, state, "pwd").Lines[0], dir)

	outcome := run(t, state, "cd captures")
	require.NoError(t, outcome.Err)
	assert.Equal(t, filepath.Join(dir, "captures"), state.CurrentDir)

	failed := run(t, state, "cd nowhere")
	assert.Error(t, failed.Err)
}

func TestCdRefusesAFile(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "a.pcap"), []byte("x"), 0o600))
	state := NewState(dir, NewConfig())

	outcome := run(t, state, "cd a.pcap")
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "not a directory")
}

func TestLsListsTheDirectory(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "a.pcap"), []byte("x"), 0o600))
	require.NoError(t, os.Mkdir(filepath.Join(dir, "sub"), 0o755))
	state := NewState(dir, NewConfig())

	outcome := run(t, state, "ls")
	require.NoError(t, outcome.Err)
	joined := strings.Join(outcome.Lines, "\n")
	assert.Contains(t, joined, "a.pcap")
	assert.Contains(t, joined, "sub"+string(os.PathSeparator))
}

func TestOpenSelectsTheCapture(t *testing.T) {
	state, demo := demoState(t)
	state.Captures, state.Current = nil, -1

	outcome := run(t, state, "open "+DemoCaptureName)
	require.NoError(t, outcome.Err)
	capture, ok := state.CurrentCapture()
	require.True(t, ok)
	assert.Equal(t, demo.Path, capture.Path)
	assert.Contains(t, state.Config.History.Last10Files, demo.Path,
		"opening a capture must put it in the recent list, which is what open completes from")
}

func TestOpeningTheSameCaptureTwiceSelectsItRatherThanFailing(t *testing.T) {
	state, _ := demoState(t)
	before := len(state.Captures)

	outcome := run(t, state, "open "+DemoCaptureName)
	require.NoError(t, outcome.Err)
	assert.Len(t, state.Captures, before, "an already-open capture must be selected, not duplicated")
}

func TestOpenReportsAMissingFile(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	outcome := run(t, state, "open nope.pcap")
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "nope.pcap")
}

func TestAnalyzeReturnsARequestRatherThanDoingTheWork(t *testing.T) {
	state, demo := demoState(t)

	outcome := run(t, state, "analyze c-bus "+demo.Path)
	require.NoError(t, outcome.Err)
	require.NotNil(t, outcome.Analysis, "the command layer must hand the model a request, not run it")
	assert.Equal(t, protocol.CBus.Name, outcome.Analysis.Protocol.Name)
	assert.Equal(t, demo.Path, outcome.Analysis.PcapFile)
	assert.Equal(t, demo.Client, outcome.Analysis.Client)
}

// TestAnalyzeAcceptsTheBacnetAlias is the regression guard for the defect the protocol registry
// was introduced to fix: the CLI accepted "bacnet" and the analyzer only ever matched
// "bacnetip".
func TestAnalyzeAcceptsTheBacnetAlias(t *testing.T) {
	state, demo := demoState(t)

	outcome := run(t, state, "analyze bacnet "+demo.Path)
	require.NoError(t, outcome.Err)
	require.NotNil(t, outcome.Analysis)
	assert.Equal(t, protocol.BacnetIP.Name, outcome.Analysis.Protocol.Name)
}

func TestAnalyzeWithNoArgumentsUsesTheSelectedCapture(t *testing.T) {
	state, demo := demoState(t)

	outcome := run(t, state, "analyze")
	require.NoError(t, outcome.Err)
	require.NotNil(t, outcome.Analysis)
	assert.Equal(t, demo.Path, outcome.Analysis.PcapFile)
}

func TestAnalyzeWithNothingOpenSaysWhatToDo(t *testing.T) {
	pinGlobals(t)
	state := NewState(t.TempDir(), NewConfig())

	outcome := run(t, state, "analyze")
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "open <pcapfile>")
}

func TestAnalyzeRejectsAnUnknownProtocol(t *testing.T) {
	state, demo := demoState(t)
	outcome := run(t, state, "analyze modbus "+demo.Path)
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "modbus")
}

func TestExtractReturnsAnExtraction(t *testing.T) {
	state, demo := demoState(t)
	outcome := run(t, state, "extract c-bus "+demo.Path)
	require.NoError(t, outcome.Err)
	require.NotNil(t, outcome.Extraction)
	assert.Nil(t, outcome.Analysis)
	assert.Equal(t, demo.Path, outcome.Extraction.PcapFile)
}

func TestHostSetAndGet(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())

	assert.Contains(t, run(t, state, "host get").Lines[0], "host set <ip>")

	require.NoError(t, run(t, state, "host set 10.0.0.1").Err)
	assert.Equal(t, "10.0.0.1", state.HostIP)
	assert.Equal(t, "10.0.0.1", state.Config.HostIp, "the address has to survive the session")
	assert.Contains(t, run(t, state, "host get").Lines[0], "10.0.0.1")
}

func TestRegisterAndUnregisterADriver(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())

	require.NoError(t, run(t, state, "register c-bus").Err)
	assert.True(t, state.IsRegistered("c-bus"))
	assert.NotNil(t, state.DriverManager)

	assert.Error(t, run(t, state, "register c-bus").Err, "registering twice must say so rather than silently succeed")
	assert.Error(t, run(t, state, "register nonsense").Err)

	require.NoError(t, run(t, state, "unregister c-bus").Err)
	assert.False(t, state.IsRegistered("c-bus"))
	assert.Error(t, run(t, state, "unregister c-bus").Err)
}

func TestLogSetChangesTheLevel(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())

	require.NoError(t, run(t, state, "log set debug").Err)
	assert.Equal(t, zerolog.DebugLevel, state.LogLevel)
	assert.Equal(t, "debug", state.Config.LogLevel)
	assert.Contains(t, run(t, state, "log get").Lines[0], "debug")

	assert.Error(t, run(t, state, "log set shouting").Err)
}

// TestConfSetConvertsToTheFieldsType is the fix for a real panic: the old implementation called
// reflect.Value.SetString on every field, so setting any bool or uint blew up and the
// dispatcher's recover reported it as "panic occurred".
func TestConfSetConvertsToTheFieldsType(t *testing.T) {
	pinGlobals(t)
	state := NewState(t.TempDir(), NewConfig())

	// Filter lives on PcapConfig, which AnalyzeConfig embeds. The reflection walks declared
	// fields only, so the embedded struct is addressed by its own name — the same shape the
	// previous version had.
	require.NoError(t, run(t, state, "conf set PcapConfig Filter tcp port 10001").Err)
	assert.Equal(t, "tcp port 10001", cliConfig.AnalyzeConfigInstance.Filter)

	require.NoError(t, run(t, state, "conf set AnalyzeConfig OnlyParse true").Err)
	assert.True(t, cliConfig.AnalyzeConfigInstance.OnlyParse)

	require.NoError(t, run(t, state, "conf set PcapConfig PackageNumberLimit 42").Err)
	assert.Equal(t, uint(42), cliConfig.PcapConfigInstance.PackageNumberLimit)

	require.NoError(t, run(t, state, "conf set RootConfig Verbosity 3").Err)
	assert.Equal(t, 3, cliConfig.RootConfigInstance.Verbosity)
}

func TestConfSetRejectsAValueOfTheWrongType(t *testing.T) {
	pinGlobals(t)
	state := NewState(t.TempDir(), NewConfig())

	outcome := run(t, state, "conf set AnalyzeConfig OnlyParse maybe")
	require.Error(t, outcome.Err)
	assert.Contains(t, outcome.Err.Error(), "true or false")
	assert.NotContains(t, outcome.Err.Error(), "panic")
}

func TestConfListShowsTheCurrentValues(t *testing.T) {
	pinGlobals(t)
	cliConfig.AnalyzeConfigInstance.Filter = "udp port 47808"
	state := NewState(t.TempDir(), NewConfig())

	joined := strings.Join(run(t, state, "conf list").Lines, "\n")
	assert.Contains(t, joined, "AnalyzeConfig")
	assert.Contains(t, joined, "udp port 47808")
}

func TestAutoRegister(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())

	assert.Contains(t, run(t, state, "conf auto-register list").Lines[0], "conf auto-register enable")

	require.NoError(t, run(t, state, "conf auto-register enable c-bus").Err)
	assert.Equal(t, []string{"c-bus"}, state.Config.AutoRegisterDrivers)
	assert.Error(t, run(t, state, "conf auto-register enable c-bus").Err)
	assert.Error(t, run(t, state, "conf auto-register enable nonsense").Err)

	require.NoError(t, run(t, state, "conf auto-register disable c-bus").Err)
	assert.Empty(t, state.Config.AutoRegisterDrivers)
	assert.Error(t, run(t, state, "conf auto-register disable c-bus").Err)
}

func TestDebugToggle(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	require.NoError(t, run(t, state, "conf plc4xpcapanalyzer-debug on").Err)
	assert.True(t, state.Debug)
	require.NoError(t, run(t, state, "conf plc4xpcapanalyzer-debug off").Err)
	assert.False(t, state.Debug)
}

func TestHistoryListsTheRememberedCommands(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	assert.Contains(t, run(t, state, "history").Lines[0], "no commands")

	state.Config.RememberCommand("open a.pcap")
	state.Config.RememberCommand("analyze c-bus a.pcap")
	joined := strings.Join(run(t, state, "history").Lines, "\n")
	assert.Contains(t, joined, "open a.pcap")
	assert.Contains(t, joined, "analyze c-bus a.pcap")
}

func TestClearTargetsTheRightPane(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())

	all := run(t, state, "clear")
	assert.True(t, all.ClearPackets && all.ClearLog && all.ClearTranscript)

	assert.True(t, run(t, state, "clear message").ClearPackets)
	assert.False(t, run(t, state, "clear message").ClearLog)
	assert.True(t, run(t, state, "clear console").ClearLog)
	assert.True(t, run(t, state, "clear command").ClearTranscript)
}

func TestQuitAndAbortAreEffectsRatherThanActions(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	assert.True(t, run(t, state, "quit").Quit)
	assert.True(t, run(t, state, "abort").Abort)
}

func TestHelpListsEveryCommand(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	joined := strings.Join(run(t, state, "help").Lines, "\n")
	for _, name := range []string{"analyze", "extract", "conf", "auto-register", "plc4x-conf", "abort"} {
		assert.Contains(t, joined, name)
	}
}

func TestCompletionsAreWholeLines(t *testing.T) {
	state, demo := demoState(t)

	for _, testCase := range []struct {
		line string
		want string
	}{
		{"an", "analyze"},
		{"conf ", "conf list"},
		{"conf a", "conf auto-register"},
		{"log s", "log set"},
		{"log set de", "log set debug"},
		{"register c", "register c-bus"},
		{"analyze c", "analyze c-bus"},
		{"analyze c-bus ", "analyze c-bus " + demo.Path},
	} {
		t.Run(testCase.line, func(t *testing.T) {
			candidates := Root().Completions(state, testCase.line)
			assert.Contains(t, candidates, testCase.want)
			for _, candidate := range candidates {
				assert.True(t, strings.HasPrefix(strings.ToLower(candidate), strings.ToLower(strings.TrimRight(testCase.line, " "))),
					"%q is not a replacement for %q; textinput matches a candidate against the whole line", candidate, testCase.line)
			}
		})
	}
}

// TestCompletionsAreSilentOnAnEmptyLine keeps a menu of every command from appearing under the
// prompt the moment it takes focus.
func TestCompletionsAreSilentOnAnEmptyLine(t *testing.T) {
	state := NewState(t.TempDir(), NewConfig())
	assert.Empty(t, Root().Completions(state, ""))
	assert.Empty(t, Root().Completions(state, "   "))
}

func TestOpenCompletesCaptureFilesAndDirectories(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "trace.pcap"), []byte("x"), 0o600))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "notes.txt"), []byte("x"), 0o600))
	require.NoError(t, os.Mkdir(filepath.Join(dir, "more"), 0o755))
	state := NewState(dir, NewConfig())

	candidates := Root().Completions(state, "open ")
	assert.Contains(t, candidates, "open trace.pcap")
	assert.Contains(t, candidates, "open more")
	assert.NotContains(t, candidates, "open notes.txt", "a text file is not a capture")
}

func TestCdCompletesOnlyDirectories(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "trace.pcap"), []byte("x"), 0o600))
	require.NoError(t, os.Mkdir(filepath.Join(dir, "more"), 0o755))
	state := NewState(dir, NewConfig())

	candidates := Root().Completions(state, "cd ")
	assert.Equal(t, []string{"cd more"}, candidates)
}

func TestValidateDriverNamesTheKnownOnes(t *testing.T) {
	err := ValidateDriver("modbus")
	require.Error(t, err)
	for _, driver := range DriverNames {
		assert.Contains(t, err.Error(), driver)
	}
	assert.True(t, slices.Contains(DriverNames, "c-bus"))
}
