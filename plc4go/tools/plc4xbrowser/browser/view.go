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

	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// Rendering.
//
// Every line the model emits is exactly the terminal width, and the last three rows -- toast,
// prompt and help -- are always present. That is not stylistic: below 100 columns the previous
// implementation added its command area to the grid with a zero row and column span, which the
// grid silently discarded, so the input field was never drawn and the user typed into
// something invisible.

// View renders the whole screen.
func (m *Model) View() tea.View {
	view := tea.NewView(m.render())
	view.AltScreen = true
	// The tview interface this replaces called EnableMouse(true), so clicking and scrolling
	// worked; leaving it off would have been a silent capability regression. CellMotion rather
	// than AllMotion: it covers clicks and the wheel, is more widely supported, and avoids the
	// stream of motion events that AllMotion produces for no benefit here.
	view.MouseMode = tea.MouseModeCellMotion
	return view
}

// render builds the screen content.
func (m *Model) render() string {
	if !m.ready {
		return ""
	}
	width, height := m.layout.Size.Width, m.layout.Size.Height

	if m.layout.Mode == tui.ModeTooSmall {
		return m.renderTooSmall(width, height)
	}

	var rows []string
	rows = append(rows, m.renderStatus(width))
	rows = append(rows, m.renderBody()...)
	if m.layout.ShowLog {
		rows = append(rows, splitLines(m.renderLogPane(width))...)
	}
	rows = append(rows, m.renderToast(width))
	rows = append(rows, m.renderPrompt(width))
	rows = append(rows, m.renderHelp(width))
	rows = fitScreen(rows, width, height)

	// Both overlays float over the panes rather than taking rows of their own. lipgloss v2 has
	// a real compositor, so neither is spliced into strings by hand, and neither can push the
	// prompt or the help footer off the bottom of the screen.
	if completion := m.prompt.CompletionView(completionRows); completion != "" {
		rows = m.overlayCompletion(rows, completion, width, height)
	}
	if m.composer != nil {
		rows = m.overlayComposer(rows, width, height)
	}

	return strings.Join(fitScreen(rows, width, height), "\n")
}

// renderTooSmall says what the tool needs rather than drawing something broken.
func (m *Model) renderTooSmall(width, height int) string {
	message := tui.TooSmallMessage() + " (" + strconv.Itoa(tui.MinWidth) + "x" + strconv.Itoa(tui.MinHeight) + " minimum)"
	lines := make([]string, 0, height)
	for i := range height {
		if i == height/2 {
			lines = append(lines, centre(m.theme.Err.Render(message), width))
			continue
		}
		lines = append(lines, strings.Repeat(" ", width))
	}
	return strings.Join(lines, "\n")
}

// renderStatus draws the top line: what the tool is, whether it is simulated, and the counts.
//
// It degrades rather than overflowing. At the widths this tool has to work at, the full line
// does not fit -- the approved design is about 91 cells wide and the target is 80 -- so pieces
// are dropped in order of how little they are missed.
func (m *Model) renderStatus(width int) string {
	theme := m.theme
	glyphs := theme.Glyphs

	name := "PLC4X Browser"
	if m.options.Version != "" {
		name += " " + m.options.Version
	}
	shown, total := m.EventCount()
	counts := fmt.Sprintf("conn %d %s drv %d %s msg %d",
		len(m.options.Session.Connections()), glyphs.Separator,
		len(m.options.Session.Drivers()), glyphs.Separator, total)
	if shown != total {
		counts += fmt.Sprintf(" (%d shown)", shown)
	}
	logLevel := m.options.Config.LogLevel
	if logLevel == "" {
		logLevel = "info"
	}

	badge := ""
	if m.options.Demo {
		badge = theme.Badge.Render("DEMO")
	}

	// Widest first; each candidate drops the least useful remaining piece.
	focusName := "pane " + m.focusName()
	candidates := [][]string{
		{name, badge, counts, "log " + logLevel, focusName},
		{name, badge, counts, "log " + logLevel},
		{name, badge, counts},
		{name, badge},
		{name},
	}
	separator := " " + theme.Muted.Render(glyphs.Separator) + " "
	for _, candidate := range candidates {
		parts := make([]string, 0, len(candidate))
		for i, part := range candidate {
			if part == "" {
				continue
			}
			if i == 0 {
				parts = append(parts, theme.Title.Render(part))
				continue
			}
			if part == badge {
				parts = append(parts, part)
				continue
			}
			parts = append(parts, theme.Muted.Render(part))
		}
		line := " " + strings.Join(parts, separator)
		if lipgloss.Width(line) <= width {
			return padTo(line, width)
		}
	}
	return padTo(" "+theme.Title.Render(name), width)
}

