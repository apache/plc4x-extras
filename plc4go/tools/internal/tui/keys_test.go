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

	"charm.land/bubbles/v2/key"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// charKey builds the key press a terminal sends for a printable character. Text has to be set
// as well as Code, because uv.Key.String prefers Text and that is what key.Matches compares.
func charKey(r rune) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: r, Text: string(r)}
}

// namedKey builds the key press for a named key, which carries no text.
func namedKey(code rune) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: code}
}

// ctrlKey builds a ctrl-modified key press.
func ctrlKey(r rune) tea.KeyPressMsg {
	return tea.KeyPressMsg{Code: r, Mod: tea.ModCtrl}
}

// bareBindings are the single-character shortcuts that the design review found offered
// alongside a prompt that has focus almost all the time. Each one must be inert at the prompt
// and live in a pane; that is the whole point of the scoping rule.
var bareBindings = []struct {
	name string
	msg  tea.KeyPressMsg
	pick func(KeyMap) key.Binding
}{
	{"quit", charKey('q'), func(k KeyMap) key.Binding { return k.Quit }},
	{"help", charKey('?'), func(k KeyMap) key.Binding { return k.Help }},
	{"pane 1", charKey('1'), func(k KeyMap) key.Binding { return k.PaneJump }},
	{"pane 4", charKey('4'), func(k KeyMap) key.Binding { return k.PaneJump }},
	{"focus prompt", charKey('i'), func(k KeyMap) key.Binding { return k.FocusPrompt }},
	{"focus prompt colon", charKey(':'), func(k KeyMap) key.Binding { return k.FocusPrompt }},
	{"move down", charKey('j'), func(k KeyMap) key.Binding { return k.Down }},
	{"move up", charKey('k'), func(k KeyMap) key.Binding { return k.Up }},
	{"top", charKey('g'), func(k KeyMap) key.Binding { return k.Top }},
	{"bottom", charKey('G'), func(k KeyMap) key.Binding { return k.Bottom }},
	{"filter", charKey('/'), func(k KeyMap) key.Binding { return k.Filter }},
	{"wrap", charKey('w'), func(k KeyMap) key.Binding { return k.Wrap }},
	{"yank", charKey('y'), func(k KeyMap) key.Binding { return k.Yank }},
	{"follow", charKey('f'), func(k KeyMap) key.Binding { return k.Follow }},
	{"expand", charKey('e'), func(k KeyMap) key.Binding { return k.Expand }},
	{"log level", charKey('L'), func(k KeyMap) key.Binding { return k.LogLevel }},
}

func TestBareCharacterBindingsAreInertWhileTextHasFocus(t *testing.T) {
	for _, binding := range bareBindings {
		t.Run(binding.name, func(t *testing.T) {
			pane := NewKeyMap().ForFocus(FocusPane)
			assert.True(t, key.Matches(binding.msg, binding.pick(pane)),
				"%q must be live in a pane", binding.msg.String())

			for _, focus := range []Focus{FocusPrompt, FocusOverlay} {
				resolved := NewKeyMap().ForFocus(focus)
				assert.False(t, key.Matches(binding.msg, binding.pick(resolved)),
					"%q must be inert at %s focus", binding.msg.String(), focus)
			}
		})
	}
}

func TestTypingACommandNameAtThePromptFiresNoPaneAction(t *testing.T) {
	// The concrete failure the scoping rule exists to prevent: the word "read" is r, e, a and
	// d, three of which were proposed as pane shortcuts.
	prompt := NewKeyMap().ForFocus(FocusPrompt)
	paneActions := []key.Binding{
		prompt.Quit, prompt.Help, prompt.PaneJump, prompt.FocusPrompt,
		prompt.NextPane, prompt.PrevPane, prompt.Up, prompt.Down,
		prompt.Top, prompt.Bottom, prompt.Filter, prompt.Wrap,
		prompt.Yank, prompt.Follow, prompt.Expand, prompt.LogLevel,
	}

	for _, line := range []string{"read", "write", "browse", "subscribe", "quit", "disconnect"} {
		for _, r := range line {
			assert.False(t, key.Matches(charKey(r), paneActions...),
				"typing %q in %q fired a pane action", r, line)
		}
	}
}

func TestPromptOnlyBindingsAreInertInAPane(t *testing.T) {
	pane := NewKeyMap().ForFocus(FocusPane)

	assert.False(t, key.Matches(namedKey(tea.KeyUp), pane.HistoryPrev), "up must not be history in a pane")
	assert.False(t, key.Matches(namedKey(tea.KeyDown), pane.HistoryNext), "down must not be history in a pane")
	assert.False(t, key.Matches(namedKey(tea.KeyTab), pane.Complete), "tab must not complete in a pane")
	assert.False(t, key.Matches(ctrlKey('n'), pane.SuggestNext), "ctrl+n must not be a suggestion key in a pane")

	// The same keys still do the pane's own job, which is why they cannot be shared.
	assert.True(t, key.Matches(namedKey(tea.KeyUp), pane.Up))
	assert.True(t, key.Matches(namedKey(tea.KeyTab), pane.NextPane))
}

