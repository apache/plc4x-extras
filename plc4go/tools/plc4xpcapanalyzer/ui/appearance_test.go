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
