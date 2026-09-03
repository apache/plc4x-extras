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
	"testing"

	tea "charm.land/bubbletea/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// The tview interface this replaced called EnableMouse(true), so clicking and scrolling both
// worked. These tests exist because dropping that was a silent capability regression, and
// nothing else would have caught it.

// clickAt sends a left click at a cell.
func clickAt(model *Model, x, y int) {
	model.Update(tea.MouseClickMsg{X: x, Y: y, Button: tea.MouseLeft})
}

// TestTheMouseIsEnabled is the direct regression test: the port must ask the terminal for
// mouse events at all.
func TestTheMouseIsEnabled(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	assert.Equal(t, tea.MouseModeCellMotion, model.View().MouseMode,
		"the tview interface enabled the mouse, so this one must too")
}

// TestClickingAPaneGivesItTheKeyboard is the behaviour a user notices first.
func TestClickingAPaneGivesItTheKeyboard(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	require.True(t, mustPromptFocused(model), "focus starts at the prompt")

	for _, target := range []pane{paneSidebar, paneMessages, paneDetail, paneLog} {
		t.Run(target.title(), func(t *testing.T) {
			region, ok := model.regionFor(target)
			require.True(t, ok, "%s must be drawn at this size", target.title())

			// Click the middle of the pane.
			clickAt(model, (region.x0+region.x1)/2, (region.y0+region.y1)/2)

			focus, promptFocused := model.Focused()
			assert.False(t, promptFocused, "clicking a pane must take the keyboard from the prompt")
			assert.Equal(t, target, focus, "the clicked pane must be the focused one")
		})
	}
}

// TestClickingOutsideThePanesReturnsToThePrompt keeps a stray click from stranding the user.
func TestClickingOutsideThePanesReturnsToThePrompt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	// Take focus off the prompt first.
	region, ok := model.regionFor(paneMessages)
	require.True(t, ok)
	clickAt(model, region.x0+2, region.y0+2)
	require.False(t, mustPromptFocused(model))

	// The prompt row is the second from the bottom.
	clickAt(model, 5, 29-1)
	assert.True(t, mustPromptFocused(model), "clicking the prompt row must return the keyboard to it")
}

// TestClickingAMessageRowSelectsIt covers the click-to-select the old lists provided.
func TestClickingAMessageRowSelectsIt(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	shown, _ := model.EventCount()
	require.Greater(t, shown, 2, "the demo catalogue should give us several rows")

	region, ok := model.regionFor(paneMessages)
	require.True(t, ok)

	// Row 0 of the body: one row of border, one row of column headings.
	clickAt(model, region.x0+3, region.y0+2)
	first := model.messages.Cursor()

	clickAt(model, region.x0+3, region.y0+3)
	second := model.messages.Cursor()

	assert.Equal(t, first+1, second, "clicking one row lower must select the next message")
}

// TestClickingASidebarRowSelectsItAndSkipsHeadings pins that a heading is not selectable, since
// the sidebar's drawn lines do not map one-to-one onto its rows.
func TestClickingASidebarRowSelectsItAndSkipsHeadings(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	region, ok := model.regionFor(paneSidebar)
	require.True(t, ok)

	// Line 0 of the body is the CONNECTIONS heading; line 1 is the first connection.
	clickAt(model, region.x0+2, region.y0+2)
	row, ok := model.SidebarSelection()
	require.True(t, ok)
	assert.True(t, row.selectable(), "the cursor must never land on a heading")
	assert.NotEmpty(t, row.connection, "the first selectable row is the demo connection")
}

// TestTheWheelScrollsTheePaneUnderThePointerWithoutTakingFocus is what makes the wheel useful:
// glancing at the log must not cost the prompt.
func TestTheWheelScrollsThePaneUnderThePointerWithoutTakingFocus(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	// Fill the log so there is something to scroll.
	for i := range 60 {
		model.AppendLog("line " + string(rune('a'+i%26)))
	}
	require.True(t, mustPromptFocused(model))

	region, ok := model.regionFor(paneLog)
	require.True(t, ok)
	before := model.logView.YOffset()

	model.Update(tea.MouseWheelMsg{X: region.x0 + 4, Y: region.y0 + 1, Button: tea.MouseWheelUp})
	assert.NotEqual(t, before, model.logView.YOffset(), "the wheel must scroll the pane under the pointer")
	assert.True(t, mustPromptFocused(model), "scrolling must not steal the keyboard from the prompt")
}

func TestTheWheelMovesTheMessageCursor(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "browse-direct " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)

	region, ok := model.regionFor(paneMessages)
	require.True(t, ok)
	before := model.messages.Cursor()

	model.Update(tea.MouseWheelMsg{X: region.x0 + 4, Y: region.y0 + 3, Button: tea.MouseWheelUp})
	assert.Less(t, model.messages.Cursor(), before, "the wheel must move the selection up")
}

// TestTheMouseIsIgnoredWhileTheComposerIsOpen: the form is modal, so a click on a pane behind
// it would move focus somewhere the user cannot see.
func TestTheMouseIsIgnoredWhileTheComposerIsOpen(t *testing.T) {
	model := sized(t, newTestModel(t), 120, 30)
	_, cmd := model.Update(tui.PromptSubmitMsg{Line: "read " + plcsession.DemoDeviceOne})
	runCmd(t, model, cmd)
	require.True(t, model.ComposerOpen())

	region, ok := model.regionFor(paneLog)
	require.True(t, ok)
	clickAt(model, region.x0+4, region.y0+1)

	assert.True(t, model.ComposerOpen(), "the form must stay open")
	focus, _ := model.Focused()
	assert.NotEqual(t, paneLog, focus, "a click behind a modal form must not move focus")
}

// TestPaneRegionsTileTheBodyWithoutOverlapping guards the coordinate mapping: two panes
// claiming the same cell would make clicks land unpredictably.
func TestPaneRegionsTileTheBodyWithoutOverlapping(t *testing.T) {
	for _, size := range []tui.Size{
		{Width: 120, Height: 40},
		{Width: 100, Height: 30},
		{Width: 100, Height: 24},
		{Width: 80, Height: 24},
	} {
		t.Run(size.String(), func(t *testing.T) {
			model := sized(t, newTestModel(t), size.Width, size.Height)
			regions := model.paneRegions()
			require.NotEmpty(t, regions)

			for i, a := range regions {
				for j, b := range regions {
					if i >= j {
						continue
					}
					overlap := a.x0 <= b.x1 && b.x0 <= a.x1 && a.y0 <= b.y1 && b.y0 <= a.y1
					assert.False(t, overlap,
						"%s and %s overlap: %v vs %v", a.pane.title(), b.pane.title(), a, b)
				}
			}
		})
	}
}

// TestNoPaneRegionCoversThePromptRow is the mouse half of "the prompt is always reachable": a
// pane claiming the prompt's row would swallow clicks meant for it.
func TestNoPaneRegionCoversThePromptRow(t *testing.T) {
	for _, size := range []tui.Size{
		{Width: 120, Height: 40},
		{Width: 100, Height: 24},
		{Width: 80, Height: 24},
		{Width: 60, Height: 12},
	} {
		t.Run(size.String(), func(t *testing.T) {
			model := sized(t, newTestModel(t), size.Width, size.Height)
			promptRow := size.Height - 2
			for _, region := range model.paneRegions() {
				assert.False(t, region.contains(1, promptRow),
					"%s covers the prompt row %d", region.pane.title(), promptRow)
			}
		})
	}
}
