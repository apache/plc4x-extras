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

package progress

import (
	"fmt"
	"io"
	"strconv"
	"strings"
	"sync"

	"charm.land/lipgloss/v2"
	"golang.org/x/term"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// DefaultWidth is the width assumed when the real terminal width cannot be determined. The
// bar being replaced was fixed at 15 cells on every terminal, which is the reason width is a
// parameter here at all.
const DefaultWidth = 80

// minBarWidth is the narrowest bar still worth drawing. Below this the bar conveys nothing and
// the space is better spent on the percentage.
const minBarWidth = 4

// CLIOption adjusts a CLI reporter at construction.
type CLIOption func(*cliReporter)

// WithHidden suppresses all drawing. It takes the flag rather than being a bare marker so that
// a caller can pass a configuration value straight through, which is what the pcap analyser's
// --hide-progress-bar does.
func WithHidden(hidden bool) CLIOption {
	return func(r *cliReporter) { r.hidden = hidden }
}

// NewCLI returns a Reporter that draws a bar of the given width into out.
//
// It writes to out and to nothing else. That is the whole point: the bar it replaces was bound
// to a package-level ANSI stderr, which is why the terminal UI could never render it. Drawing
// is a rewrite of the current line — a carriage return, not a clear-screen — so it composes
// with a terminal that is otherwise being written to line by line.
//
// It does NOT decide whether drawing is appropriate; ForWriter does. A caller that hands this
// a pipe or a file gets a bar in the pipe, which is what a test wants and a shell does not.
func NewCLI(out io.Writer, width int, theme tui.Theme, options ...CLIOption) Reporter {
	// A zero tui.Theme carries no glyphs, so a caller that forgot to resolve one would draw an
	// invisible bar. Resolve the plainest real theme instead of hardcoding glyphs here, which
	// would put a drawing character at a call site.
	if theme.Glyphs.ProgressFull == "" {
		theme = tui.NewTheme(tui.Options{NoColor: true, ASCII: true})
	}
	reporter := &cliReporter{out: out, width: width, theme: theme}
	for _, option := range options {
		option(reporter)
	}
	// A nil writer is treated as "hidden" rather than as a programming error, because the
	// analysers pass whatever writer they were given and one of them may legitimately be unset.
	if reporter.out == nil {
		reporter.hidden = true
	}
	return reporter
}

// ForWriter returns the reporter a plain command line should use for out.
//
// It is the single place that answers "may a bar be drawn here?", so that the answer cannot
// drift between the two analysers the way the old hardwired bars did. A bar is drawn only into
// a real terminal: into a pipe, a file or a test buffer it would interleave with the zerolog
// stream that shares the same descriptor, which is the corruption this package exists to end.
func ForWriter(out io.Writer, hide bool, theme tui.Theme) Reporter {
	if hide || !IsTerminal(out) {
		return Nop()
	}
	return NewCLI(out, TerminalWidth(out), theme)
}

// IsTerminal reports whether w is a terminal.
//
// It matches on the Fd method rather than on *os.File so that a wrapped descriptor still
// answers truthfully. Anything that cannot produce a descriptor — a bytes.Buffer, io.Discard,
// a pipe wrapper — is not a terminal.
func IsTerminal(w io.Writer) bool {
	descriptor, ok := w.(interface{ Fd() uintptr })
	if !ok {
		return false
	}
	return term.IsTerminal(int(descriptor.Fd()))
}

// TerminalWidth returns the width of w in cells, or DefaultWidth when w has none.
func TerminalWidth(w io.Writer) int {
	descriptor, ok := w.(interface{ Fd() uintptr })
	if !ok {
		return DefaultWidth
	}
	width, _, err := term.GetSize(int(descriptor.Fd()))
	if err != nil || width <= 0 {
		return DefaultWidth
	}
	return width
}

// cliReporter draws a single-line bar.
//
// The mutex is not decoration: the analysers may report from the packet-mapping goroutine as
// well as the main loop, and two goroutines interleaving their escape sequences produce
// garbage rather than a race the reader would notice.
type cliReporter struct {
	out    io.Writer
	width  int
	theme  tui.Theme
	hidden bool

	mu          sync.Mutex
	total       int
	current     int
	description string
	started     bool
	done        bool
	// lastKey identifies the frame on screen by its geometry: the filled cell count, the whole
	// percentage and the label. An analysis of a large capture calls Advance once per packet,
	// tens of thousands of times, for a bar that has at most a hundred or so distinct
	// appearances, so anything that does not change one of those is not worth an escape
	// sequence. The consequence is that the counts on the line are as of the last redraw rather
	// than of this instant, which is what every progress bar with a refresh interval does.
	lastKey string
}

func (r *cliReporter) Start(total int, description string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.total = total
	r.current = 0
	r.description = description
	r.started = true
	r.done = false
	r.lastKey = ""
	r.draw(false)
}

func (r *cliReporter) Advance(delta int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.current += delta
	r.draw(false)
}

func (r *cliReporter) SetDescription(description string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.description = description
	r.draw(false)
}

func (r *cliReporter) Done() {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.done {
		return
	}
	r.done = true
	// Nothing was ever drawn, so do not leave a stray line behind. A run that fails before it
	// knows how much work there is still calls Done from a defer.
	if !r.started {
		return
	}
	r.draw(true)
}

// draw writes one frame. final terminates the line, releasing it to whatever writes next.
//
// The caller must hold r.mu.
func (r *cliReporter) draw(final bool) {
	if r.hidden {
		return
	}
	// Compute the frame's identity before building it. Advance is called once per packet, so
	// on a large capture this path runs millions of times and almost always concludes that the
	// frame on screen is still correct. Rendering first and discarding the result afterwards
	// measured 21us per call, which is around twenty seconds of wasted work on a
	// million-packet capture.
	if !final && r.frameKey() == r.lastKey {
		return
	}
	line, key := r.render()
	r.lastKey = key
	suffix := ""
	if final {
		suffix = "\n"
	}
	_, _ = fmt.Fprint(r.out, "\r"+line+suffix)
}

// frameKey computes only the identity of the frame that would be drawn now, skipping the
// string building. It must stay in step with render, which is why both derive the key from
// geometry alone.
//
// The caller must hold r.mu.
func (r *cliReporter) frameKey() string {
	_, key := r.renderWith(true)
	return key
}

// render builds one frame, exactly r.width cells wide where the width allows it, together with
// the key that identifies it for redraw suppression.
//
// The caller must hold r.mu.
func (r *cliReporter) render() (string, string) {
	return r.renderWith(false)
}

// renderWith computes the frame geometry and, unless keyOnly is set, the drawn line.
//
// The caller must hold r.mu.
func (r *cliReporter) renderWith(keyOnly bool) (string, string) {
	width := r.width
	if width <= 0 {
		width = DefaultWidth
	}
	glyphs := r.theme.Glyphs

	total := r.total
	current := max(r.current, 0)
	// Overshoot is tolerated rather than reported: the analysis loops count packets they have
	// walked, and a mapping stage can emit more packets than the index promised. A bar that
	// overflowed its own brackets would be the worse outcome by far.
	if total > 0 && current > total {
		current = total
	}
	ratio := 0.0
	if total > 0 {
		ratio = float64(current) / float64(total)
	}
	percent := fmt.Sprintf("%3d%%", int(ratio*100))
	// The current count is padded to the width of the total so that the counts segment keeps a
	// constant width. Otherwise every extra digit steals a cell from the bar and the bar's own
	// length twitches as the run proceeds.
	totalText := strconv.Itoa(total)
	counts := fmt.Sprintf("%*d/%s", len(totalText), current, totalText)
	// A description long enough to squeeze out the bar defeats the purpose, so it never gets
	// more than half the line.
	label := truncate(r.description, width/2, glyphs.Ellipsis)

	// Degradation order as the line narrows. The percentage always survives; the counts go
	// first because the percentage already says the same thing less precisely, and the label
	// second because knowing which phase is running is worth more than the exact count.
	type form struct{ withLabel, withCounts bool }
	chosen := form{}
	barWidth := 0
	for _, candidate := range []form{{true, true}, {true, false}, {false, true}, {false, false}} {
		// Two bracket cells plus the space before the percentage.
		used := 2 + lipgloss.Width(percent) + 1
		if candidate.withLabel && label != "" {
			used += lipgloss.Width(label) + 1
		}
		if candidate.withCounts {
			used += lipgloss.Width(counts) + 1
		}
		if remaining := width - used; remaining >= minBarWidth {
			chosen, barWidth = candidate, remaining
			break
		}
	}
	if barWidth == 0 {
		// Nothing fits comfortably. Draw the narrowest honest bar; the clip below handles a
		// terminal that cannot hold even that.
		barWidth = max(width-2-lipgloss.Width(percent)-1, 1)
	}

	// Floor, not round: the bar must not read as complete until it is.
	filled := min(int(ratio*float64(barWidth)), barWidth)
	key := percent + "|" + strconv.Itoa(filled) + "|" + strconv.Itoa(barWidth) + "|" + label
	if keyOnly {
		// Everything below is string building, which the caller is about to discard.
		return "", key
	}

	var plain, styled strings.Builder
	add := func(text string, style lipgloss.Style) {
		plain.WriteString(text)
		styled.WriteString(style.Render(text))
	}
	space := func() {
		plain.WriteString(" ")
		styled.WriteString(" ")
	}
	if chosen.withLabel && label != "" {
		add(label, r.theme.Muted)
		space()
	}
	add("[", r.theme.Chrome)
	add(strings.Repeat(glyphs.ProgressFull, filled), r.theme.Accent)
	add(strings.Repeat(glyphs.ProgressEmpty, barWidth-filled), r.theme.Chrome)
	add("]", r.theme.Chrome)
	space()
	add(percent, r.theme.Value)
	if chosen.withCounts {
		space()
		add(counts, r.theme.Muted)
	}

	plainWidth := lipgloss.Width(plain.String())
	if plainWidth > width {
		// Narrower than "[####] 100%" can be drawn in. Keep the number and drop everything
		// else rather than wrapping, which would scroll the caller's terminal.
		return r.theme.Value.Render(truncate(percent, width, glyphs.Ellipsis)), key
	}
	// Pad to the full width. The redraw is a carriage return rather than an erase-line escape,
	// so without this a shorter frame would leave the tail of the previous, longer one behind.
	if pad := width - plainWidth; pad > 0 {
		styled.WriteString(strings.Repeat(" ", pad))
	}
	return styled.String(), key
}

// truncate shortens text to at most width cells, marking the cut with the theme's ellipsis.
//
// It measures in cells rather than bytes or runes because the Unicode glyph set contains
// multi-byte characters, and a byte count would clip mid-character.
func truncate(text string, width int, ellipsis string) string {
	if width <= 0 {
		return ""
	}
	if lipgloss.Width(text) <= width {
		return text
	}
	if ellipsis == "" {
		ellipsis = "."
	}
	ellipsisWidth := lipgloss.Width(ellipsis)
	if width <= ellipsisWidth {
		return ellipsis
	}
	runes := []rune(text)
	for len(runes) > 0 && lipgloss.Width(string(runes))+ellipsisWidth > width {
		runes = runes[:len(runes)-1]
	}
	return string(runes) + ellipsis
}
