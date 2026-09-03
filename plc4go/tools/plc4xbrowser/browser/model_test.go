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

package browser

import (
	"strings"
	"testing"
	"time"

	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// The model is driven directly with typed messages: no terminal, no goroutines, no sleeps.
// That is the point of the port. The implementation this replaces could not be tested at all,
// because its state lived in global tview widgets mutated from goroutines.

// testTheme is colourless and ASCII, so rendered output is stable and measurable.
func testTheme() tui.Theme {
	return tui.NewTheme(tui.Options{Dark: true, NoColor: true, ASCII: true})
}

// newTestModel builds a model over a connected demo session.
func newTestModel(t *testing.T) *Model {
	t.Helper()

	session := plcsession.NewDemo(plcsession.DemoOptions{
		SubscriptionInterval: time.Millisecond,
		Now:                  steppingClock(),
	})
	t.Cleanup(func() { _ = session.Close() })
	_, err := session.RegisterDriver(plcsession.DemoProtocol)
	require.NoError(t, err)
	_, err = session.Connect(t.Context(), plcsession.DemoDeviceOne)
	require.NoError(t, err)

	config := NewConfig()
	theme := testTheme()
	model := NewModel(Options{
		Session: session,
		Config:  &config,
		Demo:    true,
		Version: "1.0.0-TEST",
		Theme:   &theme,
		Now:     steppingClock(),
	})
	return model
}

// sized runs Init and drives a window-size message through the model, which is the sequence
// the runtime performs before any user input. Skipping Init leaves the prompt unfocused, and
// an unfocused text input silently discards every keystroke.
func sized(t *testing.T, model *Model, width, height int) *Model {
	t.Helper()
	model.Init()
	next, _ := model.Update(tea.WindowSizeMsg{Width: width, Height: height})
	updated, ok := next.(*Model)
	require.True(t, ok)
	return updated
}

// press sends a key and returns the command it produced.
func press(t *testing.T, model *Model, key tea.KeyPressMsg) tea.Cmd {
	t.Helper()
	_, cmd := model.Update(key)
	return cmd
}

// runCmd executes a command and feeds its message back, which is what the runtime does.
func runCmd(t *testing.T, model *Model, cmd tea.Cmd) {
	t.Helper()
	require.NotNil(t, cmd, "expected the model to return a command")
	msg := cmd()
	require.NotNil(t, msg, "expected the command to produce a message")
	model.Update(msg)
}

// renderLines renders the screen and returns its lines.
func renderLines(t *testing.T, model *Model) []string {
	t.Helper()
	view := model.View()
	assert.True(t, view.AltScreen, "the browser is a full-screen application")
	return strings.Split(view.Content, "\n")
}

// --- geometry ---

// TestEveryRenderedLineIsExactlyTheTerminalWidth is the property every multi-pane layout rests
// on. One line a cell too wide tears every row beneath it.
func TestEveryRenderedLineIsExactlyTheTerminalWidth(t *testing.T) {
	for _, size := range []tui.Size{
		{Width: 120, Height: 40},
		{Width: 100, Height: 30},
		{Width: 100, Height: 24},
		{Width: 90, Height: 24},
		{Width: 80, Height: 24},
		{Width: 70, Height: 20},
		{Width: 60, Height: 12},
	} {
		t.Run(size.String(), func(t *testing.T) {
			model := sized(t, newTestModel(t), size.Width, size.Height)
			lines := renderLines(t, model)

			assert.Len(t, lines, size.Height, "the screen must be exactly the terminal height")
			for i, line := range lines {
				assert.Equal(t, size.Width, lipgloss.Width(line),
					"line %d is %d cells, want %d: %q", i, lipgloss.Width(line), size.Width, line)
			}
		})
	}
}

// TestThePromptIsAlwaysVisible is the regression test for the defect that motivated the whole
// port: below 100 columns the previous UI added its command area with a zero row and column
// span, which the grid discarded, so the input was never drawn and the user typed blind.
func TestThePromptIsAlwaysVisible(t *testing.T) {
	for _, size := range []tui.Size{
		{Width: 200, Height: 50},
		{Width: 100, Height: 24},
		{Width: 80, Height: 24},
		{Width: 70, Height: 16},
		{Width: 60, Height: 12},
	} {
		t.Run(size.String(), func(t *testing.T) {
			model := sized(t, newTestModel(t), size.Width, size.Height)
			joined := strings.Join(renderLines(t, model), "\n")
			assert.Contains(t, joined, "$", "the command prompt must be on screen at %s", size)
		})
	}
}

// TestTheHelpFooterAlwaysAdvertisesQuit guards against bubbles/help ellipsizing the tail away
// at narrow widths, which would hide the one binding a stuck user needs.
func TestTheHelpFooterAlwaysAdvertisesQuit(t *testing.T) {
	for _, width := range []int{120, 100, 80, 70, 60} {
		t.Run(tui.Size{Width: width, Height: 24}.String(), func(t *testing.T) {
			model := sized(t, newTestModel(t), width, 24)
			joined := strings.Join(renderLines(t, model), "\n")
			assert.Contains(t, joined, "quit", "the footer must still advertise quit at %d columns", width)
		})
	}
}

// TestTheStatusBarDegradesRatherThanOverflowing pins the fix for a header that is about 91
// cells as designed and therefore does not fit the 80-column target.
func TestTheStatusBarDegradesRatherThanOverflowing(t *testing.T) {
	for _, width := range []int{120, 100, 80, 70, 60} {
		t.Run(tui.Size{Width: width, Height: 24}.String(), func(t *testing.T) {
			model := sized(t, newTestModel(t), width, 24)
			status := renderLines(t, model)[0]
			assert.Equal(t, width, lipgloss.Width(status))
			assert.Contains(t, status, "PLC4X Browser", "the tool's name is the one part that never drops")
		})
	}
}

func TestATerminalBelowTheMinimumSaysSoRatherThanDrawingSomethingBroken(t *testing.T) {
	model := sized(t, newTestModel(t), 40, 8)
	joined := strings.Join(renderLines(t, model), "\n")
	assert.Contains(t, joined, tui.TooSmallMessage())
}

// TestTheDemoBadgeIsShown matters because a user must never mistake simulated values for
// readings from real hardware.
func TestTheDemoBadgeIsShown(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	assert.Contains(t, renderLines(t, model)[0], "DEMO")
}

func TestWithoutDemoThereIsNoBadge(t *testing.T) {
	session := plcsession.NewDemo(plcsession.DemoOptions{})
	t.Cleanup(func() { _ = session.Close() })
	config := NewConfig()
	theme := testTheme()
	model := NewModel(Options{Session: session, Config: &config, Theme: &theme, Now: steppingClock()})
	model = sized(t, model, 120, 30)
	assert.NotContains(t, renderLines(t, model)[0], "DEMO")
}

// --- focus ---

// TestFocusStartsOnThePrompt: these are REPLs, so the command line has the keyboard by default.
func TestFocusStartsOnThePrompt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, promptFocused := model.Focused()
	assert.True(t, promptFocused)
}

