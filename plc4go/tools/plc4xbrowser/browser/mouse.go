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

	tea "charm.land/bubbletea/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// Mouse support.
//
// The tview interface this replaces enabled the mouse, so clicking a pane and scrolling with
// the wheel both worked. Dropping that would have been a silent capability regression, which
// is the one thing the port was not allowed to do.
//
// Bubble Tea gives coordinates rather than widgets, so the model has to know where it drew
// things. paneAt is the inverse of the layout: given a cell, which pane owns it.

// paneRegion is where one pane was drawn, in screen cells. The bounds are inclusive of the
// border, because clicking a pane's border is still clicking that pane.
type paneRegion struct {
	pane   pane
	x0, y0 int
	x1, y1 int
}

// contains reports whether a cell falls inside the region.
func (r paneRegion) contains(x, y int) bool {
	return x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1
}

// paneRegions returns where each pane sits in the current layout.
//
// It mirrors render's assembly order: the status line, then the pane row, then the log drawer.
// Compact mode draws only the message pane, so only that one is clickable.
func (m *Model) paneRegions() []paneRegion {
	if m.layout.Mode == tui.ModeTooSmall {
		return nil
	}

	const statusRows = 1
	bodyTop := statusRows
	bodyBottom := bodyTop + m.layout.BodyHeight - 1

	if m.layout.Mode == tui.ModeCompact {
		regions := []paneRegion{
			{pane: paneMessages, x0: 0, y0: bodyTop, x1: m.layout.Size.Width - 1, y1: bodyBottom},
		}
		if m.layout.ShowLog {
			regions = append(regions, paneRegion{
				pane: paneLog, x0: 0, y0: bodyBottom + 1,
				x1: m.layout.Size.Width - 1, y1: bodyBottom + m.layout.LogHeight,
			})
		}
		return regions
	}

	sidebarEnd := m.layout.SidebarWidth - 1
	mainEnd := sidebarEnd + m.layout.MainWidth
	regions := []paneRegion{
		{pane: paneSidebar, x0: 0, y0: bodyTop, x1: sidebarEnd, y1: bodyBottom},
		{pane: paneMessages, x0: sidebarEnd + 1, y0: bodyTop, x1: mainEnd, y1: bodyBottom},
	}
	if m.layout.ShowDetail {
		regions = append(regions, paneRegion{
			pane: paneDetail, x0: mainEnd + 1, y0: bodyTop,
			x1: mainEnd + m.layout.DetailWidth, y1: bodyBottom,
		})
	}
	if m.layout.ShowLog {
		regions = append(regions, paneRegion{
			pane: paneLog, x0: 0, y0: bodyBottom + 1,
			x1: m.layout.Size.Width - 1, y1: bodyBottom + m.layout.LogHeight,
		})
	}
	return regions
}

// paneAt reports which pane owns a cell.
func (m *Model) paneAt(x, y int) (pane, bool) {
	for _, region := range m.paneRegions() {
		if region.contains(x, y) {
			return region.pane, true
		}
	}
	return 0, false
}

// regionFor returns the drawn region of a pane.
func (m *Model) regionFor(target pane) (paneRegion, bool) {
	for _, region := range m.paneRegions() {
		if region.pane == target {
			return region, true
		}
	}
	return paneRegion{}, false
}

// handleMouse routes a mouse event.
//
// While the composer is open the mouse is ignored rather than acted on: the form is modal, and
// clicking a pane behind it would move focus somewhere the user cannot see.
func (m *Model) handleMouse(msg tea.MouseMsg) (tea.Model, tea.Cmd) {
	if m.composer != nil {
		return m, nil
	}

	mouse := msg.Mouse()
	switch msg.(type) {
	case tea.MouseWheelMsg:
		return m.wheel(mouse)
	case tea.MouseClickMsg:
		if mouse.Button != tea.MouseLeft {
			// Only the left button acts. A middle-click paste or a right-click menu would be
			// surprising in a tool that has neither.
			return m, nil
		}
		return m.click(mouse)
	}
	return m, nil
}