// focusName names the region holding the keyboard.
func (m *Model) focusName() string {
	if m.promptFocused {
		return "prompt"
	}
	return strings.ToLower(m.focus.title())
}

// renderBody draws the pane area for the current layout regime.
func (m *Model) renderBody() []string {
	if m.layout.Mode == tui.ModeCompact {
		return splitLines(m.renderMessagesPane(m.layout.Size.Width, m.layout.BodyHeight))
	}

	sidebar := m.renderSidebarPane(m.layout.SidebarWidth, m.layout.BodyHeight)
	main := m.renderMainColumn(m.layout.MainWidth, m.layout.BodyHeight)
	if !m.layout.ShowDetail {
		return splitLines(lipgloss.JoinHorizontal(lipgloss.Top, sidebar, main))
	}
	detail := m.renderDetailPane(m.layout.DetailWidth, m.layout.BodyHeight)
	return splitLines(lipgloss.JoinHorizontal(lipgloss.Top, sidebar, main, detail))
}

// renderMainColumn draws the message table, with the detail pane beneath it when the layout
// puts detail in the main column rather than beside it.
func (m *Model) renderMainColumn(width, height int) string {
	return m.renderMessagesPane(width, height)
}

// renderSidebarPane draws the connections and drivers.
func (m *Model) renderSidebarPane(width, height int) string {
	theme := m.theme
	glyphs := theme.Glyphs
	inner := max(width-2, 1)

	var lines []string
	for i, row := range m.sidebarRows {
		switch {
		case row.heading != "":
			if len(lines) > 0 {
				lines = append(lines, "")
			}
			lines = append(lines, theme.Key.Render(row.heading))
		default:
			marker := " "
			if i == m.sidebarIndex && m.focus == paneSidebar && !m.promptFocused {
				marker = glyphs.Selected
			}
			label := row.label
			if row.detail != "" {
				label += " " + theme.Muted.Render(row.detail)
			}
			text := marker + " " + label
			if row.marker != "" {
				text = fitInline(text, inner-2) + " " + row.marker
			}
			if i == m.sidebarIndex && m.focus == paneSidebar && !m.promptFocused {
				text = theme.SelectedRow.Render(marker + " " + row.label)
				if row.marker != "" {
					text = fitInline(text, inner-2) + " " + row.marker
				}
			}
			lines = append(lines, text)
		}
	}

	status := strconv.Itoa(len(m.options.Session.Connections()))
	pane := tui.Pane{
		Title:   "Session",
		Status:  status,
		Width:   width,
		Height:  height,
		Focused: m.focus == paneSidebar && !m.promptFocused,
	}
	return pane.Render(theme, strings.Join(lines, "\n"))
}

// renderMessagesPane draws the message table.
func (m *Model) renderMessagesPane(width, height int) string {
	theme := m.theme
	shown, total := m.EventCount()

	status := strconv.Itoa(total)
	if shown != total {
		status = strconv.Itoa(shown) + " of " + strconv.Itoa(total)
	}
	if m.filter != "" {
		status = "/" + m.filter + " " + status
	}

	body := m.messages.View()
	if total == 0 {
		// An empty pane that names the next command beats an empty pane.
		body = theme.Muted.Render("no messages yet") + "\n\n" +
			theme.Muted.Render("try: read-direct <connection> <tag>") + "\n" +
			theme.Muted.Render("  or: browse-direct <connection>")
	}

	pane := tui.Pane{
		Title:   "Messages",
		Status:  status,
		Width:   width,
		Height:  height,
		Focused: m.focus == paneMessages && !m.promptFocused,
	}
	return pane.Render(theme, body)
}

// renderDetailPane draws the selected message.
func (m *Model) renderDetailPane(width, height int) string {
	theme := m.theme
	status := ""
	body := theme.Muted.Render("select a message")
	if event, ok := m.SelectedEvent(); ok {
		status = string(event.Kind)
		body = m.detail.View()
	}
	pane := tui.Pane{
		Title:   "Detail",
		Status:  status,
		Width:   width,
		Height:  height,
		Focused: m.focus == paneDetail && !m.promptFocused,
	}
	return pane.Render(theme, body)
}

// renderLogPane draws the console drawer.
func (m *Model) renderLogPane(width int) string {
	theme := m.theme
	level := m.options.Config.LogLevel
	if level == "" {
		level = "info"
	}
	body := m.logView.View()
	if len(m.logLines) == 0 {
		body = theme.Muted.Render("no output yet")
	}
	pane := tui.Pane{
		Title:   "Log " + theme.Glyphs.Separator + " " + level,
		Status:  strconv.Itoa(len(m.logLines)),
		Width:   width,
		Height:  m.layout.LogHeight,
		Focused: m.focus == paneLog && !m.promptFocused,
	}
	return pane.Render(theme, body)
}

