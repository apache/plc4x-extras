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
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// bacnetCmd represents the bacnet command
var bacnetCmd = &cobra.Command{
	Use:   "bacnet [pcapfile]",
	Short: "Analyse a capture as BACnet/IP",
	Long: `Runs a capture through plc4x's BACnet/IP codec: the analyze command with the protocol
fixed, and with the BACnet filter flag available.

The default filter is "udp port 47808 and udp[4:2] > 29", which drops packets whose UDP length
is 29 bytes or less. That excludes a bare Who-Is, which is 12 bytes: pass
--default-bacnet-filter to widen it if the short messages are what you are looking for.

An interrupt stops the run and keeps the counts gathered so far.`,
	Args: func(cmd *cobra.Command, args []string) error {
		if demoRequested() {
			// The demo supplies the capture.
			return nil
		}
		if len(args) < 1 {
			return errors.New("requires exactly one arguments")
		}
		pcapFile := args[0]
		if _, err := os.Stat(pcapFile); errors.Is(err, os.ErrNotExist) {
			return errors.Errorf("Pcap file not found %s", pcapFile)
		}
		return nil
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		if demoRequested() {
			return analyseDemo(cmd, protocol.BacnetIP.Name)
		}
		pcapFile := args[0]
		return analyse(cmd, pcapFile, protocol.BacnetIP.Name)
	},
}

func init() {
	analyzeCmd.AddCommand(bacnetCmd)

	bacnetCmd.PersistentFlags().StringVarP(&config.BacnetConfigInstance.BacnetFilter, "default-bacnet-filter", "", "udp port 47808 and udp[4:2] > 29", "Defines the default filter when bacnet is selected")

	addAnalyzeFlags(bacnetCmd)
}
