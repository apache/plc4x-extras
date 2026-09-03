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
	tea "charm.land/bubbletea/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// Mouse support.
//
// The tview interface this replaces called EnableMouse(true), so clicking a pane and scrolling
// with the wheel both worked. Leaving it out would have been a silent capability regression.
//
// Bubble Tea reports coordinates rather than widgets, so the model has to be able to say which
// pane it drew at a given cell. mouseRegions is the inverse of geometry().

// mouseRegion is where one pane was drawn, in screen cells, borders included: clicking a
// pane's border is still clicking that pane.
type mouseRegion struct {
	pane   pane
	x0, y0 int
	x1, y1 int
}

// contains reports whether a cell falls inside the region.
func (r mouseRegion) contains(x, y int) bool {
	return x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1
}

// mouseRegions returns where each pane sits in the current layout.
//
// It follows Render's assembly: one status row, then the body, then the run panel. The body is
// the sidebar beside a right-hand column that is the main pane over the detail pane.
func (m Model) mouseRegions() []mouseRegion {
	geometry := m.geometry()
	if geometry.PaneHeight <= 0 {
		return nil
	}

	const statusRows = 1
	bodyTop := statusRows
	bodyBottom := bodyTop + geometry.PaneHeight - 1

	var regions []mouseRegion
	rightStart := 0
	if geometry.SidebarWidth > 0 {
		regions = append(regions, mouseRegion{
			pane: paneCaptures, x0: 0, y0: bodyTop,
			x1: geometry.SidebarWidth - 1, y1: bodyBottom,
		})
		rightStart = geometry.SidebarWidth
	}

	mainBottom := bodyBottom
	if geometry.DetailHeight > 0 {
		mainBottom = bodyBottom - geometry.DetailHeight
	}
	regions = append(regions, mouseRegion{
		pane: paneMain, x0: rightStart, y0: bodyTop,
		x1: m.size.Width - 1, y1: mainBottom,
	})
	if geometry.DetailHeight > 0 {
		regions = append(regions, mouseRegion{
			pane: paneDetail, x0: rightStart, y0: mainBottom + 1,
			x1: m.size.Width - 1, y1: bodyBottom,
		})
	}
	return regions
}

// paneAtCell reports which pane owns a cell.
func (m Model) paneAtCell(x, y int) (pane, bool) {
	for _, region := range m.mouseRegions() {
		if region.contains(x, y) {
			return region.pane, true
		}
	}
	return 0, false
}

// regionForPane returns the drawn region of a pane.
func (m Model) regionForPane(target pane) (mouseRegion, bool) {
	for _, region := range m.mouseRegions() {
		if region.pane == target {
			return region, true
		}
	}
	return mouseRegion{}, false
}

// handleMouse routes a mouse event.
func (m Model) handleMouse(msg tea.MouseMsg) (Model, tea.Cmd) {
	mouse := msg.Mouse()
	switch msg.(type) {
	case tea.MouseWheelMsg:
		return m.mouseWheel(mouse), nil
	case tea.MouseClickMsg:
		if mouse.Button != tea.MouseLeft {
			// Only the left button acts: this tool has neither a context menu nor a paste
			// target, so anything else would be a surprise.
			return m, nil
		}
		return m.mouseClick(mouse)
	}
	return m, nil
}

// mouseWheel scrolls the pane under the pointer without moving focus, so the wheel can be used
// to glance at a pane without giving up the prompt.
func (m Model) mouseWheel(mouse tea.Mouse) Model {
	target, ok := m.paneAtCell(mouse.X, mouse.Y)
	if !ok {
		return m
	}

	const step = 3
	delta := step
	if mouse.Button == tea.MouseWheelUp {
		delta = -step
	}

	// scrollPane works on the focused pane, so borrow the focus for the duration rather than
	// duplicating the per-pane scrolling rules here.
	restore, wasOverlay := m.pane, m.overlay
	m.pane, m.overlay = target, paneCount
	m = m.scroll(delta)
	m.pane, m.overlay = restore, wasOverlay
	return m
}

// mouseClick gives a pane the keyboard and selects the row clicked.
func (m Model) mouseClick(mouse tea.Mouse) (Model, tea.Cmd) {
	target, ok := m.paneAtCell(mouse.X, mouse.Y)
	if !ok {
		// A click on the prompt, the toast or the footer returns the keyboard to the prompt,
		// which is the least surprising reading of a click outside the panes.
		cmd := m.setFocus(tui.FocusPrompt)
		return m, cmd
	}

	// Focus the clicked pane the same way the pane-jump keys do, so the overlay bookkeeping
	// stays in one place.
	m.overlay = paneCount
	m.pane = target
	cmd := m.setFocus(tui.FocusPane)

	region, found := m.regionForPane(target)
	if !found {
		return m, cmd
	}
	// One row of border, and for the packet table one further row of column headings.
	row := mouse.Y - region.y0 - 1
	if target == paneMain && m.tab == tabPackets && row >= 1 {
		m = m.selectDrawnPacket(row - 1)
	}
	return m, cmd
}

// selectDrawnPacket moves the cursor to a drawn body row of the packet table.
//
// The window is model state, so the absolute row is simply the window's top plus the offset
// within it -- no guessing at a widget's private scroll position.
func (m Model) selectDrawnPacket(drawn int) Model {
	if drawn < 0 {
		return m
	}
	index := m.packetTop + drawn
	if index >= len(m.visible) {
		return m
	}
	m.cursor = index
	m.follow = false
	return m.revealCursor()
}
