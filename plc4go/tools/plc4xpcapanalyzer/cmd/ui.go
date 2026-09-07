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
	"os"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/ui"
)

// uiCmd represents the ui command
var uiCmd = &cobra.Command{
	Use:   "ui [pcapfile]",
	Short: "Start the terminal interface, optionally on a capture",
	Long: `Opens the terminal interface, on the given capture if one is named.

The interface does what the command line does and shows the result rather than printing it: the
packet list with a verdict against each, and a detail pane with the raw bytes, the parsed tree
and, where the round trip failed, a diff of the original bytes against the reserialized ones
with the first differing offset marked.

  --demo   generate a small capture of real C-Bus traffic and analyse it immediately, so the
           tool can be tried out and manually debugged with no capture and no device at hand

Press ? for the keys. Ctrl+C stops a run in progress rather than the session; with nothing
running it offers to quit.`,
	Args: func(_ *cobra.Command, args []string) error {
		if len(args) < 1 {
			return nil
		}
		pcapFile := args[0]
		if _, err := os.Stat(pcapFile); errors.Is(err, os.ErrNotExist) {
			return errors.Errorf("Pcap file not found %s", pcapFile)
		}
		return nil
	},
	// RunE, not Run: the terminal UI reports a failure to start by returning it, so that cobra
	// prints it and the process exits non-zero. The version this replaced could only panic.
	RunE: func(cmd *cobra.Command, args []string) error {
		var pcapFile string
		if len(args) > 0 {
			pcapFile = args[0]
		}
		return ui.Run(cmd.Context(), ui.RunOptions{
			PcapFile: pcapFile,
			// --demo generates a small capture of real C-Bus traffic and analyses it, so the
			// tool can be demonstrated and manually debugged with no capture to hand.
			Demo: config.RootConfigInstance.Demo,
			// --ascii forces the ASCII glyph set; without it the glyph set is guessed from the
			// locale.
			Ascii: config.RootConfigInstance.Ascii,
		})
	},
}

func init() {
	rootCmd.AddCommand(uiCmd)
}
