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
	"time"

	"charm.land/bubbles/v2/key"
	"charm.land/bubbles/v2/textinput"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
)

// The command line both terminal tools are driven from.
//
// Three things about it were dictated by defects in what it replaces.
//
// The field spans the whole available width. The previous UIs called SetFieldWidth(30), so
// any connection string worth typing was clipped as it was typed.
//
// Completion comes from an injected function rather than a command tree compiled in here,
// because the two tools have entirely different command sets and this package must not know
// either of them.
//
// Up and down do double duty, and the rule is stated once and tested. bubbles/v2/textinput
// binds up and down to PrevSuggestion and NextSuggestion by default (textinput.DefaultKeyMap,
// verified in v2.2.1), and those bindings are live whenever ShowSuggestions is set and there
// are matches. The sketched UI also wanted up and down for command history. Rather than
// silently letting one win, the completion list has an explicit open state: while it is open
// up and down walk the completion list, and while it is closed they walk the history.
// ctrl+p and ctrl+n always walk the completion list, so there is a pair that never changes
// meaning.

// SuggestFunc produces completion candidates for the text currently in the prompt.
//
// The candidates are whole replacement lines, not fragments: textinput matches a candidate by
// testing it against the entire value as a case-insensitive prefix, so returning "tag1" for
// the line "read ta" would match nothing. Return "read tag1".
//
// It is called on every content change, so it has to be cheap and must not block.
type SuggestFunc func(currentText string) []string

// PromptSubmitMsg is emitted when the user runs a line. The line has already been pushed onto
// the history and cleared from the field by the time this arrives.
type PromptSubmitMsg struct {
	Line string
}

// PromptTickMsg advances the inline spinner. Send it directly in a test; in a running program
// it comes from PromptTick.
type PromptTickMsg struct {
	Time time.Time
}

// promptSpinnerInterval is how often the inline spinner advances. Fast enough to read as
// motion, slow enough not to dominate the frame rate of a tool that is mostly idle.
const promptSpinnerInterval = 100 * time.Millisecond

// PromptTick returns a command that advances the spinner once. A busy prompt re-issues it
// from Update, so one PromptTick after Start keeps the spinner turning.
func PromptTick() tea.Cmd {
	return tea.Tick(promptSpinnerInterval, func(t time.Time) tea.Msg {
		return PromptTickMsg{Time: t}
	})
}

// DefaultHistoryLimit is how many lines the prompt remembers. Bounded because these tools are
// long-running session shells and an unbounded slice of every line ever typed is a leak, not
// a feature.
const DefaultHistoryLimit = 200

// Prompt is the command line. It is a component, not a tea.Model: View returns a string,
// because only the top-level model returns a tea.View.
type Prompt struct {
	input   textinput.Model
	theme   Theme
	keys    KeyMap
	suggest SuggestFunc

	// Label is drawn ahead of the field.
	Label string

	// history is oldest first. cursor indexes it while the user is walking it and equals
	// len(history) when they are not; draft holds the line that was in progress when the walk
	// started, so that walking back out of the history returns it rather than losing it.
	history      []string
	cursor       int
	draft        string
	historyLimit int

	// completionOpen is explicit state rather than "are there matches", because it decides
	// what up and down mean. Editing opens it, esc, tab and a history recall close it.
	completionOpen bool

	// busy, startedAt and frame drive the in-flight indication.
	busy      bool
	startedAt time.Time
	frame     int
	// now is the clock, injected so that the elapsed indication is assertable.
	now func() time.Time

	width int
}

