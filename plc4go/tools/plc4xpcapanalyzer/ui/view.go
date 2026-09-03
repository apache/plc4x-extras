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
	"strconv"
	"strings"
	"time"

	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Rendering.
//
// Every function here returns lines of an exact width, and the composition is done by zipping
// blocks rather than by letting a layout engine decide. That is deliberate: the defect this
// port exists to fix was a layout engine silently discarding a region — the command area, at
// anything under a hundred columns — and the only way to be sure that cannot happen again is
// for the row budget to be arithmetic that a test can check.

// Row heights. The run panel takes four rows where there is room for them: two of chrome and
// two of content. Where there is not, it keeps the progress row and drops the counters, and
// below that it disappears entirely — but never at the expense of the prompt.
const (
	runPanelFull    = 4
	runPanelCompact = 3
	// runPanelFullMin and runPanelCompactMin are the body heights those two need.
	runPanelFullMin    = 10
	runPanelCompactMin = 7
)

// hexBytesPerRow is the row width every hex dump in the detail pane uses. Eight rather than
// hex.Dump's sixteen because two dumps have to sit one above the other in a pane that is
// routinely under sixty cells wide. It is a package constant because the scroll extent has to
// count the same rows the renderer draws.
const hexBytesPerRow = 8

// View renders the program.
func (m Model) View() tea.View {
	view := tea.NewView(m.Render())
	view.AltScreen = true
	// The tview interface this replaces called EnableMouse(true); leaving the mouse off would
	// have been a silent capability regression. CellMotion covers clicks and the wheel and is
	// more widely supported than AllMotion, which would add motion events for no benefit here.
	view.MouseMode = tea.MouseModeCellMotion
	return view
}

// Render produces the screen as a string.
//
// It is exported for tests: asserting on the content and the width of each line is far more
// useful than asserting on a golden blob, and it needs no terminal.
func (m Model) Render() string {
	if m.size.Width <= 0 || m.size.Height <= 0 {
		return ""
	}
	if m.layout.Mode == tui.ModeTooSmall {
		return strings.Join(m.tooSmallLines(), "\n")
	}

	width := m.size.Width

	geometry := m.geometry()

	lines := make([]string, 0, m.size.Height)
	lines = append(lines, m.statusLine(width))
	lines = append(lines, m.bodyLines(width, geometry.PaneHeight)...)
	if geometry.RunHeight > 0 {
		lines = append(lines, m.runPanelLines(width, geometry.RunHeight)...)
	}
	lines = append(lines, m.toastLine(width))
	// The prompt, always, at its full width. This row is reserved before anything else is
	// given a share of the screen.
	lines = append(lines, fitLine(m.prompt.View(), width))
	lines = append(lines, m.footerLine(width))

	return strings.Join(lines, "\n")
}

// footerLine renders the one-line key footer.
//
// It always renders the SHORT help, even while the expanded help is showing: the expanded help
// is drawn over the body by applyOverlays, and letting help.Model render its multi-line form
// here too would grow the footer from one row to five and push the prompt off the screen.
func (m Model) footerLine(width int) string {
	footer := m.footer
	footer.SetShowAll(false)
	return fitLine(footer.View(m.keys), width)
}

// tooSmallLines is what a terminal below the minimum gets. It states the requirement and the
// actual size, rather than merely refusing, and it still draws the prompt when there is a row
// for it: the one rule that holds at every size is that the prompt is never dropped.
func (m Model) tooSmallLines() []string {
	width, height := m.size.Width, m.size.Height
	lines := []string{
		fitLine(m.theme.Warn.Render(tui.TooSmallMessage()), width),
		fitLine(m.theme.Muted.Render(itoa(tui.MinWidth)+"x"+itoa(tui.MinHeight)+" minimum, this is "+m.size.String()), width),
	}
	for len(lines) < height-1 {
		lines = append(lines, strings.Repeat(" ", width))
	}
	if height >= 3 {
		lines = append(lines, fitLine(m.prompt.View(), width))
	}
	return lines[:min(len(lines), max(height, 1))]
}

// geometry is the resolved row and column budget for one screen.
//
// It is computed in one place and consulted by both the renderer and the focus rules, because
// the two disagreeing is precisely how a pane ends up focusable but not drawn.
type geometry struct {
	// PaneHeight is the rows the panes get, RunHeight the rows the run panel gets.
	PaneHeight int
	RunHeight  int
	// DetailHeight is the rows the detail pane gets inside PaneHeight, zero when it does not
	// fit; SidebarWidth is the left column, zero when it does not fit.
	DetailHeight int
	SidebarWidth int
	RightWidth   int
}

// geometry resolves the budget for the current size, layout and run state.
func (m Model) geometry() geometry {
	resolved := geometry{RightWidth: m.size.Width}
	bodyTotal := m.size.Height - tui.ReservedRows
	if m.layout.Mode == tui.ModeTooSmall || bodyTotal < 1 {
		return geometry{}
	}

	if m.run.label != "" {
		switch {
		case bodyTotal >= runPanelFullMin:
			resolved.RunHeight = runPanelFull
		case bodyTotal >= runPanelCompactMin:
			resolved.RunHeight = runPanelCompact
		}
	}
	resolved.PaneHeight = bodyTotal - resolved.RunHeight

	if m.layout.Mode != tui.ModeWide {
		return resolved
	}
	if sidebar := min(m.layout.SidebarWidth, m.size.Width-20); sidebar >= 16 {
		resolved.SidebarWidth = sidebar
	}
	resolved.RightWidth = m.size.Width - resolved.SidebarWidth
	// The detail pane needs enough rows to be worth the two it spends on its own border. Below
	// that it is reached as an overlay instead.
	if m.layout.ShowDetail && resolved.PaneHeight >= 12 {
		resolved.DetailHeight = clamp(resolved.PaneHeight*40/100, 6, 14)
	}
	return resolved
}

