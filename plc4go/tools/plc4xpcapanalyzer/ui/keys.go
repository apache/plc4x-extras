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
	"context"
	"strconv"
	"strings"

	"charm.land/bubbles/v2/key"
	tea "charm.land/bubbletea/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// Key handling.
//
// The order below is the whole of the rule the design review insisted on: a bare character is
// a pane action only while a pane has the keyboard, and text everywhere else. Nothing here
// compares a Focus to decide that — every binding was resolved for the current focus by
// tui.KeyMap.ForFocus and tui.Scoped, so an out-of-scope binding simply has no keys and
// key.Matches refuses it.

// handleKey routes one key press.
func (m Model) handleKey(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	// Quit comes first, at every focus and in every mode. Whatever else the user has got
	// themselves into, the way out has to work.
	if key.Matches(msg, m.keys.Quit) {
		return m.quit()
	}
	// The filter line is text entry, so it claims every other key before anything else looks
	// at it.
	if m.filtering {
		return m.handleFilterKey(msg)
	}

	switch {
	case key.Matches(msg, m.keys.Help):
		m.footer.SetShowAll(!m.footer.ShowAll())
		return m, nil

	case key.Matches(msg, m.keys.Cancel):
		return m.handleEscape()

	case key.Matches(msg, m.keys.PaneJump):
		return m.jumpToPane(msg), nil

	case key.Matches(msg, m.keys.PageUp):
		return m.scroll(-m.pageSize()), nil

	case key.Matches(msg, m.keys.PageDown):
		return m.scroll(m.pageSize()), nil
	}

	if m.focus == tui.FocusPrompt {
		if key.Matches(msg, m.keys.FocusPanes) {
			cmd := m.setFocus(tui.FocusPane)
			return m, cmd
		}
		var cmd tea.Cmd
		m.prompt, cmd = m.prompt.Update(msg)
		return m, cmd
	}
	return m.handlePaneKey(msg)
}

// quit ends the program, cancelling anything still in flight on the way out.
//
// The cancellation is not cosmetic: an analysis publishes each record with a blocking send
// guarded only by its own context, so quitting without cancelling left that goroutine parked
// on a send that nothing would ever drain, still holding the capture open.
func (m Model) quit() (tea.Model, tea.Cmd) {
	if m.cancelRun != nil {
		m.cancelRun()
		m.cancelRun = nil
	}
	m.quitting = true
	return m, tea.Quit
}

// handleEscape walks the dismissal chain, most local first. Each link is only taken when the
// one before it had nothing to dismiss, so one press never does two things.
func (m Model) handleEscape() (tea.Model, tea.Cmd) {
	switch {
	case m.prompt.ConsumesEscape():
		var cmd tea.Cmd
		m.prompt, cmd = m.prompt.Update(tea.KeyPressMsg{Code: tea.KeyEscape})
		return m, cmd
	case m.footer.ShowAll():
		m.footer.SetShowAll(false)
		return m, nil
	case m.overlay != paneCount:
		m.overlay = paneCount
		return m, nil
	case m.toast != "":
		m.setToast("", false)
		return m, nil
	case m.run.active:
		return m.abortRun(), nil
	case m.focus != tui.FocusPrompt:
		return m, m.setFocus(tui.FocusPrompt)
	}
	return m, nil
}

// jumpToPane handles alt+1 to alt+4, and the bare digits while a pane has the keyboard.
//
// Selecting a pane that the current layout has no column for opens it as an overlay instead of
// doing nothing, so the binding is never dead.
func (m Model) jumpToPane(msg tea.KeyPressMsg) Model {
	digit := lastDigit(msg.String())
	if digit < 1 || digit > int(paneCount) {
		return m
	}
	target := pane(digit - 1)
	if m.paneVisible(target) {
		m.overlay = paneCount
		m.pane = target
	} else {
		m.overlay = target
	}
	if m.focus != tui.FocusPane {
		_ = m.setFocus(tui.FocusPane)
	}
	return m
}

// lastDigit pulls the digit out of a key name such as "alt+3" or "3".
func lastDigit(name string) int {
	if name == "" {
		return -1
	}
	digit, err := strconv.Atoi(name[len(name)-1:])
	if err != nil {
		return -1
	}
	return digit
}