func TestQuitStaysReachableFromEveryFocus(t *testing.T) {
	// Losing the way out is worse than losing any shortcut, so ctrl+c is global and q is only
	// the convenience.
	for _, focus := range []Focus{FocusPrompt, FocusPane, FocusOverlay} {
		t.Run(focus.String(), func(t *testing.T) {
			resolved := NewKeyMap().ForFocus(focus)
			assert.True(t, key.Matches(ctrlKey('c'), resolved.Quit), "ctrl+c must quit at %s focus", focus)
			assert.True(t, key.Matches(namedKey(tea.KeyF1), resolved.Help), "f1 must open help at %s focus", focus)
			assert.True(t, key.Matches(namedKey(tea.KeyEsc), resolved.Cancel), "esc must cancel at %s focus", focus)
		})
	}
}

func TestTextEntryFocusesKeepTheKeysNeededToTypeAndRun(t *testing.T) {
	for _, focus := range []Focus{FocusPrompt, FocusOverlay} {
		t.Run(focus.String(), func(t *testing.T) {
			resolved := NewKeyMap().ForFocus(focus)
			assert.True(t, key.Matches(namedKey(tea.KeyEnter), resolved.Submit))
		})
	}
	// enter in a pane means "open this row", which is the pane's business, not the prompt's.
	assert.False(t, key.Matches(namedKey(tea.KeyEnter), NewKeyMap().ForFocus(FocusPane).Submit))
}

func TestForFocusDoesNotCarryBindingsOverFromThePreviousFocus(t *testing.T) {
	// ForFocus resolves from the binding table rather than from the receiver, so a keymap
	// that has been through a pane cannot leak a bare letter back to the prompt.
	roundTripped := NewKeyMap().ForFocus(FocusPane).ForFocus(FocusPrompt)

	assert.Equal(t, FocusPrompt, roundTripped.Focus())
	assert.False(t, key.Matches(charKey('w'), roundTripped.Wrap))
	assert.Equal(t, NewKeyMap().ShortHelp(), roundTripped.ShortHelp())
}

func TestScopeLiveMatchesTheFocusItNames(t *testing.T) {
	for _, testCase := range []struct {
		scope Scope
		live  map[Focus]bool
	}{
		{ScopeGlobal, map[Focus]bool{FocusPrompt: true, FocusPane: true, FocusOverlay: true}},
		{ScopePane, map[Focus]bool{FocusPrompt: false, FocusPane: true, FocusOverlay: false}},
		{ScopeInput, map[Focus]bool{FocusPrompt: true, FocusPane: false, FocusOverlay: true}},
		{ScopePrompt, map[Focus]bool{FocusPrompt: true, FocusPane: false, FocusOverlay: false}},
	} {
		t.Run(testCase.scope.String(), func(t *testing.T) {
			for focus, want := range testCase.live {
				assert.Equal(t, want, testCase.scope.Live(focus), "scope %s at %s focus", testCase.scope, focus)
			}
		})
	}
}

func TestScopedGivesAToolBindingTheSameFocusScoping(t *testing.T) {
	// A tool-specific binding, such as the pcap analyzer's [b]ytes toggle, must go inert at
	// the prompt without the tool comparing focus values itself.
	bytesToggle := key.NewBinding(key.WithKeys("b"), key.WithHelp("b", "bytes"))

	assert.True(t, key.Matches(charKey('b'), Scoped(bytesToggle, ScopePane, FocusPane)))
	assert.False(t, key.Matches(charKey('b'), Scoped(bytesToggle, ScopePane, FocusPrompt)))
	assert.False(t, key.Matches(charKey('b'), Scoped(bytesToggle, ScopePane, FocusOverlay)))
	// A binding declared global stays live, which is what makes the distinction meaningful.
	assert.True(t, key.Matches(charKey('b'), Scoped(bytesToggle, ScopeGlobal, FocusPrompt)))
}

// TestHelpFooterStillAdvertisesQuitAtNarrowWidths pins the reason quit is first in ShortHelp.
// bubbles/help renders items until they no longer fit and then ellipsizes the rest, so the
// LAST binding is the one that silently disappears. At eighty columns, the width the tools
// have to work at, that used to be the quit binding.
func TestHelpFooterStillAdvertisesQuitAtNarrowWidths(t *testing.T) {
	theme := NewTheme(Options{NoColor: true, ASCII: true})

	for _, focus := range []Focus{FocusPrompt, FocusPane, FocusOverlay} {
		for _, width := range []int{120, 80, 60, 40, 30, 20} {
			t.Run(focus.String()+"/w"+strconv.Itoa(width), func(t *testing.T) {
				footer := NewHelpFooter(theme)
				footer.SetWidth(width)

				view := footer.View(NewKeyMap().ForFocus(focus))

				require.NotEmpty(t, view)
				assert.Contains(t, view, "quit", "the footer must still advertise the way out")
				assert.LessOrEqual(t, lipgloss.Width(view), width, "the footer must not overflow its width")
				assert.NotContains(t, view, "\n", "the short footer is a single line")
			})
		}
	}
}

