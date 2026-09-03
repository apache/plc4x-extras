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
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"charm.land/bubbles/v2/key"
	"charm.land/bubbles/v2/table"
	"charm.land/bubbles/v2/viewport"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"

	"github.com/rs/zerolog"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// Model is the browser's root Bubble Tea model.
//
// Every piece of state lives here and is mutated only inside Update. Nothing else may touch
// it: the implementation this replaces mutated tview widgets from goroutines through
// QueueUpdateDraw, which is precisely the pattern the Elm architecture forbids and precisely
// why none of it could be tested.

// pane identifies a focusable region.
type pane int

const (
	paneSidebar pane = iota
	paneMessages
	paneDetail
	paneLog

	paneCount
)

// title names a pane for the status bar and its border.
func (p pane) title() string {
	switch p {
	case paneSidebar:
		return "Session"
	case paneMessages:
		return "Messages"
	case paneDetail:
		return "Detail"
	case paneLog:
		return "Log"
	default:
		return "?"
	}
}

// sidebarRow is one line of the sidebar. Sections and items share a row list so that a single
// selection index walks the whole pane: the design review flagged two independent cursors in
// one focus target as a defect, and one flat list is the fix.
type sidebarRow struct {
	// heading marks a section title, which cannot be selected.
	heading string
	// label is the row text for a selectable row.
	label string
	// detail is dimmer trailing text, such as a driver's name.
	detail string
	// marker is a status glyph.
	marker string
	// connection and driver name what the row acts on when it is chosen.
	connection string
	driver     string
}

// selectable reports whether the cursor may rest on this row.
func (r sidebarRow) selectable() bool { return r.heading == "" }

// Options configures a Model.
type Options struct {
	// Session is the PLC seam: plcsession.Live normally, plcsession.Demo under --demo.
	Session plcsession.Session
	// Config is the persisted settings.
	Config *Config
	// Demo records that the session is simulated, so the UI can say so.
	Demo bool
	// Version labels the status bar.
	Version string
	// Theme overrides the theme; when nil one is derived from the environment and the
	// terminal's reported background.
	Theme *tui.Theme
	// ForceASCII pins the ASCII glyph set regardless of the locale.
	ForceASCII bool
	// Frames, when set, is the wire capture the detail pane's byte view reads. Demo mode leaves
	// it nil: a simulated device puts nothing on a wire, and inventing bytes for it would be
	// worse than saying there are none.
	Frames *plcsession.FrameLog
	// Now supplies timestamps, injected for tests.
	Now func() time.Time
}

// Model is the root model.
type Model struct {
	options  Options
	theme    tui.Theme
	keys     tui.KeyMap
	layout   tui.Layout
	registry *Registry
	env      *Env

	focus pane
	// promptFocused is tracked separately from focus because the prompt is not a pane: it is
	// always visible, and the pane focus ring is what tab walks.
	promptFocused bool

	prompt tui.Prompt
	help   tui.HelpFooter

	messages table.Model
	detail   viewport.Model
	// detailBytes switches the detail pane to the wire bytes of the selected request.
	detailBytes bool
	// follow keeps the newest message selected as events arrive. On by default, because a tool
	// watching a subscription is usually watching the latest value; it turns itself off the
	// moment the cursor is moved by hand.
	follow bool
	// filtering is true while the filter line is being typed, which claims the keyboard.
	filtering bool
	// filterDraft is the filter being typed, applied on enter.
	filterDraft string
	// toastExpanded shows the whole of a long error instead of one clipped line.
	toastExpanded bool
	// yanked records what was last copied, so the interface can confirm it.
	yanked string
	// catalogue is the tags the last browse found, kept as a reference list. Deliberately not
	// "the selected message's tags": mirroring the detail pane in the sidebar would show the
	// same thing twice, whereas a catalogue that persists is what you want beside the message
	// list once you have moved on to reading individual tags.
	catalogue           []plcsession.TagResult
	catalogueConnection string

	logView  viewport.Model
	composer *composer

	// running cancels the command in flight, and runSeq identifies it. A late outcome from an
	// aborted command has to be dropped rather than applied: cancelling a context does not
	// stop the goroutine that will eventually deliver the message, so without the sequence the
	// abort would be followed by the aborted command's own error appearing anyway.
	running context.CancelFunc
	runSeq  int

	// confirmQuit holds the "really quit?" question. ctrl+c used to kill the tool outright,
	// which is the wrong default for a session holding open connections: the same keystroke is
	// how a user stops a command that is taking too long.
	confirmQuit  bool
	sidebarRows  []sidebarRow
	sidebarIndex int

	// events backs the message table. The filter is applied when the rows are built.
	events   []plcsession.Event
	filter   string
	logLines []string

	// toast is the last error, held until dismissed. The old UI let errors scroll away in a
	// ten-row console, so a failed command could vanish before it was read.
	toast string

	streams []*Stream
	quit    bool
	ready   bool
}

// NewModel builds the root model.
func NewModel(options Options) *Model {
	if options.Now == nil {
		options.Now = time.Now
	}
	if options.Config == nil {
		config := NewConfig()
		options.Config = &config
	}

	theme := tui.NewTheme(themeOptions(true, options))
	if options.Theme != nil {
		theme = *options.Theme
	}

	registry := NewCommands()
	model := &Model{
		options:  options,
		theme:    theme,
		keys:     tui.NewKeyMap(),
		registry: registry,
		focus:    paneMessages,
	}
	model.env = &Env{
		Session:  options.Session,
		Config:   options.Config,
		Now:      options.Now,
		Registry: registry,
		Demo:     options.Demo,
		StreamContext: func() (context.Context, context.CancelFunc) {
			return context.WithCancel(context.Background())
		},
	}

	model.prompt = tui.NewPrompt(theme, func(line string) []string {
		return registry.Complete(model.env, line)
	})
	model.prompt.SetClock(options.Now)
	model.help = tui.NewHelpFooter(theme)

	model.messages = table.New(
		table.WithColumns(messageColumns(80, false)),
		table.WithFocused(false),
		table.WithStyles(tableStyles(theme)),
	)
	model.detail = viewport.New()
	model.logView = viewport.New()
	model.promptFocused = true
	model.follow = true
	model.refreshSidebar()
	return model
}