// handlePaneKey handles the bindings that are live only while a pane has the keyboard.
func (m Model) handlePaneKey(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	switch {
	case key.Matches(msg, m.keys.FocusPrompt):
		return m, m.setFocus(tui.FocusPrompt)

	case key.Matches(msg, m.keys.NextPane):
		m.pane = m.nextPane(1)
		return m, nil

	case key.Matches(msg, m.keys.PrevPane):
		m.pane = m.nextPane(-1)
		return m, nil

	case key.Matches(msg, m.keys.Up):
		return m.scroll(-1), nil

	case key.Matches(msg, m.keys.Down):
		return m.scroll(1), nil

	case key.Matches(msg, m.keys.Top):
		return m.scrollTo(0), nil

	case key.Matches(msg, m.keys.Bottom):
		return m.scrollTo(1 << 30), nil

	case key.Matches(msg, m.keys.Select):
		return m.activate()

	case key.Matches(msg, m.keys.Filter):
		if m.activePane() == paneMain && m.tab != tabLog {
			m.filtering = true
			return m, m.setFocus(tui.FocusOverlay)
		}
		m.setToast("filter applies to the packets and findings tabs", false)
		return m, nil

	case key.Matches(msg, m.keys.Wrap):
		m.detailWrap = !m.detailWrap
		m.setToast("detail wrap "+onOff(m.detailWrap), false)
		return m, nil

	case key.Matches(msg, m.keys.Yank):
		return m.yank(), nil

	case key.Matches(msg, m.keys.Follow):
		m.follow = !m.follow
		m.setToast("follow "+onOff(m.follow), false)
		return m, nil

	case key.Matches(msg, m.keys.Expand):
		return m.expand(), nil

	case key.Matches(msg, m.keys.LogLevel):
		return m.cycleLogLevel(), nil

	case key.Matches(msg, m.tools.TabNext):
		m.tab = tab((int(m.tab) + 1) % int(tabCount))
		m.refilter()
		return m, nil

	case key.Matches(msg, m.tools.TabPrev):
		m.tab = tab((int(m.tab) - 1 + int(tabCount)) % int(tabCount))
		m.refilter()
		return m, nil

	case key.Matches(msg, m.tools.ViewBytes):
		m.detail = detailBytes
		return m, nil

	case key.Matches(msg, m.tools.ViewTree):
		m.detail = detailTree
		return m, nil

	case key.Matches(msg, m.tools.ViewDiff):
		m.detail = detailDiff
		return m, nil

	case key.Matches(msg, m.tools.Open):
		return m.prefill("open ")

	case key.Matches(msg, m.tools.Analyze):
		return m.prefill(m.prefilledRun("analyze"))

	case key.Matches(msg, m.tools.Extract):
		return m.prefill(m.prefilledRun("extract"))
	}
	return m, nil
}

// handleFilterKey drives the inline filter entry in the main pane's border.
func (m Model) handleFilterKey(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	switch {
	case key.Matches(msg, m.keys.Cancel):
		m.filtering = false
		m.filter = ""
		m.refilter()
		return m, m.setFocus(tui.FocusPane)
	case key.Matches(msg, m.keys.Submit):
		m.filtering = false
		m.refilter()
		return m, m.setFocus(tui.FocusPane)
	}
	switch msg.Code {
	case tea.KeyBackspace:
		if runes := []rune(m.filter); len(runes) > 0 {
			m.filter = string(runes[:len(runes)-1])
			m.refilter()
		}
		return m, nil
	}
	if text := msg.Text; text != "" {
		m.filter += text
		m.refilter()
	}
	return m, nil
}

// prefill puts a command in the prompt and hands the keyboard back to it, so that the bare
// shortcut is a head start on typing rather than a hidden action.
func (m Model) prefill(line string) (tea.Model, tea.Cmd) {
	cmd := m.setFocus(tui.FocusPrompt)
	m.prompt.SetValue(line)
	return m, cmd
}

