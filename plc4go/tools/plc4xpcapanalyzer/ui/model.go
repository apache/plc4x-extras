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

// Package ui is the terminal interface of plc4xpcapanalyzer.
//
// It is a Bubble Tea v2 program built on the presentation vocabulary in tools/internal/tui.
// The shape it replaces was a tview grid whose widgets were mutated directly from analysis
// goroutines, whose command area was added with a zero row and column span below a hundred
// columns — so the prompt was simply not drawn and the user typed blind — and whose panes gave
// no indication of which of them had the keyboard. None of those are possible here: state is
// only ever changed on the event loop, the prompt is a reserved row in every layout, and the
// focused pane is drawn differently from the rest.
package ui

import (
	"context"
	"strings"
	"time"

	"charm.land/bubbles/v2/key"
	tea "charm.land/bubbletea/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/extractor"
)

// AppName and AppVersion title the status bar.
const (
	AppName    = "PLC4X Pcap Analyzer"
	AppVersion = "1.0.0-SNAPSHOT"
)

// Channel capacities. Each one is drained by a command that reads it and re-arms itself, so
// the buffer only has to absorb what arrives between two turns of the event loop.
const (
	progressBuffer = 64
	logBuffer      = 512
	recordBuffer   = 512
	// recordBatch caps how many records one drain returns. Handing them over one at a time
	// would put a whole turn of the event loop between consecutive packets of a six-thousand
	// packet capture; handing over everything at once would let a single message stall a
	// resize.
	recordBatch = 256
)

// pane identifies a region of the layout. The order is the order tab cycles in, and the order
// alt+1 to alt+4 select.
type pane int

const (
	paneCaptures pane = iota
	paneMain
	paneDetail
	paneRun
	paneCount
)

// String names a pane, for the status bar and for test failures.
func (p pane) String() string {
	switch p {
	case paneCaptures:
		return "captures"
	case paneMain:
		return "packets"
	case paneDetail:
		return "detail"
	case paneRun:
		return "run"
	default:
		return "unknown"
	}
}

// tab identifies one view of the main pane.
type tab int

const (
	tabPackets tab = iota
	tabLog
	tabFindings
	tabCount
)

// String names a tab, which is what the tab strip in the main pane's border shows.
func (t tab) String() string {
	switch t {
	case tabPackets:
		return "Packets"
	case tabLog:
		return "Log"
	case tabFindings:
		return "Findings"
	default:
		return "?"
	}
}

// detailView identifies what the detail pane is showing.
type detailView int

const (
	// detailDiff is the default because the byte comparison is what this tool exists to do.
	detailDiff detailView = iota
	detailBytes
	detailTree
)

// Messages. Everything that happens off the event loop reaches the model as one of these.
type (
	// progressMsg is one frame from a running analysis or extraction.
	progressMsg struct{ Update progress.Update }
	// logMsg is one line written to the log pane, whether by zerolog, by a command or by an
	// extraction's output.
	logMsg struct{ Lines []string }
	// recordsMsg is a batch of packets a running analysis has finished with.
	recordsMsg struct{ Records []Record }
	// analysisDoneMsg carries the authoritative outcome of a run.
	analysisDoneMsg struct{ Result Result }
	// extractionDoneMsg says an extraction finished.
	extractionDoneMsg struct {
		Request Request
		Err     error
	}
)

// runKind says which long-running job is in flight.
type runKind int

const (
	runKindNone runKind = iota
	runKindAnalysis
	runKindExtraction
)

// runState is what the run panel draws.
type runState struct {
	kind    runKind
	label   string
	phase   string
	current int
	total   int
	// counters accumulate from the streamed records while the run is in flight and are
	// replaced by the authoritative totals when it finishes.
	counters Counters
	active   bool
	aborted  bool
	// aborting records that a cancellation has been asked for but the run has not finished
	// reacting to it yet. Without it a second ctrl+c would abort the same run again, so a run
	// that was slow to stop left the user with no way to reach the quit question at all.
	aborting bool
	err      error
	started  time.Time
	elapsed  time.Duration
}

