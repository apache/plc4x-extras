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
	"strings"

	"charm.land/bubbles/v2/help"
	"charm.land/bubbles/v2/key"
	"charm.land/lipgloss/v2"
)

// The keymap shared by both terminal tools, and the footer that advertises it.
//
// Two properties here were forced by defects found in review rather than chosen for elegance.
//
// First, both tools are REPLs: a text prompt owns the keyboard almost all of the time. A
// design review of the sketched UI counted fifteen bare-character shortcuts (q ? 1-4 [ ] w z
// y L f e o a x c d r) offered alongside that prompt, which would mean that typing the word
// "read" fired the r, e, a and d pane actions. Every binding here therefore declares a Scope,
// and KeyMap.ForFocus resolves the map for whoever currently owns the keyboard. A binding
// that is out of scope comes back with no keys at all, which makes key.Matches refuse it and
// makes the help view omit it. No call site is able to forget the rule because no call site
// gets to see an out-of-scope binding in the first place.
//
// Second, bubbles/help ellipsizes the TAIL of ShortHelp as soon as it no longer fits the
// width (see help.Model.shouldAddItem), so at eighty columns the last binding is silently
// dropped. The quit binding is consequently first in every ShortHelp below, and the help
// labels are deliberately plain ASCII: the footer is the most width-critical line in either
// UI, and it is the one place where a glyph that a font measures as two cells instead of one
// would quietly eat a binding.

// Focus says which part of the UI owns the keyboard.
type Focus int

const (
	// FocusPrompt is the command prompt. This is the default and the common case: these are
	// REPLs, not viewers with a command line bolted on.
	FocusPrompt Focus = iota
	// FocusPane is a content pane: a table, a list or a viewport.
	FocusPane
	// FocusOverlay is a modal form, such as the browser's request composer.
	FocusOverlay
)

// String makes Focus readable in test failures and logs.
func (f Focus) String() string {
	switch f {
	case FocusPrompt:
		return "prompt"
	case FocusPane:
		return "pane"
	case FocusOverlay:
		return "overlay"
	default:
		return "unknown"
	}
}

// TextEntry reports whether this focus is one where the user is typing text, and so where a
// bare character has to mean that character and nothing else.
func (f Focus) TextEntry() bool {
	return f == FocusPrompt || f == FocusOverlay
}

// Scope says in which focus states a binding is live.
type Scope int

const (
	// ScopeGlobal is live in every focus. Only modifier combinations and named keys may be
	// global; a bare character never can, because at a text-entry focus it is text.
	ScopeGlobal Scope = iota
	// ScopePane is live only while a content pane owns the keyboard. This is where the bare
	// characters live.
	ScopePane
	// ScopeInput is live at any text-entry focus, the prompt and the modal forms alike.
	ScopeInput
	// ScopePrompt is live only at the command prompt.
	ScopePrompt
)

// String makes Scope readable in test failures.
func (s Scope) String() string {
	switch s {
	case ScopeGlobal:
		return "global"
	case ScopePane:
		return "pane"
	case ScopeInput:
		return "input"
	case ScopePrompt:
		return "prompt"
	default:
		return "unknown"
	}
}

// Live reports whether a binding of this scope is active for focus f.
func (s Scope) Live(f Focus) bool {
	switch s {
	case ScopeGlobal:
		return true
	case ScopePane:
		return f == FocusPane
	case ScopeInput:
		return f.TextEntry()
	case ScopePrompt:
		return f == FocusPrompt
	default:
		return false
	}
}

// Scoped returns b as given when scope is live for focus f, and an empty binding otherwise.
//
// Tool-specific bindings go through this so that they get exactly the same treatment as the
// shared ones. A tool should never compare a Focus itself: that is how a binding ends up live
// at the prompt by accident.
func Scoped(b key.Binding, scope Scope, f Focus) key.Binding {
	if scope.Live(f) {
		return b
	}
	return key.NewBinding()
}

// spec is the source form of a shared binding.
//
// keys are live wherever scope is live. bare holds extra single-character shortcuts that are
// live ONLY at pane focus, which is what lets an action have both a prompt-safe key and a
// convenient one: quit is ctrl+c everywhere and additionally q in a pane.
type spec struct {
	scope Scope
	keys  []string
	bare  []string
	// label is the help label to show while the bare keys are live, alt the one to show while
	// they are not. An empty alt means label is used in both cases.
	label string
	alt   string
	desc  string
}

