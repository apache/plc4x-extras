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

package tui

import (
	"strconv"
	"strings"
	"testing"
	"time"

	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// testCommands is the command set the completion tests draw on. They share a "re" prefix on
// purpose, so that a partial line has several candidates and the ordering of the completion
// list is observable.
var testCommands = []string{"read", "reconnect", "reset", "restart", "write"}

// passthroughSuggest is what a real SuggestFunc looks like at its simplest: it offers the
// whole command set and lets textinput do the prefix matching, which is the contract
// documented on SuggestFunc.
func passthroughSuggest(commands []string) SuggestFunc {
	return func(string) []string { return commands }
}

// fakeClock is the injected clock. The elapsed indication is asserted on, so it must not come
// from the wall clock.
type fakeClock struct {
	at time.Time
}

func (c *fakeClock) Now() time.Time { return c.at }

func (c *fakeClock) advance(d time.Duration) { c.at = c.at.Add(d) }

// newTestPrompt builds a focused prompt of a known width with the ASCII glyph set and no
// colour, so that assertions on rendered text are stable.
func newTestPrompt(t *testing.T, suggest SuggestFunc) Prompt {
	t.Helper()
	prompt := NewPrompt(NewTheme(Options{NoColor: true, ASCII: true}), suggest)
	prompt.SetWidth(80)
	prompt.Focus()
	require.True(t, prompt.Focused())
	return prompt
}

// typeText feeds a line in one character at a time, as a terminal would.
func typeText(prompt Prompt, text string) Prompt {
	for _, r := range text {
		prompt, _ = prompt.Update(charKey(r))
	}
	return prompt
}

// press sends one key and discards the command.
func press(prompt Prompt, msg tea.KeyPressMsg) Prompt {
	prompt, _ = prompt.Update(msg)
	return prompt
}

func TestTypingAtThePromptEntersTextRatherThanFiringShortcuts(t *testing.T) {
	prompt := newTestPrompt(t, nil)

	prompt = typeText(prompt, "read holding-register:1")

	assert.Equal(t, "read holding-register:1", prompt.Value())
}

// TestUpAndDownWalkTheCompletionListWhileItIsOpen covers the first half of the rule that
// resolves the collision with textinput's own up and down bindings.
func TestUpAndDownWalkTheCompletionListWhileItIsOpen(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "re")

	require.True(t, prompt.CompletionOpen())
	require.Equal(t, []string{"read", "reconnect", "reset", "restart"}, prompt.Completions())
	require.Equal(t, 0, prompt.CompletionIndex())

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, 1, prompt.CompletionIndex(), "down must move through the completion list")

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, 2, prompt.CompletionIndex())

	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, 1, prompt.CompletionIndex(), "up must move back through the completion list")

	// Walking the list does not commit anything to the line, and it does not touch the
	// history: the line the user typed is still the line.
	assert.Equal(t, "re", prompt.Value())
	assert.Empty(t, prompt.History())
}

// TestUpAndDownWalkTheHistoryWhileTheCompletionListIsClosed covers the second half of the
// same rule.
func TestUpAndDownWalkTheHistoryWhileTheCompletionListIsClosed(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "read a")
	prompt = press(prompt, namedKey(tea.KeyEnter))
	prompt = typeText(prompt, "write b 1")
	prompt = press(prompt, namedKey(tea.KeyEnter))

	require.Equal(t, []string{"read a", "write b 1"}, prompt.History())
	require.False(t, prompt.CompletionOpen(), "running a line closes the completion list")

	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, "write b 1", prompt.Value(), "up must recall the newest line first")

	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, "read a", prompt.Value())

	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, "read a", prompt.Value(), "the walk must stop at the oldest line, not wrap")

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, "write b 1", prompt.Value())

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, "", prompt.Value(), "walking forward out of the history returns the live line")

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, "", prompt.Value(), "the walk must stop at the live line, not wrap")
}

func TestHistoryWalkDoesNotLoseTheLineInProgress(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "read a")
	prompt = press(prompt, namedKey(tea.KeyEnter))

	// "part" matches nothing in the command set, so the completion list is closed and up is
	// unambiguously history.
	prompt = typeText(prompt, "part")
	require.False(t, prompt.CompletionOpen())

	prompt = press(prompt, namedKey(tea.KeyUp))
	require.Equal(t, "read a", prompt.Value())

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, "part", prompt.Value(), "the half-typed line must come back")
}