// themeOptions resolves the theme options for a background brightness.
func themeOptions(dark bool, options Options) tui.Options {
	themeOptions := tui.OptionsFromEnv(dark)
	if options.ForceASCII {
		themeOptions.ASCII = true
	}
	return themeOptions
}

// messageColumns sizes the message table to exactly the width it is given.
//
// The previous fixed widths claimed 48 cells, which is eight more than the pane has at the
// tool's own reference size of 100 columns. The table then truncated, and what it truncated was
// the tag -- the one field the row exists to show -- while the connection column repeated the
// same string on every line. Columns are therefore budgeted in order of what a row is for: the
// tag and its outcome first, the connection last, since with one connection open it says
// nothing at all.
func messageColumns(width int, showConnection bool) []table.Column {
	width = max(width, 24)

	// Columns are dropped, not squeezed. Squeezing is what the fixed widths did: they claimed
	// 48 cells in the 40-cell pane the reference layout gives, and the table then truncated the
	// tag -- the one field a row exists to show. So the budget is spent in priority order and
	// whatever does not fit is left out entirely, which is legible where a two-character column
	// is not.
	const tagFloor = 8

	stamp, operation, code := 13, 10, 7
	if width < 70 {
		// To the second is enough to place an event; the millisecond is in the detail pane.
		stamp, operation, code = 9, 7, 7
	}

	// number and connection are the two the tag outranks.
	number := 4
	connection := 0
	if showConnection && width >= 4+stamp+operation+code+tagFloor+16 {
		connection = 16
	}

	remaining := func() int { return width - number - stamp - operation - code - connection }
	if remaining() < tagFloor {
		connection = 0
	}
	if remaining() < tagFloor {
		// The row number is positional and repeated in the detail pane's ordering, so it is the
		// next thing the tag can have.
		number = 0
	}
	if remaining() < tagFloor {
		code = 4 // "OK" or "bad"
	}
	if remaining() < tagFloor {
		operation = 5 // "read", "brow", "subs"
	}
	if remaining() < tagFloor {
		stamp = 8 // HH:MM:SS
	}
	if remaining() < tagFloor {
		// At the very floor -- a pane in a 60-column terminal -- even the operation goes. The
		// detail pane names it, and a tag clipped to five characters names nothing.
		operation = 0
	}

	var columns []table.Column
	if number > 0 {
		columns = append(columns, table.Column{Title: "#", Width: number})
	}
	columns = append(columns, table.Column{Title: "time", Width: stamp})
	if operation > 0 {
		columns = append(columns, table.Column{Title: "op", Width: operation})
	}
	if connection > 0 {
		columns = append(columns, table.Column{Title: "connection", Width: connection})
	}
	return append(columns,
		table.Column{Title: "tag", Width: max(remaining(), 1)},
		table.Column{Title: "code", Width: code},
	)
}

// showConnectionColumn reports whether the connection is worth a column: only once more than
// one is open, since otherwise every row would carry the same string.
func (m *Model) showConnectionColumn() bool {
	return len(m.options.Session.Connections()) > 1
}

// tableStyles maps the theme onto the table's own styling.
//
// bubbles/table defaults to a palette of its own, so without this a colourless theme still
// emitted a 256-colour selected row -- which breaks NO_COLOR and makes golden output unstable.
func tableStyles(theme tui.Theme) table.Styles {
	styles := table.DefaultStyles()
	styles.Header = theme.Key.Bold(true)
	// Cell is deliberately bare rather than theme.Value. bubbles/table styles each cell and
	// then styles the selected row around them, and a cell's own styling ends with a reset --
	// so any foreground here wipes the selection from every cell after the first and leaves
	// the selected row indistinguishable. Body text wants the terminal's default colour
	// anyway, which is what a bare style renders as.
	styles.Cell = lipgloss.NewStyle()
	styles.Selected = theme.SelectedRow
	if theme.IsNoColor() {
		// Reverse video would be the obvious colourless highlight, but it fights the selection
		// glyph and hurts readability; bold alone is enough.
		styles.Header = theme.Key.Bold(true)
		styles.Selected = theme.Value.Bold(true)
	}
	return styles
}

// commandDoneMsg carries the outcome of a command back into Update.
type commandDoneMsg struct {
	line   string
	result Result
	err    error
	// seq identifies the run, so that an aborted command's outcome can be recognised and
	// dropped when it arrives after the fact.
	seq int
}

// streamEventMsg is one subscription event, plus the stream it came from so the reader can
// re-arm on the same channel.
type streamEventMsg struct {
	stream *Stream
	event  plcsession.Event
	open   bool
}

// Init starts the prompt cursor and its spinner ticker.
func (m *Model) Init() tea.Cmd {
	return tea.Batch(m.prompt.Focus(), tui.PromptTick())
}

// Update handles one message.
func (m *Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.resize(tui.Size{Width: msg.Width, Height: msg.Height})
		return m, nil

	case tea.BackgroundColorMsg:
		// The terminal reports its background at startup and whenever it changes, which is the
		// only reliable way to pick a palette that stays legible.
		if m.options.Theme == nil {
			m.theme = tui.NewTheme(themeOptions(msg.IsDark(), m.options))
			m.help = tui.NewHelpFooter(m.theme)
			m.messages.SetStyles(tableStyles(m.theme))
			m.refreshSidebar()
		}
		return m, nil

	case tea.KeyPressMsg:
		return m.handleKey(msg)

	case tea.MouseMsg:
		return m.handleMouse(msg)

	case tui.PromptSubmitMsg:
		return m.submit(msg.Line)

	case commandDoneMsg:
		return m.applyCommand(msg)

	case streamEventMsg:
		return m.applyStreamEvent(msg)
	}

	var cmd tea.Cmd
	m.prompt, cmd = m.prompt.Update(msg)
	return m, cmd
}

