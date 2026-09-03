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

package progress_test

import (
	"bytes"
	"io"
	"strings"
	"testing"

	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// testTheme is the ASCII, colourless theme. Golden-ish assertions on a bar are only stable
// without escape sequences, and the ASCII glyphs are single-byte so a substring assertion says
// what it looks like it says.
func testTheme() tui.Theme {
	return tui.NewTheme(tui.Options{NoColor: true, ASCII: true})
}

// frames splits a reporter's output into the individual redraws. Each frame is introduced by
// the carriage return that rewinds the line.
func frames(out string) []string {
	var result []string
	for frame := range strings.SplitSeq(out, "\r") {
		frame = strings.TrimSuffix(frame, "\n")
		if frame == "" {
			continue
		}
		result = append(result, frame)
	}
	return result
}

// TestCLIReporterDrawsIntoTheWriterItWasGiven is the regression test for the defect this
// package exists to fix: the bar it replaces was bound to a package-level ANSI stderr and
// ignored the writer its caller passed, so no terminal UI could ever render it.
func TestCLIReporterDrawsIntoTheWriterItWasGiven(t *testing.T) {
	var out bytes.Buffer
	reporter := progress.NewCLI(&out, 40, testTheme())

	reporter.Start(10, "Analyzing")
	reporter.Advance(5)

	got := out.String()
	require.NotEmpty(t, got, "the reporter must write to the writer it was handed")
	assert.Contains(t, got, "Analyzing", "the description belongs on the line")
	assert.Contains(t, got, " 50%")
	assert.Contains(t, got, "5/10", "the counts fit at 40 cells")
	assert.Contains(t, got, "#", "the filled part of the bar uses the theme's glyph")
	assert.Contains(t, got, "-", "the empty part of the bar uses the theme's glyph")
}

// TestCLIReporterReachesFullOnDone pins the end state, which is the frame a user is left
// looking at.
func TestCLIReporterReachesFullOnDone(t *testing.T) {
	var out bytes.Buffer
	reporter := progress.NewCLI(&out, 40, testTheme())

	reporter.Start(4, "Analyzing")
	for range 4 {
		reporter.Advance(1)
	}
	reporter.Done()

	drawn := frames(out.String())
	require.NotEmpty(t, drawn)
	last := drawn[len(drawn)-1]
	assert.Contains(t, last, "100%")
	assert.Contains(t, last, "4/4")
	assert.NotContains(t, last, "-", "at 100% no cell of the bar may still be empty")
	assert.True(t, strings.HasSuffix(out.String(), "\n"),
		"Done must terminate the line so that whatever writes next starts cleanly")
}

// TestCLIReporterNeverExceedsTheWidthItWasGiven covers the second half of the old defect: the
// bar was 15 cells wide whatever the terminal was. Widths below the bar's own minimum are
// included because the responsive rules put the tools at 60 columns and less.
func TestCLIReporterNeverExceedsTheWidthItWasGiven(t *testing.T) {
	for _, test := range []struct {
		name  string
		width int
		exact bool
	}{
		{name: "roomy", width: 120, exact: true},
		{name: "typical", width: 80, exact: true},
		{name: "narrow", width: 40, exact: true},
		{name: "cramped", width: 20, exact: true},
		{name: "bar barely fits", width: 12, exact: true},
		{name: "percentage only", width: 6, exact: false},
		{name: "absurd", width: 3, exact: false},
	} {
		t.Run(test.name, func(t *testing.T) {
			var out bytes.Buffer
			reporter := progress.NewCLI(&out, test.width, testTheme())

			reporter.Start(7, "Analyzing packages")
			for range 7 {
				reporter.Advance(1)
			}
			reporter.Done()

			drawn := frames(out.String())
			require.NotEmpty(t, drawn)
			for _, frame := range drawn {
				if test.exact {
					assert.Equal(t, test.width, lipgloss.Width(frame),
						"every frame must fill the line exactly, or a shorter frame leaves the tail of a longer one behind: %q", frame)
					continue
				}
				assert.LessOrEqual(t, lipgloss.Width(frame), test.width,
					"a frame wider than the terminal would wrap and scroll the caller's output: %q", frame)
			}
		})
	}
}

// TestCLIReporterDropsTheCountsBeforeThePercentageAsItNarrows pins the degradation order,
// which is the only thing that keeps a 60-column run readable.
func TestCLIReporterDropsTheCountsBeforeThePercentageAsItNarrows(t *testing.T) {
	render := func(width int) string {
		var out bytes.Buffer
		reporter := progress.NewCLI(&out, width, testTheme())
		reporter.Start(1000, "Analyzing packages")
		reporter.Advance(500)
		drawn := frames(out.String())
		require.NotEmpty(t, drawn)
		return drawn[len(drawn)-1]
	}

	wide := render(80)
	assert.Contains(t, wide, "Analyzing packages")
	assert.Contains(t, wide, "500/1000")
	assert.Contains(t, wide, " 50%")

	medium := render(32)
	assert.Contains(t, medium, "Analyzing", "the label survives longer than the counts")
	assert.NotContains(t, medium, "500/1000", "the counts are the first thing dropped")
	assert.Contains(t, medium, " 50%")

	tight := render(12)
	assert.NotContains(t, tight, "Analyzing")
	assert.Contains(t, tight, " 50%", "the percentage is the last thing standing")
}

// TestCLIReporterEmitsNothingWhenHidden covers --hide-progress-bar, and with it every context
// where a bar must not appear: a piped stderr, a log file, a test run.
func TestCLIReporterEmitsNothingWhenHidden(t *testing.T) {
	var out bytes.Buffer
	reporter := progress.NewCLI(&out, 80, testTheme(), progress.WithHidden(true))

	reporter.Start(10, "Analyzing")
	reporter.Advance(5)
	reporter.SetDescription("Still analyzing")
	reporter.Advance(5)
	reporter.Done()

	assert.Empty(t, out.String(), "a hidden reporter must write nothing at all, not even a newline")
}

// TestCLIReporterWithoutAWriterIsInert guards the analysers, which pass on whatever writer
// their caller gave them and cannot promise it is set.
func TestCLIReporterWithoutAWriterIsInert(t *testing.T) {
	reporter := progress.NewCLI(nil, 80, testTheme())
	assert.NotPanics(t, func() {
		reporter.Start(3, "Analyzing")
		reporter.Advance(1)
		reporter.SetDescription("elsewhere")
		reporter.Done()
	})
}

// TestCLIReporterRedrawsOnlyWhenTheFrameChanges pins the throttle. An analysis of a large
// capture calls Advance once per packet; a bar 80 cells wide has nowhere near that many
// distinct appearances, and writing an escape sequence for each one is pure cost.
func TestCLIReporterRedrawsOnlyWhenTheFrameChanges(t *testing.T) {
	const total = 5000
	const width = 40

	var out bytes.Buffer
	reporter := progress.NewCLI(&out, width, testTheme())
	reporter.Start(total, "Analyzing")
	for range total {
		reporter.Advance(1)
	}
	reporter.Done()

	// A frame is only worth writing when the whole percentage or a bar cell changed, so the
	// upper bound is 101 percentages plus one per bar cell - the bar can never be wider than
	// the line - plus the final frame. Against 5000 Advance calls that is the point.
	assert.LessOrEqual(t, len(frames(out.String())), 101+width+1,
		"redrawing an identical frame buys nothing and costs an escape sequence per packet")
	assert.Contains(t, out.String(), "100%", "throttling must not swallow the final frame")
}

// TestCLIReporterDescriptionChangeIsVisibleImmediately proves the throttle keys on the whole
// frame and not merely on the counters, so a phase change is never held back.
func TestCLIReporterDescriptionChangeIsVisibleImmediately(t *testing.T) {
	var out bytes.Buffer
	reporter := progress.NewCLI(&out, 60, testTheme())

	reporter.Start(100, "Indexing")
	reporter.SetDescription("Filtering")

	drawn := frames(out.String())
	require.Len(t, drawn, 2, "a renamed phase is a different frame and must be redrawn")
	assert.Contains(t, drawn[0], "Indexing")
	assert.Contains(t, drawn[1], "Filtering")
}

// TestNopReporterIsSafeInAnyOrder documents that "no reporter" is an ordinary choice. The
// analysers call Done from a defer and may never have reached Start.
func TestNopReporterIsSafeInAnyOrder(t *testing.T) {
	reporter := progress.Nop()
	assert.NotPanics(t, func() {
		reporter.Done()
		reporter.Advance(3)
		reporter.SetDescription("out of order")
		reporter.Start(5, "late")
		reporter.Advance(-1)
		reporter.Done()
		reporter.Done()
	})
}

// TestChannelReporterDeliversEveryStateChangeToAReader is the terminal-UI path: absolute
// counters a model can render, in the order they happened.
func TestChannelReporterDeliversEveryStateChangeToAReader(t *testing.T) {
	updates := make(chan progress.Update, 8)
	reporter := progress.NewChannel(updates)

	reporter.Start(4, "Analyzing")
	reporter.Advance(1)
	reporter.SetDescription("Comparing")
	reporter.Advance(3)
	reporter.Done()
	close(updates)

	var got []progress.Update
	for update := range updates {
		got = append(got, update)
	}
	assert.Equal(t, []progress.Update{
		{Current: 0, Total: 4, Description: "Analyzing"},
		{Current: 1, Total: 4, Description: "Analyzing"},
		{Current: 1, Total: 4, Description: "Comparing"},
		{Current: 4, Total: 4, Description: "Comparing"},
		{Current: 4, Total: 4, Description: "Comparing", Done: true},
	}, got)
}

// TestChannelReporterDropsRatherThanBlockingWhenNobodyReads is the property that keeps the
// display from throttling the analysis. An unbuffered channel with no receiver is the extreme
// case, so every send must be discarded and the calls must still return.
func TestChannelReporterDropsRatherThanBlockingWhenNobodyReads(t *testing.T) {
	updates := make(chan progress.Update)
	reporter := progress.NewChannel(updates)

	reporter.Start(100, "Analyzing")
	for range 50 {
		reporter.Advance(1)
	}
	reporter.Done()

	counter, ok := reporter.(interface{ Dropped() int })
	require.True(t, ok, "the channel reporter must expose its drop count for diagnosis")
	assert.Equal(t, 52, counter.Dropped(),
		"with nobody receiving, the Start, the 50 Advances and the Done must all be dropped")
}

// TestChannelReporterKeepsCountingAfterUpdatesAreDropped proves dropping is lossless in the
// sense that matters: Update carries absolute counters, so the next delivered frame is
// complete regardless of how many were discarded before it.
func TestChannelReporterKeepsCountingAfterUpdatesAreDropped(t *testing.T) {
	updates := make(chan progress.Update, 1)
	reporter := progress.NewChannel(updates)

	reporter.Start(10, "Analyzing")
	for range 5 {
		reporter.Advance(1)
	}
	// Drain the one frame the buffer accepted; everything after it was dropped.
	<-updates
	reporter.Advance(1)

	assert.Equal(t, progress.Update{Current: 6, Total: 10, Description: "Analyzing"}, <-updates,
		"the reporter's own counter must survive the frames nobody received")
}

// TestReportersTolerateOvershootAndRepeatedDone covers the way the analysis loops actually
// drive a reporter: they break out early, they count past the index's promise, and they call
// Done from a defer as well as on the happy path.
func TestReportersTolerateOvershootAndRepeatedDone(t *testing.T) {
	t.Run("cli", func(t *testing.T) {
		var out bytes.Buffer
		reporter := progress.NewCLI(&out, 40, testTheme())

		reporter.Start(2, "Analyzing")
		reporter.Advance(5)
		reporter.Done()
		reporter.Done()

		got := out.String()
		assert.Contains(t, got, "100%", "an overshoot is a finished bar, not a broken one")
		assert.Contains(t, got, "2/2", "the reported count is clamped to the total")
		assert.Equal(t, 1, strings.Count(got, "\n"),
			"a second Done must not terminate a second line")
	})

	t.Run("channel", func(t *testing.T) {
		updates := make(chan progress.Update, 8)
		reporter := progress.NewChannel(updates)

		reporter.Start(2, "Analyzing")
		reporter.Advance(5)
		reporter.Done()
		reporter.Done()
		close(updates)

		var done int
		for update := range updates {
			assert.LessOrEqual(t, update.Current, update.Total,
				"a model dividing Current by Total must never see a ratio above one")
			if update.Done {
				done++
			}
		}
		assert.Equal(t, 1, done, "Done is idempotent, so exactly one terminal frame is sent")
	})

	t.Run("nop", func(t *testing.T) {
		reporter := progress.Nop()
		assert.NotPanics(t, func() {
			reporter.Start(2, "Analyzing")
			reporter.Advance(5)
			reporter.Done()
			reporter.Done()
		})
	})
}

// TestForWriterDrawsNothingIntoSomethingThatIsNotATerminal is the interleaving fix stated as a
// property: a bar and the zerolog stream share stderr, so unless stderr is a terminal being
// watched by a human there must be no bar at all.
func TestForWriterDrawsNothingIntoSomethingThatIsNotATerminal(t *testing.T) {
	var out bytes.Buffer
	reporter := progress.ForWriter(&out, false, testTheme())

	reporter.Start(10, "Analyzing")
	reporter.Advance(10)
	reporter.Done()

	assert.Empty(t, out.String(), "a buffer, a pipe or a log file must never receive bar frames")
	assert.False(t, progress.IsTerminal(&out))
	assert.Equal(t, progress.DefaultWidth, progress.TerminalWidth(&out),
		"a writer with no descriptor has no width to query, so the default stands in")
}

// TestTheDoneFrameSurvivesAFullBuffer pins the one frame that must not be dropped.
//
// An intermediate frame is superseded by the next one, so losing it costs nothing. The
// terminal frame has no successor: dropping it leaves a bar stranded at whatever fraction it
// last showed, so a finished run reads as stuck. It is also bounded -- it happens once -- so
// it can afford to displace a stale update to get through.
func TestTheDoneFrameSurvivesAFullBuffer(t *testing.T) {
	// Capacity one, deliberately: after Start the buffer is already full, so every subsequent
	// send has to contend for it.
	updates := make(chan progress.Update, 1)
	reporter := progress.NewChannel(updates)

	reporter.Start(10, "analyzing")
	for range 50 {
		reporter.Advance(1)
	}
	reporter.Done()

	// Drain what is queued and look for the terminal frame.
	close(updates)
	var last progress.Update
	var sawDone bool
	for update := range updates {
		last = update
		if update.Done {
			sawDone = true
		}
	}

	assert.True(t, sawDone, "the terminal frame must reach the UI even when the buffer is full")
	assert.True(t, last.Done, "the terminal frame must be the last one queued")
	assert.Equal(t, 10, last.Current, "the final frame must show the work complete, not stranded short")
	assert.Equal(t, 10, last.Total)
}

// TestIntermediateFramesAreStillDroppedRatherThanBlocking guards the other half: making the
// terminal frame reliable must not turn ordinary progress into backpressure on the analysis.
func TestIntermediateFramesAreStillDroppedRatherThanBlocking(t *testing.T) {
	updates := make(chan progress.Update, 1)
	reporter := progress.NewChannel(updates)

	reporter.Start(1000, "analyzing")
	for range 500 {
		reporter.Advance(1)
	}

	dropper, ok := reporter.(interface{ Dropped() int })
	require.True(t, ok, "the channel reporter should expose its drop count for exactly this test")
	assert.Positive(t, dropper.Dropped(), "a full buffer must drop intermediate frames, not block the analysis")
}

// BenchmarkCLIAdvance guards the hot path. Advance runs once per packet, so on a large capture
// it runs millions of times, and almost every call concludes that the frame already on screen
// is still correct. Building the frame first and discarding it measured 21us per call here --
// roughly twenty seconds of wasted work on a million-packet capture -- which is why the frame
// key is computed before the strings are built. Expect hundreds of nanoseconds, not tens of
// microseconds.
func BenchmarkCLIAdvance(b *testing.B) {
	reporter := progress.NewCLI(io.Discard, 80, tui.NewTheme(tui.Options{Dark: true}))
	reporter.Start(b.N, "analyzing")
	b.ResetTimer()
	for range b.N {
		reporter.Advance(1)
	}
	reporter.Done()
}
