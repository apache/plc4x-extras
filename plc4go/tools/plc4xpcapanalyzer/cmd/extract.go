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

// extractCmd represents the extract command
var extractCmd = &cobra.Command{
	Use:   "extract [protocolType] [pcapfile]",
	Short: "Dump the application payloads of a capture",
	Long: `Writes out the application payload of each packet in a capture, without parsing it.

This is the step before analyze: it shows what the codec is going to be handed, which is what
you want when a capture is not being read the way you expect and the question is whether the
payloads or the codec are at fault.

The payloads are only printed at verbosity two or above, so pass -vv. Without it the command
walks the capture and prints nothing, which looks like a failure and is not one.

The protocol is bacnetip or c-bus; bacnet and cbus are accepted as aliases.

An interrupt stops the run.`,
	Args: func(cmd *cobra.Command, args []string) error {
		if len(args) < 2 {
			return errors.New("requires exactly two arguments")
		}
		if _, err := protocol.Resolve(args[0]); err != nil {
			return err
		}
		pcapFile := args[1]
		if _, err := os.Stat(pcapFile); errors.Is(err, os.ErrNotExist) {
			return errors.Errorf("Pcap file not found %s", pcapFile)
		}
		return nil
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		protocolType := args[0]
		pcapFile := args[1]
		return extract(cmd, pcapFile, protocolType)
	},
}

func init() {
	rootCmd.AddCommand(extractCmd)

	extractCmd.Flags().StringVarP(&config.ExtractConfigInstance.Client, "client", "c", "", "The client ip (this is useful for protocols where request/response is different e.g. modbus, cbus)")
	extractCmd.Flags().BoolVarP(&config.ExtractConfigInstance.ShowDirectionalIndicators, "show-directional-indicators", "", true, "Indicates if directional markers should be printed")
}