// resolve builds the binding for focus f.
//
// A binding with no keys is what disables an action: key.Binding.Enabled reports false for an
// empty key set, key.Matches skips disabled bindings, and help.Model omits them. That is the
// entire scoping mechanism, and it needs no cooperation from any call site.
func (s spec) resolve(f Focus) key.Binding {
	var keys []string
	if s.scope.Live(f) {
		keys = append(keys, s.keys...)
	}
	if f == FocusPane {
		keys = append(keys, s.bare...)
	}
	if len(keys) == 0 {
		return key.NewBinding()
	}
	label := s.label
	if f != FocusPane && s.alt != "" {
		label = s.alt
	}
	if label == "" || s.desc == "" {
		// No help text: the binding works but stays out of the footer. Used for the second
		// half of a pair whose help is carried by the first, such as down beside up.
		return key.NewBinding(key.WithKeys(keys...))
	}
	return key.NewBinding(key.WithKeys(keys...), key.WithHelp(label, s.desc))
}

// The binding table. Everything the two tools have in common lives here; anything only one
// tool has stays in that tool and is scoped with Scoped.
//
// Which keys are available is not a free choice. bubbles/v2/textinput claims right ctrl+f
// left ctrl+b alt+right ctrl+right alt+f alt+left ctrl+left alt+b alt+backspace ctrl+w
// ctrl+backspace alt+delete alt+d ctrl+delete ctrl+k ctrl+u backspace ctrl+h delete ctrl+d
// home ctrl+a end ctrl+e ctrl+v tab up down ctrl+n and ctrl+p for line editing, so a global
// binding has to come from what is left: esc, enter, shift+tab, the page keys, the function
// keys and alt+digit.
var (
	specQuit   = spec{scope: ScopeGlobal, keys: []string{"ctrl+c"}, bare: []string{"q"}, label: "q", alt: "ctrl+c", desc: "quit"}
	specHelp   = spec{scope: ScopeGlobal, keys: []string{"f1"}, bare: []string{"?"}, label: "?", alt: "f1", desc: "help"}
	specCancel = spec{scope: ScopeGlobal, keys: []string{"esc"}, label: "esc", desc: "cancel"}
	// specEOF is ctrl+d, which the tview interfaces also quit on. It cannot just be added to
	// specQuit: bubbles/textinput binds ctrl+d to delete-forward, so an unconditional quit
	// would take a line-editing key away. The shell rule resolves it -- ctrl+d ends the session
	// on an empty line and deletes a character otherwise -- and that is how every REPL a user
	// arrives from behaves. It carries no help text of its own; it shares quit's entry.
	specEOF = spec{scope: ScopeGlobal, keys: []string{"ctrl+d"}}
	// The help says "1-4" at every focus, not "alt+1-4" away from a pane. alt+digit is bound
	// but unreliable -- the common terminals bind it to switching their own tabs and never
	// deliver it -- so the tools also accept a bare digit wherever it cannot be text, and that
	// is the binding worth advertising. Each pane draws its number in its title.
	specPaneJump = spec{scope: ScopeGlobal, keys: []string{"alt+1", "alt+2", "alt+3", "alt+4"}, bare: []string{"1", "2", "3", "4"}, label: "1-4", desc: "pane"}
	specPageUp   = spec{scope: ScopeGlobal, keys: []string{"pgup"}, label: "pgup/pgdown", desc: "scroll"}
	specPageDown = spec{scope: ScopeGlobal, keys: []string{"pgdown"}}
	specSubmit   = spec{scope: ScopeInput, keys: []string{"enter"}, label: "enter", desc: "run"}
	// Select is Submit's counterpart for a pane. Submit is ScopeInput, so without this
	// nothing at all responded to enter while a pane held the keyboard, and a highlighted row
	// could not be chosen.
	specSelect      = spec{scope: ScopePane, keys: []string{"enter"}, label: "enter", desc: "select"}
	specFocusPanes  = spec{scope: ScopePrompt, keys: []string{"shift+tab"}, label: "shift+tab", desc: "panes"}
	specComplete    = spec{scope: ScopePrompt, keys: []string{"tab"}, label: "tab", desc: "complete"}
	specHistoryPrev = spec{scope: ScopePrompt, keys: []string{"up"}, label: "up/down", desc: "history"}
	specHistoryNext = spec{scope: ScopePrompt, keys: []string{"down"}}
	specSuggestNext = spec{scope: ScopePrompt, keys: []string{"ctrl+n"}, label: "ctrl+n/p", desc: "suggestion"}
	specSuggestPrev = spec{scope: ScopePrompt, keys: []string{"ctrl+p"}}
	specFocusPrompt = spec{scope: ScopePane, keys: []string{"i", ":"}, label: ":", desc: "prompt"}
	specNextPane    = spec{scope: ScopePane, keys: []string{"tab"}, label: "tab", desc: "next pane"}
	specPrevPane    = spec{scope: ScopePane, keys: []string{"shift+tab"}}
	specUp          = spec{scope: ScopePane, keys: []string{"up", "k"}, label: "up/down", desc: "move"}
	specDown        = spec{scope: ScopePane, keys: []string{"down", "j"}}
	specTop         = spec{scope: ScopePane, keys: []string{"g"}, label: "g/G", desc: "top/end"}
	specBottom      = spec{scope: ScopePane, keys: []string{"G"}}
	specFilter      = spec{scope: ScopePane, keys: []string{"/"}, label: "/", desc: "filter"}
	specWrap        = spec{scope: ScopePane, keys: []string{"w"}, label: "w", desc: "wrap"}
	specYank        = spec{scope: ScopePane, keys: []string{"y"}, label: "y", desc: "yank"}
	specFollow      = spec{scope: ScopePane, keys: []string{"f"}, label: "f", desc: "follow"}
	specExpand      = spec{scope: ScopePane, keys: []string{"e"}, label: "e", desc: "expand"}
	specLogLevel    = spec{scope: ScopePane, keys: []string{"L"}, label: "L", desc: "level"}
)