// TestTabWalksThePaneRingAndReturnsToThePrompt guards against a focus ring that can strand the
// user away from the command line.
func TestTabWalksThePaneRingAndReturnsToThePrompt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	// Shift+tab from the prompt enters the ring at the far end and walks back to the prompt.
	press(t, model, tea.KeyPressMsg{Code: tea.KeyTab, Mod: tea.ModShift})
	_, promptFocused := model.Focused()
	require.False(t, promptFocused, "shift+tab should leave the prompt")

	// Walking forward past the last pane must land back on the prompt rather than wrapping
	// endlessly through the panes.
	for range int(paneCount) + 1 {
		press(t, model, tea.KeyPressMsg{Code: tea.KeyTab, Mod: tea.ModShift})
	}
	_, promptFocused = model.Focused()
	assert.True(t, promptFocused, "the ring must return to the prompt")
}

// TestEscapeReturnsFocusToThePrompt covers the gap the design review found: no binding brought
// the keyboard back from a pane.
func TestEscapeReturnsFocusToThePrompt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	press(t, model, tea.KeyPressMsg{Code: tea.KeyTab, Mod: tea.ModShift})
	require.False(t, mustPromptFocused(model))

	press(t, model, tea.KeyPressMsg{Code: tea.KeyEscape})
	assert.True(t, mustPromptFocused(model), "escape must bring the keyboard back to the prompt")
}

// mustPromptFocused reports whether the prompt holds the keyboard.
func mustPromptFocused(model *Model) bool {
	_, promptFocused := model.Focused()
	return promptFocused
}