// handleKey routes a key press according to what currently has focus.
func (m *Model) handleKey(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	// The composer is modal: while it is open it owns the keyboard, apart from quit.
	if m.composer != nil {
		if key.Matches(msg, m.keys.ForFocus(tui.FocusOverlay).Quit) {
			return m.shutdown()
		}
		return m.updateComposer(msg)
	}

	// The quit question is modal and answered before anything else, including the filter line
	// and the prompt: a key that reached the text field while it was up would leave the
	// question on screen with no way to tell what answered it.
	if m.confirmQuit {
		return m.answerQuit(msg)
	}

	// The filter line is text entry, so it takes every key but the ones that finish it.
	if m.filtering {
		return m.filterKey(msg)
	}

	keys := m.keys.ForFocus(m.currentFocus())

	switch {
	case key.Matches(msg, keys.Quit):
		// ctrl+c stops what is happening rather than ending the session. A user whose read is
		// hanging on an unreachable device reaches for it to get the prompt back, and losing
		// every open connection instead is not a reasonable answer to that keystroke. With
		// nothing running it asks, because q and ctrl+c are both easy to hit by accident.
		if m.abortRunning() {
			return m, nil
		}
		m.confirmQuit = true
		return m, nil

	case key.Matches(msg, keys.EOF):
		// The shell rule: ctrl+d ends the session on an empty line, and is left to the text
		// input to delete a character otherwise. bubbles/textinput binds it to delete-forward,
		// so quitting unconditionally would take that away.
		if !m.promptFocused || m.prompt.Value() == "" {
			m.confirmQuit = true
			return m, nil
		}

	case key.Matches(msg, keys.Help):
		m.help.SetShowAll(!m.help.ShowAll())
		return m, nil

	case key.Matches(msg, keys.Cancel):
		// Escape unwinds one level at a time. A command in flight is the outermost level,
		// because the prompt advertises "esc cancel" for exactly as long as one is running.
		if m.abortRunning() {
			return m, nil
		}
		// Then the prompt's own use of it, then the toast, then a focused pane back to the
		// prompt.
		if m.promptFocused && m.prompt.ConsumesEscape() {
			break
		}
		if m.toast != "" {
			m.toast = ""
			return m, nil
		}
		if !m.promptFocused {
			m.focusPrompt()
			return m, nil
		}

	case key.Matches(msg, keys.PaneJump), m.promptFocused && m.prompt.Value() == "" && isPaneDigit(msg):
		// Direct pane hotkeys. The pane numbers are drawn in the pane titles, so the mapping is
		// visible rather than something to remember.
		//
		// alt+1..alt+4 is the nominal binding, but it cannot be the only one: GNOME Terminal,
		// Konsole and Windows Terminal all bind alt+digit to switching terminal tabs and never
		// deliver it to the application. A bare digit therefore works too whenever it cannot be
		// text -- inside a pane, or at a prompt with nothing typed yet. No command begins with a
		// digit, so nothing is shadowed.
		if target, ok := paneForDigit(msg.String()); ok {
			m.blurPrompt()
			m.setFocus(target)
		}
		return m, nil

	case key.Matches(msg, keys.NextPane):
		m.cyclePane(1)
		return m, nil

	case key.Matches(msg, keys.PrevPane):
		m.cyclePane(-1)
		return m, nil

	case key.Matches(msg, keys.FocusPrompt):
		m.focusPrompt()
		return m, nil

	case key.Matches(msg, keys.FocusPanes):
		// From the prompt, tab enters the pane ring only when there is nothing to complete;
		// otherwise tab is completion, which is what a REPL user expects.
		if m.promptFocused && m.prompt.CompletionOpen() {
			break
		}
		if m.promptFocused {
			m.blurPrompt()
			m.setFocus(paneSidebar)
			return m, nil
		}
	}

	if m.promptFocused {
		var cmd tea.Cmd
		m.prompt, cmd = m.prompt.Update(msg)
		return m, cmd
	}
	return m.paneKey(msg, keys)
}

// isPaneDigit reports whether a key press is a bare digit naming a pane.
func isPaneDigit(msg tea.KeyPressMsg) bool {
	name := msg.String()
	if len(name) != 1 {
		return false
	}
	_, ok := paneForDigit(name)
	return ok
}

// paneForDigit maps a pane-jump key onto a pane. The digit is the last character, so it works
// for the bare "2" and for "alt+2" alike.
func paneForDigit(name string) (pane, bool) {
	if name == "" {
		return 0, false
	}
	digit, err := strconv.Atoi(name[len(name)-1:])
	if err != nil || digit < 1 || digit > int(paneCount) {
		return 0, false
	}
	return pane(digit - 1), true
}

// filterKey handles a key while the message filter is being typed.
func (m *Model) filterKey(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	switch msg.String() {
	case "esc":
		// Escape abandons the edit and clears the filter, which is the only way back to the
		// whole list that does not require deleting what was typed.
		m.filtering = false
		m.filterDraft = ""
		m.SetFilter("")
		return m, nil
	case "enter":
		m.filtering = false
		m.SetFilter(m.filterDraft)
		return m, nil
	case "backspace":
		if m.filterDraft != "" {
			runes := []rune(m.filterDraft)
			m.filterDraft = string(runes[:len(runes)-1])
			m.SetFilter(m.filterDraft)
		}
		return m, nil
	}
	if text := msg.String(); len([]rune(text)) == 1 {
		m.filterDraft += text
		// Applied as it is typed: seeing the list narrow is the whole point of a filter.
		m.SetFilter(m.filterDraft)
	}
	return m, nil
}

// Filtering reports whether the filter line is being typed, for tests.
func (m *Model) Filtering() bool { return m.filtering }

// Following reports whether the newest message stays selected, for tests.
func (m *Model) Following() bool { return m.follow }

