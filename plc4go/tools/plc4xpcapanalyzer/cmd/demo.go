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
	"fmt"

	"github.com/pkg/errors"
	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/ui"
)

// --demo on the commands that read a capture.
//
// The interface has had it since the port: it generates a small capture of real traffic and
// analyses it, so the tool can be tried out and manually debugged with nothing to hand. The
// commands that print rather than draw accepted the flag and then demanded their two arguments,
// so the only way to run them was with a real capture on disk -- which meant a machine-readable
// report, the thing most worth showing to anyone who does not already have a capture, could not
// be produced at all without one.

// demoRequested reports whether --demo was passed. It is a persistent flag on the root, so
// every command sees it.
func demoRequested() bool { return config.RootConfigInstance.Demo }

// demoCapture generates the demo capture and returns what to analyse, plus a cleanup.
//
// wantProtocol is the protocol the user named, or empty when the command takes it from the
// demo. Naming a protocol the demo does not speak is refused rather than obeyed: analysing
// C-Bus traffic as BACnet produces a screenful of failures that say nothing about either.
func demoCapture(cmd *cobra.Command, wantProtocol string) (protocolName, pcapFile string, cleanup func(), err error) {
	demo, err := ui.NewDemo()
	if err != nil {
		return "", "", func() {}, err
	}
	cleanup = func() { _ = demo.Cleanup() }

	if wantProtocol != "" {
		requested, resolveErr := protocol.Resolve(wantProtocol)
		if resolveErr != nil {
			cleanup()
			return "", "", func() {}, resolveErr
		}
		if requested.Name != demo.Protocol.Name {
			cleanup()
			// Name the command that would work. This message is only reachable from a
			// protocol-fixed subcommand, so the command to suggest is its parent.
			generic := cmd.CommandPath()
			if parent := cmd.Parent(); parent != nil {
				generic = parent.CommandPath()
			}
			return "", "", func() {}, errors.Errorf(
				"the demo capture is %s, so it cannot be analysed as %s: run %q without a protocol, or give a real capture",
				demo.Protocol.Name, requested.Name, generic+" --demo")
		}
	}

	// The client address matters more than it looks. C-Bus encodes a request differently from a
	// response, so without it most of the capture is read the wrong way round and what should
	// be one finding is reported as six. Only when the user has not said otherwise.
	if config.AnalyzeConfigInstance.Client == "" {
		config.AnalyzeConfigInstance.Client = demo.Client
	}

	_, _ = fmt.Fprintf(cmd.OutOrStdout(),
		"Demo mode: generated %s, a %s capture of ten packets, eight of which round-trip\n",
		demo.Path, demo.Protocol.Name)
	return demo.Protocol.Name, demo.Path, cleanup, nil
}

// protocolFrom is the protocol a generic command was given, or empty when it was given none.
// With --demo the arguments are optional, so there may be nothing there.
func protocolFrom(args []string) string {
	if len(args) == 0 {
		return ""
	}
	return args[0]
}

// analyseDemo analyses the generated capture.
func analyseDemo(cmd *cobra.Command, wantProtocol string) error {
	protocolName, pcapFile, cleanup, err := demoCapture(cmd, wantProtocol)
	if err != nil {
		return err
	}
	defer cleanup()
	return analyse(cmd, pcapFile, protocolName)
}

// extractDemo extracts from the generated capture.
func extractDemo(cmd *cobra.Command, wantProtocol string) error {
	protocolName, pcapFile, cleanup, err := demoCapture(cmd, wantProtocol)
	if err != nil {
		return err
	}
	defer cleanup()
	return extract(cmd, pcapFile, protocolName)
}