// NewPrompt builds a prompt. suggest may be nil, which turns completion off.
func NewPrompt(theme Theme, suggest SuggestFunc) Prompt {
	input := textinput.New()
	// The label is rendered by View, from Label, so that the theme owns its colour and the
	// completion list can indent by exactly its width.
	input.Prompt = ""
	// No placeholder: the "$ " label already says what the row is, and placeholder text in a
	// field that is usually focused reads as a value the user did not type.
	input.Placeholder = ""

	// Every field style carries the band's background, because the row is a raised surface and
	// a terminal has no way to nest one: whatever the field renders would otherwise punch a
	// hole in the band the moment its own styling reset.
	styles := input.Styles()
	styles.Focused.Text = theme.OnSurface(theme.Value)
	styles.Focused.Suggestion = theme.OnSurface(theme.Muted)
	styles.Blurred.Text = theme.OnSurface(theme.Muted)
	styles.Blurred.Suggestion = theme.OnSurface(theme.Muted)
	styles.Focused.Placeholder = theme.OnSurface(theme.Muted)
	styles.Blurred.Placeholder = theme.OnSurface(theme.Muted)
	// The label is rendered by View, so the field's own prompt is empty -- but an unset style
	// still emits its default, a hardcoded white, and that is a colour reaching a terminal
	// that asked for none.
	styles.Focused.Prompt = theme.OnSurface(theme.Muted)
	styles.Blurred.Prompt = theme.OnSurface(theme.Muted)
	// The cursor's own colour, too. Left at the bubbles default it is a hardcoded white, which
	// is a colour reaching a terminal that asked for none, and which on the band reads as a
	// stray cell rather than as a cursor.
	if theme.IsNoColor() {
		styles.Cursor.Color = nil
	} else {
		styles.Cursor.Color = theme.CursorColor()
	}
	input.SetStyles(styles)

	prompt := Prompt{
		input:        input,
		theme:        theme,
		keys:         NewKeyMap(),
		suggest:      suggest,
		Label:        "$ ",
		historyLimit: DefaultHistoryLimit,
		now:          time.Now,
	}
	return prompt
}

// SetClock replaces the clock the elapsed indication is measured against. Tests inject a
// clock they control; nothing else should call this.
func (p *Prompt) SetClock(now func() time.Time) {
	if now != nil {
		p.now = now
	}
}

// SetHistoryLimit bounds the remembered history. A limit below one is ignored.
func (p *Prompt) SetHistoryLimit(limit int) {
	if limit < 1 {
		return
	}
	p.historyLimit = limit
	p.trimHistory()
}

// SetWidth sets the total width the prompt line has to fit in.
func (p *Prompt) SetWidth(width int) {
	p.width = max(width, 0)
	p.applyWidth()
}

// applyWidth hands the field whatever width is left over. It runs again whenever the
// in-flight indication appears or disappears, because that indication takes width from the
// field and the field's own horizontal scrolling has to agree with what is drawn.
func (p *Prompt) applyWidth() {
	p.input.SetWidth(max(p.fieldWidth(), 1))
}

// Width reports the total width the prompt renders at.
func (p Prompt) Width() int { return p.width }

// Focus gives the prompt the keyboard.
func (p *Prompt) Focus() tea.Cmd {
	return p.input.Focus()
}

// Blur takes the keyboard away, and closes the completion list with it: a completion popup
// hanging under a field that no longer has focus is just a lie about where input goes.
func (p *Prompt) Blur() {
	p.input.Blur()
	p.completionOpen = false
}

// Focused reports whether the prompt has the keyboard.
func (p Prompt) Focused() bool { return p.input.Focused() }

// Value is the line currently in the field.
func (p Prompt) Value() string { return p.input.Value() }

// SetValue replaces the line in the field, as a tool does when it pre-fills a command.
func (p *Prompt) SetValue(value string) {
	p.input.SetValue(value)
	p.input.CursorEnd()
	p.syncCompletion(false)
}

// Reset clears the field without touching the history.
func (p *Prompt) Reset() {
	p.input.Reset()
	p.cursor = len(p.history)
	p.draft = ""
	p.syncCompletion(false)
}

// History returns the remembered lines, oldest first. The copy is deliberate: the caller must
// not be able to reach in and edit the prompt's state.
func (p Prompt) History() []string {
	out := make([]string, len(p.history))
	copy(out, p.history)
	return out
}

// CompletionOpen reports whether the completion list is showing. This is what decides whether
// up and down mean completion or history.
func (p Prompt) CompletionOpen() bool { return p.completionOpen }

// Completions are the candidates the completion list is showing, in order.
func (p Prompt) Completions() []string {
	if !p.completionOpen {
		return nil
	}
	return p.input.MatchedSuggestions()
}