// currentFocus maps the model's focus onto the keymap's notion of it.
func (m *Model) currentFocus() tui.Focus {
	switch {
	case m.composer != nil:
		return tui.FocusOverlay
	case m.promptFocused:
		return tui.FocusPrompt
	default:
		return tui.FocusPane
	}
}

// paneKey handles a key aimed at the focused pane.
//
// Every binding the help advertises is handled here. That is not a nicety: the shared keymap
// puts g/G, the page keys, the filter, yank, follow, expand and the log level in the footer,
// and for a while the browser advertised all seven and implemented none of them -- the same
// dead-affordance defect the port existed to remove, reintroduced in the help text.
func (m *Model) paneKey(msg tea.KeyPressMsg, keys tui.KeyMap) (tea.Model, tea.Cmd) {
	// Bindings that mean the same thing in every pane.
	switch {
	case key.Matches(msg, keys.Top):
		return m, m.paneTop()
	case key.Matches(msg, keys.Bottom):
		return m, m.paneBottom()
	case key.Matches(msg, keys.PageUp):
		return m, m.panePage(-1)
	case key.Matches(msg, keys.PageDown):
		return m, m.panePage(1)
	case key.Matches(msg, keys.Yank):
		return m.yank()
	case key.Matches(msg, keys.Expand):
		m.toastExpanded = !m.toastExpanded
		return m, nil
	case key.Matches(msg, keys.LogLevel):
		return m.cycleLogLevel()
	}

	// Composing a request from the selected message's tag: browse, see a tag, act on it.
	// Retyping the address the pane is already showing was the sharpest edge in the tool.
	//
	// Both panes that show that message, not just the one holding the selection. Detail is
	// where the tag is actually read, and it is Detail that draws the "r read / w write /
	// s subscribe" hint -- so binding these to Messages alone put the advertisement in the one
	// pane where the keys did nothing. The sidebar is left out: its rows are connections,
	// drivers and tag names rather than a request, so acting on a message from there would be
	// acting on something the pane is not showing.
	if m.focus == paneMessages || m.focus == paneDetail {
		if spec, ok := m.composeFromSelection(msg.String()); ok {
			m.openComposer(spec)
			return m, nil
		}
	}

	switch m.focus {
	case paneSidebar:
		switch {
		case key.Matches(msg, keys.Up):
			m.moveSidebar(-1)
			return m, nil
		case key.Matches(msg, keys.Down):
			m.moveSidebar(1)
			return m, nil
		case key.Matches(msg, keys.Select):
			return m.activateSidebarRow()
		}
	case paneMessages:
		switch {
		case key.Matches(msg, keys.Filter):
			m.filtering = true
			m.filterDraft = m.filter
			return m, nil
		case key.Matches(msg, keys.Follow):
			m.follow = !m.follow
			if m.follow {
				m.messages.GotoBottom()
				m.syncDetail()
			}
			return m, nil
		case key.Matches(msg, keys.Select):
			m.syncDetail()
			return m, nil
		}
		var cmd tea.Cmd
		m.messages, cmd = m.messages.Update(msg)
		// Moving the cursor by hand means the user has taken over from the stream.
		if key.Matches(msg, keys.Up) || key.Matches(msg, keys.Down) {
			m.follow = false
		}
		m.syncDetail()
		return m, cmd
	case paneDetail:
		if key.Matches(msg, keys.Wrap) {
			m.detail.SoftWrap = !m.detail.SoftWrap
			return m, nil
		}
		if msg.String() == "b" {
			// The same key the pcap analyzer uses for its byte view, so the two tools agree.
			m.detailBytes = !m.detailBytes
			m.syncDetail()
			return m, nil
		}
		var cmd tea.Cmd
		m.detail, cmd = m.detail.Update(msg)
		return m, cmd
	case paneLog:
		var cmd tea.Cmd
		m.logView, cmd = m.logView.Update(msg)
		return m, cmd
	}
	return m, nil
}

// paneTop jumps the focused pane to its start.
func (m *Model) paneTop() tea.Cmd {
	switch m.focus {
	case paneSidebar:
		m.sidebarIndex = 0
		m.clampSidebar()
	case paneMessages:
		m.messages.GotoTop()
		m.follow = false
		m.syncDetail()
	case paneDetail:
		m.detail.GotoTop()
	case paneLog:
		m.logView.GotoTop()
	}
	return nil
}

// paneBottom jumps the focused pane to its end.
func (m *Model) paneBottom() tea.Cmd {
	switch m.focus {
	case paneSidebar:
		m.sidebarIndex = len(m.sidebarRows) - 1
		m.clampSidebar()
	case paneMessages:
		m.messages.GotoBottom()
		m.syncDetail()
	case paneDetail:
		m.detail.GotoBottom()
	case paneLog:
		m.logView.GotoBottom()
	}
	return nil
}

// panePage scrolls the focused pane by a screenful in a direction.
func (m *Model) panePage(direction int) tea.Cmd {
	switch m.focus {
	case paneSidebar:
		for range max(m.layout.BodyHeight-2, 1) {
			m.moveSidebar(direction)
		}
	case paneMessages:
		page := max(m.messages.Height(), 1)
		if direction < 0 {
			m.messages.MoveUp(page)
		} else {
			m.messages.MoveDown(page)
		}
		m.follow = false
		m.syncDetail()
	case paneDetail:
		m.detail.SetYOffset(max(m.detail.YOffset()+direction*max(m.detail.Height(), 1), 0))
	case paneLog:
		m.logView.SetYOffset(max(m.logView.YOffset()+direction*max(m.logView.Height(), 1), 0))
	}
	return nil
}

// yank copies what the focused pane is showing to the clipboard.
//
// tea.SetClipboard writes an OSC52 sequence, which the terminal forwards to the local
// clipboard, so this works over ssh as well as locally.
func (m *Model) yank() (tea.Model, tea.Cmd) {
	text := ""
	switch m.focus {
	case paneSidebar:
		if row, ok := m.SidebarSelection(); ok {
			text = row.label
		}
	case paneMessages, paneDetail:
		if event, ok := m.SelectedEvent(); ok {
			text = yankText(event)
		}
	case paneLog:
		text = strings.Join(m.logLines, "\n")
	}
	if text == "" {
		return m, nil
	}
	m.yanked = text
	m.appendLog("copied " + strconv.Itoa(len(text)) + " bytes to the clipboard")
	return m, tea.SetClipboard(text)
}