func TestARecalledLineKeepsUpOnHistoryRatherThanHandingItToCompletion(t *testing.T) {
	// A recalled line is usually a whole command, so it matches candidates. If the recall
	// re-opened the completion list, the next up press would jump to the candidate list and
	// strand the user half way through the history.
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	for _, line := range []string{"read", "reset"} {
		prompt = typeText(prompt, line)
		prompt = press(prompt, namedKey(tea.KeyEnter))
	}

	prompt = press(prompt, namedKey(tea.KeyUp))
	require.Equal(t, "reset", prompt.Value())
	require.False(t, prompt.CompletionOpen(), "a recall must not open the completion list")

	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, "read", prompt.Value(), "up must still be walking the history")
}

func TestEscapeClosesTheCompletionListAndThenBelongsToTheModel(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "re")
	require.True(t, prompt.CompletionOpen())
	require.True(t, prompt.ConsumesEscape(), "the first esc is the prompt's, to close the list")

	prompt = press(prompt, namedKey(tea.KeyEsc))
	assert.False(t, prompt.CompletionOpen())
	assert.Equal(t, "re", prompt.Value(), "closing the list must not clear the line")
	assert.False(t, prompt.ConsumesEscape(), "the next esc belongs to the model's dismiss chain")

	// With the list closed, up is history again rather than completion.
	prompt = typeText(prompt, "ad")
	prompt = press(prompt, namedKey(tea.KeyEnter))
	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, "read", prompt.Value())
}

func TestCtrlNAndCtrlPAlwaysMeanTheCompletionList(t *testing.T) {
	// The pair that never changes meaning, so there is always a way to reach the list even
	// with up and down committed to the history.
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "re")
	prompt = press(prompt, namedKey(tea.KeyEsc))
	require.False(t, prompt.CompletionOpen())

	prompt = press(prompt, ctrlKey('n'))
	require.True(t, prompt.CompletionOpen(), "ctrl+n must reopen the list")
	assert.Equal(t, 0, prompt.CompletionIndex(), "the first press only makes the list visible")

	prompt = press(prompt, ctrlKey('n'))
	assert.Equal(t, 1, prompt.CompletionIndex())

	prompt = press(prompt, ctrlKey('p'))
	assert.Equal(t, 0, prompt.CompletionIndex())
}

func TestTabAcceptsTheHighlightedCandidateAndClosesTheList(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "re")
	prompt = press(prompt, namedKey(tea.KeyDown))
	require.Equal(t, 1, prompt.CompletionIndex())

	prompt = press(prompt, namedKey(tea.KeyTab))

	assert.Equal(t, "reconnect", prompt.Value())
	assert.False(t, prompt.CompletionOpen(), "accepting a candidate closes the list")
}

func TestSubmitEmitsTheLineClearsTheFieldAndRemembersIt(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt = typeText(prompt, "  read holding-register:1  ")
	prompt, cmd := prompt.Update(namedKey(tea.KeyEnter))

	require.NotNil(t, cmd)
	submitted, ok := cmd().(PromptSubmitMsg)
	require.True(t, ok, "running a line must emit a PromptSubmitMsg")
	assert.Equal(t, "read holding-register:1", submitted.Line, "the line is trimmed before it is run")

	assert.Equal(t, "", prompt.Value())
	assert.Equal(t, []string{"read holding-register:1"}, prompt.History())
}

func TestSubmittingAnEmptyLineRunsNothingAndRemembersNothing(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))

	prompt, cmd := prompt.Update(namedKey(tea.KeyEnter))

	assert.Nil(t, cmd)
	assert.Empty(t, prompt.History())
}

func TestHistoryIsBoundedAndDropsTheOldestLines(t *testing.T) {
	prompt := newTestPrompt(t, nil)
	prompt.SetHistoryLimit(3)

	for i := range 5 {
		prompt = typeText(prompt, "read tag"+strconv.Itoa(i))
		prompt = press(prompt, namedKey(tea.KeyEnter))
	}

	assert.Equal(t, []string{"read tag2", "read tag3", "read tag4"}, prompt.History())

	// The walk still reaches the oldest surviving line and stops there.
	for range 5 {
		prompt = press(prompt, namedKey(tea.KeyUp))
	}
	assert.Equal(t, "read tag2", prompt.Value())
}