// TestTypingACommandNameDoesNotTriggerPaneActions is the scoping rule in practice: the letters
// of "quit" include q, which is a pane binding.
func TestTypingACommandNameDoesNotTriggerPaneActions(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	for _, r := range "quit" {
		press(t, model, tea.KeyPressMsg{Code: r, Text: string(r)})
	}
	assert.False(t, model.Quitting(), "typing at the prompt must not fire pane bindings")
	assert.Equal(t, "quit", model.PromptValue())
}

// --- commands ---

func TestSubmittingACommandRunsItAndRecordsIt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)

	shown, total := model.EventCount()
	assert.Positive(t, total, "browsing the demo device should produce messages")
	assert.Equal(t, shown, total)
	assert.Contains(t, strings.Join(model.LogLines(), "\n"), "browse-direct")
}

func TestAFailedCommandRaisesAToastThatSurvivesUntilDismissed(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct demo://nowhere temp/1"})
	runCmd(t, model, cmd)

	require.NotEmpty(t, model.Toast(), "a failed command must leave a visible error")
	assert.Contains(t, model.Toast(), "not connected")

	// It must still be on screen: the old UI let errors scroll away in a ten-row console.
	assert.Contains(t, strings.Join(renderLines(t, model), "\n"), "not connected")

	press(t, model, tea.KeyPressMsg{Code: tea.KeyEscape})
	assert.Empty(t, model.Toast(), "escape dismisses the toast")
}

func TestReadDirectShowsTheValueInTheDetailPane(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)

	event, ok := model.SelectedEvent()
	require.True(t, ok, "the new message should be selected")
	require.Len(t, event.Tags, 1)
	assert.Equal(t, "temp/1", event.Tags[0].Address)

	joined := strings.Join(renderLines(t, model), "\n")
	assert.Contains(t, joined, "temp/1", "the tag should appear on screen")
}

func TestQuitCommandShutsDown(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "quit"})
	runCmd(t, model, cmd)
	assert.True(t, model.Quitting())
}

func TestClearEmptiesTheMessageTable(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	_, total := model.EventCount()
	require.Positive(t, total)

	_, cmd = model.Update(tui.PromptSubmitMsg{Line: "clear messages"})
	runCmd(t, model, cmd)
	_, total = model.EventCount()
	assert.Zero(t, total)
}

func TestTheMessageFilterNarrowsTheTableWithoutLosingTheEvents(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	_, total := model.EventCount()
	require.Greater(t, total, 1)

	model.SetFilter("temp")
	shown, stillTotal := model.EventCount()
	assert.Equal(t, total, stillTotal, "filtering must not discard events")
	assert.Less(t, shown, total, "the filter must actually narrow the table")
	assert.Positive(t, shown)

	model.SetFilter("")
	shown, _ = model.EventCount()
	assert.Equal(t, total, shown)
}

// --- subscriptions ---

// TestASubscriptionStreamsIntoTheMessageTable exercises the re-arming command that drains a
// channel without any goroutine touching model state.
func TestASubscriptionStreamsIntoTheMessageTable(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "subscribe-direct " + plcsession.DemoDeviceOne + " temp/1"})
	require.NotNil(t, cmd)
	msg := cmd()
	done, ok := msg.(commandDoneMsg)
	require.True(t, ok)
	require.NoError(t, done.err)
	require.NotNil(t, done.result.Stream)

	_, streamCmd := model.Update(done)
	require.NotNil(t, streamCmd, "the model must arm a reader for the stream")

	// One turn of the loop: the reader yields an event, which the model folds in and re-arms.
	before, _ := model.EventCount()
	eventMsg := streamCmd()
	_, again := model.Update(eventMsg)
	after, _ := model.EventCount()

	assert.Greater(t, after, before, "a subscription event must reach the table")
	assert.NotNil(t, again, "the reader must re-arm itself")

	// Shutting down must cancel the subscription rather than leak it.
	model.Update(tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	assert.True(t, model.Quitting())
}

// --- composer ---

func TestTheComposerOpensForEachOfTheFourCommands(t *testing.T) {
	for _, name := range []string{"read", "write", "browse", "subscribe"} {
		t.Run(name, func(t *testing.T) {
			model := sized(t, newTestModel(t), 120, 30)
			_, cmd := model.Update(tui.PromptSubmitMsg{Line: name + " " + plcsession.DemoDeviceOne})
			runCmd(t, model, cmd)
			assert.True(t, model.ComposerOpen(), "%s must open the request composer", name)

			joined := strings.Join(renderLines(t, model), "\n")
			assert.Contains(t, strings.ToLower(joined), "request", "the form should be on screen")
		})
	}
}