// KeyMap is the resolved keymap for one focus. It satisfies help.KeyMap.
//
// Get one from NewKeyMap, which resolves for FocusPrompt because that is where a REPL starts,
// and re-resolve with ForFocus whenever focus moves.
type KeyMap struct {
	// Always available.
	Quit key.Binding
	// EOF is ctrl+d. Act on it only when there is nothing to delete; see specEOF.
	EOF      key.Binding
	Help     key.Binding
	Cancel   key.Binding
	PaneJump key.Binding
	PageUp   key.Binding
	PageDown key.Binding

	// Text entry, at the prompt or in a modal form.
	Submit key.Binding
	// Select activates the highlighted row of a pane.
	Select key.Binding

	// The prompt only.
	FocusPanes  key.Binding
	Complete    key.Binding
	HistoryPrev key.Binding
	HistoryNext key.Binding
	SuggestNext key.Binding
	SuggestPrev key.Binding

	// A content pane only. These are the bare characters that must not fire while the user is
	// typing a command name.
	FocusPrompt key.Binding
	NextPane    key.Binding
	PrevPane    key.Binding
	Up          key.Binding
	Down        key.Binding
	Top         key.Binding
	Bottom      key.Binding
	Filter      key.Binding
	Wrap        key.Binding
	Yank        key.Binding
	Follow      key.Binding
	Expand      key.Binding
	LogLevel    key.Binding

	focus Focus
}

// NewKeyMap returns the keymap for the prompt, which is where both tools start.
func NewKeyMap() KeyMap {
	return keyMapFor(FocusPrompt)
}

// Focus reports which focus this keymap was resolved for.
func (k KeyMap) Focus() Focus { return k.focus }

// ForFocus returns the keymap resolved for focus f.
//
// It ignores the receiver's own state on purpose: resolution is a pure function of the focus,
// so ForFocus can never carry a stale binding over from the focus it was called on.
func (k KeyMap) ForFocus(f Focus) KeyMap {
	return keyMapFor(f)
}

