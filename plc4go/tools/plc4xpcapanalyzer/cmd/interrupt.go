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
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/extractor"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/report"
)

// reportPath is where a report should be written, or empty for none. It reads the flag through
// the configuration singleton the rest of the tool uses, so "conf set" reaches it too.
func reportPath() string { return config.AnalyzeConfigInstance.ReportFile }

// The analysis and the extraction are reached through variables so that a test can see what
// context the command hands them. That is the thing worth pinning: the loops themselves have
// always checked their context between packets, but the entry points passed context.TODO, so
// the check could never fire and the abort was implemented and unreachable. A test that only
// reads the command's output cannot tell the difference, because the message it prints comes
// from the same context the command cancelled.
var (
	runAnalysis  = analyzer.AnalyzeWithOptions
	runExtractor = extractor.Extract
)

// interruptible returns a context cancelled by the first interrupt, and a function releasing
// the handler.
//
// The analyzer and the extractor both check their context between packets and stop cleanly,
// keeping whatever they have already gathered. Nothing ever gave them a cancellable one: the
// entry points passed context.TODO and the root passes context.Background, so the abort was
// implemented and could not be asked for. A capture of any size therefore had exactly one way
// to stop, which was for the process to be killed part way through a report.
//
// Installed per command rather than on the root, and deliberately. The ui subcommand runs a
// terminal interface that reads ctrl+c as a key press and asks before it exits; cancelling its
// context from underneath would take that decision away from the user.
func interruptible(cmd *cobra.Command) (context.Context, func()) {
	return signal.NotifyContext(cmd.Context(), os.Interrupt, syscall.SIGTERM)
}

// analyse is the body every analysing command shares: install the interrupt handler, run the
// analysis under the context it produces, collect what it found, and say how the run ended.
func analyse(cmd *cobra.Command, pcapFile, protocolName string) error {
	ctx, release := interruptible(cmd)
	defer release()

	collected := report.Report{
		Capture:  pcapFile,
		Protocol: protocolName,
		Started:  time.Now(),
	}
	options := analyzer.Options{Stdout: os.Stdout, Stderr: os.Stderr}
	// Only collect when there is somewhere to put it. A run over a large capture would
	// otherwise hold every payload in memory to no purpose.
	if wanted := reportPath(); wanted != "" {
		options.OnFinding = func(found finding.Finding) {
			collected.Findings = append(collected.Findings, found)
		}
		// The analyzer's own totals rather than a second tally over the findings: the number of
		// packets in the capture is a property of the capture, not of any finding, and counting
		// twice is how the two paths came to disagree in the first place.
		options.OnCounters = func(counters finding.Counters) {
			collected.Counters = counters
		}
	}

	if err := runAnalysis(ctx, pcapFile, protocolName, options); err != nil {
		return err
	}

	// The report is written for an interrupted run too, marked as partial. What was examined
	// before the interrupt is still evidence, and discarding it would make ctrl+c cost more
	// than it saves.
	collected.Elapsed = time.Since(collected.Started)
	collected.Aborted = ctx.Err() != nil
	if err := writeReport(cmd, collected); err != nil {
		return err
	}

	reportOutcome(cmd, ctx)
	return nil
}

// writeReport writes the report the --report flag asked for, if it asked for one.
func writeReport(cmd *cobra.Command, collected report.Report) error {
	path := reportPath()
	if path == "" {
		return nil
	}
	if err := report.WriteFile(path, collected); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Report written to %s\n", path)
	return nil
}

// extract is the same for the extracting command.
func extract(cmd *cobra.Command, pcapFile, protocolName string) error {
	ctx, release := interruptible(cmd)
	defer release()

	if err := runExtractor(ctx, pcapFile, protocolName); err != nil {
		return err
	}
	reportOutcome(cmd, ctx)
	return nil
}

// reportOutcome says how a run ended, which is not always how it was asked to end.
//
// "Done" used to be printed unconditionally. Now that an interrupt actually reaches the loop,
// printing it for a run the user stopped would claim a complete report where there is a partial
// one -- the same class of untruth as a help entry for a key that does nothing.
func reportOutcome(cmd *cobra.Command, ctx context.Context) {
	if ctx.Err() != nil {
		_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Aborted")
		return
	}
	_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Done")
}
