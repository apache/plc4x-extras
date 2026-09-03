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
	"fmt"
	"strconv"
	"strings"
	"testing"

	"charm.land/bubbles/v2/key"
	"charm.land/bubbles/v2/table"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tuitest"
)

// The browser advertised seven keys in its help and implemented none of them: g/G, the page
// keys, the filter, yank, follow, expand and the log level. That is the dead-affordance defect
// the port existed to remove, reintroduced in the footer, so each one is pinned here.

// inPane focuses a pane by its number and returns the model.
func inPane(t *testing.T, model *Model, digit rune) *Model {
	t.Helper()
	press(t, model, tea.KeyPressMsg{Code: digit, Mod: tea.ModAlt})
	_, promptFocused := model.Focused()
	require.False(t, promptFocused, "pane %c should have the keyboard", digit)
	return model
}

// withMessages returns a model carrying several distinct messages.
func withMessages(t *testing.T, addresses ...string) *Model {
	t.Helper()
	model := sized(t, newTestModel(t), 120, 30)
	for _, address := range addresses {
		_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " " + address})
		runCmd(t, model, cmd)
	}
	return model
}

// TestEveryAdvertisedPaneKeyIsHandled is the guard against the help lying again. It walks the
// bindings the footer actually shows and asserts the model reacts to each.
func TestEveryAdvertisedPaneKeyIsHandled(t *testing.T) {
	keys := tui.NewKeyMap().ForFocus(tui.FocusPane)
	advertised := map[string]key.Binding{
		"Top": keys.Top, "Bottom": keys.Bottom,
		"PageUp": keys.PageUp, "PageDown": keys.PageDown,
		"Filter": keys.Filter, "Wrap": keys.Wrap, "Yank": keys.Yank,
		"Follow": keys.Follow, "Expand": keys.Expand, "LogLevel": keys.LogLevel,
		"Select": keys.Select, "Up": keys.Up, "Down": keys.Down,
	}

	// Select is left out: enter on the already-selected row is idempotent by design, so a
	// "something changed" assertion cannot express it. It has its own tests, as does the
	// sidebar's use of it.
	delete(advertised, "Select")

	for name, binding := range advertised {
		t.Run(name, func(t *testing.T) {
			require.NotEmpty(t, binding.Keys(), "%s is advertised, so it must be bound", name)

			model := withMessages(t, "temp/1", "press/1", "flow/1")
			model = inPane(t, model, '2')
			// Park the cursor mid-list so that both ends have somewhere to go: following keeps
			// it on the last row, where G is legitimately a no-op.
			press(t, model, tea.KeyPressMsg{Code: 'g', Text: "g"})
			press(t, model, tea.KeyPressMsg{Code: 'j', Text: "j"})
			before := snapshot(model)

			press(t, model, tea.KeyPressMsg{Code: rune(binding.Keys()[0][0]), Text: binding.Keys()[0]})
			assert.NotEqual(t, before, snapshot(model),
				"%s (%v) changed nothing; the footer advertises it, so it must do something",
				name, binding.Keys())
		})
	}
}

// snapshot captures the observable state a key press could plausibly change.
func snapshot(m *Model) string {
	shown, total := m.EventCount()
	focus, promptFocused := m.Focused()
	return strings.Join([]string{
		strconv.Itoa(shown), strconv.Itoa(total), strconv.Itoa(int(focus)),
		strconv.FormatBool(promptFocused), strconv.FormatBool(m.Following()),
		strconv.FormatBool(m.Filtering()), strconv.FormatBool(m.detailBytes),
		strconv.FormatBool(m.toastExpanded), m.options.Config.LogLevel,
		strconv.Itoa(m.messages.Cursor()), m.yanked, m.PromptValue(),
		strconv.FormatBool(m.ComposerOpen()),
	}, "|")
}

func TestTopAndBottomMoveTheMessageCursor(t *testing.T) {
	model := withMessages(t, "temp/1", "press/1", "flow/1", "temp/2")
	model = inPane(t, model, '2')

	press(t, model, tea.KeyPressMsg{Code: 'g', Text: "g"})
	assert.Zero(t, model.messages.Cursor(), "g goes to the first message")

	press(t, model, tea.KeyPressMsg{Code: 'G', Text: "G"})
	shown, _ := model.EventCount()
	assert.Equal(t, shown-1, model.messages.Cursor(), "G goes to the last")
}