// keyMapFor resolves every spec for one focus.
func keyMapFor(f Focus) KeyMap {
	return KeyMap{
		focus: f,

		Quit:     specQuit.resolve(f),
		EOF:      specEOF.resolve(f),
		Help:     specHelp.resolve(f),
		Cancel:   specCancel.resolve(f),
		PaneJump: specPaneJump.resolve(f),
		PageUp:   specPageUp.resolve(f),
		PageDown: specPageDown.resolve(f),

		Submit: specSubmit.resolve(f),
		Select: specSelect.resolve(f),

		FocusPanes:  specFocusPanes.resolve(f),
		Complete:    specComplete.resolve(f),
		HistoryPrev: specHistoryPrev.resolve(f),
		HistoryNext: specHistoryNext.resolve(f),
		SuggestNext: specSuggestNext.resolve(f),
		SuggestPrev: specSuggestPrev.resolve(f),

		FocusPrompt: specFocusPrompt.resolve(f),
		NextPane:    specNextPane.resolve(f),
		PrevPane:    specPrevPane.resolve(f),
		Up:          specUp.resolve(f),
		Down:        specDown.resolve(f),
		Top:         specTop.resolve(f),
		Bottom:      specBottom.resolve(f),
		Filter:      specFilter.resolve(f),
		Wrap:        specWrap.resolve(f),
		Yank:        specYank.resolve(f),
		Follow:      specFollow.resolve(f),
		Expand:      specExpand.resolve(f),
		LogLevel:    specLogLevel.resolve(f),
	}
}

// ShortHelp is the one-line footer.
//
// Quit is first in every variant, and that ordering is load-bearing rather than tidy:
// help.Model renders items until they no longer fit and then ellipsizes the rest, so the last
// entry is the one that disappears at eighty columns. Whatever else the user loses when the
// terminal is narrow, they keep the way out.
func (k KeyMap) ShortHelp() []key.Binding {
	switch k.focus {
	case FocusPane:
		return []key.Binding{k.Quit, k.FocusPrompt, k.NextPane, k.Up, k.Filter, k.Help}
	case FocusOverlay:
		return []key.Binding{k.Quit, k.Submit, k.Cancel, k.Help}
	default:
		return []key.Binding{k.Quit, k.Submit, k.Complete, k.HistoryPrev, k.FocusPanes, k.Help}
	}
}

// FullHelp is the expanded help, one column per group. Bindings that are out of scope resolve
// to empty and drop out on their own, so the expanded help doubles as an honest statement of
// what the current focus can actually do.
func (k KeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Quit, k.Help, k.Cancel},
		{k.Submit, k.Complete, k.HistoryPrev, k.SuggestNext},
		{k.FocusPrompt, k.FocusPanes, k.NextPane, k.PaneJump},
		{k.Up, k.Top, k.PageUp, k.Filter},
		{k.Wrap, k.Yank, k.Follow, k.Expand, k.LogLevel},
	}
}

// HelpFooter renders a KeyMap as the footer line, or as the expanded help when asked.
//
// It is a component, not a tea.Model: View returns a string, because only the top-level model
// returns a tea.View.
type HelpFooter struct {
	model help.Model
}

// NewHelpFooter builds a footer styled by the theme. It takes its separator and ellipsis from
// the theme's glyphs so that the footer degrades with the rest of the UI on a terminal that
// cannot draw the Unicode set.
func NewHelpFooter(theme Theme) HelpFooter {
	model := help.New()
	model.ShortSeparator = " " + theme.Glyphs.Separator + " "
	model.FullSeparator = "   "
	model.Ellipsis = theme.Glyphs.Ellipsis
	model.Styles = help.Styles{
		ShortKey:       theme.Key,
		ShortDesc:      theme.Muted,
		ShortSeparator: theme.Chrome,
		Ellipsis:       theme.Chrome,
		FullKey:        theme.Key,
		FullDesc:       theme.Muted,
		FullSeparator:  theme.Chrome,
	}
	return HelpFooter{model: model}
}

// SetWidth sets the width the footer has to fit in.
func (h *HelpFooter) SetWidth(width int) { h.model.SetWidth(width) }

// Width reports the footer's width.
func (h HelpFooter) Width() int { return h.model.Width() }

// SetShowAll switches between the one-line footer and the expanded help.
func (h *HelpFooter) SetShowAll(showAll bool) { h.model.ShowAll = showAll }

// ShowAll reports whether the expanded help is showing.
func (h HelpFooter) ShowAll() bool { return h.model.ShowAll }