// yankText renders an event as the plain text a user would want on the clipboard: the values,
// not the styling.
func yankText(event plcsession.Event) string {
	var parts []string
	parts = append(parts, string(event.Kind)+" "+event.Connection)
	for _, tag := range event.Tags {
		line := tag.Address
		if tag.DataType != "" {
			line += " " + tag.DataType
		}
		if tag.Value != "" {
			line += " " + tag.Value
		}
		if !tag.Succeeded() {
			line += " " + tag.Code
		}
		parts = append(parts, line)
	}
	return strings.Join(parts, "\n")
}

// cycleLogLevel steps the log level, so the noise can be turned up without leaving the pane.
func (m *Model) cycleLogLevel() (tea.Model, tea.Cmd) {
	order := []zerolog.Level{
		zerolog.ErrorLevel, zerolog.WarnLevel, zerolog.InfoLevel, zerolog.DebugLevel, zerolog.TraceLevel,
	}
	current, err := zerolog.ParseLevel(m.options.Config.LogLevel)
	if err != nil {
		current = zerolog.InfoLevel
	}
	next := order[0]
	for i, level := range order {
		if level == current {
			next = order[(i+1)%len(order)]
			break
		}
	}
	m.options.Config.LogLevel = next.String()
	zerolog.SetGlobalLevel(next)
	m.appendLog("log level " + next.String())
	return m, nil
}

// composeFromSelection turns r, w or s pressed over a message into a pre-filled request for
// the tag that message is showing.
func (m *Model) composeFromSelection(pressed string) (ComposeSpec, bool) {
	var operation Operation
	switch pressed {
	case "r":
		operation = OperationRead
	case "w":
		operation = OperationWrite
	case "s":
		operation = OperationSubscribe
	default:
		return ComposeSpec{}, false
	}

	event, ok := m.SelectedEvent()
	if !ok || len(event.Tags) == 0 {
		return ComposeSpec{}, false
	}
	tags := make([]plcsession.TagSpec, 0, len(event.Tags))
	for i, tag := range event.Tags {
		if tag.Address == "" {
			continue
		}
		tags = append(tags, plcsession.TagSpec{Name: "tag" + strconv.Itoa(i+1), Address: tag.Address})
	}
	if len(tags) == 0 {
		return ComposeSpec{}, false
	}
	return ComposeSpec{Operation: operation, Connection: event.Connection, Tags: tags}, true
}

// focusPrompt returns the keyboard to the command line.
func (m *Model) focusPrompt() {
	m.promptFocused = true
	m.messages.Blur()
	_ = m.prompt.Focus()
}

// blurPrompt moves the keyboard into the pane ring.
func (m *Model) blurPrompt() {
	m.promptFocused = false
	m.prompt.Blur()
}

// cyclePane walks the focus ring, returning to the prompt past either end so that no sequence
// of tabs can strand the user away from the command line.
func (m *Model) cyclePane(delta int) {
	if m.promptFocused {
		m.blurPrompt()
		if delta < 0 {
			m.setFocus(paneLog)
		} else {
			m.setFocus(paneSidebar)
		}
		return
	}
	next := int(m.focus) + delta
	if next < 0 || next >= int(paneCount) {
		m.focusPrompt()
		return
	}
	m.setFocus(pane(next))
}

// setFocus moves focus to a pane, keeping the table's own focus flag in step.
func (m *Model) setFocus(p pane) {
	m.focus = p
	if p == paneMessages {
		m.messages.Focus()
	} else {
		m.messages.Blur()
	}
}

// Focused reports the focused pane and whether the prompt holds the keyboard, for tests.
func (m *Model) Focused() (pane, bool) { return m.focus, m.promptFocused }

// shutdown cancels every subscription and saves the configuration before quitting.
func (m *Model) shutdown() (tea.Model, tea.Cmd) {
	m.quit = true
	for _, stream := range m.streams {
		stream.Cancel()
	}
	m.streams = nil
	_ = m.options.Session.Close()
	if path, err := ConfigPath(); err == nil {
		_ = m.options.Config.Save(path)
	}
	return m, tea.Quit
}

// answerQuit resolves the quit question.
//
// Only an explicit yes ends the session. Anything else takes the question down and is swallowed
// rather than passed on, so a stray keystroke cannot both dismiss the question and do something
// else the user did not see coming.
func (m *Model) answerQuit(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	keys := m.keys.ForFocus(m.currentFocus())
	switch {
	case key.Matches(msg, keys.Quit), key.Matches(msg, keys.Submit):
		// ctrl+c again confirms, which is the pattern every shell has taught.
		return m.shutdown()
	case msg.String() == "y", msg.String() == "Y":
		return m.shutdown()
	}
	m.confirmQuit = false
	return m, nil
}

// submit runs the line the prompt handed over.
func (m *Model) submit(line string) (tea.Model, tea.Cmd) {
	trimmed := strings.TrimSpace(line)
	if trimmed == "" {
		return m, nil
	}
	m.options.Config.AddCommand(trimmed)
	m.appendLog("$ " + trimmed)
	m.prompt.Start()
	return m, m.runCommand(trimmed)
}

// abortRunning cancels the command in flight and reports whether there was one.
//
// The sequence is bumped so that the outcome already on its way back is ignored. The prompt is
// released here rather than when that outcome arrives, because the point of an abort is that
// the user gets the prompt back now.
func (m *Model) abortRunning() bool {
	if m.running == nil {
		return false
	}
	m.running()
	m.running = nil
	m.runSeq++
	m.prompt.Finish()
	if m.composer != nil && m.composer.submitting {
		m.composer.submitting = false
		m.composer.problem = "aborted"
	}
	m.appendLogAt("warn", "aborted")
	return true
}