func TestTopStopsFollowingSoTheCursorStaysPut(t *testing.T) {
	model := withMessages(t, "temp/1", "press/1")
	model = inPane(t, model, '2')
	require.True(t, model.Following())

	press(t, model, tea.KeyPressMsg{Code: 'g', Text: "g"})
	assert.False(t, model.Following(), "moving to the top by hand means taking over from the stream")
}

func TestFollowKeepsTheNewestMessageSelected(t *testing.T) {
	model := withMessages(t, "temp/1", "press/1")
	model = inPane(t, model, '2')

	// Turn following off, move away, then a new message must not steal the selection.
	press(t, model, tea.KeyPressMsg{Code: 'f', Text: "f"})
	require.False(t, model.Following())
	press(t, model, tea.KeyPressMsg{Code: 'g', Text: "g"})
	at := model.messages.Cursor()

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " flow/1"})
	runCmd(t, model, cmd)
	assert.Equal(t, at, model.messages.Cursor(), "with following off a new message must not move the cursor")

	press(t, model, tea.KeyPressMsg{Code: 'f', Text: "f"})
	require.True(t, model.Following())
	shown, _ := model.EventCount()
	assert.Equal(t, shown-1, model.messages.Cursor(), "turning it back on jumps to the newest")
}

func TestTheFilterNarrowsAsItIsTypedAndEscapeClearsIt(t *testing.T) {
	model := withMessages(t, "temp/1", "press/1", "temp/2")
	model = inPane(t, model, '2')
	_, total := model.EventCount()

	press(t, model, tea.KeyPressMsg{Code: '/', Text: "/"})
	require.True(t, model.Filtering(), "/ starts the filter line")

	for _, r := range "press" {
		press(t, model, tea.KeyPressMsg{Code: r, Text: string(r)})
	}
	shown, stillTotal := model.EventCount()
	assert.Less(t, shown, total, "the list must narrow as the filter is typed")
	assert.Equal(t, total, stillTotal, "and nothing may be discarded")

	press(t, model, tea.KeyPressMsg{Code: tea.KeyEnter})
	assert.False(t, model.Filtering(), "enter commits the filter")

	press(t, model, tea.KeyPressMsg{Code: '/', Text: "/"})
	press(t, model, tea.KeyPressMsg{Code: tea.KeyEscape})
	shown, _ = model.EventCount()
	assert.Equal(t, total, shown, "escape clears the filter, showing everything again")
}

func TestTheFilterLineTakesPrecedenceOverPaneKeys(t *testing.T) {
	model := withMessages(t, "temp/1")
	model = inPane(t, model, '2')
	press(t, model, tea.KeyPressMsg{Code: '/', Text: "/"})

	// "g" would be a top-of-list jump outside the filter; inside it, it is text.
	press(t, model, tea.KeyPressMsg{Code: 'g', Text: "g"})
	assert.True(t, model.Filtering(), "the filter line keeps the keyboard")
	assert.Equal(t, "g", model.filterDraft)
}

func TestYankCopiesTheSelectedMessage(t *testing.T) {
	model := withMessages(t, "temp/1")
	model = inPane(t, model, '2')

	_, cmd := model.Update(tea.KeyPressMsg{Code: 'y', Text: "y"})
	require.NotNil(t, cmd, "y must produce a clipboard command")
	assert.NotEmpty(t, model.yanked)
	assert.Contains(t, model.yanked, "temp/1", "the copied text must carry the tag")
	// tea.SetClipboard writes an OSC52 sequence, which works over ssh as well as locally.
	assert.NotNil(t, cmd())
}

func TestLogLevelCyclesAndIsRecorded(t *testing.T) {
	model := withMessages(t, "temp/1")
	model = inPane(t, model, '2')

	first := model.options.Config.LogLevel
	press(t, model, tea.KeyPressMsg{Code: 'L', Text: "L"})
	second := model.options.Config.LogLevel
	assert.NotEqual(t, first, second, "L must change the level")
	assert.Contains(t, strings.Join(model.LogLines(), "\n"), "log level "+second,
		"and say so, since the change is otherwise invisible")
}

