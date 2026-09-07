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

	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/extractor"
)

// The analysis and the extraction are reached through variables so that a test can see what
// context the command hands them. That is the thing worth pinning: the loops themselves have
// always checked their context between packets, but the entry points passed context.TODO, so
// the check could never fire and the abort was implemented and unreachable. A test that only
// reads the command's output cannot tell the difference, because the message it prints comes
// from the same context the command cancelled.
var (
	runAnalysis  = analyzer.Analyze
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
// analysis under the context it produces, and say how the run ended.
func analyse(cmd *cobra.Command, pcapFile, protocolName string) error {
	ctx, release := interruptible(cmd)
	defer release()

	if err := runAnalysis(ctx, pcapFile, protocolName); err != nil {
		return err
	}
	reportOutcome(cmd, ctx)
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