// Model is the root Bubble Tea model.
type Model struct {
	// Presentation.
	themeOptions tui.Options
	theme        tui.Theme
	keys         tui.KeyMap
	tools        toolKeys
	footer       tui.HelpFooter
	prompt       tui.Prompt

	size   tui.Size
	layout tui.Layout

	// Session.
	state *State
	root  Command

	// Focus.
	focus tui.Focus
	pane  pane
	// overlay is the pane shown full-width in compact mode, or paneCount when none is.
	overlay pane

	// Main pane.
	tab     tab
	packets []Record
	visible []int
	cursor  int
	// packetTop is the first visible row of the packet table.
	//
	// It is kept as state rather than derived from the cursor because deriving it pinned the
	// cursor to the bottom visible row: every press of up or down then scrolled the whole list
	// instead of moving the selection inside a stationary window, so the highlighted row was
	// never anywhere but the last drawn line. The window now moves only when the cursor
	// reaches an edge.
	packetTop int
	filter    string
	filtering bool
	follow    bool

	// Log pane.
	logLines []string
	logTop   int

	// Detail pane.
	detail     detailView
	detailWrap bool
	detailTop  int

	// Sidebar.
	sidebarCursor int

	// Run.
	run          runState
	spinnerFrame int
	cancelRun    context.CancelFunc

	// Toast.
	toast    string
	toastErr bool

	// Wiring.
	progressCh chan progress.Update
	logCh      chan string
	recordCh   chan Record
	now        func() time.Time
	// analyze runs one analysis. It is a field so a test can substitute a run that finishes
	// instantly instead of reading a capture.
	analyze func(ctx context.Context, request Request, sink Sink) Result
	// extract runs one extraction, for the same reason.
	extract func(ctx context.Context, request Request, reporter progress.Reporter, out lineWriterFunc) error
	// autoRun is the analysis to start as soon as the program does, which is how demo mode
	// arrives at something to look at.
	autoRun *Request

	quitting bool

	// confirmQuit holds the "really quit?" question. ctrl+c used to end the session outright,
	// which is the wrong default while an analysis is running: the same keystroke is how a
	// user stops a run that is taking too long.
	confirmQuit bool
}

// Options configure a Model.
type Options struct {
	// Theme selects the colour and glyph variant. Zero value means resolve from the
	// environment on the first background-colour message.
	Theme tui.Options
	// State is the session state. Required.
	State *State
	// Clock is the time source, injected so that elapsed times are assertable.
	Clock func() time.Time
	// AutoRun is an analysis to start immediately, used by demo mode.
	AutoRun *Request
}

// NewModel builds the root model.
func NewModel(options Options) Model {
	state := options.State
	if state == nil {
		state = NewState(".", NewConfig())
	}
	clock := options.Clock
	if clock == nil {
		clock = time.Now
	}

	model := Model{
		themeOptions: options.Theme,
		state:        state,
		root:         Root(),
		focus:        tui.FocusPrompt,
		pane:         paneMain,
		overlay:      paneCount,
		tab:          tabPackets,
		detail:       detailDiff,
		follow:       true,
		progressCh:   make(chan progress.Update, progressBuffer),
		logCh:        make(chan string, logBuffer),
		recordCh:     make(chan Record, recordBuffer),
		now:          clock,
		analyze:      Analyze,
		extract:      runExtraction,
		autoRun:      options.AutoRun,
		run:          runState{phase: PhaseIndex},
	}
	model.applyTheme(tui.NewTheme(options.Theme))
	model.prompt.SetClock(clock)
	// The prompt takes the keyboard here rather than in Init. Init has a value receiver, so
	// anything it changes about the model is changed on a copy the program throws away; only
	// the command it returns survives. Focusing there left the prompt blurred and the first
	// thing the user typed went nowhere.
	_ = model.prompt.Focus()
	return model
}

// applyTheme rebuilds everything whose appearance depends on the theme.
//
// The prompt is only rebuilt while its history is empty, which in practice means at startup,
// when the terminal answers the background-colour request. Rebuilding it later would discard
// the command history, and a terminal whose background changes mid-session is a far rarer
// event than one that answers the initial query.
func (m *Model) applyTheme(theme tui.Theme) {
	m.theme = theme
	m.footer = tui.NewHelpFooter(theme)
	m.footer.SetWidth(m.size.Width)
	if len(m.prompt.History()) == 0 {
		value := m.prompt.Value()
		busy := m.prompt.Busy()
		focused := m.prompt.Focused()
		m.prompt = tui.NewPrompt(theme, m.suggest)
		m.prompt.SetClock(m.now)
		m.prompt.SetValue(value)
		m.prompt.SetWidth(m.size.Width)
		if busy {
			m.prompt.Start()
		}
		// The rebuilt prompt starts blurred, so whoever had the keyboard has to be given it
		// back; otherwise answering the background-colour query silently swallows typing.
		if focused {
			_ = m.prompt.Focus()
		}
	}
	m.keys = tui.NewKeyMap().ForFocus(m.focus)
	m.tools = toolKeysFor(m.focus)
}