// CompletionIndex is the position of the highlighted candidate in Completions.
func (p Prompt) CompletionIndex() int { return p.input.CurrentSuggestionIndex() }

// Start marks a command as in flight, which turns on the spinner and the elapsed indication.
// Pair it with PromptTick to make the spinner turn.
func (p *Prompt) Start() {
	p.busy = true
	p.startedAt = p.now()
	p.frame = 0
	p.applyWidth()
}

// Finish marks the in-flight command as done.
func (p *Prompt) Finish() {
	p.busy = false
	p.applyWidth()
}

// Busy reports whether a command is in flight.
func (p Prompt) Busy() bool { return p.busy }

// Elapsed is how long the in-flight command has been running, zero when nothing is running.
func (p Prompt) Elapsed() time.Duration {
	if !p.busy {
		return 0
	}
	return p.now().Sub(p.startedAt)
}

// ConsumesEscape reports whether esc belongs to the prompt right now.
//
// esc drives a chain of increasingly drastic dismissals that the top-level model owns: close
// the completion list, dismiss the toast, abort the running command, and so on. The prompt
// cannot decide that chain, but it does know when the first link is its own, so the model asks
// rather than guessing. Without this the model would act on the same esc that closed the
// completion list.
func (p Prompt) ConsumesEscape() bool {
	return p.Focused() && p.completionOpen
}

// Update handles a message. It returns the updated prompt and any command to run; running a
// line arrives at the model as a PromptSubmitMsg.
func (p Prompt) Update(msg tea.Msg) (Prompt, tea.Cmd) {
	switch msg := msg.(type) {
	case PromptTickMsg:
		if !p.busy {
			return p, nil
		}
		frames := p.theme.Glyphs.Spinner
		if len(frames) > 0 {
			p.frame = (p.frame + 1) % len(frames)
		}
		return p, PromptTick()

	case tea.KeyPressMsg:
		if !p.Focused() {
			return p, nil
		}
		return p.handleKey(msg)
	}

	var cmd tea.Cmd
	p.input, cmd = p.input.Update(msg)
	return p, cmd
}

// handleKey resolves one key press against the prompt-scoped keymap.
func (p Prompt) handleKey(msg tea.KeyPressMsg) (Prompt, tea.Cmd) {
	switch {
	case key.Matches(msg, p.keys.Submit):
		line := strings.TrimSpace(p.input.Value())
		p.input.Reset()
		p.pushHistory(line)
		p.draft = ""
		p.syncCompletion(false)
		if line == "" {
			// An empty line is not a command. It still clears the field and closes the
			// completion list, which is what a user pressing enter on nothing expects.
			return p, nil
		}
		return p, func() tea.Msg { return PromptSubmitMsg{Line: line} }

	case key.Matches(msg, p.keys.Cancel):
		// Only the first link of the esc chain: closing the completion list. ConsumesEscape
		// tells the model whether that happened, so it knows whether to take the next link.
		if p.completionOpen {
			p.completionOpen = false
		}
		return p, nil

	case key.Matches(msg, p.keys.Complete):
		// tab is textinput's AcceptSuggestion, so accepting is forwarded rather than
		// reimplemented. A closed list reopens instead, which is what makes tab useful again
		// after an esc or a history recall.
		if !p.completionOpen {
			p.syncCompletion(true)
			return p, nil
		}
		var cmd tea.Cmd
		p.input, cmd = p.input.Update(msg)
		// Accepting closes the list: the line is now the candidate, and leaving it open would
		// put up and down back on completion for a word the user has finished.
		p.syncCompletion(false)
		return p, cmd

	case key.Matches(msg, p.keys.SuggestNext, p.keys.SuggestPrev):
		// ctrl+n and ctrl+p are the unambiguous pair: they always mean the completion list.
		// The first press spends itself opening the list, so that what the next press moves
		// is something the user can see.
		if !p.completionOpen {
			p.syncCompletion(true)
			return p, nil
		}
		var cmd tea.Cmd
		p.input, cmd = p.input.Update(msg)
		return p, cmd

	case key.Matches(msg, p.keys.HistoryPrev, p.keys.HistoryNext):
		if p.completionOpen {
			// Rule: up and down drive the completion list while it is open. textinput's own
			// default bindings already put up and down on Prev/NextSuggestion, so forwarding
			// the key unchanged is the whole implementation.
			var cmd tea.Cmd
			p.input, cmd = p.input.Update(msg)
			return p, cmd
		}
		if key.Matches(msg, p.keys.HistoryPrev) {
			p.historyBack()
		} else {
			p.historyForward()
		}
		return p, nil
	}

	// Everything else is line editing.
	before := p.input.Value()
	var cmd tea.Cmd
	p.input, cmd = p.input.Update(msg)
	// An edit re-opens the completion list, because the candidates just changed and the user
	// is mid-word. A key the field ignored, such as a page key aimed at a pane behind the
	// prompt, must not resurrect a list the user has dismissed.
	p.syncCompletion(p.input.Value() != before || p.completionOpen)
	return p, cmd
}