// runCommand executes a line off the event loop and reports the outcome as a message.
//
// This is the shape every piece of I/O in the model takes: a tea.Cmd does the work and a typed
// message carries the result back, so that nothing but Update ever writes state.
func (m *Model) runCommand(line string) tea.Cmd {
	env := m.env
	registry := m.registry
	// Cancellable, because the prompt says "esc cancel" while a command runs and that has to
	// be true. A read against an unreachable device blocks for the driver's whole timeout.
	ctx, cancel := context.WithCancel(context.Background())
	m.running = cancel
	seq := m.runSeq
	return func() tea.Msg {
		result, err := registry.Execute(ctx, env, line)
		return commandDoneMsg{line: line, result: result, err: err, seq: seq}
	}
}

// applyCommand folds a command's outcome into the model.
func (m *Model) applyCommand(msg commandDoneMsg) (tea.Model, tea.Cmd) {
	if msg.seq != m.runSeq {
		// The outcome of a command the user aborted. It has already been reported as aborted,
		// and applying it now would contradict that.
		return m, nil
	}
	m.running = nil
	m.prompt.Finish()

	// A form marked submitting is waiting for exactly this message, so it is the composer's
	// answer rather than the prompt's. On success the form has done its job and closes; on
	// failure it stays open carrying the reason, so the request can be corrected instead of
	// retyped. Without this the form stayed open showing "running…" forever.
	if m.composer != nil && m.composer.submitting {
		m.composer.submitting = false
		if msg.err != nil {
			m.composer.problem = msg.err.Error()
			m.appendLogError(msg.err.Error())
			return m, nil
		}
		m.closeComposer()
		return m.applyResult(msg.result)
	}

	if msg.err != nil {
		m.toast = msg.err.Error()
		m.appendLogError(msg.err.Error())
		return m, nil
	}
	m.prompt.Reset()
	return m.applyResult(msg.result)
}

// applyResult applies a Result. It is separate from applyCommand so the composer can reuse it.
func (m *Model) applyResult(result Result) (tea.Model, tea.Cmd) {
	var cmds []tea.Cmd

	for _, line := range result.Lines {
		m.appendLog(line)
	}
	if len(result.Events) > 0 {
		for _, event := range result.Events {
			if event.Kind == plcsession.EventBrowse && len(event.Tags) > 0 {
				m.catalogue = event.Tags
				m.catalogueConnection = event.Connection
			}
		}
		m.events = append(m.events, result.Events...)
		m.trimEvents()
		m.refreshMessages()
	}
	if result.Clear.Has(ClearMessages) {
		m.catalogue, m.catalogueConnection = nil, ""
		m.events = nil
		m.refreshMessages()
		m.detail.SetContent("")
	}
	if result.Clear.Has(ClearConsole) {
		m.logLines = nil
		m.logView.SetContent("")
	}
	if result.ConnectionsChanged || result.DriversChanged {
		m.refreshSidebar()
	}
	if result.LogLevel != nil {
		m.options.Config.LogLevel = result.LogLevel.String()
	}
	if result.Compose != nil {
		m.openComposer(*result.Compose)
	}
	if result.Stream != nil {
		m.streams = append(m.streams, result.Stream)
		cmds = append(cmds, awaitStream(result.Stream))
	}
	if result.Quit {
		return m.shutdown()
	}
	return m, tea.Batch(cmds...)
}

// awaitStream reads ONE event and re-arms itself.
//
// Reading in a loop from a goroutine would mean touching the model from outside Update. One
// value per command is how Bubble Tea consumes a channel.
func awaitStream(stream *Stream) tea.Cmd {
	return func() tea.Msg {
		event, open := <-stream.Events
		return streamEventMsg{stream: stream, event: event, open: open}
	}
}

// applyStreamEvent folds a subscription event in and re-arms the reader.
func (m *Model) applyStreamEvent(msg streamEventMsg) (tea.Model, tea.Cmd) {
	if !msg.open {
		m.dropStream(msg.stream)
		m.appendLog("subscription ended " + msg.stream.Connection + " " + msg.stream.Label)
		return m, nil
	}
	m.events = append(m.events, msg.event)
	m.trimEvents()
	m.refreshMessages()
	return m, awaitStream(msg.stream)
}

// dropStream forgets a finished subscription.
func (m *Model) dropStream(stream *Stream) {
	for i, candidate := range m.streams {
		if candidate == stream {
			m.streams = append(m.streams[:i], m.streams[i+1:]...)
			return
		}
	}
}

// trimEvents bounds the message list, as the old UI's MaxOutputLines did.
func (m *Model) trimEvents() {
	limit := m.options.Config.MaxOutputLines
	if limit > 0 && len(m.events) > limit {
		m.events = m.events[len(m.events)-limit:]
	}
}

// appendLog adds a console line at info level.
func (m *Model) appendLog(line string) {
	m.appendLogAt("info", line)
}

// appendLogError adds a console line at error level, so a failure has shape in the pane rather
// than being one more grey line among many.
func (m *Model) appendLogError(line string) {
	m.appendLogAt("error", line)
}

// appendLogAt adds a console line at a level, bounded by the configured maximum.
func (m *Model) appendLogAt(level, line string) {
	stamp := m.options.Now().Format("15:04:05")
	styled := m.theme.Muted.Render(stamp) + " " +
		m.theme.LogLevel(level).Render(logLevelTag(level)) + " " + line
	m.logLines = append(m.logLines, styled)
	if limit := m.options.Config.MaxConsoleLines; limit > 0 && len(m.logLines) > limit {
		m.logLines = m.logLines[len(m.logLines)-limit:]
	}
	m.logView.SetContent(strings.Join(m.logLines, "\n"))
	m.logView.GotoBottom()
}

// logLevelTag is the three-letter level marker, which keeps the lines aligned.
func logLevelTag(level string) string {
	switch level {
	case "error":
		return "ERR"
	case "warn":
		return "WRN"
	default:
		return "INF"
	}
}