// bodyLines renders the pane area, exactly height lines of exactly width cells.
func (m Model) bodyLines(width, height int) []string {
	if height < 1 {
		return nil
	}
	var block string
	switch {
	case m.overlay != paneCount:
		block = m.overlayPane(width, height)
	case m.layout.Mode == tui.ModeWide:
		block = m.wideBody(width, height)
	default:
		block = m.compactBody(width, height)
	}
	lines := fitBlock(block, width, height)
	return m.applyOverlays(lines, width)
}

// wideBody is the drawn layout: the sidebar on the left, and the main pane above the detail
// pane on the right.
func (m Model) wideBody(width, height int) string {
	geometry := m.geometry()
	sidebarWidth, rightWidth := geometry.SidebarWidth, geometry.RightWidth
	detailHeight := geometry.DetailHeight
	mainHeight := height - detailHeight

	right := m.mainPane(rightWidth, mainHeight)
	if detailHeight > 0 {
		right += "\n" + m.detailPane(rightWidth, detailHeight)
	}
	if sidebarWidth == 0 {
		return right
	}
	return zipColumns(m.sidebarPane(sidebarWidth, height), sidebarWidth, right, rightWidth, height)
}

// compactBody is the single-column layout. The sidebar and the detail pane become overlays
// reached by a key rather than columns competing for width that is not there.
func (m Model) compactBody(width, height int) string {
	return m.mainPane(width, height)
}

// overlayPane draws one pane over the whole body, which is how the sidebar and the detail pane
// are reached in compact mode.
func (m Model) overlayPane(width, height int) string {
	switch m.overlay {
	case paneCaptures:
		return m.sidebarPane(width, height)
	case paneDetail:
		return m.detailPane(width, height)
	default:
		return m.mainPane(width, height)
	}
}

// applyOverlays draws the completion list or the expanded help over the bottom of the body.
//
// They are drawn over the body rather than given rows of their own because rows given to them
// would have to come from somewhere, and the only place left to take them from is the prompt.
func (m Model) applyOverlays(lines []string, width int) []string {
	var block string
	switch {
	case m.footer.ShowAll():
		block = m.footer.View(m.keys)
	case m.prompt.CompletionOpen():
		block = m.prompt.CompletionView(max(len(lines)-2, 1))
	}
	if block == "" {
		return lines
	}
	overlay := splitLines(block)
	if len(overlay) > len(lines) {
		overlay = overlay[len(overlay)-len(lines):]
	}
	start := len(lines) - len(overlay)
	for i, line := range overlay {
		lines[start+i] = fitLine(line, width)
	}
	return lines
}

// statusLine renders the status bar, degrading as the width runs out.
//
// The order matters and is tested. The drawn header is about ninety cells wide and simply does
// not fit at eighty, so the parts are shed from the least useful: first the name of the
// focused pane, then the "client" label around the address, then the version, then the address
// itself, then the protocol. The application name and the demo badge are never shed — the
// badge because mistaking a demo capture for a real one is the one mistake this tool must not
// let a user make.
func (m Model) statusLine(width int) string {
	name := m.theme.Title.Render(AppName)
	version := m.theme.Muted.Render(AppVersion)
	badge := ""
	if m.state.Demo {
		badge = m.theme.Badge.Render("DEMO")
	}
	separator := " " + m.theme.Chrome.Render(m.theme.Glyphs.Separator) + " "

	protocolText := m.theme.Value.Render(m.state.Protocol.Name)
	clientLong := ""
	clientShort := ""
	if m.state.HostIP != "" {
		clientLong = m.theme.Muted.Render("client ") + m.theme.Value.Render(m.state.HostIP)
		clientShort = m.theme.Value.Render(m.state.HostIP)
	}
	paneText := m.theme.Muted.Render("pane ") + m.theme.Value.Render(m.activePane().String())

	assemble := func(head []string, tail []string) string {
		left := strings.Join(nonEmpty(head), " ")
		right := strings.Join(nonEmpty(tail), separator)
		if right == "" {
			return left
		}
		gap := width - lipgloss.Width(left) - lipgloss.Width(right) - 1
		if gap < 1 {
			return ""
		}
		return left + strings.Repeat(" ", gap) + right + " "
	}

	for _, candidate := range []string{
		assemble([]string{name, version, badge}, []string{protocolText, clientLong, paneText}),
		assemble([]string{name, version, badge}, []string{protocolText, clientLong}),
		assemble([]string{name, version, badge}, []string{protocolText, clientShort}),
		assemble([]string{name, badge}, []string{protocolText, clientShort}),
		assemble([]string{name, badge}, []string{protocolText}),
		assemble([]string{name, badge}, nil),
	} {
		if candidate != "" && lipgloss.Width(candidate) <= width {
			return fitLine(candidate, width)
		}
	}
	return fitLine(name+" "+badge, width)
}

// toastLine renders the notice above the prompt.
func (m Model) toastLine(width int) string {
	if m.toast == "" {
		return strings.Repeat(" ", width)
	}
	glyph, style := m.theme.Glyphs.Ok, m.theme.Ok
	if m.toastErr {
		glyph, style = m.theme.Glyphs.Err, m.theme.Err
	}
	return fitLine(" "+style.Render(glyph+" "+m.toast)+m.theme.Muted.Render("  esc dismiss"), width)
}

