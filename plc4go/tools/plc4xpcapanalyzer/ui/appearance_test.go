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
	"strings"
	"testing"

	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tuitest"
)

// The rest of the suite renders with the plain theme, because assertions read better against
// text than against escape sequences. These are the exception: the coloured themes are what a
// user actually sees, and a band that stops short of the edge or a style whose reset punches a
// hole in one is invisible in plain text.

// TestBandedRowsCoverTheFullWidth pins the top bar and the command bar as bars: each has to be
// painted across its whole width, at every width the status line degrades through.
func TestBandedRowsCoverTheFullWidth(t *testing.T) {
	state, _ := demoState(t)

	for _, dark := range []bool{true, false} {
		for _, width := range []int{157, 120, 100, 80, 64} {
			m := newTestModelWithTheme(t, state, tui.Size{Width: width, Height: 40},
				tui.Options{Dark: dark})
			rows := strings.Split(m.Render(), "\n")
			require.Len(t, rows, 40)

			for _, row := range []struct {
				name  string
				index int
			}{
				{"status bar", 0},
				{"command bar", len(rows) - 2},
			} {
				line := rows[row.index]
				assert.Equal(t, width, lipgloss.Width(line),
					"%s at %d columns should be exactly the screen width", row.name, width)
				assert.Equal(t, width, tuitest.PaintedCells(line),
					"%s at %d columns should be painted across its whole width", row.name, width)
			}
		}
	}
}

// TestNoColorRenderIsFreeOfColour is the NO_COLOR contract for the whole screen. A band is a
// background, and a background is the one thing that cannot degrade gracefully: it prints as a
// solid block on a terminal that was asked for no colour.
func TestNoColorRenderIsFreeOfColour(t *testing.T) {
	state, _ := demoState(t)
	m := newTestModelWithTheme(t, state, tui.Size{Width: 120, Height: 40},
		tui.Options{Dark: true, NoColor: true, ASCII: true})
	render := m.Render()

	assert.Empty(t, tuitest.ColourCodes(render), "a NO_COLOR render must set no colour")
	assert.Equal(t, 0, tuitest.PaintedCells(strings.Split(render, "\n")[0]),
		"a NO_COLOR status bar must not be banded")
}

// TestColouredRenderKeepsEveryRowExactlyTheScreenWidth guards the whole screen rather than the
// bands: a row that miscounts its cells because of an escape sequence tears every row below it.
func TestColouredRenderKeepsEveryRowExactlyTheScreenWidth(t *testing.T) {
	state, _ := demoState(t)
	for _, size := range []tui.Size{{Width: 157, Height: 50}, {Width: 100, Height: 30}, {Width: 80, Height: 24}} {
		m := newTestModelWithTheme(t, state, size, tui.Options{Dark: true})
		rows := strings.Split(m.Render(), "\n")
		assert.Len(t, rows, size.Height)
		for i, row := range rows {
			assert.Equal(t, size.Width, lipgloss.Width(row),
				"row %d at %dx%d", i, size.Width, size.Height)
		}
	}
}

// --- colour that carries meaning ---

// TestTheVerdictColumnIsColouredByWhatItMeans is the point of the column: a defect is red, a
// clean round trip green, and a skip neither, because a skip is not a finding and must not read
// as though it were.
func TestTheVerdictColumnIsColouredByWhatItMeans(t *testing.T) {
	theme := tui.NewTheme(tui.Options{Dark: true})
	state, _ := demoState(t)
	m := newTestModelWithTheme(t, state, wide, tui.Options{Dark: true})

	for verdict, want := range map[Verdict]string{
		VerdictOK:            theme.Ok.Render("x"),
		VerdictParseFail:     theme.Err.Render("x"),
		VerdictSerializeFail: theme.Err.Render("x"),
		VerdictBytesDiffer:   theme.Err.Render("x"),
		VerdictSkipped:       theme.Muted.Render("x"),
		VerdictFiltered:      theme.Muted.Render("x"),
		VerdictNoPayload:     theme.Muted.Render("x"),
	} {
		assert.Equal(t, want, m.verdictStyle(verdict).Render("x"), verdict.String())
	}

	// And the three classes are actually distinguishable from one another.
	ok := m.verdictStyle(VerdictOK).Render("x")
	issue := m.verdictStyle(VerdictParseFail).Render("x")
	quiet := m.verdictStyle(VerdictSkipped).Render("x")
	assert.NotEqual(t, ok, issue)
	assert.NotEqual(t, ok, quiet)
	assert.NotEqual(t, issue, quiet)
}