// pushHistory appends line, skipping blanks and an immediate repeat.
//
// Skipping the repeat is not cosmetic: without it, running the same read command five times
// to watch a value change fills the history with five identical entries and makes up useless.
func (p *Prompt) pushHistory(line string) {
	p.cursor = len(p.history)
	if line == "" {
		return
	}
	if len(p.history) > 0 && p.history[len(p.history)-1] == line {
		p.cursor = len(p.history)
		return
	}
	p.history = append(p.history, line)
	p.trimHistory()
	p.cursor = len(p.history)
}

// trimHistory drops the oldest lines once the limit is exceeded.
func (p *Prompt) trimHistory() {
	if len(p.history) <= p.historyLimit {
		return
	}
	drop := len(p.history) - p.historyLimit
	p.history = append([]string(nil), p.history[drop:]...)
	p.cursor = min(p.cursor, len(p.history))
}

// historyBack walks towards the oldest line. It stops at the oldest rather than wrapping,
// because wrapping in a history means a long hold silently lands somewhere unexpected.
func (p *Prompt) historyBack() {
	if len(p.history) == 0 {
		return
	}
	if p.cursor == len(p.history) {
		// Leaving the live line: remember it so that walking forward again gives it back.
		p.draft = p.input.Value()
	}
	if p.cursor == 0 {
		return
	}
	p.cursor--
	p.recall(p.history[p.cursor])
}

// historyForward walks towards the newest line, and one step past it back to the line that was
// in progress.
func (p *Prompt) historyForward() {
	if p.cursor >= len(p.history) {
		return
	}
	p.cursor++
	if p.cursor == len(p.history) {
		p.recall(p.draft)
		return
	}
	p.recall(p.history[p.cursor])
}

// recall puts a remembered line in the field.
func (p *Prompt) recall(line string) {
	p.input.SetValue(line)
	p.input.CursorEnd()
	// Closed, not open: a recalled line is complete, and leaving the list open would hand the
	// next up press to the completion list and strand the user mid-history.
	p.syncCompletion(false)
}

// syncCompletion refreshes the candidates for the current line and sets whether the list is
// showing. It is the only place p.completionOpen is assigned to true.
func (p *Prompt) syncCompletion(open bool) {
	if p.suggest == nil {
		p.input.ShowSuggestions = false
		p.input.SetSuggestions(nil)
		p.completionOpen = false
		return
	}
	candidates := p.suggest(p.input.Value())
	// ShowSuggestions gates textinput's own matching, including its up and down bindings, so
	// turning it off with no candidates keeps those keys out of the way of the history.
	p.input.ShowSuggestions = len(candidates) > 0
	p.input.SetSuggestions(candidates)
	p.completionOpen = open && len(p.input.MatchedSuggestions()) > 0
}

// fieldWidth is the width left for the text field once the label and the in-flight indication
// have taken theirs.
func (p Prompt) fieldWidth() int {
	return p.width - lipgloss.Width(p.Label) - lipgloss.Width(p.statusText()) - 1
}

