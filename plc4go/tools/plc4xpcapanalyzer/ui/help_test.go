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
	"strings"
	"testing"

	tea "charm.land/bubbletea/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tuitest"
)

// The expanded help is meant to be a complete and honest statement of what a focus can do.
// These tests hold it to both halves of that: nothing advertised may be inert, and nothing that
// works may go unadvertised. The browser has had the first half since the port; the analyzer
// had neither, which is how its eight most tool-specific keys came to work while appearing in
// neither the footer nor the expanded help.

// idempotentByDesign are the advertised bindings a "something changed" assertion cannot express.
// Each is listed with the reason, and each is covered by a test of its own elsewhere.
var idempotentByDesign = map[string]string{
	// esc is a dismissal chain: with nothing to dismiss it is correctly a no-op.
	"esc": "dismisses one thing at a time, and does nothing when there is nothing to dismiss",
}

// TestEveryAdvertisedKeyDoesSomething walks the bindings the expanded help actually shows and
// presses each one, so a binding cannot be advertised without being handled.
func TestEveryAdvertisedKeyDoesSomething(t *testing.T) {
	state, _ := demoState(t)
	reference := newTestModel(t, state, wide)
	reference = send(reference, modKey(tea.KeyTab, tea.ModShift))
	require.Equal(t, tui.FocusPane, reference.focus)

	groups := append(reference.keys.FullHelp(), reference.tools.fullHelp()...)
	seen := 0
	for _, group := range groups {
		for _, binding := range group {
			if len(binding.Keys()) == 0 {
				// Out of scope at this focus, so the help does not show it either.
				continue
			}
			pressed := binding.Keys()[0]
			if reason, skip := idempotentByDesign[pressed]; skip {
				t.Logf("skipping %q: %s", pressed, reason)
				continue
			}
			seen++

			t.Run(pressed, func(t *testing.T) {
				model := analysed(t, okRecord(1), brokenRecord(2), okRecord(3))
				model = send(model, modKey(tea.KeyTab, tea.ModShift))
				// Park the cursor mid-list so that both ends of the list have somewhere to go.
				model = send(model, charKey('G'))
				model = send(model, charKey('k'))
				// The three detail views are a radio group, so pressing the one already
				// showing is correctly a no-op. The test switches to a different one first
				// rather than excluding them: diff is the default, and it is the view the
				// whole tool exists for, so leaving it untested would be the wrong trade.
				switch pressed {
				case "b", "t":
					model = send(model, charKey('d'))
				case "d":
					model = send(model, charKey('b'))
				}

				before := model.Render()
				next, cmd := sendWithCmd(model, keyPressFor(pressed))
				// A key may act on the screen or produce a command -- yank does the latter.
				assert.True(t, next.Render() != before || cmd != nil,
					"%q (%s) changed nothing; the help advertises it, so it must do something",
					pressed, binding.Help().Desc)
			})
		}
	}
	assert.Positive(t, seen, "the help has to advertise something")
}

// TestEveryToolBindingIsAdvertised is the other direction, and the one the analyzer failed:
// its own bindings never reached the help, so reading the documentation was the only way to
// discover them.
func TestEveryToolBindingIsAdvertised(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, tui.Size{Width: 130, Height: 44})
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, namedKey(tea.KeyF1))
	require.True(t, model.footer.ShowAll(), "F1 should open the expanded help")

	screen := tuitest.Strip(model.Render())
	for _, group := range model.tools.fullHelp() {
		for _, binding := range group {
			require.NotEmpty(t, binding.Keys(), "a tool binding must be bound at pane focus")
			help := binding.Help()
			assert.Contains(t, screen, help.Key+" "+help.Desc,
				"the expanded help has to advertise %q", help.Key)
		}
	}
}

// TestToolBindingsAreSilentAtThePrompt is why they are pane-scoped: they are bare letters, and
// typing "analyze" must not fire the a, n, l, y and z bindings.
func TestToolBindingsAreSilentAtThePrompt(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	require.Equal(t, tui.FocusPrompt, model.focus)

	for _, group := range toolKeysFor(tui.FocusPrompt).fullHelp() {
		for _, binding := range group {
			assert.Empty(t, binding.Keys(),
				"%s must not be bound at the prompt", binding.Help().Desc)
		}
	}

	// And the letters reach the field as text.
	for _, r := range "analyze" {
		model = send(model, charKey(r))
	}
	assert.Equal(t, "analyze", model.prompt.Value())
	assert.False(t, model.run.active, "typing a command must not have started one")
}

// keyPressFor builds the key press a terminal sends for a binding's key name.
func keyPressFor(name string) tea.KeyPressMsg {
	switch name {
	case "enter":
		return namedKey(tea.KeyEnter)
	case "esc":
		return namedKey(tea.KeyEscape)
	case "tab":
		return namedKey(tea.KeyTab)
	case "shift+tab":
		return modKey(tea.KeyTab, tea.ModShift)
	case "up":
		return namedKey(tea.KeyUp)
	case "down":
		return namedKey(tea.KeyDown)
	case "pgup":
		return namedKey(tea.KeyPgUp)
	case "pgdown":
		return namedKey(tea.KeyPgDown)
	case "f1":
		return namedKey(tea.KeyF1)
	}
	if strings.HasPrefix(name, "alt+") && len(name) == 5 {
		return modKey(rune(name[4]), tea.ModAlt)
	}
	if strings.HasPrefix(name, "ctrl+") && len(name) == 6 {
		return modKey(rune(name[5]), tea.ModCtrl)
	}
	return charKey(rune(name[0]))
}