// TestAPacketRowIsStyledPerCell guards the defect that made the browser's selected row
// invisible: a terminal ends a style at the next reset, so a row styled as a whole loses its
// styling at the first cell that colours itself.
func TestAPacketRowIsStyledPerCell(t *testing.T) {
	theme := tui.NewTheme(tui.Options{Dark: true})
	m := newTestModelWithTheme(t, mustState(t), wide, tui.Options{Dark: true})
	columns := packetLayout(wide.Width)

	row := m.renderPacketRow(okRecord(1), true, columns, wide.Width)
	plain := tuitest.Strip(row)

	// The selection reaches the message column, which sits after the cells that colour
	// themselves. Styling the row as a whole would have ended it at the first of them.
	selection := tuitest.Escapes.FindString(theme.SelectedRow.Render("x"))
	require.NotEmpty(t, selection)
	message := strings.Index(plain, "CBusMessageToServer")
	require.Positive(t, message, "the fixture has to have a message to look for")
	assert.Contains(t, styleAt(row, message), selection,
		"the selection has to still be in force at the message column")

	// The verdict keeps its own colour even on the selected row: it is the answer the tool
	// exists to give.
	verdict := strings.Index(plain, "ok")
	require.Positive(t, verdict)
	assert.Contains(t, styleAt(row, verdict), tuitest.Escapes.FindString(theme.Ok.Render("x")),
		"a clean round trip stays green on the selected row")
}

// TestARowIsExactlyTheWidthWhateverItsColours is the arithmetic that styling must not disturb.
func TestARowIsExactlyTheWidthWhateverItsColours(t *testing.T) {
	m := newTestModelWithTheme(t, mustState(t), wide, tui.Options{Dark: true})
	for _, width := range []int{150, 120, 90, 70, 50} {
		columns := packetLayout(width)
		for _, record := range []Record{okRecord(1), brokenRecord(2)} {
			for _, selected := range []bool{true, false} {
				row := m.renderPacketRow(record, selected, columns, width)
				assert.Equal(t, width, lipgloss.Width(row),
					"%s selected=%v at %d columns", record.Verdict, selected, width)
			}
		}
	}
}

// TestAHexLineSeparatesTheBytesFromTheScaffolding checks the dump reads as data rather than as
// a wall: where you are and what it says as text are context, and only the middle is the data.
func TestAHexLineSeparatesTheBytesFromTheScaffolding(t *testing.T) {
	m := newTestModelWithTheme(t, mustState(t), wide, tui.Options{Dark: true})
	line := HexDumpLines([]byte("322100AD\r\n"), hexBytesPerRow, 4)[0]
	rendered := m.renderHexLine(line)

	assert.Equal(t, line, tuitest.Strip(rendered), "styling must not change a single character")

	plain := tuitest.Strip(rendered)
	offset := styleAt(rendered, strings.Index(plain, "00000000"))
	data := styleAt(rendered, strings.Index(plain, "33 32"))
	text := styleAt(rendered, strings.Index(plain, "|"))

	assert.NotEqual(t, offset, data, "the offset should not read as loudly as the bytes")
	assert.NotEqual(t, text, data, "nor should the text pane")
	assert.Equal(t, offset, text, "the two kinds of context should read alike")
}

// TestATreeLineDimsItsFrames is what makes plc4x's parse trees readable rather than textural:
// undifferentiated, the boxes outweigh what is inside them.
func TestATreeLineDimsItsFrames(t *testing.T) {
	m := newTestModelWithTheme(t, mustState(t), wide, tui.Options{Dark: true})
	line := "╔═CBusMessage/request═╗"
	rendered := m.renderTreeLine(line)

	assert.Equal(t, line, tuitest.Strip(rendered), "styling must not change a single character")

	plain := tuitest.Strip(rendered)
	frame := styleAt(rendered, strings.Index(plain, "╔"))
	content := styleAt(rendered, strings.Index(plain, "CBusMessage"))
	assert.NotEqual(t, frame, content, "the frame and its contents must not read alike")

	// A line with no frame at all is left entirely at full contrast.
	assert.Equal(t, content, styleAt(m.renderTreeLine("0x7e 126 RESET"), 0))
}

// TestOnlyRealBoxDrawingIsDimmed is the deliberate limit: the ASCII characters a box could be
// drawn from occur in payload text too, and dimming those would dim data.
func TestOnlyRealBoxDrawingIsDimmed(t *testing.T) {
	for _, r := range []rune{'═', '║', '╔', '╝', '┄', '┆', '─', '│'} {
		assert.True(t, isBoxDrawing(r), "%c is box drawing", r)
	}
	for _, r := range []rune{'-', '|', '+', '~', '0', 'x', '/', ' ', 'A'} {
		assert.False(t, isBoxDrawing(r), "%c is not box drawing and may carry data", r)
	}
}

// styleAt returns the styling in force at a cell offset of a rendered line, as the terminal
// would have it: a style holds until something replaces or resets it.
func styleAt(rendered string, cell int) string {
	if cell < 0 {
		return ""
	}
	current, seen, rest := "", 0, rendered
	for rest != "" {
		if location := tuitest.Escapes.FindStringIndex(rest); location != nil && location[0] == 0 {
			current = rest[:location[1]]
			rest = rest[location[1]:]
			continue
		}
		next := len(rest)
		if location := tuitest.Escapes.FindStringIndex(rest); location != nil {
			next = location[0]
		}
		for _, r := range rest[:next] {
			if seen == cell {
				return current
			}
			seen++
			_ = r
		}
		rest = rest[next:]
	}
	return current
}

// mustState is a demo session, for the renderers that only need a model to hang off.
func mustState(t *testing.T) *State {
	t.Helper()
	state, _ := demoState(t)
	return state
}
