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
	"strings"
	"testing"

	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestPromptRowsAreAlwaysReserved is the regression test for the defect that motivated this
// package: below 100 columns the old UIs never drew their command input, so the user typed
// into an invisible field. Whatever the size, the rows the prompt lives in are taken off the
// budget before any pane gets space.
func TestPromptRowsAreAlwaysReserved(t *testing.T) {
	for _, size := range []Size{
		{Width: 200, Height: 60},
		{Width: 100, Height: 24},
		{Width: 90, Height: 24},
		{Width: 89, Height: 24},
		{Width: 80, Height: 24},
		{Width: 70, Height: 20},
		{Width: 60, Height: 12},
	} {
		t.Run(size.String(), func(t *testing.T) {
			layout := Compute(size)
			require.NotEqual(t, ModeTooSmall, layout.Mode, "%v should be drawable", size)
			assert.LessOrEqual(t, layout.BodyHeight+layout.LogHeight+ReservedRows, size.Height,
				"the body plus chrome must fit inside the terminal")
			assert.Positive(t, layout.BodyHeight, "there must be room left for content")
		})
	}
}

// TestEightyByTwentyFourIsUsable pins the size the redesign is most accountable for, since it
// is where the previous tools were broken.
func TestEightyByTwentyFourIsUsable(t *testing.T) {
	layout := Compute(Size{Width: 80, Height: 24})
	assert.Equal(t, ModeCompact, layout.Mode, "80 columns is below the compact breakpoint")
	assert.Equal(t, 80, layout.MainWidth, "the single column should use the full width")
	assert.False(t, layout.ShowSidebar, "a sidebar does not fit; it becomes an overlay")
	assert.Positive(t, layout.BodyHeight)
}

func TestHundredByTwentyFourIsTheWideLayout(t *testing.T) {
	layout := Compute(Size{Width: 100, Height: 24})
	require.Equal(t, ModeWide, layout.Mode)
	assert.True(t, layout.ShowSidebar)
	assert.True(t, layout.ShowDetail)
	// The columns must exactly tile the width: a rounding error here shows up as a torn edge.
	assert.Equal(t, 100, layout.SidebarWidth+layout.MainWidth+layout.DetailWidth,
		"columns must sum to the terminal width")
}

// TestColumnsAlwaysTileTheWidth is the same property across a sweep of sizes, since an
// off-by-one in the column arithmetic is the classic way a TUI ends up with a ragged edge.
func TestColumnsAlwaysTileTheWidth(t *testing.T) {
	for width := CompactWidth; width <= 240; width++ {
		layout := Compute(Size{Width: width, Height: 40})
		require.Equal(t, ModeWide, layout.Mode)
		assert.Equal(t, width, layout.SidebarWidth+layout.MainWidth+layout.DetailWidth,
			"width %d: columns must tile exactly", width)
		assert.Positive(t, layout.MainWidth, "width %d: the main column must not vanish", width)
	}
}

func TestCompactBreakpointIsExact(t *testing.T) {
	assert.Equal(t, ModeCompact, Compute(Size{Width: CompactWidth - 1, Height: 30}).Mode)
	assert.Equal(t, ModeWide, Compute(Size{Width: CompactWidth, Height: 30}).Mode)
}

// TestPanesAreDroppedInPriorityOrder pins the degradation order: the log drawer goes before
// the detail pane, and neither takes the prompt with it.
func TestPanesAreDroppedInPriorityOrder(t *testing.T) {
	roomy := Compute(Size{Width: 120, Height: 40})
	require.True(t, roomy.ShowLog)
	require.True(t, roomy.ShowDetail)

	cramped := Compute(Size{Width: 120, Height: CrampedHeight - 1})
	assert.False(t, cramped.ShowLog, "the log drawer is the first thing to go")
	assert.False(t, cramped.ShowDetail, "the detail pane is the second")
	assert.Positive(t, cramped.BodyHeight, "the main list must survive")
	assert.Equal(t, 120, cramped.SidebarWidth+cramped.MainWidth,
		"with no detail column the remaining two must still tile")
}

func TestTerminalsBelowTheMinimumAreRefusedRatherThanDrawnBadly(t *testing.T) {
	for _, size := range []Size{
		{Width: 59, Height: 24},
		{Width: 80, Height: 11},
		{Width: 10, Height: 3},
		{Width: 0, Height: 0},
	} {
		t.Run(size.String(), func(t *testing.T) {
			assert.Equal(t, ModeTooSmall, Compute(size).Mode)
		})
	}
	assert.NotEmpty(t, TooSmallMessage())
}

func TestSidebarWidthIsClamped(t *testing.T) {
	narrow := Compute(Size{Width: CompactWidth, Height: 40})
	assert.GreaterOrEqual(t, narrow.SidebarWidth, 22, "a sidebar narrower than this cannot show a connection string")

	wide := Compute(Size{Width: 400, Height: 40})
	assert.LessOrEqual(t, wide.SidebarWidth, 34, "the sidebar must not hog a very wide terminal")
}

