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
	"context"
	"strings"
	"testing"
	"time"

	tea "charm.land/bubbletea/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// These drive Update with typed messages. No terminal is involved and nothing sleeps.

// wide and compact are the two sizes the model behaves differently at.
var (
	wide    = tui.Size{Width: 100, Height: 30}
	compact = tui.Size{Width: 80, Height: 24}
	minimal = tui.Size{Width: 60, Height: 12}
)

// fakeResult builds an analysis outcome without reading a capture, for the tests that are
// about the model rather than about the analysis.
func fakeResult(request Request, records ...Record) Result {
	result := Result{Request: request, Records: records}
	for _, record := range records {
		result.Counters = fold(result.Counters, record)
	}
	return result
}

// okRecord and brokenRecord are the two kinds of row the table has to draw.
func okRecord(number int) Record {
	return Record{
		Number:       number,
		Offset:       time.Duration(number) * time.Millisecond,
		Direction:    DirectionRequest,
		Protocol:     "c-bus",
		Summary:      "CBusMessageToServer",
		Verdict:      VerdictOK,
		Original:     []byte("~~~\r"),
		Reserialized: []byte("~~~\r"),
		Tree:         "CBusMessageToServer\n  header\n  request",
		DiffOffset:   -1,
	}
}

func brokenRecord(number int) Record {
	return Record{
		Number:       number,
		Offset:       time.Duration(number) * time.Millisecond,
		Direction:    DirectionResponse,
		Protocol:     "c-bus",
		Summary:      "DirectCommandAccess",
		Verdict:      VerdictBytesDiffer,
		Reason:       "4 bytes differ",
		Original:     []byte("\\3138000000AA3A\r"),
		Reserialized: []byte("\\30380000\r"),
		DiffOffset:   2,
	}
}

// analysed returns a model at wide size with a finished run in it.
func analysed(t *testing.T, records ...Record) Model {
	t.Helper()
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})
	return send(model, analysisDoneMsg{Result: fakeResult(request, records...)})
}

func TestWindowSizeResolvesTheLayout(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	assert.Equal(t, tui.ModeWide, model.layout.Mode)

	model = send(model, tea.WindowSizeMsg{Width: compact.Width, Height: compact.Height})
	assert.Equal(t, tui.ModeCompact, model.layout.Mode)

	model = send(model, tea.WindowSizeMsg{Width: 40, Height: 10})
	assert.Equal(t, tui.ModeTooSmall, model.layout.Mode)
}

func TestBackgroundColourRebuildsTheTheme(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	require.True(t, model.theme.IsDark())

	model = send(model, tea.BackgroundColorMsg{Color: lightBackground{}})
	assert.False(t, model.theme.IsDark(), "a light terminal must get the light palette")

	model = send(model, tea.BackgroundColorMsg{Color: darkBackground{}})
	assert.True(t, model.theme.IsDark())
}

// lightBackground and darkBackground stand in for what a terminal reports.
type lightBackground struct{}

func (lightBackground) RGBA() (r, g, b, a uint32) { return 0xffff, 0xffff, 0xffff, 0xffff }

type darkBackground struct{}

func (darkBackground) RGBA() (r, g, b, a uint32) { return 0, 0, 0, 0xffff }

// TestBareKeysAreInertAtThePrompt is the rule the whole scoping mechanism exists for: typing
// the word "analyze" must type it, not fire the a, n, l, y, z and e pane actions.
func TestBareKeysAreInertAtThePrompt(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	require.Equal(t, tui.FocusPrompt, model.focus)

	for _, line := range []string{"analyze", "extract", "quit", "log get", "wify"} {
		typed := model
		for _, r := range line {
			typed = send(typed, charKey(r))
		}
		assert.Equal(t, line, typed.prompt.Value(), "typing %q must put %q in the prompt", line, line)
		assert.Equal(t, tui.FocusPrompt, typed.focus, "typing %q must not move the keyboard", line)
		assert.False(t, typed.quitting, "typing %q must not quit", line)
		assert.Equal(t, tabPackets, typed.tab, "typing %q must not switch tabs", line)
	}
}

