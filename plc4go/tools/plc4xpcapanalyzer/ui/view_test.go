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
	"time"

	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// View tests. Everything renders with the no-colour ASCII theme, so an assertion on the
// content is an assertion on characters and a width is a count of them.

// sizes are the three the layout has to work at. Eighty by twenty-four is the one the tools
// being replaced were catastrophically broken at, and sixty by twelve is the declared minimum.
var sizes = []tui.Size{
	{Width: 100, Height: 30},
	{Width: 80, Height: 24},
	{Width: 60, Height: 12},
}

// render draws a model at a size and returns its lines.
func render(model Model, size tui.Size) []string {
	return splitLines(send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height}).Render())
}

// TestEveryLineIsExactlyTheTerminalWidth is the invariant every other view assertion rests on.
// A line one cell too wide wraps, which pushes every row below it down one and eventually
// pushes the prompt off the bottom.
func TestEveryLineIsExactlyTheTerminalWidth(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2), okRecord(3))
	for _, size := range sizes {
		t.Run(size.String(), func(t *testing.T) {
			lines := render(model, size)
			require.Len(t, lines, size.Height, "the screen must be exactly as tall as the terminal")
			for i, line := range lines {
				assert.Equal(t, size.Width, lipgloss.Width(line), "line %d is %q", i, line)
			}
		})
	}
}

// TestThePromptIsNeverDroppedAndNeverClipped is the rule the responsive design was written
// around. The tview version added its command area to the grid with a zero row and column span
// below a hundred columns, so the input was not drawn at all and the user typed blind.
func TestThePromptIsNeverDroppedAndNeverClipped(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	for _, size := range append(sizes, tui.Size{Width: 60, Height: 12}, tui.Size{Width: 90, Height: 14}) {
		t.Run(size.String(), func(t *testing.T) {
			sized := send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height})
			sized.prompt.SetValue("analyze c-bus cbus-demo.pcap")
			lines := splitLines(sized.Render())

			// The prompt is the second-to-last row, above the help footer.
			require.GreaterOrEqual(t, len(lines), 2)
			prompt := lines[len(lines)-2]
			assert.Contains(t, prompt, "$", "the prompt row must carry the prompt label at %s", size)
			assert.Equal(t, size.Width, lipgloss.Width(prompt))
			assert.True(t, strings.Contains(prompt, "analyze"),
				"what the user typed has to be visible at %s, got %q", size, prompt)
		})
	}
}

// TestThePromptSurvivesATerminalBelowTheMinimum keeps the one invariant true even where the
// layout has given up on everything else.
func TestThePromptSurvivesATerminalBelowTheMinimum(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, tui.Size{Width: 50, Height: 10})
	model.prompt.SetValue("open")

	lines := splitLines(model.Render())
	require.NotEmpty(t, lines)
	assert.Contains(t, strings.Join(lines, "\n"), tui.TooSmallMessage())
	assert.Contains(t, strings.Join(lines, "\n"), "60x12 minimum")
	assert.Contains(t, lines[len(lines)-1], "open", "even here the prompt is drawn")
}

// TestTheStatusBarDegradesInOrder pins the shedding order. The drawn header is about ninety
// cells and does not fit at eighty; what goes first has to be the least useful part.
func TestTheStatusBarDegradesInOrder(t *testing.T) {
	state, _ := demoState(t)
	state.HostIP = "192.168.178.101"
	model := newTestModel(t, state, wide)

	at := func(width int) string {
		sized := send(model, tea.WindowSizeMsg{Width: width, Height: 30})
		return sized.statusLine(width)
	}

	full := at(120)
	assert.Contains(t, full, AppName)
	assert.Contains(t, full, AppVersion)
	assert.Contains(t, full, "DEMO")
	assert.Contains(t, full, "c-bus")
	assert.Contains(t, full, "client 192.168.178.101")
	assert.Contains(t, full, "pane ")

	eighty := at(80)
	assert.NotContains(t, eighty, "pane ", "the focused pane's name is the first thing shed")
	assert.Contains(t, eighty, "192.168.178.101")
	assert.Contains(t, eighty, "DEMO")

	sixty := at(60)
	assert.NotContains(t, sixty, "client ", "the label around the address goes before the address does")
	assert.Contains(t, sixty, "192.168.178.101")
	assert.Contains(t, sixty, "DEMO")

	forty := at(40)
	assert.Contains(t, forty, AppName)
	assert.Contains(t, forty, "DEMO", "the demo badge is never shed: taking a demo capture for a real one is the one mistake to prevent")

	for _, width := range []int{120, 100, 90, 80, 70, 60, 50, 40, 30, 20} {
		assert.LessOrEqual(t, lipgloss.Width(at(width)), width, "the status bar overflowed at %d", width)
	}
}