func TestExpandTogglesTheToast(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct demo://nowhere temp/1"})
	runCmd(t, model, cmd)
	require.NotEmpty(t, model.Toast())

	model = inPane(t, model, '2')
	require.False(t, model.toastExpanded)
	press(t, model, tea.KeyPressMsg{Code: 'e', Text: "e"})
	assert.True(t, model.toastExpanded, "e must expand the error")
}

// TestRWSComposeFromTheSelectedMessage is the workflow fix: browse, see a tag, act on it
// without retyping the address the pane is already showing.
func TestRWSComposeFromTheSelectedMessage(t *testing.T) {
	for pressed, want := range map[rune]Operation{
		'r': OperationRead,
		'w': OperationWrite,
		's': OperationSubscribe,
	} {
		t.Run(string(pressed), func(t *testing.T) {
			model := withMessages(t, "temp/1")
			model = inPane(t, model, '2')

			press(t, model, tea.KeyPressMsg{Code: pressed, Text: string(pressed)})
			require.True(t, model.ComposerOpen(), "%c must open the composer", pressed)
			assert.Equal(t, want, model.composer.operation)
			assert.Equal(t, plcsession.DemoDeviceOne, model.composer.connection)
			require.Len(t, model.composer.rows, 1)
			assert.Equal(t, "temp/1", model.composer.rows[0].address,
				"the tag must be carried over rather than retyped")
		})
	}
}

func TestComposingFromABrowseCarriesEveryTag(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	model = inPane(t, model, '2')

	press(t, model, tea.KeyPressMsg{Code: 'r', Text: "r"})
	require.True(t, model.ComposerOpen())
	assert.Greater(t, len(model.composer.rows), 1,
		"reading a browse result should offer every tag it found, not just the first")
}

// TestTheMessageColumnsFitThePaneExactly is the regression test for a table that claimed 48
// cells in a 40-cell pane at the tool's own reference size. It truncated, and what it
// truncated was the tag -- the field the row exists to show.
func TestTheMessageColumnsFitThePaneExactly(t *testing.T) {
	for _, width := range []int{24, 30, 38, 40, 48, 58, 70, 78, 96, 140} {
		for _, showConnection := range []bool{false, true} {
			name := strconv.Itoa(width)
			if showConnection {
				name += "+conn"
			}
			t.Run(name, func(t *testing.T) {
				total := 0
				tagWidth := 0
				for _, column := range messageColumns(width, showConnection) {
					total += column.Width
					assert.Positive(t, column.Width, "%q must not be given zero width", column.Title)
					if column.Title == "tag" {
						tagWidth = column.Width
					}
				}
				assert.Equal(t, width, total,
					"the columns must use exactly the pane's width, not more")
				assert.GreaterOrEqual(t, tagWidth, 8,
					"the tag is what the row is for; it must never be squeezed below eight cells")
			})
		}
	}
}

// TestTheConnectionColumnOnlyAppearsWhenItDistinguishesSomething: with one connection open it
// would repeat the same string on every row while the tag paid for the space.
func TestTheConnectionColumnOnlyAppearsWhenItDistinguishesSomething(t *testing.T) {
	hasConnection := func(columns []table.Column) bool {
		for _, column := range columns {
			if column.Title == "connection" {
				return true
			}
		}
		return false
	}

	assert.False(t, hasConnection(messageColumns(140, false)),
		"one connection open: the column would say the same thing on every row")
	assert.True(t, hasConnection(messageColumns(140, true)),
		"several connections: now it tells rows apart")
	assert.False(t, hasConnection(messageColumns(44, true)),
		"narrow: the tag keeps the space even when there are connections to distinguish")
}

func TestTheModelOffersTheConnectionColumnOnlyWithSeveralConnections(t *testing.T) {
	model := sized(t, newTestModel(t), 140, 30)
	assert.False(t, model.showConnectionColumn(), "the fixture opens one connection")

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "connect " + plcsession.DemoDeviceTwo})
	runCmd(t, model, cmd)
	assert.True(t, model.showConnectionColumn())
}

