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
	"testing"
	"time"

	tea "charm.land/bubbletea/v2"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	cliConfig "github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
)

// Test helpers.
//
// Two pieces of process-global state have to be pinned in every test that touches an analysis:
// the CLI configuration singletons, which are where the analysis options live, and the global
// zerolog logger, which the C-Bus analyzer writes several lines per packet to.

// TestMain silences the global logger for the whole package.
//
// The plc4x driver registration and the C-Bus analyzer both log several lines per call through
// it, and a test run that prints a thousand JSON records buries its own failures.
func TestMain(m *testing.M) {
	zerolog.SetGlobalLevel(zerolog.Disabled)
	os.Exit(m.Run())
}

// testTheme is the theme every view test renders with: no colour and the ASCII glyph set, so
// that an assertion on the content is an assertion on the content and a width is a count of
// characters.
func testTheme() tui.Options {
	return tui.Options{Dark: true, NoColor: true, ASCII: true}
}

// fixedClock is a clock that does not move, so an elapsed time is whatever the test says it is.
func fixedClock() func() time.Time {
	base := time.Date(2024, 1, 1, 12, 0, 0, 0, time.UTC)
	return func() time.Time { return base }
}

// pinGlobals saves and restores the configuration singletons and the logger.
func pinGlobals(t *testing.T) {
	t.Helper()
	savedRoot := cliConfig.RootConfigInstance
	savedPcap := cliConfig.PcapConfigInstance
	savedAnalyze := cliConfig.AnalyzeConfigInstance
	savedExtract := cliConfig.ExtractConfigInstance
	savedBacnet := cliConfig.BacnetConfigInstance
	savedCBus := cliConfig.CBusConfigInstance
	savedLevel := zerolog.GlobalLevel()
	t.Cleanup(func() {
		cliConfig.RootConfigInstance = savedRoot
		cliConfig.PcapConfigInstance = savedPcap
		cliConfig.AnalyzeConfigInstance = savedAnalyze
		cliConfig.ExtractConfigInstance = savedExtract
		cliConfig.BacnetConfigInstance = savedBacnet
		cliConfig.CBusConfigInstance = savedCBus
		zerolog.SetGlobalLevel(savedLevel)
	})
	// The C-Bus analyzer logs at debug for every packet, which would bury a test's own output.
	zerolog.SetGlobalLevel(zerolog.Disabled)
	cliConfig.RootConfigInstance.HideProgressBar = true
}

// demoState builds a session with a generated demo capture open, in a directory that the test
// framework removes.
func demoState(t *testing.T) (*State, Demo) {
	t.Helper()
	pinGlobals(t)
	demo, err := NewDemoIn(t.TempDir())
	require.NoError(t, err)

	state := NewState(demo.Dir, NewConfig())
	state.Demo = true
	state.Protocol = demo.Protocol
	state.HostIP = demo.Client
	_, err = state.Open(demo.Path)
	require.NoError(t, err)
	return state, demo
}

// newTestModel builds a model at a size, with a fixed clock and a stable theme.
func newTestModel(t *testing.T, state *State, size tui.Size) Model {
	t.Helper()
	model := NewModel(Options{Theme: testTheme(), State: state, Clock: fixedClock()})
	return send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height})
}

// send delivers one message and returns the model that came back.
func send(model Model, msg tea.Msg) Model {
	next, _ := model.Update(msg)
	return next.(Model)
}

// sendWithCmd delivers one message and returns the model and the command it produced.
func sendWithCmd(model Model, msg tea.Msg) (Model, tea.Cmd) {
	next, cmd := model.Update(msg)
	return next.(Model), cmd
}

// charKey builds the key press a terminal sends for a printable character. Text has to be set
// as well as Code, because the key name is taken from Text and that is what key.Matches
// compares against.
func charKey(r rune) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: r, Text: string(r)}
}

// namedKey builds the key press for a named key, which carries no text.
func namedKey(code rune) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: code}
}

// modKey builds a modified key press, such as alt+3 or shift+tab.
func modKey(code rune, mod tea.KeyMod) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: code, Mod: mod}
}
