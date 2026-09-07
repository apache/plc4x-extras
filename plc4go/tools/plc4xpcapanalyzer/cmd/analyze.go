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
	"math"
	"os"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// analyzeCmd represents the analyze command
var analyzeCmd = &cobra.Command{
	Use:   "analyze [protocolType] [pcapfile]",
	Short: "Parse, reserialize and compare every packet in a capture",
	Long: `Runs a capture through a plc4x codec and reports what it cannot handle.

Each packet's application payload is parsed, the message that comes back is re-serialized, and
the bytes are compared against the original. Three things can go wrong, and they are counted
separately because they mean different things:

  parse       the codec was handed a message and could not read it
  serialize   the codec read the message but could not write it back
  compare     it wrote it back as different bytes, so reader and writer disagree

A packet the protocol itself says is not a whole message -- a split transmission, an empty
packet, an echo -- is skipped rather than failed. A skip is not a defect and is not counted as
one.

The protocol is bacnetip or c-bus; bacnet and cbus are accepted as aliases. The bacnet and c-bus
subcommands do the same job with the protocol fixed and their own filter flags available.

An interrupt stops the run and keeps the counts gathered so far.`,
	Args: func(cmd *cobra.Command, args []string) error {
		if demoRequested() {
			// The demo supplies the capture, and for the generic commands the protocol too.
			return nil
		}
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
		if demoRequested() {
			return analyseDemo(cmd, protocolFrom(args))
		}
		protocolType := args[0]
		pcapFile := args[1]
		return analyse(cmd, pcapFile, protocolType)
	},
}

func init() {
	rootCmd.AddCommand(analyzeCmd)

	addAnalyzeFlags(analyzeCmd)
}

func addAnalyzeFlags(command *cobra.Command) {
	command.Flags().StringVarP(&config.AnalyzeConfigInstance.Filter, "filter", "f", "", "BFF filter to apply")
	command.Flags().BoolVarP(&config.AnalyzeConfigInstance.NoFilter, "no-filter", "n", false, "disable filter")
	command.Flags().BoolVarP(&config.AnalyzeConfigInstance.OnlyParse, "only-parse", "o", false, "only parse messaged")
	command.Flags().BoolVarP(&config.AnalyzeConfigInstance.NoBytesCompare, "no-bytes-compare", "b", false, "don't compare original bytes with serialized bytes")
	command.Flags().BoolVarP(&config.AnalyzeConfigInstance.NoCustomMapping, "no-custom-mapping", "", false, "don't use the custom mapper for protocols")
	command.Flags().StringVarP(&config.AnalyzeConfigInstance.Client, "client", "c", "", "The client ip (this is useful for protocols where request/response is different e.g. modbus, cbus)")
	command.Flags().UintVarP(&config.AnalyzeConfigInstance.StartPackageNumber, "start-package-umber", "s", 0, "Defines with what package number should be started")
	command.Flags().UintVarP(&config.AnalyzeConfigInstance.PackageNumberLimit, "package-number-limit", "l", math.MaxUint, "Defines how many packages should be parsed")
	command.Flags().StringVarP(&config.AnalyzeConfigInstance.ReportFile, "report", "r", "",
		"write a machine-readable report of what the run found: JUnit XML, or JSON if the name ends in .json")
}