// sidebarPane draws the captures, drivers and options column.
func (m Model) sidebarPane(width, height int) string {
	rows := m.sidebarRows()
	selectable := m.sidebarSelectable()
	inner := max(width-2, 1)
	visibleRows := max(height-2, 1)

	// Keep the cursor on screen. The sidebar has no scrollbar; the ellipsis row at the bottom
	// says when there is more below.
	cursorRow := 0
	if m.sidebarCursor < len(selectable) {
		cursorRow = selectable[m.sidebarCursor]
	}
	top := 0
	if cursorRow >= top+visibleRows {
		top = cursorRow - visibleRows + 1
	}
	top = max(min(top, max(len(rows)-visibleRows, 0)), 0)

	focused := m.activePane() == paneCaptures && m.focus != tui.FocusPrompt
	var content strings.Builder
	for i := top; i < min(top+visibleRows, len(rows)); i++ {
		if i > top {
			content.WriteString("\n")
		}
		content.WriteString(m.renderSidebarRow(rows[i], i == cursorRow && focused, inner))
	}

	pane := tui.Pane{
		Title:   paneTitle(paneCaptures),
		Status:  itoa(len(m.state.Captures)),
		Width:   width,
		Height:  height,
		Focused: focused,
	}
	return pane.Render(m.theme, content.String())
}

// sidebarKind says how a sidebar row is drawn and what selecting it does.
type sidebarKind int

const (
	sidebarHeader sidebarKind = iota
	sidebarCapture
	sidebarDriver
	sidebarOption
	sidebarHint
	sidebarBlank
)

// sidebarRow is one line of the sidebar.
type sidebarRow struct {
	kind  sidebarKind
	label string
	value string
	// key identifies what the row acts on: a capture path, a driver name, an option name.
	key string
}

// selectable reports whether the cursor can land on this row. Selecting a row always does
// something; a row that would do nothing is not selectable in the first place.
func (r sidebarRow) selectable() bool {
	switch r.kind {
	case sidebarCapture, sidebarDriver, sidebarOption:
		return true
	default:
		return false
	}
}

// sidebarRows builds the sidebar's contents.
func (m Model) sidebarRows() []sidebarRow {
	rows := make([]sidebarRow, 0, 24)
	if len(m.state.Captures) == 0 {
		// An empty pane says what to do next, and names the command that does it.
		rows = append(rows,
			sidebarRow{kind: sidebarHint, label: "no captures open"},
			sidebarRow{kind: sidebarHint, label: "open <pcapfile>"})
	}
	for _, capture := range m.state.Captures {
		value := "-"
		if capture.Packets >= 0 {
			value = itoa(capture.Packets)
		}
		rows = append(rows, sidebarRow{kind: sidebarCapture, label: capture.Name, value: value, key: capture.Path})
	}
	rows = append(rows,
		sidebarRow{kind: sidebarBlank},
		sidebarRow{kind: sidebarHint, label: m.state.CurrentDir},
		sidebarRow{kind: sidebarBlank},
		sidebarRow{kind: sidebarHeader, label: "DRIVERS", value: itoa(len(m.state.Registered))})
	for _, driver := range DriverNames {
		marker := m.theme.Glyphs.No
		if m.state.IsRegistered(driver) {
			marker = m.theme.Glyphs.Yes
		}
		rows = append(rows, sidebarRow{kind: sidebarDriver, label: driver, value: marker, key: driver})
	}

	options := analysisOptions()
	filter := options.Filter
	if filter == "" {
		filter = "default"
	}
	if options.NoFilter {
		filter = "disabled"
	}
	client := m.state.HostIP
	if client == "" {
		client = "unset"
	}
	rows = append(rows,
		sidebarRow{kind: sidebarBlank},
		sidebarRow{kind: sidebarHeader, label: "OPTIONS"},
		sidebarRow{kind: sidebarOption, label: "protocol", value: m.state.Protocol.Name, key: "protocol"},
		sidebarRow{kind: sidebarOption, label: "client", value: client, key: "client"},
		sidebarRow{kind: sidebarOption, label: "filter", value: filter, key: "filter"},
		sidebarRow{kind: sidebarOption, label: "no-filter", value: onOff(options.NoFilter), key: "no-filter"},
		sidebarRow{kind: sidebarOption, label: "only-parse", value: onOff(options.OnlyParse), key: "only-parse"},
		sidebarRow{kind: sidebarOption, label: "bytes-compare", value: onOff(!options.NoBytesCompare), key: "bytes-compare"})
	return rows
}

// sidebarSelectable indexes the rows the cursor may land on.
func (m Model) sidebarSelectable() []int {
	var indexes []int
	for i, row := range m.sidebarRows() {
		if row.selectable() {
			indexes = append(indexes, i)
		}
	}
	return indexes
}

// renderSidebarRow draws one row at an exact width.
func (m Model) renderSidebarRow(row sidebarRow, selected bool, width int) string {
	switch row.kind {
	case sidebarBlank:
		return strings.Repeat(" ", width)
	case sidebarHeader:
		return fitLine(" "+m.theme.Key.Render(row.label)+"  "+m.theme.Muted.Render(row.value), width)
	case sidebarHint:
		return fitLine(" "+m.theme.Muted.Render(shorten(row.label, width-2, m.theme.Glyphs.Ellipsis)), width)
	}

	marker := m.theme.Glyphs.Unselected
	style := m.theme.Value
	if selected {
		marker = m.theme.Glyphs.Selected
		style = m.theme.SelectedRow
	}
	// The label wins the width contest. A label cut down to "cli." says nothing at all,
	// whereas a truncated address is still recognisable, and the value has a full-width home
	// in the status bar and in conf list anyway.
	label := shorten(row.label, max(width-7, 1), m.theme.Glyphs.Ellipsis)
	value := shorten(row.value, max(width-lipgloss.Width(label)-3, 1), m.theme.Glyphs.Ellipsis)
	gap := max(width-2-lipgloss.Width(label)-lipgloss.Width(value)-1, 1)
	return fitLine(marker+" "+style.Render(label)+strings.Repeat(" ", gap)+m.theme.Muted.Render(value), width)
}

