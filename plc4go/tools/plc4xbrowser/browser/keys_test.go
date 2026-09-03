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

	"charm.land/bubbles/v2/key"
	"charm.land/bubbles/v2/table"
	tea "charm.land/bubbletea/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
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

	rendered := renderEventDetail(testTheme(), event)
	occurrences := strings.Count(rendered, plcsession.DemoDeviceOne)
	assert.Equal(t, 1, occurrences,
		"the connection should be named once, not in a header and again in a summary:\n%s", rendered)
	assert.NotContains(t, rendered, "tags in",
		"the timing sentence belongs in the log, not repeated in the detail body")
	assert.Contains(t, rendered, "r read", "the detail pane should advertise what can be done with the tag")
}