// suggest is the prompt's completion source.
func (m *Model) suggest(currentText string) []string {
	return m.root.Completions(m.state, currentText)
}

// Init starts the program.
func (m Model) Init() tea.Cmd {
	cmds := []tea.Cmd{
		// The terminal answers with a BackgroundColorMsg, which is what decides the light or
		// dark palette. It also arrives again whenever the background changes.
		tea.RequestBackgroundColor,
		m.prompt.Focus(),
		waitForProgress(m.progressCh),
		waitForLog(m.logCh),
		waitForRecords(m.recordCh),
	}
	if m.autoRun != nil {
		cmds = append(cmds, func() tea.Msg { return startAnalysisMsg{Request: *m.autoRun} })
	}
	return tea.Batch(cmds...)
}

// startAnalysisMsg asks the model to start an analysis. It exists so that demo mode can queue
// a run from Init without duplicating the bookkeeping Update already does.
type startAnalysisMsg struct{ Request Request }

// Update handles one message.
func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		return m.resize(tui.Size{Width: msg.Width, Height: msg.Height}), nil

	case tea.BackgroundColorMsg:
		options := m.themeOptions
		options.Dark = msg.IsDark()
		m.themeOptions = options
		m.applyTheme(tui.NewTheme(options))
		return m, nil

	case tea.KeyPressMsg:
		return m.handleKey(msg)

	case tea.MouseMsg:
		return m.handleMouse(msg)

	case tui.PromptSubmitMsg:
		return m.runLine(msg.Line)

	case tui.PromptTickMsg:
		if frames := len(m.theme.Glyphs.Spinner); frames > 0 {
			m.spinnerFrame = (m.spinnerFrame + 1) % frames
		}
		var cmd tea.Cmd
		m.prompt, cmd = m.prompt.Update(msg)
		return m, cmd

	case startAnalysisMsg:
		return m.startAnalysis(msg.Request)

	case progressMsg:
		if m.run.active {
			m.run.current = msg.Update.Current
			m.run.total = msg.Update.Total
			if msg.Update.Description != "" {
				m.run.phase = msg.Update.Description
			}
			m.run.elapsed = m.now().Sub(m.run.started)
		}
		return m, waitForProgress(m.progressCh)

	case logMsg:
		m.appendLog(msg.Lines...)
		return m, waitForLog(m.logCh)

	case recordsMsg:
		if m.run.active && m.run.kind == runKindAnalysis {
			for _, record := range msg.Records {
				m.packets = append(m.packets, record)
				m.run.counters = fold(m.run.counters, record)
			}
			m.refilter()
			if m.follow {
				m.cursor = max(len(m.visible)-1, 0)
				m = m.revealCursor()
			}
		}
		return m, waitForRecords(m.recordCh)

	case analysisDoneMsg:
		return m.finishAnalysis(msg.Result)

	case extractionDoneMsg:
		m.run.active = false
		m.run.err = msg.Err
		m.run.elapsed = m.now().Sub(m.run.started)
		m.prompt.Finish()
		m.cancelRun = nil
		if msg.Err != nil {
			m.setToast("extract failed: "+msg.Err.Error(), true)
		} else {
			m.setToast("extracted "+msg.Request.Protocol.Name+" from "+msg.Request.PcapFile, false)
		}
		return m, nil
	}

	var cmd tea.Cmd
	m.prompt, cmd = m.prompt.Update(msg)
	return m, cmd
}

// resize recomputes the layout and hands the new width to everything that needs it.
func (m Model) resize(size tui.Size) Model {
	m.size = size
	m.layout = tui.Compute(size)
	m.prompt.SetWidth(size.Width)
	m.footer.SetWidth(size.Width)
	// Leaving compact mode closes an overlay that only exists there, so that the pane does not
	// stay stuck over a layout that has room for it as a column.
	if m.layout.Mode == tui.ModeWide && m.overlay != paneCount {
		m.pane = m.overlay
		m.overlay = paneCount
	}
	// A resize can take the column out from under the focused pane — dragging a window from
	// wide to compact while the detail pane has the keyboard used to leave the focus on a pane
	// the renderer no longer draws, so nothing looked focused and up, down and enter went
	// nowhere. The keyboard follows the layout back to a pane that is actually on screen.
	if m.overlay == paneCount && !m.paneVisible(m.pane) {
		m.pane = paneMain
	}
	return m
}

