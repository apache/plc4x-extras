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

import "strconv"

// Responsive behaviour for the terminal tools.
//
// This exists because of a specific, verified defect in the UIs being replaced: below 100
// columns they added the command area to their grid with a zero row and column span, which
// the layout engine silently discards, so the input field was never drawn and the user typed
// blind. The rule that follows from that is the one invariant here: whatever else is dropped
// as space runs out, the prompt is never dropped and never clipped.

// Breakpoints. These are display cells.
const (
	// CompactWidth is the width below which the layout collapses to a single column.
	CompactWidth = 90
	// CrampedHeight is the height below which optional panes start being dropped.
	CrampedHeight = 14
	// MinWidth and MinHeight are the smallest terminal the tools attempt to draw in.
	MinWidth  = 60
	MinHeight = 12
)

// Mode is the layout regime a terminal size falls into.
type Mode int

const (
	// ModeWide is the full multi-column layout.
	ModeWide Mode = iota
	// ModeCompact is a single column, with the sidebar reachable as an overlay.
	ModeCompact
	// ModeTooSmall is a terminal too small to draw anything useful in.
	ModeTooSmall
)

// String makes Mode readable in test failures and logs.
func (m Mode) String() string {
	switch m {
	case ModeWide:
		return "wide"
	case ModeCompact:
		return "compact"
	case ModeTooSmall:
		return "too-small"
	default:
		return "unknown"
	}
}

// Size is a terminal size in cells.
type Size struct {
	Width  int
	Height int
}

// String renders a size as WxH, for subtest names and log lines.
func (s Size) String() string {
	return strconv.Itoa(s.Width) + "x" + strconv.Itoa(s.Height)
}

// Layout is the resolved geometry for one terminal size.
type Layout struct {
	Size Size
	Mode Mode

	// SidebarWidth is the width of the left column, zero in compact mode.
	SidebarWidth int
	// MainWidth is the width of the primary column.
	MainWidth int
	// DetailWidth is the width of the trailing detail column, zero when it is not shown.
	DetailWidth int

	// ShowSidebar, ShowDetail and ShowLog say which optional regions fit.
	ShowSidebar bool
	ShowDetail  bool
	ShowLog     bool

	// BodyHeight is the height available to the panes, after the status bar and the pinned
	// prompt rows have been reserved.
	BodyHeight int
	// LogHeight is the height of the log drawer, zero when it is not shown.
	LogHeight int
}

// Rows that are always reserved, in every mode: the status bar, the toast line, the prompt
// and the help footer. These are the rows the prompt lives among, so they come off the top of
// the budget rather than being squeezed out of it.
const (
	statusRows = 1
	toastRows  = 1
	promptRows = 1
	helpRows   = 1

	// ReservedRows is the total chrome the body never gets to use.
	ReservedRows = statusRows + toastRows + promptRows + helpRows
)

// Compute resolves the layout for a terminal size.
func Compute(size Size) Layout {
	layout := Layout{Size: size}

	if size.Width < MinWidth || size.Height < MinHeight {
		layout.Mode = ModeTooSmall
		return layout
	}

	layout.BodyHeight = size.Height - ReservedRows
	if layout.BodyHeight < 1 {
		layout.Mode = ModeTooSmall
		return layout
	}

	if size.Width < CompactWidth {
		layout.Mode = ModeCompact
		layout.MainWidth = size.Width
		// In compact mode the sidebar and the detail pane become overlays reached by a key,
		// rather than columns competing for width that is not there.
		layout.ShowSidebar = false
		layout.ShowDetail = false
		// The log drawer is the first thing to go when rows are scarce.
		layout.ShowLog = size.Height >= CrampedHeight
		layout.LogHeight = logHeightFor(layout)
		layout.BodyHeight -= layout.LogHeight
		return layout
	}

	layout.Mode = ModeWide
	layout.ShowSidebar = true
	// Detail is the second thing to go: the main list stays useful without it.
	layout.ShowDetail = size.Height >= CrampedHeight
	layout.ShowLog = size.Height >= CrampedHeight
	layout.LogHeight = logHeightFor(layout)
	layout.BodyHeight -= layout.LogHeight

	layout.SidebarWidth = clamp(size.Width/4, 22, 34)
	remaining := size.Width - layout.SidebarWidth
	if layout.ShowDetail {
		// Give the detail column a little under half of what is left, so the primary list
		// keeps the larger share.
		layout.DetailWidth = clamp(remaining*45/100, 30, 52)
		layout.MainWidth = remaining - layout.DetailWidth
	} else {
		layout.MainWidth = remaining
	}
	return layout
}

// logHeightFor sizes the log drawer as a fraction of the terminal, within sane bounds.
//
// The cap used to be eight rows at any height, which on a tall terminal was the wrong way
// round: the panes were handed thirty-eight rows they could not fill while the log -- the one
// region whose content actually grows -- stayed at eight and scrolled. A quarter of the screen
// up to sixteen rows keeps a short terminal's panes intact while letting a tall one put its
// spare rows where there is something to read.
func logHeightFor(layout Layout) int {
	if !layout.ShowLog {
		return 0
	}
	return clamp(layout.Size.Height/4, 4, 16)
}

// TooSmallMessage is what to render when the terminal cannot be drawn in. It states the
// requirement rather than just refusing, so the user knows what to do.
func TooSmallMessage() string {
	return "terminal too small"
}

// clamp constrains value to [lowest, highest].
func clamp(value, lowest, highest int) int {
	if value < lowest {
		return lowest
	}
	if value > highest {
		return highest
	}
	return value
}