// View renders the footer for a keymap that has already been resolved for the current focus.
//
// The fitting to the width is done here rather than left to help.Model, because help.Model
// does not actually do it. help.Model.shouldAddItem truncates only while there is still room
// for its own ellipsis and otherwise falls through and adds the item anyway, then carries on
// adding the rest (verified in bubbles/v2 v2.2.1): asking it for the six-binding prompt
// footer at forty columns returns eighty-four cells, which wraps and pushes every row of the
// layout down one. Fitting here drops whole bindings from the TAIL, which is the other half
// of why ShortHelp leads with quit.
func (h HelpFooter) View(keys KeyMap) string {
	if h.model.ShowAll {
		return h.fullView(keys.FullHelp())
	}
	return h.shortView(keys.ShortHelp())
}

// ViewWith renders the footer for a keymap plus the bindings a tool adds of its own.
//
// A tool's own bindings have to reach the help the same way the shared ones do. The pcap
// analyzer's eight most tool-specific keys -- the tab pair, the three detail views, and open,
// analyze and extract -- worked but appeared in neither the footer nor the expanded help, so
// reading the documentation was the only way to find out they existed. That is the same
// dead-affordance defect as a help that advertises a key it does not handle, in the other
// direction: the expanded help is meant to be a complete statement of what a focus can do.
//
// Out-of-scope bindings resolve to empty and drop out on their own, so a caller passes its
// whole set and lets the focus decide, exactly as the shared bindings do.
func (h HelpFooter) ViewWith(keys KeyMap, short []key.Binding, full [][]key.Binding) string {
	if h.model.ShowAll {
		return h.fullView(append(keys.FullHelp(), full...))
	}
	return h.shortView(append(keys.ShortHelp(), short...))
}

// unlimited returns the help model with its width limit removed, so that its own truncation
// cannot interfere with the fitting done here.
func (h HelpFooter) unlimited() help.Model {
	model := h.model
	model.SetWidth(0)
	return model
}

// shortView renders the one-line footer, dropping trailing bindings until it fits.
func (h HelpFooter) shortView(bindings []key.Binding) string {
	width := h.model.Width()
	model := h.unlimited()

	view := model.ShortHelpView(bindings)
	if width <= 0 || lipgloss.Width(view) <= width {
		return view
	}

	// The ellipsis says that something was dropped. It has to be budgeted for, or the fitting
	// reintroduces the overflow it exists to prevent.
	ellipsis := " " + h.model.Styles.Ellipsis.Inline(true).Render(h.model.Ellipsis)
	for count := len(bindings) - 1; count > 0; count-- {
		candidate := model.ShortHelpView(bindings[:count])
		if lipgloss.Width(candidate)+lipgloss.Width(ellipsis) <= width {
			return candidate + ellipsis
		}
	}

	// Narrower than the first binding plus an ellipsis. This is below MinWidth, where the
	// tools draw the too-small notice instead, but clipping beats overflowing at any size.
	return fitLine(model.ShortHelpView(bindings[:1]), width)
}

// fullView renders the expanded help, dropping trailing columns until it fits. Columns whose
// bindings are all out of scope drop out on their own inside help.Model.
func (h HelpFooter) fullView(groups [][]key.Binding) string {
	width := h.model.Width()
	model := h.unlimited()

	view := model.FullHelpView(groups)
	if width <= 0 || lipgloss.Width(view) <= width {
		return view
	}
	for count := len(groups) - 1; count > 0; count-- {
		candidate := model.FullHelpView(groups[:count])
		if lipgloss.Width(candidate) <= width {
			return candidate
		}
	}
	// Narrower than a single column. Clipping beats overflowing, and a terminal this narrow
	// is showing the too-small notice anyway.
	return clipLines(model.FullHelpView(groups[:1]), width)
}

// clipLines clips every line of a block to width, measured in display cells so that styled
// text and wide glyphs are counted correctly. Unlike fitLine it does not pad, because a
// column of help is not a full-width row.
func clipLines(block string, width int) string {
	lines := strings.Split(block, "\n")
	clip := lipgloss.NewStyle().MaxWidth(max(width, 1))
	for i, line := range lines {
		if lipgloss.Width(line) > width {
			lines[i] = clip.Render(line)
		}
	}
	return strings.Join(lines, "\n")
}