// TestQuitBindingsQuit covers both ways out, and that each of them asks first: q and ctrl+c
// are easy to hit by accident, and the answer to a mistaken one should not be a closed capture.
func TestQuitBindingsQuit(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	asked, cmd := sendWithCmd(model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	require.True(t, asked.confirmQuit, "ctrl+c asks before ending the session")
	assert.False(t, asked.quitting, "and does not end it until answered")
	assert.Nil(t, cmd)

	quitted, cmd := sendWithCmd(asked, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	assert.True(t, quitted.quitting, "a second ctrl+c confirms")
	require.NotNil(t, cmd)

	panes := send(model, modKey(tea.KeyTab, tea.ModShift))
	require.Equal(t, tui.FocusPane, panes.focus)
	asked = send(panes, charKey('q'))
	require.True(t, asked.confirmQuit, "q asks too, while a pane has the keyboard")
	quitted = send(asked, charKey('y'))
	assert.True(t, quitted.quitting, "y confirms")
}

// TestAnyOtherKeyKeepsTheSession is the point of asking: the question has to be refusable, and
// the key that refuses it must not also do whatever it would normally have done.
func TestAnyOtherKeyKeepsTheSession(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	for _, refusal := range []tea.KeyPressMsg{
		namedKey(tea.KeyEscape),
		charKey('n'),
		charKey('a'),
		charKey('/'),
		modKey(tea.KeyTab, tea.ModShift),
	} {
		asked := send(model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
		require.True(t, asked.confirmQuit)

		stayed := send(asked, refusal)
		assert.False(t, stayed.quitting, "%s must not quit", refusal.String())
		assert.False(t, stayed.confirmQuit, "%s takes the question down", refusal.String())
		// Swallowed, not passed on: 'a' would have started an analysis and '/' a filter.
		assert.False(t, stayed.filtering, "%s must not also do its usual job", refusal.String())
		assert.False(t, stayed.run.active, "%s must not also do its usual job", refusal.String())
		assert.Equal(t, model.focus, stayed.focus, "%s must not also move the keyboard", refusal.String())
	}
}

func TestShiftTabMovesToThePanesAndBackToThePrompt(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	assert.Equal(t, tui.FocusPane, model.focus)

	model = send(model, charKey(':'))
	assert.Equal(t, tui.FocusPrompt, model.focus, "one binding always leads back to the prompt")
}

func TestTabCyclesTheVisiblePanesOnly(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	require.Equal(t, tui.FocusPane, model.focus)

	visited := []pane{model.pane}
	for range len(model.visiblePanes()) {
		model = send(model, namedKey(tea.KeyTab))
		visited = append(visited, model.pane)
	}
	for _, visited := range visited {
		assert.True(t, model.paneVisible(visited), "tab landed on %s, which the layout does not show", visited)
	}
	assert.Equal(t, visited[0], visited[len(visited)-1], "tab must cycle back round")
}

func TestPaneJumpOpensAnOverlayForAPaneTheLayoutHasNoRoomFor(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, tea.WindowSizeMsg{Width: compact.Width, Height: compact.Height})
	require.Equal(t, tui.ModeCompact, model.layout.Mode)
	require.False(t, model.paneVisible(paneCaptures))

	model = send(model, modKey('1', tea.ModAlt))
	assert.Equal(t, paneCaptures, model.overlay, "alt+1 must not be a dead key just because there is no column")
	assert.Equal(t, paneCaptures, model.activePane())

	model = send(model, namedKey(tea.KeyEscape))
	assert.Equal(t, paneCount, model.overlay, "esc closes the overlay")
}

func TestLeavingCompactModeClosesTheOverlay(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, tea.WindowSizeMsg{Width: compact.Width, Height: compact.Height})
	model = send(model, modKey('3', tea.ModAlt))
	require.Equal(t, paneDetail, model.overlay)

	model = send(model, tea.WindowSizeMsg{Width: wide.Width, Height: wide.Height})
	assert.Equal(t, paneCount, model.overlay, "a pane that has a column again must not stay stuck as an overlay")
	assert.Equal(t, paneDetail, model.pane, "the focus follows it into the column")
}

// TestAResizeNeverLeavesTheKeyboardOnAnUndrawnPane is the other half of
// TestNoPaneIsFocusableWithoutBeingDrawn, which only pins that paneVisible agrees with the
// geometry. Dragging a window from wide to compact with the detail pane focused took its
// column away and left the focus on it: nothing rendered as focused and up, down and enter all
// went somewhere the user could not see.
func TestAResizeNeverLeavesTheKeyboardOnAnUndrawnPane(t *testing.T) {
	model := analysed(t, okRecord(1), okRecord(2))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, modKey('3', tea.ModAlt))
	require.Equal(t, paneDetail, model.pane)
	require.True(t, model.paneVisible(paneDetail))

	for _, size := range []tui.Size{compact, minimal, {Width: 90, Height: 14}, wide} {
		t.Run(size.String(), func(t *testing.T) {
			resized := send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height})
			assert.True(t, resized.paneVisible(resized.pane),
				"the keyboard is on %s, which the layout at %s does not draw", resized.pane, size)
		})
	}
}