// TestTheDemoBadgeIsOnlyShownInDemoMode keeps the badge honest.
func TestTheDemoBadgeIsOnlyShownInDemoMode(t *testing.T) {
	state, _ := demoState(t)
	state.Demo = false
	model := newTestModel(t, state, wide)
	assert.NotContains(t, model.Render(), "DEMO")

	state.Demo = true
	assert.Contains(t, newTestModel(t, state, wide).Render(), "DEMO")
}

func TestTheWideLayoutDrawsEveryPane(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	screen := model.Render()

	assert.Contains(t, screen, "Captures", "the sidebar")
	assert.Contains(t, screen, "Packets", "the main pane's tab strip")
	assert.Contains(t, screen, "Findings")
	assert.Contains(t, screen, "Detail", "the detail pane sits beneath the main one")
	assert.Contains(t, screen, "Run ", "the run panel")
	assert.Contains(t, screen, "DRIVERS")
	assert.Contains(t, screen, "OPTIONS")
}

// TestTheCompactLayoutIsASingleColumnWithOverlays is the eighty-column story.
func TestTheCompactLayoutIsASingleColumnWithOverlays(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	model = send(model, tea.WindowSizeMsg{Width: compact.Width, Height: compact.Height})
	require.Equal(t, tui.ModeCompact, model.layout.Mode)

	screen := model.Render()
	assert.Contains(t, screen, "Packets")
	assert.NotContains(t, screen, "DRIVERS", "the sidebar is an overlay here, not a column")

	opened := send(model, modKey('1', tea.ModAlt))
	assert.Contains(t, opened.Render(), "DRIVERS", "and alt+1 reaches it")

	detail := send(model, modKey('3', tea.ModAlt))
	assert.Contains(t, detail.Render(), "Detail")
}

// TestAnOverlayFillsTheBodyWithoutOverflowing keeps the compact-mode overlays honest at the
// sizes the design was never drawn at.
func TestAnOverlayFillsTheBodyWithoutOverflowing(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	for _, size := range []tui.Size{{Width: 80, Height: 24}, {Width: 60, Height: 12}} {
		for _, overlay := range []struct {
			name string
			key  tea.KeyPressMsg
		}{{"captures", modKey('1', tea.ModAlt)}, {"detail", modKey('3', tea.ModAlt)}} {
			t.Run(size.String()+"/"+overlay.name, func(t *testing.T) {
				sized := send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height})
				sized = send(sized, overlay.key)
				lines := splitLines(sized.Render())
				require.Len(t, lines, size.Height)
				for i, line := range lines {
					assert.Equal(t, size.Width, lipgloss.Width(line), "line %d is %q", i, line)
				}
				assert.Contains(t, lines[len(lines)-2], "$", "the prompt keeps its row under an overlay")
			})
		}
	}
}

// TestNoPaneIsFocusableWithoutBeingDrawn is the invariant the geometry exists to hold. A pane
// tab can reach but the renderer skips is a keyboard black hole.
func TestNoPaneIsFocusableWithoutBeingDrawn(t *testing.T) {
	model := analysed(t, okRecord(1))
	for _, size := range append(sizes, tui.Size{Width: 90, Height: 14}, tui.Size{Width: 100, Height: 16}) {
		t.Run(size.String(), func(t *testing.T) {
			sized := send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height})
			geometry := sized.geometry()
			assert.Equal(t, geometry.SidebarWidth > 0, sized.paneVisible(paneCaptures))
			assert.Equal(t, geometry.DetailHeight > 0, sized.paneVisible(paneDetail))
			assert.Equal(t, geometry.RunHeight > 0, sized.paneVisible(paneRun))
		})
	}
}

func TestTheFocusedPaneIsVisiblyDistinct(t *testing.T) {
	model := analysed(t, okRecord(1))
	// Colour is off in these tests, so focus has to be visible in the characters themselves.
	// tui.Pane draws the focused pane with the same border, so the difference has to come from
	// somewhere the reader can see: the selection marker in the focused pane's rows.
	atPrompt := send(model, tea.WindowSizeMsg{Width: wide.Width, Height: wide.Height})
	inPane := send(atPrompt, modKey(tea.KeyTab, tea.ModShift))

	assert.NotEqual(t, atPrompt.Render(), inPane.Render(),
		"moving the keyboard into a pane has to change what is drawn, or the user cannot tell where it went")
	assert.Contains(t, inPane.Render(), tui.ASCIIGlyphs().Selected)
}