// activateSidebarRow performs the action of the highlighted sidebar row.
func (m Model) activateSidebarRow() (tea.Model, tea.Cmd) {
	rows := m.sidebarRows()
	selectable := m.sidebarSelectable()
	if len(selectable) == 0 || m.sidebarCursor >= len(selectable) {
		return m, nil
	}
	row := rows[selectable[m.sidebarCursor]]
	switch row.kind {
	case sidebarCapture:
		for i, capture := range m.state.Captures {
			if capture.Path == row.key {
				m.state.Current = i
				m.setToast("selected "+capture.Name, false)
			}
		}
		return m, nil

	case sidebarDriver:
		verb := "register "
		if m.state.IsRegistered(row.key) {
			verb = "unregister "
		}
		return m.runLine(verb + row.key)

	case sidebarOption:
		return m.activateOption(row.key)
	}
	return m, nil
}

// activateOption toggles or edits one analysis option.
//
// A boolean is flipped in place; a value that needs typing pre-fills the prompt with the
// command that sets it, so that the sidebar and the command line never disagree about where
// the setting lives.
func (m Model) activateOption(name string) (tea.Model, tea.Cmd) {
	switch name {
	case "protocol":
		all := protocol.All()
		next := all[0]
		for i, candidate := range all {
			if candidate.Name == m.state.Protocol.Name {
				next = all[(i+1)%len(all)]
				break
			}
		}
		m.state.Protocol = next
		m.setToast("protocol "+next.Name, false)
		return m, nil
	case "client":
		return m.prefill("host set " + m.state.HostIP)
	case "filter":
		// PcapConfig, not AnalyzeConfig: Filter is declared on the config AnalyzeConfig embeds,
		// and the conf tree addresses a field by the struct that declares it.
		return m.prefill("conf set PcapConfig Filter " + analysisOptions().Filter)
	case "no-filter":
		return m.runLine("conf set AnalyzeConfig NoFilter " + strconv.FormatBool(!analysisOptions().NoFilter))
	case "only-parse":
		return m.runLine("conf set AnalyzeConfig OnlyParse " + strconv.FormatBool(!analysisOptions().OnlyParse))
	case "bytes-compare":
		return m.runLine("conf set AnalyzeConfig NoBytesCompare " + strconv.FormatBool(!analysisOptions().NoBytesCompare))
	}
	return m, nil
}

// mainPane draws the tabbed packets, log and findings pane.
func (m Model) mainPane(width, height int) string {
	focused := m.activePane() == paneMain && m.focus != tui.FocusPrompt
	inner := max(width-2, 1)
	rowsAvailable := max(height-2, 1)

	var content string
	switch m.tab {
	case tabLog:
		content = strings.Join(m.logRows(inner, rowsAvailable), "\n")
	default:
		content = strings.Join(m.packetRows(inner, rowsAvailable), "\n")
	}

	pane := tui.Pane{
		Title:   paneLabel(paneMain) + " " + m.tabStrip(),
		Status:  m.mainStatus(),
		Width:   width,
		Height:  height,
		Focused: focused,
	}
	return pane.Render(m.theme, content)
}

// tabStrip is the tab list carried in the main pane's border. The active tab is marked with
// the selection glyph rather than a colour, so that it stays legible with colour switched off
// and so that the pane's own title styling is not fought with a nested style.
func (m Model) tabStrip() string {
	parts := make([]string, 0, tabCount)
	for t := range tabCount {
		if t == m.tab {
			parts = append(parts, m.theme.Glyphs.Selected+t.String())
			continue
		}
		parts = append(parts, " "+t.String())
	}
	return strings.TrimSpace(strings.Join(parts, " "))
}

// mainStatus is the count carried at the right of the main pane's border.
func (m Model) mainStatus() string {
	if m.filtering || m.filter != "" {
		return "/" + m.filter + "  " + itoa(len(m.visible)) + " of " + itoa(len(m.packets))
	}
	switch m.tab {
	case tabLog:
		return itoa(len(m.logLines)) + " lines"
	case tabFindings:
		return itoa(len(m.visible)) + " findings"
	default:
		issues := len(m.findings())
		if issues == 0 {
			return itoa(len(m.packets)) + " " + m.theme.Glyphs.Separator + " no issues"
		}
		return itoa(len(m.packets)) + " " + m.theme.Glyphs.Separator + " " + itoa(issues) + " issues"
	}
}

// packetColumns are the fixed-width columns of the packets table, widest layout first. The
// message column takes whatever is left.
type packetColumn struct {
	title string
	width int
}

// packetRows renders the packets table, header included.
func (m Model) packetRows(width, rows int) []string {
	if len(m.packets) == 0 {
		return m.emptyPackets(width, rows)
	}
	if len(m.visible) == 0 {
		return padRows([]string{
			fitLine(" "+m.theme.Muted.Render("nothing matches "+strconv.Quote(m.filter)), width),
			fitLine(" "+m.theme.Muted.Render("esc clears the filter"), width),
		}, width, rows)
	}

	columns := packetLayout(width)
	header := m.renderPacketHeader(columns, width)
	bodyRows := max(rows-1, 1)

	// The window is model state, not a function of the cursor: deriving it here pinned the
	// selection to the bottom row. Clamp it to what is drawable in case the pane shrank since
	// the cursor last moved.
	top := min(max(m.packetTop, 0), max(len(m.visible)-bodyRows, 0))
	if m.cursor < top {
		top = m.cursor
	} else if m.cursor >= top+bodyRows {
		top = m.cursor - bodyRows + 1
	}
	out := []string{header}
	for i := top; i < min(top+bodyRows, len(m.visible)); i++ {
		out = append(out, m.renderPacketRow(m.packets[m.visible[i]], i == m.cursor, columns, width))
	}
	return padRows(out, width, rows)
}

