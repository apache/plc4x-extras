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

package cmd

import (
	"bytes"
	"context"
	"encoding/json"
	"encoding/xml"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/spf13/cobra"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
)

// An interrupt has to reach the analysis loop, and the outcome has to say which way the run
// ended. Both were broken in the same way: the loop checked a context nothing could cancel, and
// the command printed "Done" whatever happened.

// TestInterruptibleCancelsWithItsParent checks the returned context is derived from the
// command's, so an already-cancelled command does not start work at all.
func TestInterruptibleCancelsWithItsParent(t *testing.T) {
	parent, cancel := context.WithCancel(context.Background())
	command := &cobra.Command{}
	command.SetContext(parent)

	ctx, release := interruptible(command)
	defer release()
	require.NoError(t, ctx.Err(), "a live parent means a live context")

	cancel()
	<-ctx.Done()
	assert.Error(t, ctx.Err(), "cancelling the parent has to cancel the run")
}

// TestReportOutcomeDistinguishesAnAbortFromCompletion is the honesty half. "Done" used to be
// printed unconditionally, so once an interrupt could actually stop the loop it would have
// claimed a complete report over a partial one.
func TestReportOutcomeDistinguishesAnAbortFromCompletion(t *testing.T) {
	for name, test := range map[string]struct {
		ctx  func() context.Context
		want string
	}{
		"completed": {
			ctx:  context.Background,
			want: "Done",
		},
		"interrupted": {
			ctx: func() context.Context {
				ctx, cancel := context.WithCancel(context.Background())
				cancel()
				return ctx
			},
			want: "Aborted",
		},
	} {
		out := &bytes.Buffer{}
		command := &cobra.Command{}
		command.SetOut(out)

		reportOutcome(command, test.ctx())

		assert.Equal(t, test.want, strings.TrimSpace(out.String()), name)
	}
}

// TestEveryCommandDocumentsItself is the same rule as the interfaces' help: a placeholder is
// worse than nothing, because it looks like documentation. Six commands shipped with "TODO:
// document me" as their long help, and the ui command's described the bacnet command instead.
func TestEveryCommandDocumentsItself(t *testing.T) {
	var walk func(command *cobra.Command)
	seen := 0
	walk = func(command *cobra.Command) {
		if command.Name() != "help" && command.Name() != "completion" {
			seen++
			t.Run(command.CommandPath(), func(t *testing.T) {
				assert.NotEmpty(t, command.Short, "needs a one-line description")
				require.NotEmpty(t, command.Long, "needs a long description")
				for _, placeholder := range []string{"TODO", "todo", "document me", "describe me"} {
					assert.NotContains(t, command.Long, placeholder,
						"a placeholder reads as documentation and is worse than none")
					assert.NotContains(t, command.Short, placeholder)
				}
				assert.Greater(t, len(command.Long), len(command.Short),
					"the long description should say more than the short one")
			})
		}
		for _, child := range command.Commands() {
			walk(child)
		}
	}
	walk(rootCmd)
	assert.Positive(t, seen, "the tree has to have commands in it")
}

// TestEveryRunCommandHandsDownTheCommandsContext is the guard for the original defect: the
// loops have always checked their context between packets, but the entry points passed
// context.TODO, so the check could never fire. A test reading only the command's output cannot
// see that -- the message it prints comes from the same context the command cancelled -- so
// these swap the seam and inspect the context while the run is still in progress. It has to be
// inspected there: the interrupt handler is released when the command returns, which cancels
// the context, so afterwards every context looks cancelled whatever it was derived from.
func TestEveryRunCommandHandsDownTheCommandsContext(t *testing.T) {
	capture := existingPcapPath(t)

	for name, test := range map[string]struct {
		args []string
		seam func(func(context.Context)) func()
	}{
		// Four distinct commands, and distinct on purpose. "analyze bacnet" resolves to the
		// bacnet subcommand rather than to analyze's own body, so reaching the generic path
		// takes a protocol name that is not also a subcommand -- bacnetip. Running the same
		// command twice would not work either: cobra hands a child the context from the
		// previous Execute, so the second run would start already cancelled.
		"analyze":        {args: []string{"analyze", "bacnetip", capture}, seam: withAnalysisSeam},
		"analyze bacnet": {args: []string{"analyze", "bacnet", capture}, seam: withAnalysisSeam},
		"analyze c-bus":  {args: []string{"analyze", "c-bus", capture}, seam: withAnalysisSeam},
		"extract":        {args: []string{"extract", "c-bus", capture}, seam: withExtractionSeam},
	} {
		t.Run(name, func(t *testing.T) {
			resetContexts(rootCmd)
			parent, cancel := context.WithCancel(context.Background())
			var live, derived, called bool

			restore := test.seam(func(ctx context.Context) {
				called = true
				live = ctx.Err() == nil
				// Cancelling the command's context has to cancel the one the run was given.
				// Bounded: a context that is not derived from the command's never becomes
				// done, and waiting on it unbounded would hang the suite rather than fail it.
				cancel()
				select {
				case <-ctx.Done():
					derived = true
				case <-time.After(2 * time.Second):
					derived = false
				}
			})
			defer restore()

			out := &bytes.Buffer{}
			rootCmd.SetOut(out)
			rootCmd.SetErr(out)
			rootCmd.SetArgs(test.args)
			t.Cleanup(func() {
				rootCmd.SetOut(nil)
				rootCmd.SetErr(nil)
				rootCmd.SetArgs(nil)
			})

			require.NoError(t, rootCmd.ExecuteContext(parent))
			require.True(t, called, "the command has to run the work at all")
			assert.True(t, live, "the run has to start with a context that is not already done")
			assert.True(t, derived,
				"the context handed down has to be the command's, not a fresh background one")
			assert.Contains(t, out.String(), "Aborted",
				"and a run stopped part way has to say so rather than reporting Done")
		})
	}
}

