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
	"testing"
	"time"

	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tuitest"
)

// The rest of the suite renders with the plain theme, because assertions read better against
// text than against escape sequences. These tests are the exception: they exist because the
// coloured themes are what a user actually sees, and three of the mistakes made building this
// screen -- a band that stopped a cell short of the edge, a style whose reset punched a hole
// in the band, and a background reaching a NO_COLOR terminal -- are invisible in plain text.

// TestBandedRowsCoverTheFullWidth pins the fix for a command bar whose background stopped one
// cell short of the right edge, which reads as a notch in the bar rather than as a bar.
func TestBandedRowsCoverTheFullWidth(t *testing.T) {
	for _, dark := range []bool{true, false} {
		theme := tui.NewTheme(tui.Options{Dark: dark})
		m := newTestModelWithTheme(t, theme)

		// Several widths: the status bar drops pieces as it narrows, and each candidate has to
		// pad back out to the full width.
		for _, width := range []int{157, 120, 100, 80, 64} {
			m = sized(t, m, width, 40)
			rows := strings.Split(m.render(), "\n")
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
					"%s at %d columns should be banded across its whole width", row.name, width)
			}
		}
	}
}

// TestNoColorRenderIsFreeOfColour is the NO_COLOR contract for the whole screen rather than for
// one style. Bold and reverse video stay: NO_COLOR asks for no colour, and those are how a
// title and a cursor still read without one. A background is the one thing that cannot degrade
// gracefully, because it prints as a solid block.
func TestNoColorRenderIsFreeOfColour(t *testing.T) {
	m := newTestModelWithTheme(t, tui.NewTheme(tui.Options{Dark: true, NoColor: true}))
	m = sized(t, m, 120, 40)
	m.appendLogAt("error", "something failed")
	m.appendLogAt("warn", "something is odd")
	render := m.render()

	assert.Empty(t, tuitest.ColourCodes(render), "a NO_COLOR render must set no colour")
	// Whatever else it does, it must not paint a background anywhere.
	assert.Equal(t, 0, tuitest.PaintedCells(strings.Split(render, "\n")[0]),
		"a NO_COLOR status bar must not be banded")
}

// TestColouredRenderKeepsEveryRowExactlyTheScreenWidth guards the whole screen, not just the
// bands: a row that miscounts its cells because of an escape sequence tears every row below it.
func TestColouredRenderKeepsEveryRowExactlyTheScreenWidth(t *testing.T) {
	m := newTestModelWithTheme(t, tui.NewTheme(tui.Options{Dark: true}))
	for _, size := range [][2]int{{157, 50}, {100, 30}, {80, 24}, {64, 14}} {
		m = sized(t, m, size[0], size[1])
		rows := strings.Split(m.render(), "\n")
		assert.Len(t, rows, size[1])
		for i, row := range rows {
			assert.Equal(t, size[0], lipgloss.Width(row), "row %d at %dx%d", i, size[0], size[1])
		}
	}
}

// TestTagValuesAreColouredByType checks that the four type families reach the screen: the
// point of typing the values was that a number, a flag, a string and a time read differently.
func TestTagValuesAreColouredByType(t *testing.T) {
	theme := tui.NewTheme(tui.Options{Dark: true})

	rendered := map[string]string{}
	for _, tag := range []plcsession.TagResult{
		{Name: "a", DataType: "REAL", Value: "21.5"},
		{Name: "b", DataType: "BOOL", Value: "true"},
		{Name: "c", DataType: "STRING", Value: "running"},
		{Name: "d", DataType: "TIME", Value: "1h2m"},
	} {
		rendered[tag.DataType] = renderTagValue(theme, tag)
	}

	seen := map[string]string{}
	for dataType, value := range rendered {
		codes := tuitest.Escapes.FindAllString(value, -1)
		require.NotEmpty(t, codes, "%s should be styled", dataType)
		if other, clash := seen[codes[0]]; clash {
			t.Fatalf("%s and %s render in the same colour", dataType, other)
		}
		seen[codes[0]] = dataType
		assert.Contains(t, tuitest.Strip(value), rendered[dataType][:0]+value[:0])
	}
}

// TestRenderTagValueSplitsAccessFlags covers the browse case: the value carries the access
// triple and then the tag's human name, and the two read better apart.
func TestRenderTagValueSplitsAccessFlags(t *testing.T) {
	theme := tui.NewTheme(tui.Options{Dark: true, NoColor: true})

	for name, test := range map[string]struct {
		tag  plcsession.TagResult
		want string
	}{
		"flags and name":  {plcsession.TagResult{Value: "rw- Temperature 1"}, "rw- Temperature 1"},
		"flags alone":     {plcsession.TagResult{Value: "rws"}, "rws"},
		"all denied":      {plcsession.TagResult{Value: "--- Hidden"}, "--- Hidden"},
		"plain value":     {plcsession.TagResult{DataType: "REAL", Value: "21.5"}, "21.5"},
		"three-char word": {plcsession.TagResult{DataType: "STRING", Value: "off now"}, "off now"},
	} {
		assert.Equal(t, test.want, renderTagValue(theme, test.tag), name)
	}
}

// TestIsAccessFlagsRejectsLookalikes is why isAccessFlags exists at all: a three-character
// value that merely looks like a triple must not be styled as one.
func TestIsAccessFlagsRejectsLookalikes(t *testing.T) {
	for _, flags := range []string{"rws", "rw-", "r--", "---", "-w-", "--s"} {
		assert.True(t, isAccessFlags(flags), flags)
	}
	for _, other := range []string{"", "rw", "rwsx", "off", "abc", "RWS", "r w", "srw", "wrs"} {
		assert.False(t, isAccessFlags(other), other)
	}
}