func TestTheComposerValidatesBeforeSubmitting(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	// The pre-filled row has no address yet, so submitting must complain rather than send.
	press(t, model, tea.KeyPressMsg{Code: tea.KeyEnter})
	assert.Contains(t, model.ComposerProblem(), "address")
	assert.True(t, model.ComposerOpen(), "an invalid form stays open")
}

func TestTheComposerRunsAValidRequest(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	_, submitCmd := model.Update(tea.KeyPressMsg{Code: tea.KeyEnter})
	require.NotNil(t, submitCmd)
	model.Update(submitCmd())

	shown, _ := model.EventCount()
	assert.Positive(t, shown, "the composed read must produce a message")
}

func TestEscapeClosesTheComposerAndReturnsToThePrompt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	press(t, model, tea.KeyPressMsg{Code: tea.KeyEscape})
	assert.False(t, model.ComposerOpen())
	assert.True(t, mustPromptFocused(model))
}

func TestTheComposerCanGrowATagRow(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	before := len(model.composer.rows)
	press(t, model, tea.KeyPressMsg{Code: 'n', Mod: tea.ModCtrl})
	assert.Equal(t, before+1, len(model.composer.rows), "ctrl+n adds a tag row")
}

// TestTheComposerOverlaysWithoutBreakingTheGeometry: the modal is composited over the panes,
// and compositing must not change the screen's shape.
func TestTheComposerOverlaysWithoutBreakingTheGeometry(t *testing.T) {
	model := sized(t, newTestModel(t), 100, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	lines := renderLines(t, model)
	assert.Len(t, lines, 30)
	for i, line := range lines {
		assert.Equal(t, 100, lipgloss.Width(line), "line %d: %q", i, line)
	}
}

// --- sidebar ---

// TestSelectingASidebarRowDoesSomething is the regression test for the dead affordances: both
// handlers in the previous UI contained only a TODO comment.
func TestSelectingASidebarRowDoesSomething(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	// Enter the pane ring and land on the sidebar.
	press(t, model, tea.KeyPressMsg{Code: tea.KeyTab, Mod: tea.ModShift})
	for {
		focus, promptFocused := model.Focused()
		if !promptFocused && focus == paneSidebar {
			break
		}
		press(t, model, tea.KeyPressMsg{Code: tea.KeyTab, Mod: tea.ModShift})
	}

	row, ok := model.SidebarSelection()
	require.True(t, ok)
	require.True(t, row.selectable())

	press(t, model, tea.KeyPressMsg{Code: tea.KeyEnter})
	assert.NotEmpty(t, model.PromptValue(), "selecting a row must do something, not nothing")
}

func TestTheSidebarCursorSkipsSectionHeadings(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	for range 20 {
		row, ok := model.SidebarSelection()
		require.True(t, ok)
		assert.True(t, row.selectable(), "the cursor must never rest on a heading")
		model.moveSidebar(1)
	}
}

func TestTheSidebarShowsAnEmptyStateNamingTheNextCommand(t *testing.T) {
	session := plcsession.NewDemo(plcsession.DemoOptions{})
	t.Cleanup(func() { _ = session.Close() })
	config := NewConfig()
	theme := testTheme()
	model := NewModel(Options{Session: session, Config: &config, Theme: &theme, Now: steppingClock()})
	model = sized(t, model, 120, 30)

	joined := strings.Join(renderLines(t, model), "\n")
	assert.Contains(t, joined, "connect", "an empty pane should name the command that fills it")
}

// --- background colour ---

// TestTheThemeFollowsTheTerminalBackground covers the only reliable way to stay legible.
func TestTheThemeFollowsTheTerminalBackground(t *testing.T) {
	session := plcsession.NewDemo(plcsession.DemoOptions{})
	t.Cleanup(func() { _ = session.Close() })
	config := NewConfig()
	// No Theme override, so the model derives one from the reported background.
	model := NewModel(Options{Session: session, Config: &config, Now: steppingClock()})
	model = sized(t, model, 120, 30)

	dark := model.theme.IsDark()
	model.Update(tea.BackgroundColorMsg{Color: lipgloss.Color("#ffffff")})
	assert.NotEqual(t, dark, model.theme.IsDark(), "a light background must switch the palette")
}