// emptyPackets is the empty state. It names the command that would fill the pane, with the
// arguments the session already has, rather than merely saying that there is nothing here.
func (m Model) emptyPackets(width, rows int) []string {
	hint := "analyze " + m.state.Protocol.Name + " <pcapfile>"
	if capture, ok := m.state.CurrentCapture(); ok {
		hint = "analyze " + m.state.Protocol.Name + " " + capture.Name
	}
	lines := []string{
		fitLine(" "+m.theme.Muted.Render("no packets analysed yet"), width),
		fitLine(" "+m.theme.Muted.Render("run: ")+m.theme.Accent.Render(hint), width),
	}
	if len(m.state.Captures) == 0 {
		lines = append(lines, fitLine(" "+m.theme.Muted.Render("or open one first: ")+m.theme.Accent.Render("open <pcapfile>"), width))
	}
	return padRows(lines, width, rows)
}

// packetLayout picks the columns that fit the available width, dropping the least useful
// first. The number, the message and the verdict always survive: they are what makes a row
// worth reading at all.
func packetLayout(width int) []packetColumn {
	full := []packetColumn{
		{"no.", 5}, {"time", 10}, {"dir", 3}, {"proto", 7}, {"message", 0}, {"verdict", 7},
	}
	drop := []string{"proto", "dir", "time"}
	columns := full
	for {
		fixed := 2 // the selection marker and the space after it
		for _, column := range columns {
			fixed += column.width + 1
		}
		if fixed+minMessageWidth <= width || len(drop) == 0 {
			return columns
		}
		name := drop[0]
		drop = drop[1:]
		var kept []packetColumn
		for _, column := range columns {
			if column.title != name {
				kept = append(kept, column)
			}
		}
		columns = kept
	}
}

// minMessageWidth is the narrowest the message column may be before a whole column is dropped
// instead.
const minMessageWidth = 12

// renderPacketHeader draws the table's header row.
func (m Model) renderPacketHeader(columns []packetColumn, width int) string {
	cells := make([]string, 0, len(columns))
	for _, column := range columns {
		cells = append(cells, pad(column.title, m.columnWidth(column, columns, width)))
	}
	return fitLine("  "+m.theme.Key.Render(strings.Join(cells, " ")), width)
}

// renderPacketRow draws one packet.
func (m Model) renderPacketRow(record Record, selected bool, columns []packetColumn, width int) string {
	marker := m.theme.Glyphs.Unselected
	if selected {
		marker = m.theme.Glyphs.Selected
	}
	cells := make([]string, 0, len(columns))
	for _, column := range columns {
		size := m.columnWidth(column, columns, width)
		value := ""
		switch column.title {
		case "no.":
			value = itoa(record.Number)
		case "time":
			value = formatOffset(record.Offset)
		case "dir":
			value = m.directionGlyph(record.Direction)
		case "proto":
			value = record.Protocol
		case "message":
			value = record.Summary
			if value == "" {
				value = record.Reason
			}
		case "verdict":
			value = record.Verdict.String()
		}
		cells = append(cells, pad(shorten(value, size, m.theme.Glyphs.Ellipsis), size))
	}
	row := marker + " " + strings.Join(cells, " ")
	style := m.theme.Value
	switch {
	case selected:
		style = m.theme.SelectedRow
	case record.Verdict.IsIssue():
		style = m.theme.Warn
	}
	return fitLine(style.Render(pad(row, width)), width)
}

// columnWidth resolves a column's width, giving the message column whatever is left.
func (m Model) columnWidth(column packetColumn, columns []packetColumn, width int) int {
	if column.width > 0 {
		return column.width
	}
	used := 2
	for _, other := range columns {
		if other.width > 0 {
			used += other.width + 1
		}
	}
	return max(width-used-1, 4)
}

// directionGlyph renders which way a packet travelled.
func (m Model) directionGlyph(direction Direction) string {
	switch direction {
	case DirectionRequest:
		return m.theme.Glyphs.Outbound
	case DirectionResponse:
		return m.theme.Glyphs.Inbound
	default:
		return "?"
	}
}

// logRows renders the log tab.
func (m Model) logRows(width, rows int) []string {
	if len(m.logLines) == 0 {
		return padRows([]string{
			fitLine(" "+m.theme.Muted.Render("nothing logged yet"), width),
			fitLine(" "+m.theme.Muted.Render("commands and analysis output appear here"), width),
		}, width, rows)
	}
	top := clampIndex(m.logTop, len(m.logLines))
	if len(m.logLines)-top < rows {
		top = max(len(m.logLines)-rows, 0)
	}
	out := make([]string, 0, rows)
	for i := top; i < min(top+rows, len(m.logLines)); i++ {
		out = append(out, fitLine(" "+m.theme.Value.Render(shorten(m.logLines[i], width-1, m.theme.Glyphs.Ellipsis)), width))
	}
	return padRows(out, width, rows)
}

// detailInnerWidth is the width the detail pane's content is drawn at: the whole screen when
// it is showing as an overlay, and the right-hand column otherwise. The scroll extent needs it
// because wrapping decides how many lines the tree view has.
func (m Model) detailInnerWidth() int {
	if m.overlay == paneDetail {
		return max(m.size.Width-2, 1)
	}
	return max(m.geometry().RightWidth-2, 1)
}