func TestTheTabStripMarksTheActiveTab(t *testing.T) {
	model := analysed(t, okRecord(1))
	marker := tui.ASCIIGlyphs().Selected

	assert.Contains(t, model.tabStrip(), marker+"Packets")
	assert.Equal(t, tabLog, send(send(model, modKey(tea.KeyTab, tea.ModShift)), charKey(']')).tab)
	assert.Contains(t, send(send(model, modKey(tea.KeyTab, tea.ModShift)), charKey(']')).tabStrip(), marker+"Log")
}

func TestTheMainPaneCarriesTheCountAndTheIssues(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2), okRecord(3))
	assert.Contains(t, model.Render(), "3 | 1 issues")

	clean := analysed(t, okRecord(1))
	assert.Contains(t, clean.Render(), "no issues")
}

// TestEmptyPanesNameTheCommandThatFillsThem is the no-dead-end rule. An empty pane that only
// says "empty" leaves a first-time user with nowhere to go.
func TestEmptyPanesNameTheCommandThatFillsThem(t *testing.T) {
	pinGlobals(t)
	state := NewState(t.TempDir(), NewConfig())
	model := newTestModel(t, state, wide)
	screen := model.Render()

	assert.Contains(t, screen, "no captures open")
	assert.Contains(t, screen, "open <pcapfile>")
	assert.Contains(t, screen, "no packets analysed yet")
	assert.Contains(t, screen, "analyze c-bus <pcapfile>")
}

func TestTheEmptyPacketPaneUsesTheOpenCapture(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	assert.Contains(t, model.Render(), "analyze c-bus "+DemoCaptureName)
}

func TestTheEmptyLogSaysWhatWillAppearThere(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey(']'))
	require.Equal(t, tabLog, model.tab)
	assert.Contains(t, model.Render(), "nothing logged yet")
}

func TestTheDetailPaneSaysHowToFillIt(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	assert.Contains(t, model.Render(), "no packet selected")
}

// TestTheDetailPaneShowsTheByteDifference is the most valuable thing on the screen, so it is
// pinned in detail.
func TestTheDetailPaneShowsTheByteDifference(t *testing.T) {
	model := analysed(t, brokenRecord(1))
	screen := model.Render()

	assert.Contains(t, screen, "original")
	assert.Contains(t, screen, "reserialized")
	assert.Contains(t, screen, "4 bytes differ")
	assert.Contains(t, screen, "first difference at offset 2")
	assert.Contains(t, screen, "Detail | packet 1")

	// The caret has to line up with the differing byte in both dumps.
	lines := splitLines(screen)
	var caret, first, second string
	for i, line := range lines {
		if strings.Contains(line, "first difference at offset") {
			caret, first, second = line, lines[i-2], lines[i-1]
			break
		}
	}
	require.NotEmpty(t, caret)
	column := strings.Index(caret, "^^")
	require.Positive(t, column)
	assert.NotEqual(t, first[column:column+2], second[column:column+2],
		"the caret must point at bytes that actually differ:\n%s\n%s\n%s", first, second, caret)
}

// TestTheCaretSurvivesAShortDetailPane pins which lines go when the detail column bottoms out
// at its six-row minimum, which it does at every height from twenty to twenty-six once a run
// panel is on screen — a hundred by twenty-four included. Padding kept the HEAD of the block,
// so what got dropped was the caret: the offset of the first differing byte, which is the
// entire finding.
func TestTheCaretSurvivesAShortDetailPane(t *testing.T) {
	model := analysed(t, brokenRecord(1))
	for _, height := range []int{20, 22, 24, 26, 30, 40} {
		t.Run(itoa(height), func(t *testing.T) {
			sized := send(model, tea.WindowSizeMsg{Width: 100, Height: height})
			require.Positive(t, sized.geometry().DetailHeight, "the detail pane has to be drawn at all")
			assert.Contains(t, sized.Render(), "first difference at offset 2")
		})
	}
}

func TestTheDetailPaneSaysWhenTheBytesMatch(t *testing.T) {
	model := analysed(t, okRecord(1))
	assert.Contains(t, model.Render(), "identical")
}