func TestBracketsSwitchTheTabs(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	require.Equal(t, tabPackets, model.tab)

	model = send(model, charKey(']'))
	assert.Equal(t, tabLog, model.tab)
	model = send(model, charKey(']'))
	assert.Equal(t, tabFindings, model.tab)
	assert.Len(t, model.visible, 1, "the findings tab shows only the records that are defects")

	model = send(model, charKey('['))
	assert.Equal(t, tabLog, model.tab)
}

func TestDetailViewKeys(t *testing.T) {
	model := analysed(t, brokenRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	require.Equal(t, detailDiff, model.detail, "the byte comparison is the default because it is the point of the tool")

	assert.Equal(t, detailBytes, send(model, charKey('b')).detail)
	assert.Equal(t, detailTree, send(model, charKey('t')).detail)
	assert.Equal(t, detailDiff, send(send(model, charKey('t')), charKey('d')).detail)
}

// TestTheDetailPaneCannotBeScrolledPastItsContent pins the clamp. The offset used to be held
// against an arbitrary ceiling, so a held-down cursor key ran it into the thousands: the pane
// still drew its last line, but getting back to the top took one press for every press spent
// overshooting, and in the diff view — which does not scroll at all — the overshoot was
// invisible until the bytes view inherited it.
func TestTheDetailPaneCannotBeScrolledPastItsContent(t *testing.T) {
	model := analysed(t, brokenRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, modKey('3', tea.ModAlt))

	diff := model
	for range 40 {
		diff = send(diff, charKey('j'))
	}
	assert.Zero(t, diff.detailTop, "the diff view draws a fixed block, so there is nowhere to scroll to")

	bytesView := send(model, charKey('b'))
	rows := len(HexDumpLines(brokenRecord(1).Original, hexBytesPerRow, 1<<16))
	for range 40 {
		bytesView = send(bytesView, charKey('j'))
	}
	assert.Equal(t, rows-1, bytesView.detailTop, "the hex dump stops at its last row")

	// And one press of up has to move, rather than paying off an overshoot.
	assert.Equal(t, rows-2, send(bytesView, charKey('k')).detailTop)
}

// TestAPageIsTheHeightOfTheDrawnPane keeps the page keys measured against what is on screen.
// tui.Layout.BodyHeight reserves rows for a log drawer, and this tool draws the log as a tab
// of the main pane instead, so taking the page size from there scrolled short.
func TestAPageIsTheHeightOfTheDrawnPane(t *testing.T) {
	model := analysed(t, okRecord(1))
	for _, size := range sizes {
		t.Run(size.String(), func(t *testing.T) {
			sized := send(model, tea.WindowSizeMsg{Width: size.Width, Height: size.Height})
			geometry := sized.geometry()
			mainHeight := geometry.PaneHeight - geometry.DetailHeight
			assert.LessOrEqual(t, sized.pageSize(), geometry.PaneHeight,
				"a page must not be taller than the pane area it scrolls")
			assert.GreaterOrEqual(t, sized.pageSize(), mainHeight-2,
				"nor shorter than the rows the main pane actually draws")
		})
	}
}

// TestQuitCancelsAnInFlightRun keeps the analysis goroutine from outliving the program. It
// publishes each record with a blocking send guarded only by its own context, so quitting
// without cancelling parked it on a send nothing would ever drain.
func TestQuitCancelsAnInFlightRun(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	observed := make(chan error, 1)
	model.analyze = func(ctx context.Context, request Request, _ Sink) Result {
		<-ctx.Done()
		observed <- ctx.Err()
		return Result{Request: request, Aborted: true}
	}
	model, cmd := sendWithCmd(model, startAnalysisMsg{Request: state.RequestFor(demo.Protocol, demo.Path)})
	require.True(t, model.run.active)

	// The run is one member of a batch, and calling a batched command only hands its members
	// back. Starting them here is what actually puts the analysis on its own goroutine.
	batch, ok := cmd().(tea.BatchMsg)
	require.True(t, ok, "startAnalysis has to batch the run with the spinner tick")
	for _, batched := range batch {
		go func() { batched() }()
	}

	// ctrl+c with a run in flight stops the run rather than the session: that is what the
	// keystroke is for, and ending the session would throw away the records already collected.
	model = send(model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	require.False(t, model.quitting, "ctrl+c during a run aborts the run, not the session")
	require.False(t, model.confirmQuit, "and does not ask to quit either")

	select {
	case err := <-observed:
		assert.ErrorIs(t, err, context.Canceled)
	case <-time.After(3 * time.Second):
		t.Fatal("ctrl+c left the analysis running")
	}
}

func TestProgressAdvancesTheBar(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})
	require.True(t, model.run.active)

	model = send(model, progressMsg{Update: progress.Update{Current: 0, Total: 0, Description: PhaseIndex}})
	assert.Equal(t, PhaseIndex, model.run.phase)

	model = send(model, progressMsg{Update: progress.Update{Current: 30, Total: 120, Description: PhaseAnalyze}})
	assert.Equal(t, PhaseAnalyze, model.run.phase)
	assert.Equal(t, 30, model.run.current)
	assert.Equal(t, 120, model.run.total)
	assert.Contains(t, model.Render(), "25%", "the bar has to show the fraction it was given")
}