// detailPane draws the original bytes against what the codec wrote back.
func (m Model) detailPane(width, height int) string {
	focused := m.activePane() == paneDetail && m.focus != tui.FocusPrompt
	inner := max(width-2, 1)
	rows := max(height-2, 1)

	title := paneTitle(paneDetail)
	var content []string
	if record, ok := m.selectedRecord(); ok {
		title = paneTitle(paneDetail) + " " + m.theme.Glyphs.Separator + " packet " + itoa(record.Number)
		content = m.detailLines(record, rows, inner)
	} else {
		content = []string{
			m.theme.Muted.Render("no packet selected"),
			m.theme.Muted.Render("move the cursor in Packets, then press enter"),
		}
	}

	pane := tui.Pane{
		Title:   title,
		Status:  "[b]ytes [t]ree [d]iff",
		Width:   width,
		Height:  height,
		Focused: focused,
	}
	return pane.Render(m.theme, strings.Join(content, "\n"))
}

// detailLines renders the detail pane's body for one record.
func (m Model) detailLines(record Record, rows, width int) []string {
	switch m.detail {
	case detailBytes:
		return m.detailBytesLines(record, rows, width)
	case detailTree:
		return m.detailTreeLines(record, rows, width)
	default:
		return m.detailDiffLines(record, rows, width)
	}
}

// detailDiffLines is the view this tool exists for: the captured payload against the
// re-serialized one, with the first differing offset marked.
//
// The five load-bearing rows — the two previews, the two hex rows and the caret — are built
// first and a blank separator is inserted only when there is a row to spare. The pane is
// routinely six rows tall, and dropping the caret to make room for whitespace would throw away
// the one line that says what is actually wrong.
func (m Model) detailDiffLines(record Record, rows, width int) []string {
	const (
		bytesPerRow = hexBytesPerRow
		labelWidth  = 14
	)
	label := func(text string) string { return m.theme.Key.Render(pad(text, labelWidth)) }

	// The verdict sits at the right of the reserialized row, so the space it needs comes off
	// the preview column rather than off the end of the pane.
	verdict := ""
	if record.DiffOffset >= 0 {
		verdict = m.theme.Glyphs.Err + " " + record.Reason
	}
	previewWidth := max(width-labelWidth-lipgloss.Width(verdict)-2, 8)

	original := fitLine(label("original")+m.theme.Value.Render(Preview(record.Original, previewWidth, m.theme.Glyphs.Ellipsis)), width)

	switch {
	case record.Reserialized == nil:
		lines := []string{
			original,
			fitLine(label("reserialized")+m.theme.Muted.Render("(not produced: "+record.Verdict.String()+")"), width),
		}
		if record.Reason != "" {
			lines = append(lines, fitLine(m.theme.Err.Render(m.theme.Glyphs.Err+" "+shorten(record.Reason, width-2, m.theme.Glyphs.Ellipsis)), width))
		}
		lines = append(lines, m.hexBlock(record.Original, 0, rows-len(lines), width)...)
		return padRows(lines, width, rows)

	case record.DiffOffset < 0:
		lines := []string{
			original,
			fitLine(label("reserialized")+m.theme.Ok.Render(m.theme.Glyphs.Ok+" identical, "+itoa(len(record.Original))+" bytes"), width),
		}
		lines = append(lines, m.hexBlock(record.Original, 0, rows-len(lines), width)...)
		return padRows(lines, width, rows)
	}

	// Both dumps start at the row containing the first difference, so the two lines the reader
	// has to compare are directly above one another and the caret lines up with both.
	rowStart := record.DiffOffset / bytesPerRow * bytesPerRow
	essential := []string{
		original,
		// The preview is padded to its full column so the verdict lands in the same place on
		// every packet, rather than jumping about with the length of the payload.
		fitLine(label("reserialized")+m.theme.Value.Render(pad(Preview(record.Reserialized, previewWidth, m.theme.Glyphs.Ellipsis), previewWidth))+
			"  "+m.theme.Err.Render(verdict), width),
		fitLine(m.theme.Value.Render(hexRowFrom(record.Original, rowStart, bytesPerRow)), width),
		fitLine(m.theme.Value.Render(hexRowFrom(record.Reserialized, rowStart, bytesPerRow)), width),
		fitLine(strings.Repeat(" ", hexRowCaretColumn(record.DiffOffset, bytesPerRow))+
			m.theme.Err.Render("^^ first difference at offset "+itoa(record.DiffOffset)), width),
	}
	if rows > len(essential) {
		// Room to breathe: separate the previews from the dumps.
		spaced := append([]string{essential[0], essential[1], strings.Repeat(" ", width)}, essential[2:]...)
		return padRows(spaced, width, rows)
	}
	if rows < len(essential) {
		// Fewer rows than load-bearing lines, which the geometry produces whenever the detail
		// column bottoms out at its six-row minimum — a hundred by twenty-four with a run panel
		// is one such size. padRows keeps the HEAD of what it is given, so leaving this to it
		// dropped the caret, which is the offset, which is the entire finding. The previews go
		// instead: they are a convenience, and the payload is in the dump above the caret
		// either way.
		return padRows(essential[len(essential)-rows:], width, rows)
	}
	return padRows(essential, width, rows)
}

// detailBytesLines is the plain hex dump of the captured payload.
func (m Model) detailBytesLines(record Record, rows, width int) []string {
	header := fitLine(m.theme.Key.Render("original ")+m.theme.Muted.Render(itoa(len(record.Original))+" bytes"), width)
	return padRows(append([]string{header}, m.hexBlock(record.Original, m.detailTop, rows-1, width)...), width, rows)
}

