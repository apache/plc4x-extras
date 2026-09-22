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

	"charm.land/lipgloss/v2"
)

// Pane draws a bordered region whose name lives in its top border.
//
// The name is in the border rather than on a line of its own for a measured reason: the UIs
// being replaced titled their panes with a centred text widget occupying a full three-row grid
// cell, so four titles cost twelve rows of chrome to display four words. Carrying the title in
// the border costs nothing and leaves those rows for content.
type Pane struct {
	// Title is the pane's name, drawn at the left of the top border.
	Title string
	// Status is optional text drawn at the right of the top border, for a count or a mode.
	Status string
	// Width and Height are the pane's outer dimensions, borders included.
	Width  int
	Height int
	// Focused selects the focused styling, so the keyboard's target is always visible.
	Focused bool
}

// Render draws the pane around content.
//
// content is clipped to the inner area rather than being allowed to overflow, because a pane
// that overflows corrupts every pane drawn beside it.
func (p Pane) Render(theme Theme, content string) string {
	border := theme.Glyphs.Border
	// Chrome, not PaneStyle: PaneStyle carries a Border, so rendering a single border glyph
	// with it would draw a whole box around that one glyph.
	chromeStyle := theme.ChromeStyle(p.Focused)
	titleStyle := theme.PaneTitleStyle(p.Focused)

	// Two columns and two rows go to the border itself.
	innerWidth := max(p.Width-2, 1)
	innerHeight := max(p.Height-2, 1)

	// edge colours a run of border characters.
	edge := func(s string) string {
		if s == "" {
			return ""
		}
		return chromeStyle.Render(s)
	}

	var out strings.Builder
	out.WriteString(p.topBorder(theme, border, edge, titleStyle, innerWidth))
	out.WriteString("\n")

	lines := fitLines(content, innerWidth, innerHeight)
	for _, line := range lines {
		out.WriteString(edge(border.Left))
		out.WriteString(line)
		out.WriteString(edge(border.Right))
		out.WriteString("\n")
	}

	out.WriteString(edge(border.BottomLeft))
	out.WriteString(edge(strings.Repeat(border.Bottom, innerWidth)))
	out.WriteString(edge(border.BottomRight))
	return out.String()
}

// topBorder builds the top edge, embedding the title and any status text.
func (p Pane) topBorder(theme Theme, border lipgloss.Border, edge func(string) string, titleStyle lipgloss.Style, innerWidth int) string {
	// A single border cell of padding either side of the title keeps it from touching the
	// corner glyphs.
	title := ""
	if p.Title != "" {
		title = titleStyle.Render(p.Title)
	}
	status := ""
	if p.Status != "" {
		status = theme.Muted.Render(p.Status)
	}

	titleWidth := lipgloss.Width(title)
	statusWidth := lipgloss.Width(status)

	// Padding: one border char before the title, one after; likewise around the status.
	const gaps = 4
	if titleWidth+statusWidth+gaps > innerWidth {
		// Not enough room for both: the status is the expendable half.
		status, statusWidth = "", 0
		if titleWidth+2 > innerWidth {
			// Not even the title fits; draw a plain edge rather than a broken one.
			return edge(border.TopLeft) + edge(strings.Repeat(border.Top, innerWidth)) + edge(border.TopRight)
		}
	}

	fill := max(innerWidth-titleWidth-statusWidth-2, 0)

	var out strings.Builder
	out.WriteString(edge(border.TopLeft))
	out.WriteString(edge(border.Top))
	if title != "" {
		out.WriteString(title)
	}
	out.WriteString(edge(strings.Repeat(border.Top, fill)))
	if status != "" {
		out.WriteString(status)
	}
	out.WriteString(edge(border.Top))
	out.WriteString(edge(border.TopRight))
	return out.String()
}

// fitLines forces content into exactly height lines of exactly width cells, truncating what
// is too long and padding what is too short.
func fitLines(content string, width, height int) []string {
	raw := strings.Split(content, "\n")
	out := make([]string, 0, height)
	for i := range height {
		line := ""
		if i < len(raw) {
			line = raw[i]
		}
		out = append(out, fitLine(line, width))
	}
	return out
}

// fitLine truncates or pads a single line to exactly width cells, measuring in display cells
// so that styled text and wide glyphs are handled correctly.
func fitLine(line string, width int) string {
	actual := lipgloss.Width(line)
	switch {
	case actual == width:
		return line
	case actual < width:
		return line + strings.Repeat(" ", width-actual)
	default:
		// Truncate to width. lipgloss keeps the styling intact while cutting to a cell count.
		return lipgloss.NewStyle().MaxWidth(width).Render(line)
	}
}