// TestHelpFooterNeverOverflowsItsWidth is the regression test for a defect in bubbles/help
// itself. help.Model.shouldAddItem truncates only while there is room for its own ellipsis;
// once there is not, it falls through, adds the item anyway and keeps going, so the footer
// grows without bound. Left alone that wraps and pushes every row of the layout down one,
// which is precisely the class of breakage the responsive rules exist to prevent.
func TestHelpFooterNeverOverflowsItsWidth(t *testing.T) {
	theme := NewTheme(Options{NoColor: true, ASCII: true})

	for _, focus := range []Focus{FocusPrompt, FocusPane, FocusOverlay} {
		for width := 1; width <= 130; width++ {
			footer := NewHelpFooter(theme)
			footer.SetWidth(width)

			short := footer.View(NewKeyMap().ForFocus(focus))
			require.LessOrEqual(t, lipgloss.Width(short), width,
				"short footer at %s focus overflowed width %d: %q", focus, width, short)
			require.NotContains(t, short, "\n")

			footer.SetShowAll(true)
			expanded := footer.View(NewKeyMap().ForFocus(focus))
			for line := range strings.SplitSeq(expanded, "\n") {
				require.LessOrEqual(t, lipgloss.Width(line), width,
					"expanded help at %s focus overflowed width %d", focus, width)
			}
		}
	}
}

func TestHelpFooterOmitsBindingsThatAreOutOfScope(t *testing.T) {
	theme := NewTheme(Options{NoColor: true, ASCII: true})
	footer := NewHelpFooter(theme)
	footer.SetWidth(200)

	prompt := footer.View(NewKeyMap().ForFocus(FocusPrompt))
	assert.Contains(t, prompt, "complete")
	assert.NotContains(t, prompt, "wrap", "a pane action must not be advertised while the prompt has focus")

	footer.SetShowAll(true)
	expanded := footer.View(NewKeyMap().ForFocus(FocusPrompt))
	assert.Contains(t, expanded, "history")
	assert.NotContains(t, expanded, "yank", "the expanded help must describe what this focus can do, not everything")

	expandedPane := footer.View(NewKeyMap().ForFocus(FocusPane))
	assert.Contains(t, expandedPane, "yank")
	assert.NotContains(t, expandedPane, "complete")
}

func TestHelpFooterUsesTheThemeGlyphsSoItDegradesWithTheRestOfTheUI(t *testing.T) {
	unicode := NewHelpFooter(NewTheme(Options{NoColor: true}))
	ascii := NewHelpFooter(NewTheme(Options{NoColor: true, ASCII: true}))
	unicode.SetWidth(200)
	ascii.SetWidth(200)

	keys := NewKeyMap()
	assert.Contains(t, unicode.View(keys), UnicodeGlyphs().Separator)
	assert.Contains(t, ascii.View(keys), ASCIIGlyphs().Separator)
	assert.NotContains(t, ascii.View(keys), UnicodeGlyphs().Separator)
}

func TestHelpLabelsCarryNoWideGlyphsThatCouldMismeasureTheFooter(t *testing.T) {
	// The footer is the most width-critical line in either UI and the one place where a glyph
	// a font measures as two cells would eat a binding. Its labels are therefore ASCII.
	for _, focus := range []Focus{FocusPrompt, FocusPane, FocusOverlay} {
		for _, binding := range NewKeyMap().ForFocus(focus).ShortHelp() {
			label := binding.Help().Key + binding.Help().Desc
			for _, r := range label {
				require.Less(t, r, rune(0x80), "help label %q at %s focus is not ASCII", label, focus)
			}
			assert.Equal(t, len(label), lipgloss.Width(label), "help label %q must measure one cell per byte", label)
		}
	}
}

func TestShortHelpLeadsWithQuitInEveryFocus(t *testing.T) {
	for _, focus := range []Focus{FocusPrompt, FocusPane, FocusOverlay} {
		t.Run(focus.String(), func(t *testing.T) {
			short := NewKeyMap().ForFocus(focus).ShortHelp()
			require.NotEmpty(t, short)
			assert.Equal(t, "quit", short[0].Help().Desc,
				"quit must be first, because help ellipsizes the tail")
		})
	}
}

func TestShortHelpAdvertisesOnlyBindingsThatActuallyWork(t *testing.T) {
	// A footer that names a key which does nothing is worse than a shorter footer.
	for _, focus := range []Focus{FocusPrompt, FocusPane, FocusOverlay} {
		t.Run(focus.String(), func(t *testing.T) {
			for _, binding := range NewKeyMap().ForFocus(focus).ShortHelp() {
				assert.True(t, binding.Enabled(),
					"%q is advertised at %s focus but has no keys", binding.Help().Desc, focus)
				assert.NotEmpty(t, strings.TrimSpace(binding.Help().Key))
			}
		})
	}
}