// TestABrowseIsOneMessageCarryingEveryTag is the flooding fix: one message per tag meant
// browsing an eight-tag device pushed eight rows in and shoved real events off the screen.
func TestABrowseIsOneMessageCarryingEveryTag(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)

	shown, total := model.EventCount()
	assert.Equal(t, 1, total, "a browse is one message, however many tags it found")
	assert.Equal(t, 1, shown)

	event, ok := model.SelectedEvent()
	require.True(t, ok)
	assert.Greater(t, len(event.Tags), 1, "and it carries every tag it found")
	assert.Equal(t, plcsession.EventBrowse, event.Kind)
}

// TestTheSidebarKeepsTheBrowseCatalogue: it is a reference you refer back to while reading
// individual tags, which is why it is not simply the selected message's tags -- that would
// show the same list the detail pane is already showing.
func TestTheSidebarKeepsTheBrowseCatalogue(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)

	// While the browse is the selected message the detail pane is already showing this list, so
	// the sidebar stays out of the way rather than printing the same eight tags a second time.
	rendered := strings.Join(renderLines(t, model), "\n")
	assert.NotContains(t, rendered, "TAGS ",
		"the sidebar must not repeat the list the detail pane is showing:\n%s", rendered)

	// Move on to a read: now the catalogue earns its space as a reference.
	_, cmd = model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " temp/1"})
	runCmd(t, model, cmd)
	rendered = strings.Join(renderLines(t, model), "\n")
	assert.Contains(t, rendered, "TAGS ", "once the selection moves on, the catalogue is a reference")
	assert.Contains(t, rendered, "motor/speed", "and it lists the tags the browse found")
}

// TestTheStatusBarCountsFailures: a message count says nothing about whether any failed, which
// is the one number worth glancing at.
func TestTheStatusBarCountsFailures(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	status := renderLines(t, model)[0]
	assert.NotContains(t, status, "failed", "nothing has failed yet")

	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " no/such/tag"})
	runCmd(t, model, cmd)

	assert.Equal(t, 1, model.FailedCount())
	assert.Contains(t, renderLines(t, model)[0], "1 failed")
}

// TestTheDetailPaneDoesNotRepeatItself pins the de-duplication: the header already names the
// operation and the connection, and the log carries the timing line verbatim.
func TestTheDetailPaneDoesNotRepeatItself(t *testing.T) {
	model := withMessages(t, "temp/1")
	event, ok := model.SelectedEvent()
	require.True(t, ok)

	rendered := renderEventDetail(testTheme(), event, 40)
	occurrences := strings.Count(rendered, plcsession.DemoDeviceOne)
	assert.Equal(t, 1, occurrences,
		"the connection should be named once, not in a header and again in a summary:\n%s", rendered)
	assert.NotContains(t, rendered, "tags in",
		"the timing sentence belongs in the log, not repeated in the detail body")
	assert.Contains(t, rendered, "r read", "the detail pane should advertise what can be done with the tag")
}

// --- aborting, and the quit question ---

// TestCompletionOffersTagsAsSoonAsTheConnectionIsFollowedByASpace is the reported bug: the tag
// list only appeared once a letter of a tag had been typed.
//
// The cause was that Resolve trimmed the argument text, which destroyed the one thing that
// distinguishes a word still being typed from a word that is finished.
func TestCompletionOffersTagsAsSoonAsTheConnectionIsFollowedByASpace(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	line := "read-direct " + plcsession.DemoDeviceOne
	for _, r := range line {
		press(t, model, tea.KeyPressMsg{Code: r, Text: string(r)})
	}
	// Still being typed: the connections are what to suggest, and the one open connection is
	// already complete, so the only candidate is the line itself.
	require.Equal(t, []string{line}, model.PromptCompletions())

	press(t, model, tea.KeyPressMsg{Code: ' ', Text: " "})

	completions := model.PromptCompletions()
	require.NotEmpty(t, completions, "a space after the connection should offer the tags")
	assert.Len(t, completions, len(plcsession.DemoTagAddresses()),
		"every tag in the catalogue should be offered")
	for _, address := range plcsession.DemoTagAddresses() {
		assert.Contains(t, completions, line+" "+address)
	}
	assert.True(t, model.PromptCompletionOpen(), "and the list should be showing")

	// And tab then takes the first of them, rather than needing a letter typed first.
	press(t, model, tea.KeyPressMsg{Code: tea.KeyTab})
	assert.Equal(t, completions[0], model.PromptValue())
}