// TestProgressReArmsItself is the whole streaming contract: one value read, one message
// returned, and the command re-issued. Losing the re-arm freezes the bar after one frame.
func TestProgressReArmsItself(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	_, cmd := sendWithCmd(model, progressMsg{Update: progress.Update{Current: 1, Total: 2}})
	require.NotNil(t, cmd, "handling a progress frame must re-arm the drain")

	model.progressCh <- progress.Update{Current: 2, Total: 2}
	msg := cmd()
	assert.Equal(t, progressMsg{Update: progress.Update{Current: 2, Total: 2}}, msg)
}

func TestRecordsStreamIntoTheTableWhileTheRunIsInFlight(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})

	model = send(model, recordsMsg{Records: []Record{okRecord(1), brokenRecord(2)}})
	assert.Len(t, model.packets, 2)
	assert.Len(t, model.visible, 2)
	assert.Equal(t, 1, model.run.counters.CompareFail, "the counters have to move while the run is in flight")
	assert.Equal(t, 2, model.run.counters.Parsed,
		"a packet that parsed and then failed the byte comparison still parsed")
}

func TestRecordsArriveAfterTheRunAreIgnored(t *testing.T) {
	model := analysed(t, okRecord(1))
	require.False(t, model.run.active)

	model = send(model, recordsMsg{Records: []Record{okRecord(99)}})
	assert.Len(t, model.packets, 1, "a batch that overtook the result must not double the table")
}

func TestAnalysisDoneReplacesTheTableWithTheAuthoritativeRecords(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})
	model = send(model, recordsMsg{Records: []Record{okRecord(1)}})

	result := fakeResult(request, okRecord(1), brokenRecord(2), okRecord(3))
	model = send(model, analysisDoneMsg{Result: result})

	assert.Equal(t, result.Records, model.packets)
	assert.Equal(t, result.Counters, model.run.counters)
	assert.False(t, model.run.active)
	assert.False(t, model.prompt.Busy(), "the prompt's in-flight indication has to stop with the run")
	assert.Contains(t, model.toast, "1 issues")

	capture, ok := state.CurrentCapture()
	require.True(t, ok)
	assert.Equal(t, 3, capture.Packets, "the sidebar's packet count comes from the finished run")
	assert.Equal(t, 1, capture.Issues)
}