func TestTheDetailPaneSwitchesToBytesAndTree(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))

	bytesView := send(model, charKey('b')).Render()
	assert.Contains(t, bytesView, "4 bytes")
	assert.Contains(t, bytesView, "00000000")

	treeView := send(model, charKey('t')).Render()
	assert.Contains(t, treeView, "CBusMessageToServer")
}

func TestTheRunPanelShowsThePhasesTheBarAndTheCounters(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})
	model = send(model, progressMsg{Update: progress2(3741, 6128, PhaseAnalyze)})
	model = send(model, recordsMsg{Records: []Record{okRecord(1), brokenRecord(2)}})

	screen := model.Render()
	assert.Contains(t, screen, "Run | analyze c-bus "+DemoCaptureName)
	for _, phase := range Phases {
		assert.Contains(t, screen, phase, "the breadcrumb must name every phase")
	}
	assert.Contains(t, screen, "3741 / 6128 pkt")
	assert.Contains(t, screen, "61%")
	assert.Contains(t, screen, strings.Repeat(tui.ASCIIGlyphs().ProgressFull, 4), "the bar has to be drawn")
	assert.Contains(t, screen, "parse-fail")
	assert.Contains(t, screen, "compare-fail")
	assert.Contains(t, screen, "esc abort")
}

func TestTheRunPanelOffersARerunOnceItIsFinished(t *testing.T) {
	model := analysed(t, okRecord(1))
	assert.Contains(t, model.Render(), "enter re-run")
}

// TestTheRunPanelSurvivesANarrowTerminal keeps the drawn panel from overflowing at the sizes
// it was never sketched at.
func TestTheRunPanelSurvivesANarrowTerminal(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	model = send(model, startAnalysisMsg{Request: state.RequestFor(demo.Protocol, demo.Path)})
	model = send(model, progressMsg{Update: progress2(50, 100, PhaseFilter)})

	for _, size := range sizes {
		t.Run(size.String(), func(t *testing.T) {
			lines := render(model, size)
			joined := strings.Join(lines, "\n")
			assert.Contains(t, joined, "50 / 100 pkt", "the packet count must never be the thing that gets clipped")
			for i, line := range lines {
				assert.Equal(t, size.Width, lipgloss.Width(line), "line %d", i)
			}
		})
	}
}

func TestTheProgressBarIsExactlyTheWidthAsked(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	for _, width := range []int{1, 5, 8, 20, 60} {
		for _, fraction := range []float64{0, 0.5, 1} {
			assert.Equal(t, width, lipgloss.Width(model.progressBar(width, fraction)),
				"width %d fraction %v", width, fraction)
		}
	}
	assert.Empty(t, model.progressBar(0, 0.5))
}

func TestTheToastLineCarriesTheDismissHint(t *testing.T) {
	model := analysed(t, okRecord(1))
	require.NotEmpty(t, model.toast)
	lines := splitLines(model.Render())
	toast := lines[len(lines)-3]
	assert.Contains(t, toast, model.toast)
	assert.Contains(t, toast, "esc dismiss")
}

func TestTheFooterAlwaysKeepsTheWayOut(t *testing.T) {
	model := analysed(t, okRecord(1))
	for _, size := range sizes {
		t.Run(size.String(), func(t *testing.T) {
			lines := render(model, size)
			footer := lines[len(lines)-1]
			assert.Contains(t, footer, "quit", "whatever else is shed, the user keeps the way out")
			assert.Equal(t, size.Width, lipgloss.Width(footer))
		})
	}
}

func TestTheExpandedHelpIsDrawnOverTheBodyRatherThanTakingThePromptsRow(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, tea.KeyPressMsg{Code: tea.KeyF1})
	lines := splitLines(model.Render())

	require.Len(t, lines, wide.Height)
	assert.Contains(t, lines[len(lines)-2], "$", "the prompt keeps its row while the help is up")
	for i, line := range lines {
		assert.Equal(t, wide.Width, lipgloss.Width(line), "line %d", i)
	}
}

func TestTheCompletionListIsDrawnOverTheBody(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	// Editing the line opens the list on its own; tab would accept the highlighted candidate
	// and close it again, which is exactly what tab is for.
	for _, r := range "analyz" {
		model = send(model, charKey(r))
	}
	require.True(t, model.prompt.CompletionOpen())

	lines := splitLines(model.Render())
	require.Len(t, lines, wide.Height)
	assert.Contains(t, strings.Join(lines, "\n"), "analyze")
	assert.Contains(t, lines[len(lines)-2], "$")
	for i, line := range lines {
		assert.Equal(t, wide.Width, lipgloss.Width(line), "line %d", i)
	}
}