// setFocus moves the keyboard and re-resolves the keymap for wherever it landed.
func (m *Model) setFocus(focus tui.Focus) tea.Cmd {
	m.focus = focus
	m.keys = tui.NewKeyMap().ForFocus(focus)
	m.tools = toolKeysFor(focus)
	if focus == tui.FocusPrompt {
		return m.prompt.Focus()
	}
	m.prompt.Blur()
	return nil
}

// setToast shows a one-line notice above the prompt.
func (m *Model) setToast(text string, isError bool) {
	m.toast = text
	m.toastErr = isError
}

// appendLog adds lines to the log pane, keeping it bounded.
func (m *Model) appendLog(lines ...string) {
	for _, line := range lines {
		if line == "" {
			continue
		}
		m.logLines = append(m.logLines, line)
	}
	limit := m.state.Config.MaxConsoleLines
	if limit < 1 {
		limit = 500
	}
	if len(m.logLines) > limit {
		m.logLines = append([]string(nil), m.logLines[len(m.logLines)-limit:]...)
	}
}

// runLine executes a command line and applies its outcome.
//
// Commands run here, on the event loop, because they change State and State belongs to the
// model. The only work that leaves the loop is an analysis or an extraction, and those are
// started as commands that return a message rather than reaching back into the model.
func (m Model) runLine(line string) (tea.Model, tea.Cmd) {
	m.appendLog("$ " + line)
	m.state.Config.RememberCommand(line)

	outcome := m.root.Execute(context.Background(), m.state, line)
	m.appendLog(outcome.Lines...)

	if outcome.Err != nil {
		m.setToast(outcome.Err.Error(), true)
		m.appendLog("error: " + outcome.Err.Error())
		m.tab = tabLog
		return m, nil
	}
	m.setToast("", false)

	switch {
	case outcome.Quit:
		return m.quit()
	case outcome.Abort:
		return m.abortRun(), nil
	case outcome.Analysis != nil:
		return m.startAnalysis(*outcome.Analysis)
	case outcome.Extraction != nil:
		return m.startExtraction(*outcome.Extraction)
	}
	if outcome.ClearPackets {
		m.packets, m.visible, m.cursor = nil, nil, 0
	}
	if outcome.ClearLog {
		m.logLines, m.logTop = nil, 0
	}
	if outcome.ClearTranscript {
		// The transcript and the log share one pane, so clearing either empties it. Saying so
		// is better than silently doing nothing to a command the user typed.
		m.logLines, m.logTop = nil, 0
	}
	return m, nil
}

// startAnalysis begins a run.
func (m Model) startAnalysis(request Request) (tea.Model, tea.Cmd) {
	if m.run.active {
		m.setToast("a run is already in flight, esc aborts it", true)
		return m, nil
	}
	ctx, cancel := context.WithCancel(context.Background())
	m.cancelRun = cancel
	m.packets, m.visible, m.cursor = nil, nil, 0
	m.run = runState{
		kind:    runKindAnalysis,
		label:   request.Label(),
		phase:   PhaseIndex,
		active:  true,
		started: m.now(),
	}
	m.tab = tabPackets
	m.prompt.Start()

	analyze := m.analyze
	progressCh, recordCh := m.progressCh, m.recordCh
	run := func() tea.Msg {
		result := analyze(ctx, request, Sink{
			Progress: progress.NewChannel(progressCh),
			Observe: func(record Record) {
				// The only thing this goroutine may do with a record is hand it over. A
				// blocking send is safe because the drain re-arms itself, and the context is
				// cancelled when the program quits.
				select {
				case recordCh <- record:
				case <-ctx.Done():
				}
			},
		})
		return analysisDoneMsg{Result: result}
	}
	return m, tea.Batch(run, tui.PromptTick())
}

