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
	"strconv"
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
	return newTestModelWithTheme(t, testTheme())
}

// newTestModelWithTheme is newTestModel with the theme chosen by the caller, so that a test can
// exercise the coloured themes rather than only the plain one the assertions read.
func newTestModelWithTheme(t *testing.T, theme tui.Theme) *Model {
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
// quit takes the tool through the quit question, which every key-driven exit now goes through.
func quit(t *testing.T, model *Model) {
	t.Helper()
	press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	require.True(t, model.ConfirmingQuit(), "ctrl+c should ask before ending the session")
	press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
}

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
	// A mix of tags, so a filter has something to exclude. A browse is a single message now,
	// so it cannot supply the variety on its own.
	for _, address := range []string{"temp/1", "press/1", "temp/2", "flow/1"} {
		_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " " + address})
		runCmd(t, model, cmd)
	}
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

	// Shutting down must cancel the subscription rather than leak it. Quitting is confirmed,
	// so it takes the question and then the answer.
	quit(t, model)
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

// TestTheComposerClosesWhenItsRequestAnswers is the regression test for a form that stayed on
// screen showing "running…" forever: the message carrying the answer was applied to the model
// but nothing ever cleared the submitting flag or dismissed the form.
func TestTheComposerClosesWhenItsRequestAnswers(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	_, submit := model.Update(tea.KeyPressMsg{Code: tea.KeyEnter})
	require.NotNil(t, submit)
	model.Update(submit())

	assert.False(t, model.ComposerOpen(), "a completed request must dismiss its form")
	assert.True(t, mustPromptFocused(model), "and hand the keyboard back to the prompt")

	joined := strings.Join(renderLines(t, model), "\n")
	assert.NotContains(t, joined, "running", "the form must not be left showing a running request")

	shown, _ := model.EventCount()
	assert.Positive(t, shown, "the result must still reach the message table")
}

// TestAFailedComposerRequestKeepsTheFormOpenWithTheReason: the point of a form is not having to
// retype it, so a failure must not throw the request away.
func TestAFailedComposerRequestKeepsTheFormOpenWithTheReason(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	// label/1 exists but cannot be subscribed to, so the request fails at the session.
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "subscribe " + plcsession.DemoDeviceOne + " label/1"})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	_, submit := model.Update(tea.KeyPressMsg{Code: tea.KeyEnter})
	require.NotNil(t, submit)
	model.Update(submit())

	require.True(t, model.ComposerOpen(), "a failed request must leave the form open to be corrected")
	assert.NotEmpty(t, model.ComposerProblem(), "and it must say why")
	assert.NotContains(t, strings.Join(renderLines(t, model), "\n"), "running",
		"the form must not still claim to be running")
}

// TestPaneHotkeysJumpDirectly covers the navigation that was missing: the keymap declared
// alt+1..alt+4 but the model never acted on them, so the only way between four panes was
// cycling with tab.
func TestPaneHotkeysJumpDirectly(t *testing.T) {
	for digit, want := range map[rune]pane{
		'1': paneSidebar,
		'2': paneMessages,
		'3': paneDetail,
		'4': paneLog,
	} {
		t.Run(string(digit), func(t *testing.T) {
			model := sized(t, newTestModel(t), 120, 30)
			// alt+N works from the prompt; the bare digit would be text there, by design.
			// Text must be left empty on a modified key: with Text set, String() returns the
			// text and the modifier is lost, which is also what a terminal does -- alt+1 sends
			// an escape sequence, not the character.
			press(t, model, tea.KeyPressMsg{Code: digit, Mod: tea.ModAlt})

			focus, promptFocused := model.Focused()
			assert.False(t, promptFocused, "a pane hotkey must move the keyboard out of the prompt")
			assert.Equal(t, want, focus)
		})
	}
}