func TestARepeatedLineIsRememberedOnce(t *testing.T) {
	// Running the same read five times to watch a value change would otherwise fill the
	// history with five identical entries and make up useless.
	prompt := newTestPrompt(t, nil)

	for range 3 {
		prompt = typeText(prompt, "read a")
		prompt = press(prompt, namedKey(tea.KeyEnter))
	}
	prompt = typeText(prompt, "read b")
	prompt = press(prompt, namedKey(tea.KeyEnter))

	assert.Equal(t, []string{"read a", "read b"}, prompt.History())
}

func TestHistoryReturnedToCallersCannotBeUsedToEditThePrompt(t *testing.T) {
	prompt := newTestPrompt(t, nil)
	prompt = typeText(prompt, "read a")
	prompt = press(prompt, namedKey(tea.KeyEnter))

	stolen := prompt.History()
	stolen[0] = "rm -rf"

	assert.Equal(t, []string{"read a"}, prompt.History())
}

func TestPromptWithoutASuggestFuncNeverOpensCompletionAndKeepsUpOnHistory(t *testing.T) {
	prompt := newTestPrompt(t, nil)

	prompt = typeText(prompt, "read a")
	prompt = press(prompt, namedKey(tea.KeyEnter))
	assert.False(t, prompt.CompletionOpen())
	assert.Empty(t, prompt.Completions())

	prompt = press(prompt, namedKey(tea.KeyDown))
	assert.Equal(t, "", prompt.Value(), "down at the live line is a no-op")

	prompt = press(prompt, namedKey(tea.KeyUp))
	assert.Equal(t, "read a", prompt.Value())
}

// TestPromptRendersExactlyOneLineOfTheGivenWidth pins the invariant the responsive rules are
// built around: the prompt is never dropped and never clipped, whatever the terminal size.
// The UI this replaces hardcoded a thirty-column field, which clipped any real connection
// string as it was typed.
func TestPromptRendersExactlyOneLineOfTheGivenWidth(t *testing.T) {
	longLine := "read c-bus://192.168.178.101?srchk=false holding-register:1234:UINT[8]"

	for _, width := range []int{MinWidth, 72, 80, 90, 120, 200} {
		t.Run("w"+strconv.Itoa(width), func(t *testing.T) {
			for _, line := range []string{"", "read", longLine} {
				prompt := newTestPrompt(t, passthroughSuggest(testCommands))
				prompt.SetWidth(width)
				prompt = typeText(prompt, line)

				view := prompt.View()

				assert.NotContains(t, view, "\n", "the prompt is one row in every layout")
				assert.Equal(t, width, lipgloss.Width(view), "the prompt fills its width exactly")
				assert.Contains(t, view, "$", "the label must survive")
			}
		})
	}
}

// deterministicLine builds a line of exactly n cells whose every position is identifiable, so
// that a clipped field cannot accidentally satisfy an assertion about the whole line.
func deterministicLine(n int) string {
	const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
	var out strings.Builder
	for i := range n {
		out.WriteByte(alphabet[i%len(alphabet)])
	}
	return out.String()
}

// TestThePromptFieldSpansTheAvailableWidth is the regression test for the other half of the
// prompt defect: the UI this replaces called SetFieldWidth(30), so a real connection string
// was clipped as it was typed and the user could not see what they had entered.
func TestThePromptFieldSpansTheAvailableWidth(t *testing.T) {
	for _, width := range []int{60, 80, 120} {
		t.Run("w"+strconv.Itoa(width), func(t *testing.T) {
			// Three cells go to the "$ " label and the cursor; everything else is the field.
			line := deterministicLine(width - 3)

			prompt := newTestPrompt(t, nil)
			prompt.SetWidth(width)
			prompt = typeText(prompt, line)

			view := prompt.View()

			require.Equal(t, line, prompt.Value())
			assert.Contains(t, view, line, "the whole line must be visible, not a fixed-width window onto it")
			assert.Equal(t, width, lipgloss.Width(view))

			// Past that, the field scrolls rather than growing, so the end the user is typing
			// at stays visible and the row keeps its width.
			prompt = typeText(prompt, "TAIL")
			scrolled := prompt.View()
			assert.Contains(t, scrolled, "TAIL")
			assert.Equal(t, width, lipgloss.Width(scrolled))
		})
	}
}