// statusText is the unstyled in-flight indication: spinner frame, elapsed time and the hint
// that esc aborts. Empty when nothing is running.
func (p Prompt) statusText() string {
	if !p.busy {
		return ""
	}
	frames := p.theme.Glyphs.Spinner
	frame := ""
	if len(frames) > 0 {
		frame = frames[p.frame%len(frames)]
	}
	return frame + " " + formatElapsed(p.Elapsed()) + " esc cancel"
}

// View renders the prompt as exactly one line of exactly the configured width.
//
// One line, always: the responsive rules reserve a row for it in every layout, and the bug
// this replaces was a prompt that was allowed to vanish. The completion list is rendered
// separately by CompletionView so that the caller can place it as an overlay without the
// prompt row growing.
func (p Prompt) View() string {
	if p.width <= 0 {
		return ""
	}

	// The marker is drawn in the accent rather than muted: it is the one place on the screen
	// that takes typing, so it should read as active chrome and not as a label.
	label := p.theme.OnSurface(p.theme.Accent.Bold(true)).Render(p.Label)
	status := ""
	if text := p.statusText(); text != "" {
		status = p.theme.OnSurface(p.theme.Accent).Render(" " + text)
	}

	// The field gets whatever is left. fitLine then forces the assembled row to the exact
	// width, so a long line scrolls inside the field instead of pushing the status off the
	// end of the terminal.
	fieldRoom := max(p.width-lipgloss.Width(label)-lipgloss.Width(status), 1)
	field := p.padOnSurface(p.input.View(), fieldRoom)

	return p.padOnSurface(label+field+status, p.width)
}

// padOnSurface is fitLine with the band's background on the padding, so the raised row runs
// the full width instead of stopping where the text does.
func (p Prompt) padOnSurface(line string, width int) string {
	if actual := lipgloss.Width(line); actual < width {
		return line + p.theme.Surface.Render(strings.Repeat(" ", width-actual))
	}
	return fitLine(line, width)
}

// CompletionView renders the completion list, at most maxRows entries, or the empty string
// when the list is closed. The caller places it; on a short terminal it is an overlay over
// the pane above the prompt rather than a region competing for rows.
func (p Prompt) CompletionView(maxRows int) string {
	if !p.completionOpen || maxRows < 1 {
		return ""
	}
	candidates := p.input.MatchedSuggestions()
	if len(candidates) == 0 {
		return ""
	}

	selected := p.input.CurrentSuggestionIndex()
	// Scroll the window so the highlighted candidate is always in it.
	start := 0
	if selected >= maxRows {
		start = selected - maxRows + 1
	}
	end := min(start+maxRows, len(candidates))

	indent := strings.Repeat(" ", lipgloss.Width(p.Label))
	inner := max(p.width-lipgloss.Width(indent), 1)

	var out strings.Builder
	for i := start; i < end; i++ {
		if i > start {
			out.WriteString("\n")
		}
		marker := p.theme.Glyphs.Unselected
		style := p.theme.Muted
		if i == selected {
			marker = p.theme.Glyphs.Selected
			style = p.theme.SelectedRow
		}
		row := fitLine(marker+" "+candidates[i], inner)
		out.WriteString(indent)
		out.WriteString(style.Render(row))
	}
	// More candidates than rows: say so rather than letting the list look complete.
	if end < len(candidates) {
		out.WriteString("\n")
		out.WriteString(indent)
		out.WriteString(p.theme.Muted.Render(fitLine(p.theme.Glyphs.Ellipsis+" "+strconv.Itoa(len(candidates)-end)+" more", inner)))
	}
	return out.String()
}

// formatElapsed renders a duration compactly enough for the prompt row: tenths below a
// minute, minutes and seconds above it.
func formatElapsed(d time.Duration) string {
	if d < 0 {
		d = 0
	}
	if d < time.Minute {
		tenths := int64(d / (100 * time.Millisecond))
		return strconv.FormatInt(tenths/10, 10) + "." + strconv.FormatInt(tenths%10, 10) + "s"
	}
	minutes := int64(d / time.Minute)
	seconds := int64(d % time.Minute / time.Second)
	out := strconv.FormatInt(minutes, 10) + "m"
	if seconds < 10 {
		out += "0"
	}
	return out + strconv.FormatInt(seconds, 10) + "s"
}
