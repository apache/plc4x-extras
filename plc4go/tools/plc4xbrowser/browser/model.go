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
	"strings"
	"time"

	"charm.land/bubbles/v2/key"
	"charm.land/bubbles/v2/table"
	"charm.land/bubbles/v2/viewport"
	tea "charm.land/bubbletea/v2"

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

	messages     table.Model
	detail       viewport.Model
	logView      viewport.Model
	composer     *composer
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
		table.WithColumns(messageColumns(80)),
		table.WithFocused(false),
		table.WithStyles(tableStyles(theme)),
	)
	model.detail = viewport.New()
	model.logView = viewport.New()
	model.promptFocused = true
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

// messageColumns sizes the message table for a pane width.
func messageColumns(width int) []table.Column {
	// The fixed columns are sized to their content; the connection column takes what is left,
	// because a connection string is the one field that is unboundedly long.
	const fixed = 4 + 14 + 10 + 12 + 8
	connection := max(width-fixed, 10)
	return []table.Column{
		{Title: "#", Width: 4},
		{Title: "time", Width: 14},
		{Title: "op", Width: 10},
		{Title: "connection", Width: connection},
		{Title: "tag", Width: 12},
		{Title: "code", Width: 8},
	}
}

// tableStyles maps the theme onto the table's own styling.
//
// bubbles/table defaults to a palette of its own, so without this a colourless theme still
// emitted a 256-colour selected row -- which breaks NO_COLOR and makes golden output unstable.
func tableStyles(theme tui.Theme) table.Styles {
	styles := table.DefaultStyles()
	styles.Header = theme.Key.Bold(true)
	styles.Cell = theme.Value
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

	keys := m.keys.ForFocus(m.currentFocus())

	switch {
	case key.Matches(msg, keys.Quit):
		return m.shutdown()

	case key.Matches(msg, keys.Help):
		m.help.SetShowAll(!m.help.ShowAll())
		return m, nil

	case key.Matches(msg, keys.Cancel):
		// Escape unwinds one level at a time: the prompt's own use of it first, then the
		// toast, then a focused pane back to the prompt.
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
func (m *Model) paneKey(msg tea.KeyPressMsg, keys tui.KeyMap) (tea.Model, tea.Cmd) {
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
		if key.Matches(msg, keys.Select) {
			m.syncDetail()
			return m, nil
		}
		var cmd tea.Cmd
		m.messages, cmd = m.messages.Update(msg)
		m.syncDetail()
		return m, cmd
	case paneDetail:
		if key.Matches(msg, keys.Wrap) {
			m.detail.SoftWrap = !m.detail.SoftWrap
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

// runCommand executes a line off the event loop and reports the outcome as a message.
//
// This is the shape every piece of I/O in the model takes: a tea.Cmd does the work and a typed
// message carries the result back, so that nothing but Update ever writes state.
func (m *Model) runCommand(line string) tea.Cmd {
	env := m.env
	registry := m.registry
	return func() tea.Msg {
		result, err := registry.Execute(context.Background(), env, line)
		return commandDoneMsg{line: line, result: result, err: err}
	}
}

// applyCommand folds a command's outcome into the model.
func (m *Model) applyCommand(msg commandDoneMsg) (tea.Model, tea.Cmd) {
	m.prompt.Finish()
	if msg.err != nil {
		m.toast = msg.err.Error()
		m.appendLog(m.theme.Glyphs.Err + " " + msg.err.Error())
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
		m.events = append(m.events, result.Events...)
		m.trimEvents()
		m.refreshMessages()
	}
	if result.Clear.Has(ClearMessages) {
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

// appendLog adds a console line, bounded by the configured maximum.
func (m *Model) appendLog(line string) {
	stamp := m.options.Now().Format("15:04:05")
	m.logLines = append(m.logLines, stamp+" "+line)
	if limit := m.options.Config.MaxConsoleLines; limit > 0 && len(m.logLines) > limit {
		m.logLines = m.logLines[len(m.logLines)-limit:]
	}
	m.logView.SetContent(strings.Join(m.logLines, "\n"))
	m.logView.GotoBottom()
}

// AppendLog exposes the console so a caller can write a startup banner before the program runs.
func (m *Model) AppendLog(line string) { m.appendLog(line) }

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
	rows := make([]table.Row, 0, len(events))
	for i, event := range events {
		rows = append(rows, table.Row{
			fmt.Sprintf("%d", i+1),
			event.Received.Format("15:04:05.000"),
			string(event.Kind),
			event.Connection,
			firstTagAddress(event),
			eventCode(event),
		})
	}
	m.messages.SetRows(rows)
	if len(rows) > 0 {
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
	m.detail.SetContent(renderEventDetail(m.theme, events[cursor]))
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

// resize recomputes the layout and resizes every child.
func (m *Model) resize(size tui.Size) {
	m.layout = tui.Compute(size)
	m.ready = true
	if m.layout.Mode == tui.ModeTooSmall {
		return
	}

	// The prompt and the help footer span the full width less one cell of margin either side.
	m.prompt.SetWidth(max(size.Width-2, 1))
	m.help.SetWidth(max(size.Width-2, 1))

	mainWidth := m.layout.MainWidth
	if m.layout.Mode == tui.ModeCompact {
		mainWidth = size.Width
	}

	tableHeight := m.layout.BodyHeight - 2
	if m.layout.ShowDetail {
		tableHeight = (m.layout.BodyHeight - 4) / 2
	}
	tableHeight = max(tableHeight, 1)

	m.messages.SetWidth(max(mainWidth-2, 1))
	m.messages.SetHeight(tableHeight)
	m.messages.SetColumns(messageColumns(max(mainWidth-2, 12)))

	m.detail.SetWidth(max(detailWidth(m.layout)-2, 1))
	m.detail.SetHeight(max(m.layout.BodyHeight-tableHeight-4, 1))

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