func TestAnalysisFailureIsReportedRatherThanSwallowed(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})

	result := Result{Request: request, Err: assertError{}}
	model = send(model, analysisDoneMsg{Result: result})

	assert.True(t, model.toastErr)
	assert.Contains(t, model.toast, "analyze failed")
	assert.Equal(t, tabLog, model.tab, "a failure moves the user to where the detail is")
	assert.Contains(t, strings.Join(model.logLines, "\n"), "boom")
}

// assertError is a stable error for the tests above.
type assertError struct{}

func (assertError) Error() string { return "boom" }

func TestEscapeAbortsARunningAnalysis(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	model.analyze = func(ctx context.Context, request Request, _ Sink) Result {
		<-ctx.Done()
		return Result{Request: request, Aborted: true}
	}
	request := state.RequestFor(demo.Protocol, demo.Path)
	model, cmd := sendWithCmd(model, startAnalysisMsg{Request: request})
	require.True(t, model.run.active)
	require.NotNil(t, cmd)

	// esc has nothing more local to dismiss, so it reaches the run.
	model = send(model, namedKey(tea.KeyEscape))
	assert.Contains(t, model.toast, "abort")

	// The batched command holds the analysis, which is parked on its cancelled context. It has
	// to come back, or esc would only ever look as though it aborted something.
	//
	// The run has to be pulled out of the batch to be started. Calling the batched command
	// itself only hands its members back — it never enters the analysis — so waiting on that
	// return proved nothing at all.
	batch, ok := cmd().(tea.BatchMsg)
	require.True(t, ok)
	done := make(chan tea.Msg, len(batch))
	for _, batched := range batch {
		go func() { done <- batched() }()
	}
	deadline := time.After(3 * time.Second)
	for range batch {
		select {
		case <-done:
		case <-deadline:
			t.Fatal("the cancelled analysis did not return")
		}
	}
}

// TestEscapeChainTakesOneLinkAtATime pins that a single press never does two things.
func TestEscapeChainTakesOneLinkAtATime(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, tea.WindowSizeMsg{Width: compact.Width, Height: compact.Height})
	model = send(model, modKey('1', tea.ModAlt))
	require.Equal(t, paneCaptures, model.overlay)
	require.NotEmpty(t, model.toast)

	model = send(model, namedKey(tea.KeyEscape))
	assert.Equal(t, paneCount, model.overlay, "the first press closes the overlay")
	assert.NotEmpty(t, model.toast, "and leaves the toast alone")

	model = send(model, namedKey(tea.KeyEscape))
	assert.Empty(t, model.toast, "the second press dismisses the toast")

	model = send(model, namedKey(tea.KeyEscape))
	assert.Equal(t, tui.FocusPrompt, model.focus, "and the last press returns the keyboard to the prompt")
}

func TestFilterNarrowsTheTableAndIsNotAPaneShortcut(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('/'))
	require.True(t, model.filtering)
	assert.Equal(t, tui.FocusOverlay, model.focus, "typing a filter is text entry, so the bare pane keys go dead")

	for _, r := range "bytes" {
		model = send(model, charKey(r))
	}
	assert.Equal(t, "bytes", model.filter)
	assert.Len(t, model.visible, 1, "only the record whose verdict is bytes survives")

	model = send(model, namedKey(tea.KeyEnter))
	assert.False(t, model.filtering)
	assert.Equal(t, tui.FocusPane, model.focus)
	assert.Contains(t, model.Render(), "1 of 2", "the counter has to say how much was hidden")

	model = send(model, charKey('/'))
	model = send(model, namedKey(tea.KeyEscape))
	assert.Empty(t, model.filter, "esc clears the filter rather than leaving the table narrowed")
	assert.Len(t, model.visible, 2)
}

