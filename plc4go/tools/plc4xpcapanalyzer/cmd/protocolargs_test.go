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
	"path/filepath"
	"testing"

	"github.com/spf13/cobra"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// existingPcapPath creates an empty file so the argument validators, which only stat the path,
// get past their existence check. No capture parsing happens at this layer.
func existingPcapPath(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "capture.pcap")
	require.NoError(t, os.WriteFile(path, nil, 0o600))
	return path
}

// TestAnalyzeArgsAcceptsEveryAdvertisedProtocolSpelling is the command-layer regression test for
// the defect where "bacnet" was accepted here but rejected further down, so the run ended in a
// panic instead of an analysis.
func TestAnalyzeArgsAcceptsEveryAdvertisedProtocolSpelling(t *testing.T) {
	pcapFile := existingPcapPath(t)
	for _, name := range []string{"bacnet", "bacnetip", "c-bus", "cbus", "BACnet", "C-Bus"} {
		t.Run(name, func(t *testing.T) {
			require.NoError(t, analyzeCmd.Args(analyzeCmd, []string{name, pcapFile}))
		})
	}
}

func TestExtractArgsAcceptsEveryAdvertisedProtocolSpelling(t *testing.T) {
	pcapFile := existingPcapPath(t)
	for _, name := range []string{"bacnet", "bacnetip", "c-bus", "cbus"} {
		t.Run(name, func(t *testing.T) {
			require.NoError(t, extractCmd.Args(extractCmd, []string{name, pcapFile}))
		})
	}
}

func TestProtocolArgsRejectUnknownProtocolWithAnActionableMessage(t *testing.T) {
	pcapFile := existingPcapPath(t)
	for _, tc := range []struct {
		name string
		cmd  *cobra.Command
	}{
		{"analyze", analyzeCmd},
		{"extract", extractCmd},
	} {
		t.Run(tc.name, func(t *testing.T) {
			err := tc.cmd.Args(tc.cmd, []string{"modbus", pcapFile})
			require.Error(t, err)
			// The old message printed a Go map of empty-interface values, e.g.
			// "map[bacnet:<nil> c-bus:<nil>]". A user needs the real names instead.
			assert.Contains(t, err.Error(), "bacnetip")
			assert.Contains(t, err.Error(), "c-bus")
			assert.NotContains(t, err.Error(), "<nil>", "the protocol list must not leak a Go map dump")
		})
	}
}

func TestProtocolArgsRejectMissingPcapFile(t *testing.T) {
	err := analyzeCmd.Args(analyzeCmd, []string{"bacnet", filepath.Join(t.TempDir(), "absent.pcap")})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "not found")
}

func TestProtocolArgsRejectTooFewArguments(t *testing.T) {
	require.Error(t, analyzeCmd.Args(analyzeCmd, []string{"bacnet"}))
	require.Error(t, extractCmd.Args(extractCmd, []string{"bacnet"}))
}

// TestCommandsUseRunESoFailuresAreNotPanics pins the second half of the same defect: the
// commands used to call panic(err), so any analysis error surfaced as a goroutine dump rather
// than a CLI error message.
func TestCommandsUseRunESoFailuresAreNotPanics(t *testing.T) {
	for name, cmd := range map[string]*cobra.Command{
		"analyze": analyzeCmd,
		"extract": extractCmd,
		"bacnet":  bacnetCmd,
		"cbus":    cbusCmd,
		"ui":      uiCmd,
	} {
		t.Run(name, func(t *testing.T) {
			assert.NotNil(t, cmd.RunE, "%s must report failures as errors", name)
			assert.Nil(t, cmd.Run, "%s must not also define Run, which would shadow RunE", name)
		})
	}
}