// AppendLog exposes the console so a caller can write a startup banner before the program runs.
func (m *Model) AppendLog(line string) { m.appendLog(line) }

// FocusName names the region holding the keyboard, for tests.
func (m *Model) FocusName() string { return m.focusName() }

// ConfirmingQuit reports whether the quit question is up, for tests.
func (m *Model) ConfirmingQuit() bool { return m.confirmQuit }

// LogLines exposes the console contents, for tests.
func (m *Model) LogLines() []string { return append([]string(nil), m.logLines...) }

// Toast exposes the pending error line, for tests.
func (m *Model) Toast() string { return m.toast }

// SetFilter sets the message filter.
func (m *Model) SetFilter(filter string) {
	m.filter = filter
	m.refreshMessages()
}

// filteredEvents returns the events the table shows.
//
// bubbles/table has no filtering of its own -- verified: the package contains no reference to
// filtering at all -- so the filter is applied to the row slice here and the "n of m" counter
// is derived from the two lengths.
func (m *Model) filteredEvents() []plcsession.Event {
	if m.filter == "" {
		return m.events
	}
	needle := strings.ToLower(m.filter)
	var out []plcsession.Event
	for _, event := range m.events {
		if strings.Contains(strings.ToLower(eventHaystack(event)), needle) {
			out = append(out, event)
		}
	}
	return out
}

// eventHaystack is the text a filter matches against.
func eventHaystack(event plcsession.Event) string {
	parts := []string{string(event.Kind), event.Connection, event.Summary}
	for _, tag := range event.Tags {
		parts = append(parts, tag.Address, tag.Name, tag.Value, tag.Code)
	}
	return strings.Join(parts, " ")
}

// refreshMessages rebuilds the table rows from the events.
func (m *Model) refreshMessages() {
	events := m.filteredEvents()
	columns := m.messages.Columns()
	// The row has to match the columns the table was given: a cell more or fewer than there are
	// columns is silently dropped or leaves a hole.
	withConnection, withNumber, withOperation := false, false, false
	stampWidth := 9
	for _, column := range columns {
		switch column.Title {
		case "connection":
			withConnection = true
		case "#":
			withNumber = true
		case "op":
			withOperation = true
		case "time":
			stampWidth = column.Width
		}
	}
	layout := "15:04:05"
	if stampWidth >= 13 {
		layout = "15:04:05.000"
	}

	rows := make([]table.Row, 0, len(events))
	for i, event := range events {
		var row table.Row
		if withNumber {
			row = append(row, strconv.Itoa(i+1))
		}
		row = append(row, event.Received.Format(layout))
		if withOperation {
			row = append(row, string(event.Kind))
		}
		if withConnection {
			row = append(row, event.Connection)
		}
		// The outcome carries its own colour. bubbles/table styles every cell alike, so the
		// meaning has to travel in the cell's own text; the table measures width with an
		// ANSI-aware width function, so the escapes do not disturb the column arithmetic.
		rows = append(rows, append(row, firstTagAddress(event), m.renderCode(event)))
	}
	m.messages.SetRows(rows)
	if m.follow && len(rows) > 0 {
		m.messages.GotoBottom()
	}
	m.syncDetail()
}

// firstTagAddress is the tag column's content.
func firstTagAddress(event plcsession.Event) string {
	switch len(event.Tags) {
	case 0:
		return ""
	case 1:
		return event.Tags[0].Address
	default:
		return fmt.Sprintf("%s +%d", event.Tags[0].Address, len(event.Tags)-1)
	}
}

// renderCode is eventCode with its meaning in colour: an OK is green and a failure red, so a
// bad row is findable in a full pane without reading it.
func (m *Model) renderCode(event plcsession.Event) string {
	code := eventCode(event)
	switch {
	case code == "":
		return ""
	case code == plcsession.ResponseCodeOK:
		return m.theme.Ok.Render(code)
	default:
		return m.theme.Err.Render(code)
	}
}

// eventCode summarises an event's outcome for the code column.
func eventCode(event plcsession.Event) string {
	failed := 0
	for _, tag := range event.Tags {
		if !tag.Succeeded() {
			failed++
		}
	}
	switch {
	case len(event.Tags) == 0:
		return ""
	case failed == 0:
		return plcsession.ResponseCodeOK
	default:
		return fmt.Sprintf("%d bad", failed)
	}
}

// syncDetail rebuilds the detail pane for the selected message.
func (m *Model) syncDetail() {
	events := m.filteredEvents()
	cursor := m.messages.Cursor()
	if cursor < 0 || cursor >= len(events) {
		m.detail.SetContent("")
		return
	}
	event := events[cursor]
	if m.detailBytes {
		m.detail.SetContent(renderEventFrames(m.theme, event, m.options.Frames))
		return
	}
	m.detail.SetContent(renderEventDetail(m.theme, event, m.detail.Width()))
}

// SelectedEvent exposes the highlighted message, for tests and for the detail pane.
func (m *Model) SelectedEvent() (plcsession.Event, bool) {
	events := m.filteredEvents()
	cursor := m.messages.Cursor()
	if cursor < 0 || cursor >= len(events) {
		return plcsession.Event{}, false
	}
	return events[cursor], true
}

// detailShowsCatalogue reports whether the detail pane is currently displaying the same tag
// list the sidebar keeps as its catalogue.
func (m *Model) detailShowsCatalogue() bool {
	event, ok := m.SelectedEvent()
	if !ok || event.Kind != plcsession.EventBrowse {
		return false
	}
	return event.Connection == m.catalogueConnection && len(event.Tags) == len(m.catalogue)
}

// FailedCount reports how many held messages carry a failed tag.
func (m *Model) FailedCount() int {
	failed := 0
	for _, event := range m.events {
		for _, tag := range event.Tags {
			if !tag.Succeeded() {
				failed++
				break
			}
		}
	}
	return failed
}