func TestThePacketTableShedsColumnsRatherThanOverflowing(t *testing.T) {
	assert.Len(t, packetLayout(120), 6, "everything fits at 120")
	narrow := packetLayout(46)
	names := make([]string, 0, len(narrow))
	for _, column := range narrow {
		names = append(names, column.title)
	}
	assert.Contains(t, names, "no.")
	assert.Contains(t, names, "message")
	assert.Contains(t, names, "verdict")
	assert.NotContains(t, names, "proto", "the protocol is the same for every row, so it goes first")
}

func TestFormatOffset(t *testing.T) {
	for duration, want := range map[time.Duration]string{
		0:                                     "00:00.000",
		time.Millisecond:                      "00:00.001",
		1500 * time.Millisecond:               "00:01.500",
		90*time.Second + 250*time.Millisecond: "01:30.250",
		-time.Second:                          "00:00.000",
	} {
		assert.Equal(t, want, formatOffset(duration), "duration %v", duration)
	}
}

func TestShortenMarksThatItTruncated(t *testing.T) {
	assert.Equal(t, "abc", shorten("abc", 3, "."))
	assert.Equal(t, "ab.", shorten("abcd", 3, "."))
	assert.Empty(t, shorten("abc", 0, "."))
}

func TestSidebarRowsCoverCapturesDriversAndOptions(t *testing.T) {
	model := analysed(t, okRecord(1))
	kinds := map[sidebarKind]int{}
	for _, row := range model.sidebarRows() {
		kinds[row.kind]++
	}
	assert.Positive(t, kinds[sidebarCapture])
	assert.Equal(t, len(DriverNames), kinds[sidebarDriver])
	assert.Positive(t, kinds[sidebarOption])
	assert.Positive(t, kinds[sidebarHeader])
}

// progress2 builds a progress update, kept short because the tests above use several.
func progress2(current, total int, phase string) progress.Update {
	return progress.Update{Current: current, Total: total, Description: phase}
}

// TestTheCursorMovesInsideTheWindowRatherThanDraggingTheList is the regression test for a
// scroll window derived from the cursor. Deriving it pinned the selection to the bottom
// visible row, so every press of up or down scrolled the whole list by one and the highlighted
// packet was never anywhere else.
func TestTheCursorMovesInsideTheWindowRatherThanDraggingTheList(t *testing.T) {
	// Enough packets to overflow the pane: with a list that fits, the window never scrolls and
	// a derived window is indistinguishable from a stored one.
	records := make([]Record, 0, 60)
	for i := 1; i <= 60; i++ {
		records = append(records, okRecord(i))
	}
	model := analysed(t, records...)
	require.Greater(t, len(model.visible), 40, "the fixture must not fit in the pane")

	// Leave the prompt first: the bare pane-jump keys are pane-scoped and inert while the
	// prompt has the keyboard, which is the scoping rule working as intended.
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('2'))
	require.Equal(t, paneMain, model.activePane(), "the packet pane must have the keyboard")
	startCursor, startTop := model.cursor, model.packetTop

	model = send(model, charKey('k'))
	assert.Equal(t, startCursor-1, model.cursor, "up must move the cursor")
	assert.Equal(t, startTop, model.packetTop,
		"the window must stay put while the cursor is still inside it; a derived window would move with it")

	model = send(model, charKey('k'))
	assert.Equal(t, startCursor-2, model.cursor)
	assert.Equal(t, startTop, model.packetTop)
}

// TestTheWindowScrollsOnlyWhenTheCursorReachesAnEdge is the other half of the rule.
func TestTheWindowScrollsOnlyWhenTheCursorReachesAnEdge(t *testing.T) {
	records := make([]Record, 0, 40)
	for i := 1; i <= 40; i++ {
		records = append(records, okRecord(i))
	}
	model := analysed(t, records...)
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('2'))
	require.Equal(t, paneMain, model.activePane())

	// Walk to the top; the window has to follow once the cursor passes its first row.
	for range len(model.visible) {
		model = send(model, charKey('k'))
	}
	assert.Zero(t, model.cursor, "walking up far enough must reach the first packet")
	assert.Zero(t, model.packetTop, "and the window must have followed it to the top")

	// Walk back down; the window follows again at the far edge.
	for range len(model.visible) {
		model = send(model, charKey('j'))
	}
	assert.Equal(t, len(model.visible)-1, model.cursor)
	assert.Positive(t, model.packetTop, "the window must have scrolled to keep the last packet visible")
}