func TestModeStringIsReadable(t *testing.T) {
	assert.Equal(t, "wide", ModeWide.String())
	assert.Equal(t, "compact", ModeCompact.String())
	assert.Equal(t, "too-small", ModeTooSmall.String())
}

// --- Pane ---

// TestPaneRendersExactlyItsRequestedBox is the property every multi-pane layout depends on:
// a pane that renders one cell too wide or one line too tall corrupts its neighbours.
func TestPaneRendersExactlyItsRequestedBox(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	for _, tc := range []struct{ w, h int }{
		{40, 10}, {24, 5}, {80, 24}, {12, 3}, {100, 6},
	} {
		t.Run(Size{Width: tc.w, Height: tc.h}.String(), func(t *testing.T) {
			pane := Pane{Title: "Messages", Status: "14", Width: tc.w, Height: tc.h}
			rendered := pane.Render(theme, "one\ntwo\nthree")

			lines := strings.Split(rendered, "\n")
			assert.Len(t, lines, tc.h, "a pane must render exactly its height")
			for i, line := range lines {
				assert.Equal(t, tc.w, lipgloss.Width(line),
					"line %d must be exactly %d cells, got %d: %q", i, tc.w, lipgloss.Width(line), line)
			}
		})
	}
}

// TestPaneClipsOverlongContent guards against content pushing a pane out of shape.
func TestPaneClipsOverlongContent(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	long := strings.Repeat("this line is far too long to fit ", 20)
	tall := strings.Repeat("row\n", 100)

	pane := Pane{Title: "T", Width: 30, Height: 6}
	rendered := pane.Render(theme, long+"\n"+tall)

	lines := strings.Split(rendered, "\n")
	assert.Len(t, lines, 6)
	for i, line := range lines {
		assert.Equal(t, 30, lipgloss.Width(line), "line %d overflowed: %q", i, line)
	}
}

func TestPaneShowsItsTitleAndStatus(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	rendered := Pane{Title: "Connections", Status: "2", Width: 40, Height: 5}.Render(theme, "")
	top, _, _ := strings.Cut(rendered, "\n")
	assert.Contains(t, top, "Connections", "the title belongs in the top border")
	assert.Contains(t, top, "2", "the status belongs in the top border")
}

// TestPaneDropsStatusBeforeTitleWhenNarrow: when the border cannot hold both, the count is
// the expendable half, and the result must still be a well-formed box.
func TestPaneDropsStatusBeforeTitleWhenNarrow(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	rendered := Pane{Title: "Connections", Status: "1234567890", Width: 18, Height: 4}.Render(theme, "")
	top, _, _ := strings.Cut(rendered, "\n")
	assert.Contains(t, top, "Connections")
	assert.NotContains(t, top, "1234567890")
	assert.Equal(t, 18, lipgloss.Width(top))
}

// TestPaneWithATitleTooLongStaysAWellFormedBox is the pathological case.
func TestPaneWithATitleTooLongStaysAWellFormedBox(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	rendered := Pane{Title: strings.Repeat("very long title ", 5), Width: 14, Height: 4}.Render(theme, "x")
	for i, line := range strings.Split(rendered, "\n") {
		assert.Equal(t, 14, lipgloss.Width(line), "line %d: %q", i, line)
	}
}

// TestPaneGeometryIsIdenticalAcrossGlyphSets is the payoff of the width-parity rule: choosing
// the ASCII fallback must not change the layout by a single cell.
func TestPaneGeometryIsIdenticalAcrossGlyphSets(t *testing.T) {
	unicode := NewTheme(Options{Dark: true, NoColor: true, ASCII: false})
	ascii := NewTheme(Options{Dark: true, NoColor: true, ASCII: true})

	pane := Pane{Title: "Messages", Status: "14", Width: 44, Height: 8}
	unicodeLines := strings.Split(pane.Render(unicode, "a\nb\nc"), "\n")
	asciiLines := strings.Split(pane.Render(ascii, "a\nb\nc"), "\n")

	require.Len(t, asciiLines, len(unicodeLines))
	for i := range unicodeLines {
		assert.Equal(t, lipgloss.Width(unicodeLines[i]), lipgloss.Width(asciiLines[i]),
			"line %d differs in width between glyph sets", i)
	}
}

func TestPaneFocusChangesAppearanceNotGeometry(t *testing.T) {
	theme := NewTheme(Options{Dark: true})
	base := Pane{Title: "Messages", Width: 30, Height: 5}
	focused := base
	focused.Focused = true

	plain := base.Render(theme, "x")
	lit := focused.Render(theme, "x")

	assert.NotEqual(t, plain, lit, "focus must be visible")
	plainLines, litLines := strings.Split(plain, "\n"), strings.Split(lit, "\n")
	require.Len(t, litLines, len(plainLines))
	for i := range plainLines {
		assert.Equal(t, lipgloss.Width(plainLines[i]), lipgloss.Width(litLines[i]),
			"focus must not change geometry, line %d", i)
	}
}