// detailTreeLines is the parsed message rendered as text.
func (m Model) detailTreeLines(record Record, rows, width int) []string {
	if record.Tree == "" {
		return padRows([]string{
			fitLine(m.theme.Muted.Render("packet "+itoa(record.Number)+" did not parse"), width),
			fitLine(m.theme.Muted.Render(shorten(record.Reason, width, m.theme.Glyphs.Ellipsis)), width),
		}, width, rows)
	}
	source := splitLines(record.Tree)
	if m.detailWrap {
		source = wrapLines(source, width)
	}
	top := clampIndex(m.detailTop, len(source))
	out := make([]string, 0, rows)
	for i := top; i < min(top+rows, len(source)); i++ {
		out = append(out, fitLine(m.theme.Value.Render(shorten(source[i], width, m.theme.Glyphs.Ellipsis)), width))
	}
	return padRows(out, width, rows)
}

// hexBlock renders up to rows of a hex dump, starting at a row offset.
func (m Model) hexBlock(data []byte, top, rows, width int) []string {
	if rows < 1 {
		return nil
	}
	all := HexDumpLines(data, hexBytesPerRow, 1<<16)
	top = clampIndex(top, len(all))
	out := make([]string, 0, rows)
	for i := top; i < min(top+rows, len(all)); i++ {
		out = append(out, fitLine(m.theme.Value.Render(all[i]), width))
	}
	return out
}

// hexRowFrom renders one row of a hex dump, tolerating an offset past the end of the data.
func hexRowFrom(data []byte, from, bytesPerRow int) string {
	if from >= len(data) {
		return strings.Repeat(" ", hexRowCaretColumn(0, bytesPerRow)) + "(no more bytes)"
	}
	return hexRow(data, from, min(from+bytesPerRow, len(data)), bytesPerRow)
}

// runPanelLines draws the run panel: the phase breadcrumb, the bar, and the counters.
func (m Model) runPanelLines(width, height int) []string {
	focused := m.activePane() == paneRun && m.focus != tui.FocusPrompt
	inner := max(width-2, 1)

	status := "enter re-run"
	if m.run.active {
		status = "esc abort"
	}

	content := []string{m.runProgressLine(inner)}
	if height >= runPanelFull {
		content = append(content, m.runCountersLine(inner))
	}

	pane := tui.Pane{
		Title:   paneTitle(paneRun) + " " + m.theme.Glyphs.Separator + " " + m.run.label,
		Status:  status,
		Width:   width,
		Height:  height,
		Focused: focused,
	}
	return splitLines(pane.Render(m.theme, strings.Join(content, "\n")))
}

// runProgressLine is the spinner, the phase breadcrumb, the bar and the packet count.
func (m Model) runProgressLine(width int) string {
	spinner := " "
	if m.run.active {
		frames := m.theme.Glyphs.Spinner
		if len(frames) > 0 {
			spinner = frames[m.spinnerFrame%len(frames)]
		}
	} else if m.run.err != nil {
		spinner = m.theme.Glyphs.Err
	} else if m.run.label != "" {
		spinner = m.theme.Glyphs.Ok
	}

	breadcrumb := m.phaseBreadcrumb()
	count := itoa(m.run.current) + " / " + itoa(m.run.total) + " pkt"
	fraction := 0.0
	if m.run.total > 0 {
		fraction = min(float64(m.run.current)/float64(m.run.total), 1)
	}
	percent := itoa(int(fraction*100+0.5)) + "%"

	// The bar takes whatever the fixed parts leave. Below a usable minimum it is dropped
	// entirely rather than drawn as a stub that says nothing. The twelve is the four literal
	// gaps this line is assembled from; it is spelled out rather than hidden in a magic number
	// because getting it wrong clips the packet count off the right-hand end.
	const gaps = 1 + 1 + 3 + 3 + 3 + 1
	fixed := lipgloss.Width(spinner) + lipgloss.Width(breadcrumb) + len(percent) + len(count) + gaps
	barWidth := width - fixed
	bar := ""
	if barWidth >= 8 {
		bar = m.progressBar(barWidth, fraction) + "   "
	}
	return fitLine(" "+m.theme.Accent.Render(spinner)+" "+breadcrumb+"   "+bar+
		m.theme.Value.Render(percent)+"   "+m.theme.Muted.Render(count), width)
}

// phaseBreadcrumb renders "index > filter > analyze" with the current phase picked out.
func (m Model) phaseBreadcrumb() string {
	parts := make([]string, 0, len(Phases))
	for _, phase := range Phases {
		if phase == m.run.phase {
			parts = append(parts, m.theme.Accent.Render(phase))
			continue
		}
		parts = append(parts, m.theme.Muted.Render(phase))
	}
	return strings.Join(parts, m.theme.Chrome.Render(" "+m.theme.Glyphs.Selected+" "))
}

// progressBar draws a bar of exactly width cells.
//
// It is drawn from the theme's glyphs rather than with bubbles/progress. That package needs
// github.com/charmbracelet/harmonica for its spring animation, which is not in this module's
// go.sum, and its colouring cannot be switched off, which would fight the no-colour theme the
// golden tests render with. The glyph set already carries a matched pair of full and empty
// characters for exactly this.
func (m Model) progressBar(width int, fraction float64) string {
	if width < 1 {
		return ""
	}
	filled := clamp(int(fraction*float64(width)+0.5), 0, width)
	return m.theme.Accent.Render(strings.Repeat(m.theme.Glyphs.ProgressFull, filled)) +
		m.theme.Chrome.Render(strings.Repeat(m.theme.Glyphs.ProgressEmpty, width-filled))
}