// prefilledRun builds "analyze <protocol> <file>" from what the session already has open.
func (m Model) prefilledRun(verb string) string {
	line := verb + " " + m.state.Protocol.Name + " "
	if capture, ok := m.state.CurrentCapture(); ok {
		line += capture.Path
	}
	return line
}

// activate does whatever selecting the highlighted row means in the focused pane.
//
// Every row that can be highlighted does something. The version this replaces attached
// selection handlers whose entire body was a TODO comment.
func (m Model) activate() (tea.Model, tea.Cmd) {
	switch m.activePane() {
	case paneCaptures:
		return m.activateSidebarRow()
	case paneMain:
		if m.tab == tabLog {
			return m, nil
		}
		// Selecting a packet moves the detail pane onto it, and in compact mode brings that
		// pane into view, since there is no column for it there.
		if _, ok := m.selectedRecord(); !ok {
			m.setToast("no packet selected", false)
			return m, nil
		}
		// The detail pane already follows the cursor, so selecting a packet means "take me to
		// it": as a column where there is one, as an overlay where there is not.
		if m.paneVisible(paneDetail) {
			m.pane = paneDetail
		} else {
			m.overlay = paneDetail
		}
		return m, nil
	case paneRun:
		if m.run.active {
			return m.abortRun(), nil
		}
		if m.run.label == "" {
			m.setToast("nothing has been run yet", false)
			return m, nil
		}
		return m.prefill(m.prefilledRun("analyze"))
	}
	return m, nil
}

// yank copies the detail pane's current text into the log, where the terminal's own selection
// can reach it. There is no clipboard dependency here on purpose: these tools run over ssh as
// often as not, and a clipboard write that silently does nothing is worse than a visible copy.
func (m Model) yank() Model {
	record, ok := m.selectedRecord()
	if !ok {
		m.setToast("no packet selected", false)
		return m
	}
	m.appendLog("packet " + itoa(record.Number) + " " + record.Verdict.String())
	m.appendLog(m.detailLines(record, 200, 78)...)
	m.setToast("packet "+itoa(record.Number)+" written to the log", false)
	m.tab = tabLog
	return m
}

// expand writes the selected record's full reason into the log, for the errors that are too
// long for the table's verdict column.
func (m Model) expand() Model {
	record, ok := m.selectedRecord()
	if !ok {
		m.setToast("no packet selected", false)
		return m
	}
	if record.Reason == "" {
		m.setToast("packet "+itoa(record.Number)+" has nothing to expand", false)
		return m
	}
	m.appendLog("packet " + itoa(record.Number) + ": " + record.Reason)
	m.tab = tabLog
	m.setToast("", false)
	return m
}

// cycleLogLevel steps the level the log pane is filtered at.
func (m Model) cycleLogLevel() Model {
	levels := logLevels
	current := m.state.LogLevel.String()
	next := levels[0]
	for i, level := range levels {
		if level == current {
			next = levels[(i+1)%len(levels)]
			break
		}
	}
	outcome := m.root.Execute(context.Background(), m.state, "log set "+next)
	m.appendLog(outcome.Lines...)
	m.setToast("log level "+next, false)
	return m
}

// scroll moves the focused pane's cursor by delta rows.
func (m Model) scroll(delta int) Model {
	switch m.activePane() {
	case paneCaptures:
		m.sidebarCursor = clampIndex(m.sidebarCursor+delta, len(m.sidebarSelectable()))
	case paneMain:
		if m.tab == tabLog {
			m.logTop = clampIndex(m.logTop+delta, len(m.logLines))
			return m
		}
		m.cursor = clampIndex(m.cursor+delta, len(m.visible))
		m = m.revealCursor()
		// Moving the cursor by hand means the user has taken over from the run, so following
		// stops. Leaving it on would yank the selection away on the next batch of packets.
		if delta != 0 {
			m.follow = false
		}
	case paneDetail:
		m.detailTop = clampIndex(m.detailTop+delta, m.detailExtent())
	}
	return m
}

// revealCursor scrolls the packet window the minimum distance needed to show the cursor.
//
// The window only moves when the cursor would leave it, which is what keeps the selection
// moving inside a stationary list instead of dragging the whole list with it.
func (m Model) revealCursor() Model {
	rows := max(m.geometry().PaneHeight-3, 1)
	switch {
	case m.cursor < m.packetTop:
		m.packetTop = m.cursor
	case m.cursor >= m.packetTop+rows:
		m.packetTop = m.cursor - rows + 1
	}
	m.packetTop = min(max(m.packetTop, 0), max(len(m.visible)-1, 0))
	return m
}