// TestQuitWorksWhileTypingAFilter is the rule that the way out is never captured by a mode.
func TestQuitWorksWhileTypingAFilter(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('/'))
	require.True(t, model.filtering)

	model = send(model, tea.KeyPressMsg{Code: 'c', Mod: tea.ModCtrl})
	require.True(t, model.confirmQuit, "ctrl+c has to work even while a mode owns the keyboard")
	model = send(model, charKey('y'))
	assert.True(t, model.quitting)
}

func TestSelectingAPacketMovesToTheDetail(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey('2', tea.ModAlt))
	require.Equal(t, paneMain, model.activePane())

	wideSelection := send(model, namedKey(tea.KeyEnter))
	assert.Equal(t, paneDetail, wideSelection.pane, "where there is a detail column, enter goes to it")

	narrow := send(model, tea.WindowSizeMsg{Width: compact.Width, Height: compact.Height})
	narrow = send(narrow, modKey('2', tea.ModAlt))
	narrowSelection := send(narrow, namedKey(tea.KeyEnter))
	assert.Equal(t, paneDetail, narrowSelection.overlay, "where there is not, enter opens it as an overlay")
}

func TestFilterBackspace(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('/'))
	for _, r := range "byt" {
		model = send(model, charKey(r))
	}
	model = send(model, namedKey(tea.KeyBackspace))
	assert.Equal(t, "by", model.filter)
}

func TestMovingTheCursorTurnsFollowOff(t *testing.T) {
	model := analysed(t, okRecord(1), okRecord(2), okRecord(3))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	require.True(t, model.follow)

	model = send(model, charKey('k'))
	assert.False(t, model.follow, "taking the cursor by hand has to stop the run dragging it away")

	model = send(model, charKey('f'))
	assert.True(t, model.follow)
}

func TestSelectingASidebarDriverRegistersIt(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey('1', tea.ModAlt))
	require.Equal(t, paneCaptures, model.activePane())

	// Walk to the first driver row.
	rows := model.sidebarRows()
	selectable := model.sidebarSelectable()
	index := -1
	for i, row := range selectable {
		if rows[row].kind == sidebarDriver && rows[row].key == "c-bus" {
			index = i
			break
		}
	}
	require.GreaterOrEqual(t, index, 0)
	model.sidebarCursor = index

	model = send(model, namedKey(tea.KeyEnter))
	assert.True(t, model.state.IsRegistered("c-bus"), "selecting a driver row must actually register it")

	model = send(model, namedKey(tea.KeyEnter))
	assert.False(t, model.state.IsRegistered("c-bus"), "and selecting it again must unregister it")
}

// TestNoSelectableSidebarRowIsInert is the guard against the defect this port set out to
// remove: the old UI's list handlers contained nothing but a TODO comment.
func TestNoSelectableSidebarRowIsInert(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey('1', tea.ModAlt))

	rows := model.sidebarRows()
	for cursor, row := range model.sidebarSelectable() {
		t.Run(rows[row].label, func(t *testing.T) {
			before := model
			before.sidebarCursor = cursor
			after := send(before, namedKey(tea.KeyEnter))
			changed := after.toast != before.toast ||
				after.prompt.Value() != before.prompt.Value() ||
				after.focus != before.focus ||
				len(after.logLines) != len(before.logLines) ||
				after.state.Protocol.Name != before.state.Protocol.Name
			assert.True(t, changed, "selecting %q did nothing at all", rows[row].label)
		})
	}
}

func TestSelectingAnOptionTogglesIt(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey('1', tea.ModAlt))

	rows := model.sidebarRows()
	for cursor, row := range model.sidebarSelectable() {
		if rows[row].key != "only-parse" {
			continue
		}
		model.sidebarCursor = cursor
		before := analysisOptions().OnlyParse
		model = send(model, namedKey(tea.KeyEnter))
		assert.NotEqual(t, before, analysisOptions().OnlyParse, "the option row has to change the option")
		return
	}
	t.Fatal("the only-parse row is missing from the sidebar")
}