// TestRoundDurationDropsNoiseAndKeepsSignal pins the precision rule. A demo read reporting
// "62.25µs" is noise; a real one taking 1.2 seconds must not be rounded to "1s".
func TestRoundDurationDropsNoiseAndKeepsSignal(t *testing.T) {
	for _, test := range []struct {
		in   time.Duration
		want string
	}{
		{62250 * time.Nanosecond, "62µs"},
		{999 * time.Nanosecond, "1µs"},
		{4*time.Millisecond + 213*time.Microsecond, "4.2ms"},
		{999 * time.Millisecond, "999ms"},
		{1234 * time.Millisecond, "1.23s"},
		{2*time.Second + 4*time.Millisecond, "2s"},
		{90 * time.Second, "1m30s"},
	} {
		assert.Equal(t, test.want, roundDuration(test.in), test.in.String())
	}

	// Whatever the precision, a duration never renders as nothing.
	for _, d := range []time.Duration{0, time.Nanosecond, time.Hour} {
		assert.NotEmpty(t, roundDuration(d))
	}
}

// TestLogLevelTagsAreAlignedAndDistinct matters because the tags are what gives a wall of log
// lines shape: unequal widths would ragged the left edge of every message.
func TestLogLevelTagsAreAlignedAndDistinct(t *testing.T) {
	tags := map[string]string{}
	for _, level := range []string{"error", "warn", "info", "debug", "trace", "", "nonsense"} {
		tag := logLevelTag(level)
		assert.Len(t, tag, 3, "%q should be a three-letter tag", level)
		tags[level] = tag
	}

	assert.Equal(t, "ERR", tags["error"])
	assert.Equal(t, "WRN", tags["warn"])
	assert.Equal(t, "INF", tags["info"])
	// Anything else is an ordinary line rather than a fourth marker.
	for _, level := range []string{"debug", "trace", "", "nonsense"} {
		assert.Equal(t, "INF", tags[level], level)
	}
}

// TestLogLinesCarryTheirLevel checks the level survives into the rendered console, which is
// what lets a failure be spotted in the log without reading it.
func TestLogLinesCarryTheirLevel(t *testing.T) {
	m := newTestModelWithTheme(t, tui.NewTheme(tui.Options{Dark: true}))
	m = sized(t, m, 120, 40)

	m.appendLogAt("info", "connected")
	m.appendLogAt("error", "read failed")

	lines := m.LogLines()
	require.GreaterOrEqual(t, len(lines), 2)
	info, failure := lines[len(lines)-2], lines[len(lines)-1]

	assert.Contains(t, tuitest.Strip(info), "INF")
	assert.Contains(t, tuitest.Strip(failure), "ERR")

	// The two lines have to be told apart by colour and not only by their three letters. The
	// comparison is over the whole set of codes in each line rather than the first, because
	// both lines open with the same muted timestamp; it is the level marker that differs.
	theme := tui.NewTheme(tui.Options{Dark: true})
	errCode := tuitest.Escapes.FindAllString(theme.Err.Render("x"), -1)[0]
	assert.Contains(t, tuitest.Escapes.FindAllString(failure, -1), errCode, "an error line should be red")
	assert.NotContains(t, tuitest.Escapes.FindAllString(info, -1), errCode, "an info line should not")
}

// TestSelectedMessageRowIsVisuallyDistinct guards a regression that no plain-theme test can
// see. bubbles/table styles each cell and then styles the selected row around them, so a
// foreground on the cell style ends with a reset that wipes the selection from every cell
// after the first -- and the selected row becomes indistinguishable from its neighbours.
func TestSelectedMessageRowIsVisuallyDistinct(t *testing.T) {
	theme := tui.NewTheme(tui.Options{Dark: true})
	m := newTestModelWithTheme(t, theme)
	m = sized(t, m, 120, 30)
	for _, address := range []string{"temp/1", "temp/2", "press/1"} {
		_, cmd := m.Update(tui.PromptSubmitMsg{Line: "read-direct " + plcsession.DemoDeviceOne + " " + address})
		runCmd(t, m, cmd)
	}
	_, total := m.EventCount()
	require.Equal(t, 3, total)

	rows := messageRows(t, m)
	require.Len(t, rows, 3)

	// Follow keeps the newest selected, so the last row is the one carrying the selection.
	selected, plain := rows[2], rows[0]
	selectionCode := tuitest.Escapes.FindString(theme.SelectedRow.Render("x"))
	require.NotEmpty(t, selectionCode)

	assert.Contains(t, selected, selectionCode, "the selected row should carry the selection style")
	assert.NotContains(t, plain, selectionCode, "an unselected row should not")

	// And the selection has to survive past the first cell, which is the actual bug: the row
	// is styled once, and each cell's own styling would end it at the first reset. The run the
	// selection opens therefore has to still be open when the time column arrives.
	run := selected[strings.Index(selected, selectionCode)+len(selectionCode):]
	if end := tuitest.Escapes.FindStringIndex(run); end != nil {
		run = run[:end[0]]
	}
	assert.Contains(t, run, "12:",
		"the selection should still be in force at the time column, not ended after the first cell")
}

// messageRows returns the body rows of the message table, stripped of the pane chrome around
// them but with their styling intact.
func messageRows(t *testing.T, m *Model) []string {
	t.Helper()
	var rows []string
	for line := range strings.SplitSeq(m.render(), "\n") {
		plain := tuitest.Strip(line)
		for index := 1; index <= 3; index++ {
			marker := "\u2502" + strconv.Itoa(index) + "   "
			if strings.Contains(plain, marker) {
				rows = append(rows, line)
			}
		}
	}
	return rows
}