// scrollTo jumps the focused pane's cursor to an absolute row.
func (m Model) scrollTo(row int) Model {
	switch m.activePane() {
	case paneCaptures:
		m.sidebarCursor = clampIndex(row, len(m.sidebarSelectable()))
	case paneMain:
		if m.tab == tabLog {
			m.logTop = clampIndex(row, len(m.logLines))
			return m
		}
		m.cursor = clampIndex(row, len(m.visible))
		m = m.revealCursor()
		m.follow = false
	case paneDetail:
		m.detailTop = clampIndex(row, m.detailExtent())
	}
	return m
}

// detailExtent is how many lines the detail pane's current view holds for the selected packet,
// and so how far it can be scrolled.
//
// The offset used to be clamped against an arbitrary ceiling instead. Forty presses of down in
// a two-row hex dump therefore cost forty presses of up to undo, and in the diff view — which
// draws a fixed block and does not scroll at all — they cost nothing visible and then landed
// the bytes view at the end of its dump for no reason the user could see.
func (m Model) detailExtent() int {
	record, ok := m.selectedRecord()
	if !ok {
		return 0
	}
	switch m.detail {
	case detailBytes:
		return len(HexDumpLines(record.Original, hexBytesPerRow, 1<<16))
	case detailTree:
		lines := splitLines(record.Tree)
		if m.detailWrap {
			lines = wrapLines(lines, m.detailInnerWidth())
		}
		return len(lines)
	default:
		return 0
	}
}

// pageSize is how far a page key moves, which is the height of the focused pane's content.
//
// It comes from geometry rather than from tui.Layout.BodyHeight: Compute reserves rows there
// for a log drawer, and this tool draws the log as a tab of the main pane instead, so the
// layout's figure is three rows short of what is actually on screen.
func (m Model) pageSize() int {
	return max(m.geometry().PaneHeight-2, 1)
}

// activePane is the pane the keyboard is acting on: the overlay when one is open, otherwise
// the focused column.
func (m Model) activePane() pane {
	if m.overlay != paneCount {
		return m.overlay
	}
	return m.pane
}

// paneVisible reports whether a pane has a place in the current layout.
func (m Model) paneVisible(p pane) bool {
	switch p {
	case paneCaptures:
		return m.geometry().SidebarWidth > 0
	case paneMain:
		return true
	case paneDetail:
		// The same test the renderer applies. A pane that tab can reach but the renderer does
		// not draw is a keyboard black hole.
		return m.geometry().DetailHeight > 0
	case paneRun:
		return m.run.label != ""
	default:
		return false
	}
}

// visiblePanes are the panes tab cycles through, in order.
func (m Model) visiblePanes() []pane {
	var panes []pane
	for p := range paneCount {
		if m.paneVisible(p) {
			panes = append(panes, p)
		}
	}
	return panes
}

// nextPane steps the focus by delta places through the visible panes, wrapping.
func (m Model) nextPane(delta int) pane {
	panes := m.visiblePanes()
	if len(panes) == 0 {
		return paneMain
	}
	index := 0
	for i, p := range panes {
		if p == m.pane {
			index = i
			break
		}
	}
	index = (index + delta + len(panes)) % len(panes)
	return panes[index]
}

// clampIndex constrains an index to [0, length), returning zero for an empty collection.
func clampIndex(index, length int) int {
	if length <= 0 {
		return 0
	}
	return min(max(index, 0), length-1)
}

// onOff renders a toggle for a toast.
func onOff(on bool) string {
	if on {
		return "on"
	}
	return "off"
}

// itoa is strconv.Itoa under a shorter name, because the view builds a great many small
// strings and the longer name buries them.
func itoa(n int) string { return strconv.Itoa(n) }

// splitLines splits text into lines, dropping a trailing empty one.
func splitLines(text string) []string {
	lines := strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n")
	if len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	return lines
}