// finishAnalysis applies the authoritative outcome of a run.
func (m Model) finishAnalysis(result Result) (tea.Model, tea.Cmd) {
	m.run.active = false
	m.run.aborted = result.Aborted
	m.run.aborting = false
	m.run.err = result.Err
	m.run.counters = result.Counters
	m.run.elapsed = result.Elapsed
	m.run.phase = PhaseAnalyze
	m.run.current, m.run.total = result.Counters.Walked, result.Counters.Walked
	m.prompt.Finish()
	m.cancelRun = nil

	// The streamed records are replaced rather than appended to: the batches and the done
	// message travel by different routes, so the result is the only ordering that can be
	// trusted.
	m.packets = result.Records
	m.state.RecordAnalysis(result)
	m.refilter()
	if m.follow {
		m.cursor = max(len(m.visible)-1, 0)
		m = m.revealCursor()
	}

	switch {
	case result.Err != nil:
		m.setToast("analyze failed: "+result.Err.Error(), true)
		m.appendLog("error: " + result.Err.Error())
		m.tab = tabLog
	case result.Aborted:
		m.setToast("aborted after "+itoa(result.Counters.Walked)+" packets", false)
	default:
		m.setToast(summarise(result.Counters), result.Counters.Issues() > 0)
	}
	return m, nil
}

// startExtraction begins an extraction.
func (m Model) startExtraction(request Request) (tea.Model, tea.Cmd) {
	if m.run.active {
		m.setToast("a run is already in flight, esc aborts it", true)
		return m, nil
	}
	ctx, cancel := context.WithCancel(context.Background())
	m.cancelRun = cancel
	m.run = runState{
		kind:    runKindExtraction,
		label:   "extract " + request.Protocol.Name + " " + baseName(request.PcapFile),
		phase:   PhaseAnalyze,
		active:  true,
		started: m.now(),
	}
	m.tab = tabLog
	m.prompt.Start()

	extract := m.extract
	progressCh, logCh := m.progressCh, m.logCh
	run := func() tea.Msg {
		err := extract(ctx, request, progress.NewChannel(progressCh), func(line string) {
			select {
			case logCh <- line:
			default:
				// The log is a courtesy: an extraction that outruns the display drops lines
				// rather than being throttled by it.
			}
		})
		return extractionDoneMsg{Request: request, Err: err}
	}
	return m, tea.Batch(run, tui.PromptTick())
}

// abortRun cancels whatever is in flight.
func (m Model) abortRun() Model {
	if m.cancelRun != nil {
		m.cancelRun()
		m.run.aborting = true
		m.setToast("aborting", false)
	} else {
		m.setToast("nothing to abort", false)
	}
	return m
}

// lineWriterFunc receives one line of output.
type lineWriterFunc func(line string)

// runExtraction is the real extraction, wired to the shared extractor.
//
// This is where progress.NewChannel meets extractor.Options: the extraction publishes values
// and the model turns them into messages, so nothing in the extractor ever touches the model.
func runExtraction(ctx context.Context, request Request, reporter progress.Reporter, out lineWriterFunc) error {
	writer := newLineWriter(out)
	defer writer.Flush()
	// A theme with colour switched off: the output goes into the log pane, which applies the
	// UI's own styling, and escape sequences arriving from underneath would fight it.
	theme := tui.NewTheme(tui.Options{Dark: true, NoColor: true, ASCII: true})
	return extractor.ExtractWithOptions(ctx, request.PcapFile, request.Protocol.Name, extractor.Options{
		Stdout:   writer,
		Stderr:   writer,
		Progress: reporter,
		Theme:    &theme,
	})
}

// waitForProgress reads ONE progress frame and returns it as a message, re-arming itself.
//
// This is the whole concurrency contract of the program: an analysis running on its own
// goroutine may publish values on a channel and nothing else. The version this replaces called
// application.QueueUpdateDraw from inside the analysis and mutated widgets there.
func waitForProgress(ch chan progress.Update) tea.Cmd {
	return func() tea.Msg {
		update, ok := <-ch
		if !ok {
			return nil
		}
		return progressMsg{Update: update}
	}
}

// waitForLog reads one log line, then takes whatever else is already queued, so that a burst
// of logging costs one turn of the event loop rather than one per line.
func waitForLog(ch chan string) tea.Cmd {
	return func() tea.Msg {
		line, ok := <-ch
		if !ok {
			return nil
		}
		lines := []string{line}
		for len(lines) < recordBatch {
			select {
			case next, ok := <-ch:
				if !ok {
					return logMsg{Lines: lines}
				}
				lines = append(lines, next)
			default:
				return logMsg{Lines: lines}
			}
		}
		return logMsg{Lines: lines}
	}
}

// waitForRecords reads one record and then drains what is already queued behind it.
func waitForRecords(ch chan Record) tea.Cmd {
	return func() tea.Msg {
		record, ok := <-ch
		if !ok {
			return nil
		}
		records := []Record{record}
		for len(records) < recordBatch {
			select {
			case next, ok := <-ch:
				if !ok {
					return recordsMsg{Records: records}
				}
				records = append(records, next)
			default:
				return recordsMsg{Records: records}
			}
		}
		return recordsMsg{Records: records}
	}
}

