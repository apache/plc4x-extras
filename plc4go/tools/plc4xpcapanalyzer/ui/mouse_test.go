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
	"testing"

	tea "charm.land/bubbletea/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// The tview interface this replaced called EnableMouse(true), so clicking a pane and scrolling
// both worked. These tests exist because losing that was a silent capability regression and
// nothing else in the suite would have noticed.

// leftClick sends a left click at a cell.
func leftClick(model Model, x, y int) Model {
	return send(model, tea.MouseClickMsg{X: x, Y: y, Button: tea.MouseLeft})
}

func TestTheMouseIsEnabled(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	assert.Equal(t, tea.MouseModeCellMotion, model.View().MouseMode,
		"the tview interface enabled the mouse, so this one must too")
}

func TestClickingAPaneGivesItTheKeyboard(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	for _, target := range []pane{paneCaptures, paneMain, paneDetail} {
		t.Run(target.String(), func(t *testing.T) {
			region, ok := model.regionForPane(target)
			require.True(t, ok, "%s must be drawn at %s", target, wide)

			clicked := leftClick(model, (region.x0+region.x1)/2, (region.y0+region.y1)/2)
			assert.Equal(t, tui.FocusPane, clicked.focus, "a click must move the keyboard into the panes")
			assert.Equal(t, target, clicked.activePane(), "the clicked pane must be the active one")
		})
	}
}

func TestClickingOutsideThePanesReturnsToThePrompt(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	region, ok := model.regionForPane(paneMain)
	require.True(t, ok)
	model = leftClick(model, region.x0+2, region.y0+2)
	require.Equal(t, tui.FocusPane, model.focus)

	// The prompt sits on the second-to-last row.
	model = leftClick(model, 4, wide.Height-2)
	assert.Equal(t, tui.FocusPrompt, model.focus, "clicking the prompt row must return the keyboard to it")
}

// TestClickingAPacketRowSelectsIt is the click-to-select the old list provided.
func TestClickingAPacketRowSelectsIt(t *testing.T) {
	records := make([]Record, 0, 8)
	for i := 1; i <= 8; i++ {
		records = append(records, okRecord(i))
	}
	model := analysed(t, records...)

	region, ok := model.regionForPane(paneMain)
	require.True(t, ok)

	// One border row and one heading row precede the body.
	first := leftClick(model, region.x0+4, region.y0+2)
	second := leftClick(model, region.x0+4, region.y0+3)

	assert.Equal(t, first.cursor+1, second.cursor,
		"clicking one row lower must select the next packet")
	assert.False(t, second.follow, "picking a packet by hand must stop following the run")
}

func TestClickingAPacketRowBeyondTheListIsIgnored(t *testing.T) {
	model := analysed(t, okRecord(1))
	region, ok := model.regionForPane(paneMain)
	require.True(t, ok)

	// Far below the single row that exists.
	clicked := leftClick(model, region.x0+4, region.y1-1)
	assert.Equal(t, model.cursor, clicked.cursor, "a click past the last packet must not move the cursor")
}

// TestTheWheelScrollsThePaneUnderThePointerWithoutTakingFocus is what makes the wheel worth
// having: glancing at a pane must not cost the prompt.
func TestTheWheelScrollsThePaneUnderThePointerWithoutTakingFocus(t *testing.T) {
	records := make([]Record, 0, 60)
	for i := 1; i <= 60; i++ {
		records = append(records, okRecord(i))
	}
	model := analysed(t, records...)
	require.Equal(t, tui.FocusPrompt, model.focus, "the prompt starts with the keyboard")

	region, ok := model.regionForPane(paneMain)
	require.True(t, ok)
	before := model.cursor

	scrolled := send(model, tea.MouseWheelMsg{X: region.x0 + 4, Y: region.y0 + 3, Button: tea.MouseWheelUp})
	assert.Less(t, scrolled.cursor, before, "the wheel must move the selection in the pane under the pointer")
	assert.Equal(t, tui.FocusPrompt, scrolled.focus, "scrolling must not steal the keyboard")
}

func TestTheWheelDoesNotChangeTheActivePane(t *testing.T) {
	records := make([]Record, 0, 20)
	for i := 1; i <= 20; i++ {
		records = append(records, okRecord(i))
	}
	model := analysed(t, records...)
	before := model.activePane()

	region, ok := model.regionForPane(paneDetail)
	require.True(t, ok)
	scrolled := send(model, tea.MouseWheelMsg{X: region.x0 + 4, Y: region.y0 + 1, Button: tea.MouseWheelDown})

	assert.Equal(t, before, scrolled.activePane(),
		"the wheel scrolls under the pointer; it must not move focus there")
}

// TestMouseRegionsDoNotOverlap guards the coordinate mapping: two panes claiming a cell would
// make clicks land unpredictably.
func TestMouseRegionsDoNotOverlap(t *testing.T) {
	for _, size := range []tui.Size{wide, {Width: 100, Height: 24}, {Width: 80, Height: 24}, {Width: 60, Height: 12}} {
		t.Run(size.String(), func(t *testing.T) {
			state, _ := demoState(t)
			model := newTestModel(t, state, size)
			regions := model.mouseRegions()
			require.NotEmpty(t, regions)

			for i, a := range regions {
				for j, b := range regions {
					if i >= j {
						continue
					}
					overlap := a.x0 <= b.x1 && b.x0 <= a.x1 && a.y0 <= b.y1 && b.y0 <= a.y1
					assert.False(t, overlap, "%s and %s overlap: %+v vs %+v", a.pane, b.pane, a, b)
				}
			}
		})
	}
}

// TestNoMouseRegionCoversThePromptRow is the mouse half of "the prompt is always reachable".
func TestNoMouseRegionCoversThePromptRow(t *testing.T) {
	for _, size := range []tui.Size{wide, {Width: 100, Height: 24}, {Width: 80, Height: 24}, {Width: 60, Height: 12}} {
		t.Run(size.String(), func(t *testing.T) {
			state, _ := demoState(t)
			model := newTestModel(t, state, size)
			promptRow := size.Height - 2
			for _, region := range model.mouseRegions() {
				assert.False(t, region.contains(1, promptRow),
					"%s covers the prompt row %d", region.pane, promptRow)
			}
		})
	}
}

// TestOnlyTheLeftButtonActs keeps a right-click from doing something arbitrary in a tool with
// no context menu.
func TestOnlyTheLeftButtonActs(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	region, ok := model.regionForPane(paneMain)
	require.True(t, ok)

	clicked := send(model, tea.MouseClickMsg{X: region.x0 + 4, Y: region.y0 + 2, Button: tea.MouseMiddle})
	assert.Equal(t, tui.FocusPrompt, clicked.focus, "a middle click must do nothing")
}