// EventCount reports how many messages are held and how many the filter shows.
func (m *Model) EventCount() (shown, total int) {
	return len(m.filteredEvents()), len(m.events)
}

// refreshSidebar re-reads the session into the sidebar rows.
func (m *Model) refreshSidebar() {
	glyphs := m.theme.Glyphs
	rows := []sidebarRow{{heading: "CONNECTIONS"}}
	connections := m.options.Session.Connections()
	if len(connections) == 0 {
		// An empty pane that names the command to run beats an empty pane.
		rows = append(rows, sidebarRow{label: "none, try: connect"})
	}
	for _, connection := range connections {
		rows = append(rows, sidebarRow{
			label:      connection.ID,
			marker:     glyphs.Yes,
			connection: connection.ID,
		})
	}

	rows = append(rows, sidebarRow{heading: "DRIVERS"})
	drivers := m.options.Session.Drivers()
	if len(drivers) == 0 {
		rows = append(rows, sidebarRow{label: "none, try: register"})
	}
	for _, driver := range drivers {
		marker := glyphs.No
		if driver.SupportsDiscovery {
			marker = glyphs.Yes
		}
		rows = append(rows, sidebarRow{
			label:  driver.Code,
			detail: driver.Name,
			marker: marker,
			driver: driver.Code,
		})
	}

	m.sidebarRows = rows
	m.clampSidebar()
}

// clampSidebar keeps the cursor on a selectable row.
func (m *Model) clampSidebar() {
	if len(m.sidebarRows) == 0 {
		m.sidebarIndex = 0
		return
	}
	m.sidebarIndex = min(max(m.sidebarIndex, 0), len(m.sidebarRows)-1)
	if !m.sidebarRows[m.sidebarIndex].selectable() {
		m.moveSidebar(1)
	}
}

// moveSidebar walks to the next selectable row in a direction, stopping at the ends.
func (m *Model) moveSidebar(delta int) {
	for i := m.sidebarIndex + delta; i >= 0 && i < len(m.sidebarRows); i += delta {
		if m.sidebarRows[i].selectable() {
			m.sidebarIndex = i
			return
		}
	}
}

// activateSidebarRow acts on the highlighted row.
//
// Both of these handlers were "// TODO: disconnect popup" in the previous UI: selecting a
// connection or a driver did nothing at all, which is the kind of dead affordance this port
// exists to remove. Selecting pre-fills the prompt rather than acting immediately, because a
// destructive action one keystroke away from a cursor move is too easy to trigger by accident.
func (m *Model) activateSidebarRow() (tea.Model, tea.Cmd) {
	row, ok := m.SidebarSelection()
	if !ok {
		return m, nil
	}
	switch {
	case row.connection != "":
		m.prompt.SetValue("read " + row.connection + " ")
		m.focusPrompt()
	case row.driver != "":
		m.prompt.SetValue("discover " + row.driver)
		m.focusPrompt()
	}
	return m, nil
}

// SidebarSelection exposes the highlighted row, for tests.
func (m *Model) SidebarSelection() (sidebarRow, bool) {
	if m.sidebarIndex < 0 || m.sidebarIndex >= len(m.sidebarRows) {
		return sidebarRow{}, false
	}
	return m.sidebarRows[m.sidebarIndex], true
}

// PromptValue exposes the command line's contents, for tests.
func (m *Model) PromptValue() string { return m.prompt.Value() }

// PromptCompletions exposes the completion candidates, for tests.
func (m *Model) PromptCompletions() []string { return m.prompt.Completions() }

// PromptCompletionOpen reports whether the completion list is showing, for tests.
func (m *Model) PromptCompletionOpen() bool { return m.prompt.CompletionOpen() }

// PromptBusy reports whether a command is in flight, for tests.
func (m *Model) PromptBusy() bool { return m.prompt.Busy() }

// resize recomputes the layout and resizes every child.
func (m *Model) resize(size tui.Size) {
	m.layout = tui.Compute(size)
	m.ready = true
	if m.layout.Mode == tui.ModeTooSmall {
		return
	}

	// The prompt and the help footer span the full width less one cell of margin either side.
	// One cell narrower than the screen, not two: renderPrompt draws a single gutter cell to
	// the prompt's left, and together they have to cover the row exactly, or the command bar's
	// band stops one cell short of the right edge.
	m.prompt.SetWidth(max(size.Width-1, 1))
	m.help.SetWidth(max(size.Width-2, 1))

	mainWidth := m.layout.MainWidth
	if m.layout.Mode == tui.ModeCompact {
		mainWidth = size.Width
	}

	// Every pane in this layout is a full-height column: the sidebar, the messages and the
	// detail sit side by side, so each viewport gets the whole body less its own two border
	// rows. This used to halve the height for the messages and give the remainder to the
	// detail, which is the geometry of a layout that stacked the two -- and that layout no
	// longer exists. The cost was that both panes drew a full-height box and then filled the
	// lower half of it with blank rows, and content past the halfway mark was simply clipped:
	// a browse of eight tags lost the action hint drawn beneath them.
	paneHeight := max(m.layout.BodyHeight-2, 1)

	m.messages.SetWidth(max(mainWidth-2, 1))
	m.messages.SetHeight(paneHeight)
	m.messages.SetColumns(messageColumns(max(mainWidth-2, 12), m.showConnectionColumn()))

	m.detail.SetWidth(max(detailWidth(m.layout)-2, 1))
	m.detail.SetHeight(paneHeight)

	m.logView.SetWidth(max(size.Width-2, 1))
	m.logView.SetHeight(max(m.layout.LogHeight-2, 1))

	m.refreshMessages()
}

// detailWidth is the width the detail pane gets in the current layout.
func detailWidth(layout tui.Layout) int {
	if layout.ShowDetail && layout.DetailWidth > 0 {
		return layout.DetailWidth
	}
	return layout.MainWidth
}

// Quitting reports whether the model has asked to exit, for tests.
func (m *Model) Quitting() bool { return m.quit }