// renderToast draws the held error line, or an empty row to keep the geometry stable.
func (m *Model) renderToast(width int) string {
	if m.toast == "" {
		return strings.Repeat(" ", width)
	}
	theme := m.theme
	hint := theme.Muted.Render("esc dismiss")
	message := theme.Err.Render(theme.Glyphs.Err + " " + m.toast)
	line := " " + message
	// Right-align the hint when it fits; drop it rather than wrap when it does not.
	if gap := width - lipgloss.Width(line) - lipgloss.Width(hint) - 1; gap > 0 {
		line += strings.Repeat(" ", gap) + hint
	}
	return fitInline(line, width)
}

// renderPrompt draws the command line.
//
// It is emitted after everything droppable and before the help footer, so no layout decision
// can remove it.
func (m *Model) renderPrompt(width int) string {
	return fitInline(" "+m.prompt.View(), width)
}

// overlayCompletion floats the completion list directly above the prompt.
func (m *Model) overlayCompletion(rows []string, completion string, width, height int) []string {
	lines := splitLines(completion)
	// Anchor it so its last line sits just above the prompt, which is the second-to-last row,
	// and cover whole rows: a partial-width overlay lets the pane underneath show through at
	// the margins, which reads as a corrupted screen rather than as a popup.
	y := max(height-2-len(lines), 0)

	base := lipgloss.NewLayer(strings.Join(rows, "\n"))
	overlay := lipgloss.NewLayer(m.opaqueBlock(indentBlock(completion, 3), width)).X(0).Y(y).Z(1)
	return splitLines(lipgloss.NewCompositor(base, overlay).Render())
}

// opaqueBlock squares a block off to a single rectangle, clipped to a maximum width.
//
// A floating list has to be opaque. Left ragged, the pane underneath shows through the short
// lines and the result reads as a corrupted screen rather than as a popup.
func (m *Model) opaqueBlock(block string, maxWidth int) string {
	lines := splitLines(block)
	for i, line := range lines {
		lines[i] = fitInline(line, maxWidth)
	}
	return strings.Join(lines, "\n")
}

// indentBlock shifts every line of a block right, so the completion list sits under the text
// of the prompt rather than under its "$" marker.
func indentBlock(block string, by int) string {
	prefix := strings.Repeat(" ", by)
	lines := splitLines(block)
	for i, line := range lines {
		lines[i] = prefix + line
	}
	return strings.Join(lines, "\n")
}

// completionRows bounds the completion popup so it cannot push the prompt off the screen.
const completionRows = 4

// renderHelp draws the key hints.
func (m *Model) renderHelp(width int) string {
	return fitInline(" "+m.help.View(m.keys.ForFocus(m.currentFocus())), width)
}

// overlayComposer composites the modal form over the pane area.
func (m *Model) overlayComposer(rows []string, width, height int) []string {
	form := m.renderComposer(width, height)
	formWidth := lipgloss.Width(strings.SplitN(form, "\n", 2)[0])
	formHeight := len(splitLines(form))

	// Centre it over the panes, never over the prompt: the form is modal but the command line
	// stays visible so the user can see what they were doing.
	x := max((width-formWidth)/2, 0)
	y := max((height-tui.ReservedRows-formHeight)/2, 1)

	base := lipgloss.NewLayer(strings.Join(rows, "\n"))
	overlay := lipgloss.NewLayer(form).X(x).Y(y).Z(1)
	return splitLines(lipgloss.NewCompositor(base, overlay).Render())
}

// renderEventDetail renders one message for the detail pane.
func renderEventDetail(theme tui.Theme, event plcsession.Event) string {
	glyphs := theme.Glyphs
	var lines []string

	header := theme.Value.Render(string(event.Kind))
	if event.Connection != "" {
		header += " " + theme.Muted.Render(event.Connection)
	}
	lines = append(lines, header)
	lines = append(lines, theme.Muted.Render("received "+event.Received.Format("15:04:05.000")))
	if event.Summary != "" {
		lines = append(lines, "", theme.Muted.Render(event.Summary))
	}

	if len(event.Tags) > 0 {
		lines = append(lines, "")
		for _, tag := range event.Tags {
			marker := theme.Ok.Render(glyphs.Ok)
			if !tag.Succeeded() {
				marker = theme.Err.Render(glyphs.Err)
			}
			line := marker + " " + theme.Value.Render(tag.Address)
			if tag.DataType != "" {
				line += " " + theme.Muted.Render(tag.DataType)
			}
			if tag.Value != "" {
				line += " " + theme.Value.Render(tag.Value)
			}
			if !tag.Succeeded() {
				line += " " + theme.Err.Render(tag.Code)
			}
			lines = append(lines, line)
		}
	}
	return strings.Join(lines, "\n")
}