// TestABareDigitJumpsFromAnEmptyPromptAndIsTextOtherwise pins the compromise that makes pane
// hotkeys actually reachable. alt+digit is the nominal binding, but GNOME Terminal, Konsole and
// Windows Terminal all bind alt+digit to switching terminal tabs and never deliver it, so a
// bare digit has to work too -- and it can, safely, wherever it cannot be text.
func TestABareDigitJumpsFromAnEmptyPromptAndIsTextOtherwise(t *testing.T) {
	t.Run("empty prompt jumps", func(t *testing.T) {
		model := sized(t, newTestModel(t), 120, 30)
		require.Empty(t, model.PromptValue())

		press(t, model, tea.KeyPressMsg{Code: '2', Text: "2"})
		focus, promptFocused := model.Focused()
		assert.False(t, promptFocused, "a digit typed at an empty prompt is a pane hotkey")
		assert.Equal(t, paneMessages, focus)
		assert.Empty(t, model.PromptValue(), "and it must not leave the digit behind as text")
	})

	t.Run("mid-command it is text", func(t *testing.T) {
		model := sized(t, newTestModel(t), 120, 30)
		for _, r := range "read-direct demo://plant-" {
			press(t, model, tea.KeyPressMsg{Code: r, Text: string(r)})
		}
		press(t, model, tea.KeyPressMsg{Code: '2', Text: "2"})

		assert.True(t, mustPromptFocused(model), "a digit inside a command is text, not a hotkey")
		assert.Equal(t, "read-direct demo://plant-2", model.PromptValue())
	})
}

// TestCtrlDEndsTheSessionOnAnEmptyLine is the shell rule, and the reason ctrl+d could not
// simply be added to the quit binding: bubbles/textinput owns it for delete-forward.
func TestCtrlDEndsTheSessionOnAnEmptyLine(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	require.Empty(t, model.PromptValue())

	press(t, model, tea.KeyPressMsg{Code: 'd', Mod: tea.ModCtrl})
	require.True(t, model.ConfirmingQuit(), "ctrl+d on an empty line offers to end the session")
	assert.False(t, model.Quitting(), "but not before it is answered")

	press(t, model, tea.KeyPressMsg{Code: 'y', Text: "y"})
	assert.True(t, model.Quitting(), "ctrl+d on an empty line ends the session, as in any shell")
}

func TestCtrlDWithTextTypedDoesNotQuit(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	for _, r := range "help" {
		press(t, model, tea.KeyPressMsg{Code: r, Text: string(r)})
	}
	require.NotEmpty(t, model.PromptValue())

	press(t, model, tea.KeyPressMsg{Code: 'd', Mod: tea.ModCtrl})
	assert.False(t, model.Quitting(),
		"with a line in progress ctrl+d belongs to the text input, not to quitting")
}

func TestCtrlDQuitsFromAPaneRegardlessOfThePrompt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	for _, r := range "help" {
		press(t, model, tea.KeyPressMsg{Code: r, Text: string(r)})
	}
	// Move into a pane: there is nothing to delete there, so ctrl+d is unambiguous. A pane
	// hotkey rather than shift+tab, because with a line typed the completion list is open and
	// shift+tab belongs to it.
	press(t, model, tea.KeyPressMsg{Code: '2', Mod: tea.ModAlt})
	require.False(t, mustPromptFocused(model))

	press(t, model, tea.KeyPressMsg{Code: 'd', Mod: tea.ModCtrl})
	require.True(t, model.ConfirmingQuit())
	press(t, model, tea.KeyPressMsg{Code: 'y', Text: "y"})
	assert.True(t, model.Quitting())
}

// TestTheByteViewSaysWhyDemoModeHasNoFrames is the honest half of the wire-byte view: a
// simulated device puts nothing on a wire, and inventing bytes for it would be worse than
// saying there are none.
func TestTheByteViewSaysWhyDemoModeHasNoFrames(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)
	require.Positive(t, func() int { shown, _ := model.EventCount(); return shown }())

	rendered := renderEventFrames(model.theme, mustSelectedEvent(t, model), nil)
	assert.Contains(t, rendered, "no wire capture")
	assert.Contains(t, rendered, "demo mode", "it must say why, not just that there is nothing")
}

// TestTheByteViewShowsTheFramesOfARequest covers the association: the frames that crossed the
// transport while the request was in flight.
func TestTheByteViewShowsTheFramesOfARequest(t *testing.T) {
	base := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameOutbound, base.Add(-time.Second), []byte("earlier"))
	log.Add(plcsession.FrameOutbound, base.Add(time.Millisecond), []byte("~~~\r"))
	log.Add(plcsession.FrameInbound, base.Add(2*time.Millisecond), []byte("322100AD\r\n"))
	log.Add(plcsession.FrameInbound, base.Add(time.Minute), []byte("later"))

	event := plcsession.Event{
		Kind:       plcsession.EventRead,
		Connection: "c-bus://10.0.0.5",
		Started:    base,
		Received:   base.Add(5 * time.Millisecond),
	}

	theme := testTheme()
	rendered := renderEventFrames(theme, event, log)

	assert.Contains(t, rendered, "sent", "an outbound frame must be labelled")
	assert.Contains(t, rendered, "received")
	// The hex of "~~~\r" is 7e 7e 7e 0d.
	assert.Contains(t, rendered, "7e 7e 7e 0d", "the request's own bytes must be shown")
	assert.NotContains(t, rendered, "earlier", "a frame before the request must not be attributed to it")
	assert.NotContains(t, rendered, "later", "nor one after it")
	assert.Contains(t, rendered, "not one protocol message",
		"the pane must say what a frame is, since a transport has no message framing")
}