func TestOpenAnalyzeAndExtractShortcutsPrefillThePrompt(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))

	opened := send(model, charKey('o'))
	assert.Equal(t, "open ", opened.prompt.Value())
	assert.Equal(t, tui.FocusPrompt, opened.focus, "a shortcut is a head start on typing, not a hidden action")

	analyze := send(model, charKey('a'))
	assert.True(t, strings.HasPrefix(analyze.prompt.Value(), "analyze c-bus "))
	assert.Contains(t, analyze.prompt.Value(), DemoCaptureName)

	extract := send(model, charKey('x'))
	assert.True(t, strings.HasPrefix(extract.prompt.Value(), "extract c-bus "))
}

func TestSubmittingACommandRunsItAndLogsIt(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	model = send(model, tui.PromptSubmitMsg{Line: "pwd"})
	joined := strings.Join(model.logLines, "\n")
	assert.Contains(t, joined, "$ pwd")
	assert.Contains(t, joined, state.CurrentDir)
	assert.Contains(t, state.Config.History.Last10Commands, "pwd")
}

func TestSubmittingAFailingCommandShowsTheError(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	model = send(model, tui.PromptSubmitMsg{Line: "cd /definitely/not/here"})
	assert.True(t, model.toastErr)
	assert.Equal(t, tabLog, model.tab)
	assert.Contains(t, strings.Join(model.logLines, "\n"), "error:")
}

func TestSubmittingQuitQuits(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	model, cmd := sendWithCmd(model, tui.PromptSubmitMsg{Line: "quit"})
	assert.True(t, model.quitting)
	require.NotNil(t, cmd)
	assert.Equal(t, tea.QuitMsg{}, cmd())
}

func TestClearEmptiesThePanes(t *testing.T) {
	model := analysed(t, okRecord(1), brokenRecord(2))
	require.NotEmpty(t, model.packets)

	model = send(model, tui.PromptSubmitMsg{Line: "clear message"})
	assert.Empty(t, model.packets)
	assert.Empty(t, model.visible)

	model = send(model, tui.PromptSubmitMsg{Line: "clear console"})
	assert.Empty(t, model.logLines)
}

func TestLogLinesArriveAsMessagesAndReArmTheDrain(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	model, cmd := sendWithCmd(model, logMsg{Lines: []string{"first", "second"}})
	assert.Equal(t, []string{"first", "second"}, model.logLines)
	require.NotNil(t, cmd, "handling a log batch must re-arm the drain")

	model.logCh <- "third"
	assert.Equal(t, logMsg{Lines: []string{"third"}}, cmd())
}

func TestTheLogIsBounded(t *testing.T) {
	state, _ := demoState(t)
	state.Config.MaxConsoleLines = 5
	model := newTestModel(t, state, wide)

	for i := range 20 {
		model = send(model, logMsg{Lines: []string{itoa(i)}})
	}
	assert.Len(t, model.logLines, 5, "a session shell that never forgets a log line is a leak")
	assert.Equal(t, "19", model.logLines[4])
}

func TestASecondRunIsRefusedWhileOneIsInFlight(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model = send(model, startAnalysisMsg{Request: request})
	require.True(t, model.run.active)

	model = send(model, startAnalysisMsg{Request: request})
	assert.True(t, model.toastErr)
	assert.Contains(t, model.toast, "already in flight")
}

func TestTheRunPanelOnlyAppearsOnceSomethingHasRun(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)
	assert.False(t, model.paneVisible(paneRun))
	assert.NotContains(t, model.Render(), "Run ")

	model = analysed(t, okRecord(1))
	assert.True(t, model.paneVisible(paneRun))
	assert.Contains(t, model.Render(), "Run ")
}

func TestSpinnerAdvancesOnTick(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	model = send(model, startAnalysisMsg{Request: state.RequestFor(demo.Protocol, demo.Path)})

	before := model.spinnerFrame
	model = send(model, tui.PromptTickMsg{Time: time.Now()})
	assert.NotEqual(t, before, model.spinnerFrame)
}

func TestYankWritesTheDetailIntoTheLog(t *testing.T) {
	model := analysed(t, brokenRecord(7))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('y'))

	assert.Equal(t, tabLog, model.tab)
	joined := strings.Join(model.logLines, "\n")
	assert.Contains(t, joined, "packet 7")
	assert.Contains(t, joined, "first difference at offset 2")
}