// withAnalysisSeam replaces the analysis with an observer of the context it is handed.
func withAnalysisSeam(observe func(context.Context)) func() {
	saved := runAnalysis
	runAnalysis = func(ctx context.Context, _, _ string, _ analyzer.Options) error {
		observe(ctx)
		return nil
	}
	return func() { runAnalysis = saved }
}

// withExtractionSeam does the same for the extraction.
func withExtractionSeam(observe func(context.Context)) func() {
	saved := runExtractor
	runExtractor = func(ctx context.Context, _, _ string) error {
		observe(ctx)
		return nil
	}
	return func() { runExtractor = saved }
}

// --- the report flag ---

// TestTheReportFlagWritesAJUnitReport is the feature end to end, through the command a user
// actually runs. The three TODOs this replaces sat on the three failure counters: a finding
// existed only as a log line among info lines for every packet, so a codec regression could not
// survive the run.
func TestTheReportFlagWritesAJUnitReport(t *testing.T) {
	capture := realCapture(t)
	path := filepath.Join(t.TempDir(), "target", "surefire-reports", "analyzer.xml")

	out := runCommand(t, []string{"analyze", "c-bus", capture, "-c", pcapfixture.ClientIP, "--report", path})
	assert.Contains(t, out, "Report written to "+path)
	assert.Contains(t, out, "Done")

	document, err := os.ReadFile(path)
	require.NoError(t, err, "the report has to be where it said it was")

	var parsed struct {
		Tests    int `xml:"tests,attr"`
		Failures int `xml:"failures,attr"`
		Skipped  int `xml:"skipped,attr"`
		Suites   []struct {
			Name       string `xml:"name,attr"`
			Properties []struct {
				Name  string `xml:"name,attr"`
				Value string `xml:"value,attr"`
			} `xml:"properties>property"`
			Cases []struct {
				Name    string `xml:"name,attr"`
				Failure *struct {
					Type   string `xml:"type,attr"`
					Detail string `xml:",chardata"`
				} `xml:"failure"`
				Skipped *struct{} `xml:"skipped"`
			} `xml:"testcase"`
		} `xml:"testsuite"`
	}
	require.NoError(t, xml.Unmarshal(document, &parsed), "CI has to be able to parse it")

	// The same capture the interface's demo uses, and the same verdicts: eight clean round
	// trips, one parse failure and one skip.
	assert.Equal(t, 10, parsed.Tests)
	assert.Equal(t, 1, parsed.Failures, "one defect, and the skip is not one")
	assert.Equal(t, 1, parsed.Skipped)

	require.Len(t, parsed.Suites, 1)
	assert.Equal(t, filepath.Base(capture), parsed.Suites[0].Name)

	failures := 0
	for _, one := range parsed.Suites[0].Cases {
		if one.Failure == nil {
			continue
		}
		failures++
		assert.Equal(t, "parse", one.Failure.Type)
		assert.Contains(t, one.Failure.Detail, "original (", "the bytes have to be in there")
	}
	assert.Equal(t, 1, failures)
}