// fitScreen forces the rendered rows into exactly height lines of exactly width cells.
//
// This is the last line of defence: a pane that miscounts by one cell would otherwise tear
// every row beneath it.
func fitScreen(rows []string, width, height int) []string {
	out := make([]string, 0, height)
	for i := range height {
		if i < len(rows) {
			out = append(out, fitInline(rows[i], width))
			continue
		}
		out = append(out, strings.Repeat(" ", width))
	}
	return out
}

// fitInline pads or truncates one line to exactly width display cells.
func fitInline(line string, width int) string {
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

// padTo pads a line to a width without truncating it.
func padTo(line string, width int) string {
	if gap := width - lipgloss.Width(line); gap > 0 {
		return line + strings.Repeat(" ", gap)
	}
	return line
}

// centre centres text in a width.
func centre(text string, width int) string {
	gap := width - lipgloss.Width(text)
	if gap <= 0 {
		return fitInline(text, width)
	}
	left := gap / 2
	return strings.Repeat(" ", left) + text + strings.Repeat(" ", gap-left)
}

// splitLines splits rendered output into lines.
func splitLines(s string) []string {
	if s == "" {
		return nil
	}
	return strings.Split(s, "\n")
}

// renderEventFrames renders the wire bytes captured while a request was in flight.
//
// The association is temporal, not identified: a transport carries no request identifier, so
// what can honestly be shown is the frames that crossed during the request's window. On a
// connection also carrying a subscription that window may include unrelated traffic, and the
// heading says so rather than implying these are the request's own bytes.
func renderEventFrames(theme tui.Theme, event plcsession.Event, log *plcsession.FrameLog) string {
	glyphs := theme.Glyphs

	if log == nil {
		return theme.Muted.Render("no wire capture") + "\n\n" +
			theme.Muted.Render("demo mode simulates the device, so") + "\n" +
			theme.Muted.Render("nothing is put on a wire to capture.") + "\n\n" +
			theme.Muted.Render("connect to a real device to see bytes here.")
	}

	from, to := event.Started, event.Received
	if from.IsZero() {
		from = to
	}
	frames := log.Between(from, to)
	if len(frames) == 0 {
		return theme.Muted.Render("no frames captured for this request") + "\n\n" +
			theme.Muted.Render("nothing crossed the transport between") + "\n" +
			theme.Muted.Render(from.Format("15:04:05.000")+" and "+to.Format("15:04:05.000")) + "\n\n" +
			theme.Muted.Render("press b for the decoded values")
	}

	var lines []string
	lines = append(lines,
		theme.Value.Render("wire frames")+" "+
			theme.Muted.Render("during "+from.Format("15:04:05.000")+strings.Repeat(" ", 1)+glyphs.Separator+" "+to.Format("15:04:05.000")))
	lines = append(lines, theme.Muted.Render("a frame is one transport read or write,"))
	lines = append(lines, theme.Muted.Render("not one protocol message"))
	lines = append(lines, "")

	for i, frame := range frames {
		marker := theme.Ok.Render(glyphs.Outbound)
		label := "sent"
		if frame.Direction == plcsession.FrameInbound {
			marker = theme.Accent.Render(glyphs.Inbound)
			label = "received"
		}
		lines = append(lines, marker+" "+
			theme.Value.Render(label)+" "+
			theme.Muted.Render(frame.At.Format("15:04:05.000")+" "+strconv.Itoa(len(frame.Bytes))+" bytes"))
		lines = append(lines, hexDump(theme, frame.Bytes)...)
		if i < len(frames)-1 {
			lines = append(lines, "")
		}
	}
	return strings.Join(lines, "\n")
}

// hexBytesPerRow is how many bytes a hex row shows. Eight keeps a row inside a narrow detail
// column, which is where this pane usually lives.
const hexBytesPerRow = 8

// hexDump renders bytes as offset, hex and printable text.
func hexDump(theme tui.Theme, data []byte) []string {
	var lines []string
	for offset := 0; offset < len(data); offset += hexBytesPerRow {
		end := min(offset+hexBytesPerRow, len(data))
		chunk := data[offset:end]

		var hex, text strings.Builder
		for _, b := range chunk {
			fmt.Fprintf(&hex, "%02x ", b)
			if b >= 0x20 && b < 0x7f {
				text.WriteByte(b)
			} else {
				text.WriteByte('.')
			}
		}
		// Pad the hex column so the text column lines up on a short final row.
		padding := strings.Repeat("   ", hexBytesPerRow-len(chunk))
		lines = append(lines,
			theme.Muted.Render(fmt.Sprintf("%08x", offset))+"  "+
				theme.Value.Render(hex.String())+padding+
				theme.Muted.Render("|"+text.String()+"|"))
	}
	return lines
}