func TestExpandWritesTheReasonIntoTheLog(t *testing.T) {
	model := analysed(t, brokenRecord(7))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	model = send(model, charKey('e'))
	assert.Contains(t, strings.Join(model.logLines, "\n"), "4 bytes differ")
}

func TestLogLevelKeyCyclesTheLevel(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	before := model.state.LogLevel

	model = send(model, charKey('L'))
	assert.NotEqual(t, before, model.state.LogLevel)
	assert.Contains(t, model.toast, "log level")
}

func TestWrapToggle(t *testing.T) {
	model := analysed(t, okRecord(1))
	model = send(model, modKey(tea.KeyTab, tea.ModShift))
	assert.True(t, send(model, charKey('w')).detailWrap)
}

func TestHelpKeyTogglesTheExpandedHelp(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	model = send(model, tea.KeyPressMsg{Code: tea.KeyF1})
	assert.True(t, model.footer.ShowAll())
	model = send(model, tea.KeyPressMsg{Code: tea.KeyF1})
	assert.False(t, model.footer.ShowAll())
}

func TestExtractionOutcome(t *testing.T) {
	state, demo := demoState(t)
	model := newTestModel(t, state, wide)
	model.extract = func(context.Context, Request, progress.Reporter, lineWriterFunc) error { return nil }
	request := state.RequestFor(demo.Protocol, demo.Path)

	model = send(model, tui.PromptSubmitMsg{Line: "extract c-bus " + demo.Path})
	require.True(t, model.run.active)
	assert.Equal(t, tabLog, model.tab, "extraction output is text, so it belongs in the log")

	model = send(model, extractionDoneMsg{Request: request})
	assert.False(t, model.run.active)
	assert.False(t, model.toastErr)
	assert.Contains(t, model.toast, "extracted")
}

// TestInitArmsEveryDrain runs what Init returns rather than merely checking that it returned
// something. A batch of the wrong commands is non-nil too, and every message that reaches this
// program from off the event loop comes through one of these three drains.
func TestInitArmsEveryDrain(t *testing.T) {
	state, _ := demoState(t)
	model := newTestModel(t, state, wide)

	init := model.Init()
	require.NotNil(t, init)
	batch, ok := init().(tea.BatchMsg)
	require.True(t, ok, "Init has to batch the background-colour request with the drains")

	// A value waiting on each channel, so that the command draining it can return.
	model.progressCh <- progress.Update{Current: 1, Total: 2, Description: PhaseFilter}
	model.logCh <- "a line from off the loop"
	model.recordCh <- okRecord(1)

	messages := make(chan tea.Msg, len(batch))
	for _, batched := range batch {
		go func() { messages <- batched() }()
	}

	seen := map[string]bool{}
	deadline := time.After(3 * time.Second)
	for range batch {
		select {
		case msg := <-messages:
			switch msg := msg.(type) {
			case progressMsg:
				seen["progress"] = true
			case logMsg:
				seen["log"] = msg.Lines[0] == "a line from off the loop"
			case recordsMsg:
				seen["records"] = len(msg.Records) == 1
			}
		case <-deadline:
			t.Fatal("a command returned by Init never came back")
		}
	}
	assert.True(t, seen["progress"], "the progress drain is not armed")
	assert.True(t, seen["log"], "the log drain is not armed")
	assert.True(t, seen["records"], "the record drain is not armed")
}

func TestAutoRunStartsAnAnalysis(t *testing.T) {
	state, demo := demoState(t)
	request := state.RequestFor(demo.Protocol, demo.Path)
	model := NewModel(Options{Theme: testTheme(), State: state, Clock: fixedClock(), AutoRun: &request})
	model = send(model, tea.WindowSizeMsg{Width: wide.Width, Height: wide.Height})

	// Init queues the run as a message rather than performing it, so the bookkeeping in Update
	// is the only place a run is ever started.
	model = send(model, startAnalysisMsg{Request: request})
	assert.True(t, model.run.active)
	assert.Equal(t, request.Label(), model.run.label)
}