// TestCtrlCAbortsTheCommandRatherThanTheSession is the rule the prompt has always advertised
// while a command runs. It used to be a lie twice over: nothing cancelled the command, and
// ctrl+c ended the session and took every open connection with it.
func TestCtrlCAbortsTheCommandRatherThanTheSession(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)

	// Submitting without running the returned command leaves it in flight, which is the state
	// a read against an unreachable device sits in for the driver's whole timeout.
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " temp/1"})
	require.NotNil(t, cmd)
	require.True(t, model.PromptBusy(), "the prompt should show the command as running")

	press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})

	assert.False(t, model.Quitting(), "ctrl+c during a command must not end the session")
	assert.False(t, model.ConfirmingQuit(), "nor ask to")
	assert.False(t, model.PromptBusy(), "it releases the prompt")
	assert.Contains(t, strings.Join(model.LogLines(), "\n"), "aborted")

	// The outcome is still on its way back. Applying it would contradict the abort, so it is
	// dropped: cancelling a context does not stop the goroutine that will deliver the message.
	before, _ := model.EventCount()
	runCmd(t, model, cmd)
	after, _ := model.EventCount()
	assert.Equal(t, before, after, "an aborted command's outcome must not be applied")

	// And now that nothing is running, ctrl+c means the session again.
	press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	assert.True(t, model.ConfirmingQuit(), "with nothing running ctrl+c asks to quit")
}

// TestEscAlsoAbortsARunningCommand covers the other half of what the prompt advertises: the
// in-flight indication names esc, not ctrl+c.
func TestEscAlsoAbortsARunningCommand(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " temp/1"})
	require.NotNil(t, cmd)
	require.True(t, model.PromptBusy())

	press(t, model, tea.KeyPressMsg{Code: tea.KeyEscape})

	assert.False(t, model.PromptBusy(), "esc aborts the command it says it aborts")
	assert.False(t, model.Quitting())
}

// TestQuitAsksFirstAndOnlyYesAnswersIt is why the question exists: q and ctrl+c are both easy
// to hit by accident, and the answer to a mistaken one should not be a closed session.
func TestQuitAsksFirstAndOnlyYesAnswersIt(t *testing.T) {
	for name, answer := range map[string]tea.KeyPressMsg{
		"y":            {Code: 'y', Text: "y"},
		"Y":            {Code: 'Y', Text: "Y"},
		"enter":        {Code: tea.KeyEnter},
		"another ctrl": {Code: 'c', Mod: tea.ModCtrl},
	} {
		model := sized(t, newTestModel(t), 120, 30)
		press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
		require.True(t, model.ConfirmingQuit(), name)
		require.False(t, model.Quitting(), "%s: asking is not quitting", name)

		press(t, model, answer)
		assert.True(t, model.Quitting(), "%s should answer the question", name)
	}
}

// TestAnyOtherKeyKeepsTheSession is the point of asking. The refusing key is swallowed rather
// than passed on, so it cannot both dismiss the question and do something unseen.
func TestAnyOtherKeyKeepsTheSession(t *testing.T) {
	for name, refusal := range map[string]tea.KeyPressMsg{
		"esc":   {Code: tea.KeyEscape},
		"n":     {Code: 'n', Text: "n"},
		"slash": {Code: '/', Text: "/"},
		"digit": {Code: '2', Text: "2"},
	} {
		model := sized(t, newTestModel(t), 120, 30)
		press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
		require.True(t, model.ConfirmingQuit(), name)

		press(t, model, refusal)

		assert.False(t, model.Quitting(), "%s must not quit", name)
		assert.False(t, model.ConfirmingQuit(), "%s takes the question down", name)
		assert.Empty(t, model.PromptValue(), "%s must not also reach the text field", name)
		assert.True(t, mustPromptFocused(model), "%s must not also move the keyboard", name)
	}
}

