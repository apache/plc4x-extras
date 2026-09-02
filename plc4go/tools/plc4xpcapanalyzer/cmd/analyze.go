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
	"math"
	"os"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/analyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// analyzeCmd represents the analyze command
var analyzeCmd = &cobra.Command{
	Use:   "analyze [protocolType] [pcapfile]",
	Short: "analyzes a pcap file using a driver supplied driver",
	Long: `Analyzes a pcap file using a driver
TODO: document me
`,
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
		if err := analyzer.Analyze(pcapFile, protocolType); err != nil {
			return err
		}
		_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Done")
		return nil
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
}