// fold adds one record to a set of counters, the same way Run does.
func fold(counters Counters, record Record) Counters {
	counters.Walked++
	switch record.Verdict {
	case VerdictParseFail:
		counters.ParseFail++
	case VerdictSerializeFail:
		counters.Parsed++
		counters.SerializeFail++
	case VerdictBytesDiffer:
		counters.Parsed++
		counters.CompareFail++
	case VerdictOK:
		counters.Parsed++
	default:
		counters.Skipped++
	}
	return counters
}

// summarise is the toast a finished run shows.
func summarise(counters Counters) string {
	if counters.Issues() == 0 {
		return "analysed " + itoa(counters.Walked) + " packets, no issues"
	}
	return "analysed " + itoa(counters.Walked) + " packets, " + itoa(counters.Issues()) + " issues"
}

// refilter recomputes which packets the table shows and keeps the cursor in range.
func (m *Model) refilter() {
	m.visible = m.visible[:0]
	needle := strings.ToLower(strings.TrimSpace(m.filter))
	for i, record := range m.packets {
		if m.tab == tabFindings && !record.Verdict.IsIssue() {
			continue
		}
		if needle != "" && !recordMatches(record, needle) {
			continue
		}
		m.visible = append(m.visible, i)
	}
	if m.cursor >= len(m.visible) {
		m.cursor = max(len(m.visible)-1, 0)
	}
	if m.cursor < 0 {
		m.cursor = 0
	}
	// Follow, filter and a fresh result all move the cursor without a keypress. The window has
	// to keep up, or the stored offset says one thing while the renderer clamps to another.
	*m = m.revealCursor()
}

// recordMatches reports whether a record satisfies the table filter.
//
// bubbles/v2/table has no filtering of any kind, so this is ours. The full slice is kept and a
// display slice is derived from it, which is also what keeps the "n of m" counter honest.
func recordMatches(record Record, needle string) bool {
	for _, field := range []string{
		itoa(record.Number),
		record.Protocol,
		record.Summary,
		record.Verdict.String(),
		record.Reason,
	} {
		if strings.Contains(strings.ToLower(field), needle) {
			return true
		}
	}
	return false
}

// selectedRecord is the record the cursor is on.
func (m Model) selectedRecord() (Record, bool) {
	if m.cursor < 0 || m.cursor >= len(m.visible) {
		return Record{}, false
	}
	return m.packets[m.visible[m.cursor]], true
}

// findings are the records that represent a defect.
func (m Model) findings() []Record {
	var out []Record
	for _, record := range m.packets {
		if record.Verdict.IsIssue() {
			out = append(out, record)
		}
	}
	return out
}

// toolKeys are the bindings this tool adds to the shared set. They are all bare characters and
// therefore all pane-scoped: at the prompt they must be the letters they look like.
type toolKeys struct {
	TabNext   key.Binding
	TabPrev   key.Binding
	ViewBytes key.Binding
	ViewTree  key.Binding
	ViewDiff  key.Binding
	Open      key.Binding
	Analyze   key.Binding
	Extract   key.Binding
}

// toolKeysFor resolves the tool bindings for a focus, through the shared Scoped helper so they
// get exactly the same treatment as the shared ones.
func toolKeysFor(focus tui.Focus) toolKeys {
	scoped := func(keys, label, description string) key.Binding {
		return tui.Scoped(
			key.NewBinding(key.WithKeys(strings.Split(keys, ",")...), key.WithHelp(label, description)),
			tui.ScopePane, focus)
	}
	return toolKeys{
		TabNext:   scoped("]", "]", "next tab"),
		TabPrev:   scoped("[", "[", "prev tab"),
		ViewBytes: scoped("b", "b", "bytes"),
		ViewTree:  scoped("t", "t", "tree"),
		ViewDiff:  scoped("d", "d", "diff"),
		Open:      scoped("o", "o", "open"),
		Analyze:   scoped("a", "a", "analyze"),
		Extract:   scoped("x", "x", "extract"),
	}
}

// baseName is the file name part of a path, for a label that has to fit in a border.
func baseName(path string) string {
	if index := strings.LastIndexAny(path, `/\`); index >= 0 {
		return path[index+1:]
	}
	return path
}