// TestTheQuitQuestionIsOnScreen matters because a modal state that swallows every key has to
// say so: the row above the prompt asks, and the footer says what answers it.
func TestTheQuitQuestionIsOnScreen(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	press(t, model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	require.True(t, model.ConfirmingQuit())

	screen := model.render()
	assert.Contains(t, screen, "quit?")
	assert.Contains(t, screen, "stay", "the footer has to say how to refuse")
	assert.NotContains(t, screen, "tab complete",
		"and must not advertise bindings that are inert until the question is answered")
}

// --- the Detail pane's own hints ---

// TestEveryDetailHintKeyWorksAtDetailFocus is the guard the footer's equivalent could not give.
//
// The Detail pane draws its own action line, and r, w and s were bound only at Messages focus:
// the pane advertised three keys that did nothing in the pane doing the advertising, which is
// the dead-affordance defect this port existed to remove. The test walks exactly what the pane
// draws, so a key cannot be advertised there without being handled there.
func TestEveryDetailHintKeyWorksAtDetailFocus(t *testing.T) {
	require.NotEmpty(t, detailActions, "the hint line has to advertise something")

	for _, action := range detailActions {
		model := browsed(t)
		focusDetail(t, model)
		require.Contains(t, tuitest.Strip(model.render()), action.Key+" "+action.Label,
			"the pane should be advertising %q", action.Key)

		_, cmd := model.Update(charKeyPress(action.Key))
		acted := model.ComposerOpen() || cmd != nil
		assert.True(t, acted, "%s (%s) does nothing at detail focus", action.Key, action.Label)
	}
}

// TestActingOnABrowseFromTheDetailPane is the workflow the hint promises: browse, read the tag
// list in Detail, and act on it from there without retyping an address.
func TestActingOnABrowseFromTheDetailPane(t *testing.T) {
	for key, operation := range map[string]Operation{
		"r": OperationRead,
		"w": OperationWrite,
		"s": OperationSubscribe,
	} {
		model := browsed(t)
		focusDetail(t, model)

		press(t, model, charKeyPress(key))
		require.True(t, model.ComposerOpen(), "%s should open the composer from the detail pane", key)

		spec := model.ComposerSpec()
		assert.Equal(t, operation, spec.Operation, "%s should compose a %s", key, operation)
		assert.Equal(t, plcsession.DemoDeviceOne, spec.Connection,
			"%s should carry the browsed connection", key)
		require.Len(t, spec.Tags, len(plcsession.DemoTagAddresses()),
			"%s should carry every tag the browse found", key)
		addresses := make([]string, 0, len(spec.Tags))
		for _, tag := range spec.Tags {
			addresses = append(addresses, tag.Address)
		}
		assert.ElementsMatch(t, plcsession.DemoTagAddresses(), addresses,
			"%s should not have to be told the addresses again", key)
	}
}

// TestAReadComposedFromTheDetailPaneActuallyRuns closes the loop: the composer the hint opens
// has to produce a request, not just a form.
func TestAReadComposedFromTheDetailPaneActuallyRuns(t *testing.T) {
	model := browsed(t)
	before, _ := model.EventCount()

	focusDetail(t, model)
	press(t, model, charKeyPress("r"))
	require.True(t, model.ComposerOpen())

	_, cmd := model.Update(tea.KeyPressMsg{Code: tea.KeyEnter})
	require.NotNil(t, cmd, "enter should run the composed request")
	runCmd(t, model, cmd)

	after, _ := model.EventCount()
	assert.Greater(t, after, before, "the composed read has to reach the message list")
	assert.False(t, model.ComposerOpen(), "and the form closes once it has done its job")
	assert.Empty(t, model.Toast(), "with no error held")
}

// TestYankFromTheDetailPaneCopiesWhatItShows covers the fourth hint, which is the one that
// produces a command rather than a form.
func TestYankFromTheDetailPaneCopiesWhatItShows(t *testing.T) {
	model := browsed(t)
	focusDetail(t, model)

	_, cmd := model.Update(charKeyPress("y"))
	require.NotNil(t, cmd, "y should produce a clipboard command")

	// bubbletea's clipboard message is unexported, so what it carries is read as text rather
	// than type-asserted. The OSC52 payload is the whole point of the assertion either way.
	copied := fmt.Sprint(cmd())
	for _, address := range plcsession.DemoTagAddresses() {
		assert.Contains(t, copied, address, "the yank should carry every tag shown")
	}
}

// browsed returns a model with one browse in its history, which is what puts a tag list in the
// Detail pane for the hint keys to act on.
//
// Tall enough that the pane can show the whole catalogue and its hint line beneath: the hint
// sits below the tags, so at a shorter height it is simply scrolled out of view.
func browsed(t *testing.T) *Model {
	t.Helper()
	model := sized(t, newTestModel(t), 120, 44)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	require.NotNil(t, cmd)
	runCmd(t, model, cmd)
	return model
}

// focusDetail moves the keyboard to the Detail pane by its number, the way a user does.
func focusDetail(t *testing.T, model *Model) {
	t.Helper()
	press(t, model, charKeyPress("3"))
	require.Equal(t, "detail", model.FocusName(), "pane 3 should be the detail pane")
}

// charKeyPress builds the key press a terminal sends for a printable character.
func charKeyPress(s string) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: rune(s[0]), Text: s}
}