// wheel scrolls whichever pane the pointer is over, without moving focus.
//
// Scrolling under the pointer rather than in the focused pane is what a terminal user expects,
// and it means the wheel can be used to glance at the log without giving up the prompt.
func (m *Model) wheel(mouse tea.Mouse) (tea.Model, tea.Cmd) {
	target, ok := m.paneAt(mouse.X, mouse.Y)
	if !ok {
		return m, nil
	}

	delta := 3
	if mouse.Button == tea.MouseWheelUp {
		delta = -3
	}

	switch target {
	case paneSidebar:
		for range 3 {
			if delta < 0 {
				m.moveSidebar(-1)
			} else {
				m.moveSidebar(1)
			}
		}
	case paneMessages:
		if delta < 0 {
			m.messages.MoveUp(3)
		} else {
			m.messages.MoveDown(3)
		}
		m.syncDetail()
	case paneDetail:
		m.detail.SetYOffset(max(m.detail.YOffset()+delta, 0))
	case paneLog:
		m.logView.SetYOffset(max(m.logView.YOffset()+delta, 0))
	}
	return m, nil
}

// click gives a pane the keyboard and, where the pane has rows, selects the row clicked.
func (m *Model) click(mouse tea.Mouse) (tea.Model, tea.Cmd) {
	target, ok := m.paneAt(mouse.X, mouse.Y)
	if !ok {
		// A click on the prompt row, the toast or the help footer returns to the prompt, which
		// is the least surprising thing a click outside the panes can do.
		m.focusPrompt()
		return m, nil
	}

	m.blurPrompt()
	m.setFocus(target)

	region, found := m.regionFor(target)
	if !found {
		return m, nil
	}
	// One row of border at the top, and for the message table one further row of column
	// headings.
	row := mouse.Y - region.y0 - 1

	switch target {
	case paneSidebar:
		m.selectSidebarRow(row)
	case paneMessages:
		if row >= 1 {
			m.selectMessageRow(row - 1)
		}
	}
	return m, nil
}

// selectSidebarRow moves the sidebar cursor to a drawn row, ignoring headings and blanks.
//
// The sidebar's drawn lines do not map one-to-one onto its rows, because a section heading is
// preceded by a blank line. Walking the same construction the renderer uses keeps the two in
// step rather than duplicating the arithmetic.
func (m *Model) selectSidebarRow(drawn int) {
	line := 0
	for i, row := range m.sidebarRows {
		if row.heading != "" {
			if line > 0 {
				line++ // the blank line the renderer inserts before a heading
			}
			if line == drawn {
				return // a heading is not selectable
			}
			line++
			continue
		}
		if line == drawn {
			m.sidebarIndex = i
			return
		}
		line++
	}
}

// selectMessageRow moves the table cursor to a drawn body row.
//
// bubbles/table keeps its scroll offset private, so the clicked row cannot be derived from the
// cursor and the height -- trying that is the same mistake as deriving a scroll window, and it
// selects the wrong row as soon as the table has scrolled. Instead the row is read off the
// screen: the first column carries the message's own number, which is exactly the information
// needed and is by construction what the user clicked.
func (m *Model) selectMessageRow(drawn int) {
	if drawn < 0 {
		return
	}
	lines := splitLines(m.messages.View())
	// One line of column headings precedes the body.
	index := drawn + 1
	if index < 0 || index >= len(lines) {
		return
	}
	number, ok := leadingNumber(lines[index])
	if !ok {
		return
	}
	// The column is 1-based, matching what the pane shows.
	m.messages.SetCursor(number - 1)
	m.syncDetail()
}

// leadingNumber reads the first run of digits from a rendered row, skipping any styling.
func leadingNumber(line string) (int, bool) {
	digits := make([]rune, 0, 8)
	seen := false
	for _, r := range stripANSI(line) {
		switch {
		case r >= '0' && r <= '9':
			digits = append(digits, r)
			seen = true
		case seen:
			// The number has ended.
			value, err := strconv.Atoi(string(digits))
			return value, err == nil
		}
	}
	if !seen {
		return 0, false
	}
	value, err := strconv.Atoi(string(digits))
	return value, err == nil
}

// stripANSI removes escape sequences so a rendered line can be read as text.
func stripANSI(s string) string {
	var out strings.Builder
	inEscape := false
	for _, r := range s {
		switch {
		case r == 0x1b:
			inEscape = true
		case inEscape && (r == 'm' || r == 'K' || r == 'H'):
			inEscape = false
		case !inEscape:
			out.WriteRune(r)
		}
	}
	return out.String()
}