// runCountersLine is the second row of the run panel.
func (m Model) runCountersLine(width int) string {
	counters := m.run.counters
	pairs := [][2]string{
		{"parsed", itoa(counters.Parsed)},
		{"parse-fail", itoa(counters.ParseFail)},
		{"serialize-fail", itoa(counters.SerializeFail)},
		{"compare-fail", itoa(counters.CompareFail)},
	}
	// The labels are shed from the right when the row is narrow, because the leftmost counter
	// is the one that says whether anything worked at all.
	for keep := len(pairs); keep > 0; keep-- {
		parts := make([]string, 0, keep)
		for _, pair := range pairs[:keep] {
			style := m.theme.Value
			if pair[1] != "0" && pair[0] != "parsed" {
				style = m.theme.Err
			}
			parts = append(parts, m.theme.Key.Render(pair[0])+" "+style.Render(pair[1]))
		}
		line := " " + strings.Join(parts, "   ")
		if lipgloss.Width(line) <= width {
			return fitLine(line, width)
		}
	}
	return fitLine(" "+m.theme.Key.Render("parsed ")+m.theme.Value.Render(itoa(counters.Parsed)), width)
}

// formatOffset renders how long after the first packet this one arrived, as mm:ss.mmm.
func formatOffset(offset time.Duration) string {
	if offset < 0 {
		offset = 0
	}
	totalMillis := int64(offset / 1e6)
	minutes := totalMillis / 60000
	seconds := totalMillis % 60000 / 1000
	millis := totalMillis % 1000
	return pad2(minutes) + ":" + pad2(seconds) + "." + pad3(millis)
}

// pad2 and pad3 zero-pad a number to a fixed number of digits.
func pad2(value int64) string {
	if value < 10 {
		return "0" + strconv.FormatInt(value, 10)
	}
	return strconv.FormatInt(value, 10)
}

func pad3(value int64) string {
	switch {
	case value < 10:
		return "00" + strconv.FormatInt(value, 10)
	case value < 100:
		return "0" + strconv.FormatInt(value, 10)
	default:
		return strconv.FormatInt(value, 10)
	}
}

// zipColumns places two blocks side by side, line for line, at exact widths.
//
// This is done by hand rather than with lipgloss.JoinHorizontal because the widths here are a
// budget that has to add up: a column that is one cell wider than it claimed would push the
// column beside it off the screen, and a zip that pads and clips every line makes that
// impossible rather than merely unlikely.
func zipColumns(left string, leftWidth int, right string, rightWidth, height int) string {
	leftLines := fitBlock(left, leftWidth, height)
	rightLines := fitBlock(right, rightWidth, height)
	out := make([]string, height)
	for i := range height {
		out[i] = leftLines[i] + rightLines[i]
	}
	return strings.Join(out, "\n")
}

// fitBlock forces a block to exactly height lines of exactly width cells.
func fitBlock(block string, width, height int) []string {
	source := strings.Split(block, "\n")
	out := make([]string, height)
	for i := range height {
		line := ""
		if i < len(source) {
			line = source[i]
		}
		out[i] = fitLine(line, width)
	}
	return out
}

// padRows pads a slice of rendered rows out to exactly count rows.
func padRows(rows []string, width, count int) []string {
	for len(rows) < count {
		rows = append(rows, strings.Repeat(" ", width))
	}
	return rows[:count]
}

// fitLine forces one line to exactly width cells, measured in display cells so that styled
// text and wide glyphs are counted correctly.
func fitLine(line string, width int) string {
	if width <= 0 {
		return ""
	}
	actual := lipgloss.Width(line)
	switch {
	case actual == width:
		return line
	case actual < width:
		return line + strings.Repeat(" ", width-actual)
	default:
		return lipgloss.NewStyle().MaxWidth(width).Render(line)
	}
}

// pad right-pads plain text to a width. It is for text with no styling; fitLine is for text
// that may carry escape sequences.
func pad(text string, width int) string {
	if width <= 0 {
		return ""
	}
	if length := lipgloss.Width(text); length < width {
		return text + strings.Repeat(" ", width-length)
	}
	return text
}

// shorten truncates text to width cells, marking that it did so.
func shorten(text string, width int, ellipsis string) string {
	if width <= 0 {
		return ""
	}
	if lipgloss.Width(text) <= width {
		return text
	}
	runes := []rune(text)
	keep := min(max(width-lipgloss.Width(ellipsis), 0), len(runes))
	return string(runes[:keep]) + ellipsis
}

// wrapLines hard-wraps lines at a width, for the detail pane's wrap toggle.
func wrapLines(lines []string, width int) []string {
	if width < 1 {
		return lines
	}
	var out []string
	for _, line := range lines {
		runes := []rune(line)
		if len(runes) == 0 {
			out = append(out, "")
			continue
		}
		for start := 0; start < len(runes); start += width {
			out = append(out, string(runes[start:min(start+width, len(runes))]))
		}
	}
	return out
}

// nonEmpty drops the empty members of a slice, so that an absent badge does not leave a double
// space in the status bar.
func nonEmpty(values []string) []string {
	out := values[:0:0]
	for _, value := range values {
		if value != "" {
			out = append(out, value)
		}
	}
	return out
}

// clamp constrains value to [lowest, highest].
func clamp(value, lowest, highest int) int {
	return min(max(value, lowest), highest)
}

// paneTitle prefixes a pane's name with the key that jumps to it, the way btop does.
//
// The hotkeys were unreachable in practice for two reasons: alt+digit is swallowed by every
// terminal that binds it to switching terminal tabs, and nothing on screen said which number
// belonged to which pane. Drawing the number fixes the second half; accepting a bare digit
// wherever it cannot be text fixes the first.
func paneTitle(p pane) string {
	name := p.String()
	return paneLabel(p) + " " + strings.ToUpper(name[:1]) + name[1:]
}

// paneLabel is the bracketed jump key on its own, for a title that carries its own text.
func paneLabel(p pane) string {
	return "[" + itoa(int(p)+1) + "]"
}