// TestEveryPaneUsesItsFullHeight is the geometry the side-by-side layout implies.
//
// The sizing code used to halve the body height for the messages and hand the remainder to the
// detail, which is the geometry of a layout that stacked the two. No layout does. Both panes
// drew a full-height box and then filled the lower half with blank rows, and content past the
// halfway mark was clipped -- a browse of eight tags lost the action hint drawn beneath them.
func TestEveryPaneUsesItsFullHeight(t *testing.T) {
	for _, size := range [][2]int{{157, 50}, {120, 44}, {120, 30}, {100, 24}} {
		model := sized(t, newTestModel(t), size[0], size[1])
		want := model.layout.BodyHeight - 2
		require.Positive(t, want, "%dx%d should have a body", size[0], size[1])

		// The table spends one of its rows on the column header, so the rows it offers for
		// messages are one fewer than the pane interior. The viewport has no header.
		assert.Equal(t, want-1, model.messages.Height(),
			"the message table should use the whole pane at %dx%d", size[0], size[1])
		assert.Equal(t, want, model.detail.Height(),
			"the detail viewport should use the whole pane at %dx%d", size[0], size[1])

		// Whatever the arithmetic, neither may be sized to about half the pane, which is what
		// the stacked-layout geometry produced.
		assert.Greater(t, model.messages.Height(), want*2/3,
			"the table should not be sized as if something sat beneath it")
		assert.Greater(t, model.detail.Height(), want*2/3,
			"nor the detail as if something sat above it")
	}
}

// TestAllTheTagsAndTheHintFitInTheDetailPane is the behavioural half of the same fix: what the
// pane has room to show, it shows.
func TestAllTheTagsAndTheHintFitInTheDetailPane(t *testing.T) {
	model := browsed(t)
	focusDetail(t, model)
	screen := tuitest.Strip(model.render())

	for _, address := range plcsession.DemoTagAddresses() {
		assert.Contains(t, screen, address, "every browsed tag should be on screen")
	}
	for _, action := range detailActions {
		assert.Contains(t, screen, action.Key+" "+action.Label,
			"the hint beneath them should be too")
	}
}

// TestTheDetailHintNeverLosesAnAction covers the wrap itself. The hint is one line where it
// fits and several where it does not, and at eighty columns it used to lose its last action
// half way through a word -- "y yan". A truncated advertisement is worse than a second row.
func TestTheDetailHintNeverLosesAnAction(t *testing.T) {
	theme := testTheme()
	for width := 8; width <= 60; width++ {
		lines := detailHint(theme, width)
		require.NotEmpty(t, lines, "width %d should still advertise something", width)

		joined := strings.Join(lines, "\n")
		for _, action := range detailActions {
			assert.Contains(t, joined, action.Key+" "+action.Label,
				"width %d dropped or cut %q", width, action.Key)
		}
		// And no line may overflow the pane, since the pane clips rather than wraps.
		for _, line := range lines {
			if lipgloss.Width(line) > width {
				// One action on its own is allowed to exceed a very narrow pane: there is
				// nothing left to break, and clipping "s subscribe" still reads as the key.
				assert.Len(t, strings.Split(tuitest.Strip(line), " "+theme.Glyphs.Separator+" "), 1,
					"width %d line %q overflows and could have been broken", width, line)
			}
		}
	}
}