func TestTheByteViewSaysWhenNothingCrossedDuringTheRequest(t *testing.T) {
	base := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameInbound, base.Add(time.Hour), []byte("unrelated"))

	event := plcsession.Event{Started: base, Received: base.Add(time.Millisecond)}
	rendered := renderEventFrames(testTheme(), event, log)
	assert.Contains(t, rendered, "no frames captured")
	assert.NotContains(t, rendered, "unrelated")
}

// TestTheDetailPaneTogglesToBytes covers the key that switches the view.
func TestTheDetailPaneTogglesToBytes(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)

	// Focus the detail pane directly, then toggle.
	press(t, model, tea.KeyPressMsg{Code: '3', Mod: tea.ModAlt})
	focus, _ := model.Focused()
	require.Equal(t, paneDetail, focus)

	require.False(t, model.detailBytes)
	press(t, model, tea.KeyPressMsg{Code: 'b', Text: "b"})
	assert.True(t, model.detailBytes, "b must switch the detail pane to the wire bytes")

	press(t, model, tea.KeyPressMsg{Code: 'b', Text: "b"})
	assert.False(t, model.detailBytes, "and back again")
}

// mustSelectedEvent returns the highlighted message or fails.
func mustSelectedEvent(t *testing.T, model *Model) plcsession.Event {
	t.Helper()
	event, ok := model.SelectedEvent()
	require.True(t, ok, "a message should be selected")
	return event
}

// TestAHexDumpLinesUpItsColumns keeps a short final row from shifting the text column.
func TestAHexDumpLinesUpItsColumns(t *testing.T) {
	theme := testTheme()
	lines := hexDump(theme, []byte("hello world!!"))
	require.Len(t, lines, 2, "thirteen bytes at eight per row is two rows")

	// The hex column is padded so the text column starts at the same offset on every row. The
	// text itself is naturally shorter on a short final row, so the line widths differ; what
	// has to line up is where the text begins.
	assert.Equal(t, strings.Index(lines[0], "|"), strings.Index(lines[1], "|"),
		"a short final row must pad its hex column so the text column lines up:\n%s\n%s", lines[0], lines[1])
	assert.Contains(t, lines[0], "|hello wo|")
	assert.Contains(t, lines[1], "|rld!!|")
}

func TestAHexDumpMarksUnprintableBytes(t *testing.T) {
	lines := hexDump(testTheme(), []byte{0x00, 0x1f, 0x41, 0x7f})
	require.Len(t, lines, 1)
	assert.Contains(t, lines[0], "00 1f 41 7f")
	assert.Contains(t, lines[0], "|..A.|", "only printable ASCII belongs in the text column")
}

// TestPaneTitlesShowTheirJumpKey is the discoverability half of the hotkey fix: the numbers
// were unreachable partly because nothing on screen said which number belonged to which pane.
func TestPaneTitlesShowTheirJumpKey(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	joined := strings.Join(renderLines(t, model), "\n")

	for i, name := range []string{"Session", "Messages", "Detail", "Log"} {
		label := "[" + strconv.Itoa(i+1) + "] " + name
		assert.Contains(t, joined, label, "the pane title must advertise its jump key")
	}
}

// TestEveryPaneNumberActuallyJumpsToTheNamedPane keeps the drawn label and the binding in
// step: a title saying [3] that jumps somewhere else would be worse than no label.
func TestEveryPaneNumberActuallyJumpsToTheNamedPane(t *testing.T) {
	for i, want := range []pane{paneSidebar, paneMessages, paneDetail, paneLog} {
		digit := rune('1' + i)
		t.Run(string(digit), func(t *testing.T) {
			model := sized(t, newTestModel(t), 120, 30)
			press(t, model, tea.KeyPressMsg{Code: digit, Text: string(digit)})

			focus, promptFocused := model.Focused()
			require.False(t, promptFocused)
			assert.Equal(t, want, focus)
			assert.Contains(t, paneTitle(focus), "["+strconv.Itoa(i+1)+"]",
				"the pane reached must be the one the label names")
		})
	}
}