// TestTheReportFollowsItsExtension covers the other format, and that the flag is the only thing
// choosing between them.
func TestTheReportFollowsItsExtension(t *testing.T) {
	capture := realCapture(t)
	path := filepath.Join(t.TempDir(), "report.json")

	runCommand(t, []string{"analyze", "c-bus", capture, "-c", pcapfixture.ClientIP, "--report", path})

	document, err := os.ReadFile(path)
	require.NoError(t, err)

	var parsed struct {
		Protocol string `json:"protocol"`
		Aborted  bool   `json:"aborted"`
		Counters struct {
			Walked         int `json:"Walked"`
			TotalInCapture int `json:"TotalInCapture"`
		} `json:"counters"`
		Findings []struct {
			Verdict string `json:"verdict"`
			Issue   bool   `json:"issue"`
		} `json:"findings"`
	}
	require.NoError(t, json.Unmarshal(document, &parsed))

	assert.Equal(t, "c-bus", parsed.Protocol)
	assert.False(t, parsed.Aborted)
	assert.Equal(t, 10, parsed.Counters.Walked)
	assert.Equal(t, 10, parsed.Counters.TotalInCapture,
		"the packet count comes from the analyzer, which is the only thing that knows it")
	require.Len(t, parsed.Findings, 10)

	issues := 0
	for _, found := range parsed.Findings {
		if found.Issue {
			issues++
		}
	}
	assert.Equal(t, 1, issues)
}

// TestNoReportIsWrittenWithoutTheFlag is the default, and the reason the findings are only
// collected when asked for: a run over a large capture would otherwise hold every payload in
// memory to no purpose.
func TestNoReportIsWrittenWithoutTheFlag(t *testing.T) {
	capture := realCapture(t)
	directory := t.TempDir()

	out := runCommand(t, []string{"analyze", "c-bus", capture, "-c", pcapfixture.ClientIP})
	assert.NotContains(t, out, "Report written")

	entries, err := os.ReadDir(directory)
	require.NoError(t, err)
	assert.Empty(t, entries, "nothing should have been written anywhere")
}

// realCapture writes the demo capture: real C-Bus traffic, eight packets that round-trip and
// two the codec will not accept.
func realCapture(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "cbus.pcap")
	require.NoError(t, pcapfixture.WriteCBus(path, pcapfixture.CBusSessionWithFailures()))
	return path
}

// runCommand runs the real command tree and returns what it printed.
func runCommand(t *testing.T, args []string) string {
	t.Helper()
	pinCommandGlobals(t)
	resetContexts(rootCmd)

	out := &bytes.Buffer{}
	rootCmd.SetOut(out)
	rootCmd.SetErr(out)
	rootCmd.SetArgs(args)
	t.Cleanup(func() {
		rootCmd.SetOut(nil)
		rootCmd.SetErr(nil)
		rootCmd.SetArgs(nil)
	})

	require.NoError(t, rootCmd.ExecuteContext(context.Background()))
	return out.String()
}

// resetContexts clears the context every command in the tree is holding.
//
// cobra propagates the root's context to the command it resolved only when that command has
// none: "if cmd.ctx == nil { cmd.ctx = c.ctx }" (cobra v1.9.1, command.go:1144). So a command
// that has run once keeps that first context for the life of the process, and a test that
// cancelled one -- which the seam test above does deliberately -- leaves the next run of the
// same command starting already cancelled, reporting nothing and looking like a broken feature.
//
// nil rather than a fresh background context, for the same reason: anything non-nil is kept,
// which would leave the command with a context that never cancels and defeat the seam test.
//
// The tool itself cannot hit this, because Execute runs once per process. A test binary that
// runs the tree repeatedly hits it constantly.
func resetContexts(command *cobra.Command) {
	command.SetContext(nil)
	for _, child := range command.Commands() {
		resetContexts(child)
	}
}

// pinCommandGlobals saves and restores the configuration singletons the commands write into,
// including the report path, which is a flag bound to one of them.
func pinCommandGlobals(t *testing.T) {
	t.Helper()
	savedRoot, savedPcap := config.RootConfigInstance, config.PcapConfigInstance
	savedAnalyze, savedCBus := config.AnalyzeConfigInstance, config.CBusConfigInstance
	savedLogger, savedLevel := log.Logger, zerolog.GlobalLevel()
	t.Cleanup(func() {
		config.RootConfigInstance, config.PcapConfigInstance = savedRoot, savedPcap
		config.AnalyzeConfigInstance, config.CBusConfigInstance = savedAnalyze, savedCBus
		log.Logger = savedLogger
		zerolog.SetGlobalLevel(savedLevel)
	})
	config.RootConfigInstance.HideProgressBar = true
	config.AnalyzeConfigInstance.ReportFile = ""
	config.PcapConfigInstance.PackageNumberLimit = ^uint(0)
	log.Logger = zerolog.New(io.Discard)
	zerolog.SetGlobalLevel(zerolog.Disabled)
}