func TestPromptShowsTheSpinnerAndElapsedTimeWhileACommandIsInFlight(t *testing.T) {
	clock := &fakeClock{at: time.Unix(1700000000, 0).UTC()}
	prompt := newTestPrompt(t, nil)
	prompt.SetClock(clock.Now)

	assert.NotContains(t, prompt.View(), "esc cancel", "an idle prompt advertises no abort")

	prompt.Start()
	require.True(t, prompt.Busy())
	clock.advance(1500 * time.Millisecond)

	prompt, cmd := prompt.Update(PromptTickMsg{Time: clock.at})
	require.NotNil(t, cmd, "a busy prompt must keep the spinner turning")

	frames := ASCIIGlyphs().Spinner
	view := prompt.View()
	assert.Contains(t, view, frames[1], "one tick advances one frame")
	assert.Contains(t, view, "1.5s")
	assert.Contains(t, view, "esc cancel")
	assert.Equal(t, 80, lipgloss.Width(view), "the in-flight indication must not widen the row")

	prompt.Finish()
	clock.advance(time.Hour)
	assert.Zero(t, prompt.Elapsed(), "a finished command has no elapsed time")
	assert.NotContains(t, prompt.View(), "esc cancel")

	// An idle prompt stops re-arming the tick, so an idle tool does not spin forever.
	_, idleCmd := prompt.Update(PromptTickMsg{Time: clock.at})
	assert.Nil(t, idleCmd)
}

func TestElapsedIsFormattedForTheOneRowTheresRoomFor(t *testing.T) {
	for _, testCase := range []struct {
		elapsed time.Duration
		want    string
	}{
		{0, "0.0s"},
		{99 * time.Millisecond, "0.0s"},
		{1500 * time.Millisecond, "1.5s"},
		{59900 * time.Millisecond, "59.9s"},
		{time.Minute, "1m00s"},
		{91 * time.Second, "1m31s"},
		{125 * time.Minute, "125m00s"},
	} {
		t.Run(testCase.want, func(t *testing.T) {
			assert.Equal(t, testCase.want, formatElapsed(testCase.elapsed))
		})
	}
}

func TestCompletionViewMarksTheHighlightedCandidateAndSaysWhatItHid(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))
	prompt = typeText(prompt, "re")
	require.Len(t, prompt.Completions(), 4)

	glyphs := ASCIIGlyphs()

	full := prompt.CompletionView(4)
	lines := strings.Split(full, "\n")
	require.Len(t, lines, 4)
	assert.Contains(t, lines[0], glyphs.Selected+" read")
	assert.NotContains(t, lines[1], glyphs.Selected)

	// A window shorter than the list has to say so, or a truncated list looks complete.
	windowed := prompt.CompletionView(2)
	windowedLines := strings.Split(windowed, "\n")
	require.Len(t, windowedLines, 3)
	assert.Contains(t, windowedLines[2], "2 more")

	for _, line := range append(lines, windowedLines...) {
		assert.LessOrEqual(t, lipgloss.Width(line), prompt.Width(), "the completion list must not overflow")
	}

	// The window follows the highlight, so a selection past the bottom stays visible.
	prompt = press(prompt, namedKey(tea.KeyDown))
	prompt = press(prompt, namedKey(tea.KeyDown))
	require.Equal(t, 2, prompt.CompletionIndex())
	scrolled := strings.Split(prompt.CompletionView(2), "\n")
	assert.Contains(t, scrolled[1], glyphs.Selected+" reset")

	assert.Empty(t, prompt.CompletionView(0))
}

func TestClosedCompletionListRendersNothing(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))
	prompt = typeText(prompt, "re")
	prompt = press(prompt, namedKey(tea.KeyEsc))

	assert.Empty(t, prompt.CompletionView(5))
	assert.Empty(t, prompt.Completions())
}

func TestABlurredPromptIgnoresKeysAndDropsTheCompletionList(t *testing.T) {
	prompt := newTestPrompt(t, passthroughSuggest(testCommands))
	prompt = typeText(prompt, "re")
	require.True(t, prompt.CompletionOpen())

	prompt.Blur()
	assert.False(t, prompt.CompletionOpen(), "a list under an unfocused field lies about where input goes")

	prompt = typeText(prompt, "ad")
	assert.Equal(t, "re", prompt.Value(), "a blurred prompt must not swallow the pane's keys")
	assert.False(t, prompt.ConsumesEscape())
}
