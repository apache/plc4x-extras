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

package protocol

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestResolveAcceptsBacnetAlias is the regression test for the defect that made every BACnet
// route into the analyzer fail: the CLI accepted "bacnet" while the analyzer only ever matched
// "bacnetip", so the name never reached a branch that could handle it.
func TestResolveAcceptsBacnetAlias(t *testing.T) {
	for _, name := range []string{"bacnet", "bacnetip"} {
		t.Run(name, func(t *testing.T) {
			got, err := Resolve(name)
			require.NoError(t, err, "the CLI has always advertised %q, so it must resolve", name)
			assert.Equal(t, BacnetIP.Name, got.Name,
				"both spellings must collapse onto the one canonical name the analyzer switches on")
		})
	}
}

func TestResolveAcceptsCBusSpellings(t *testing.T) {
	for _, name := range []string{"c-bus", "cbus"} {
		t.Run(name, func(t *testing.T) {
			got, err := Resolve(name)
			require.NoError(t, err)
			assert.Equal(t, CBus.Name, got.Name)
		})
	}
}

func TestResolveIsCaseAndSpaceInsensitive(t *testing.T) {
	tests := map[string]Protocol{
		"BACnet":    BacnetIP,
		"BACNETIP":  BacnetIP,
		"  bacnet ": BacnetIP,
		"C-Bus":     CBus,
		"\tcbus\n":  CBus,
	}
	for input, want := range tests {
		t.Run(input, func(t *testing.T) {
			got, err := Resolve(input)
			require.NoError(t, err)
			assert.Equal(t, want.Name, got.Name)
		})
	}
}

func TestResolveRejectsUnknownProtocolAndSaysWhatIsAccepted(t *testing.T) {
	_, err := Resolve("modbus")
	require.Error(t, err)
	// The message has to be actionable: a user who typo'd needs to see the real options.
	assert.Contains(t, err.Error(), "modbus", "the rejected input should be echoed back")
	assert.Contains(t, err.Error(), "bacnetip")
	assert.Contains(t, err.Error(), "c-bus")
}

func TestResolveRejectsEmptyName(t *testing.T) {
	_, err := Resolve("")
	assert.Error(t, err, "an empty protocol must not silently resolve to the first entry")
}

func TestNamesAreCanonicalAndSorted(t *testing.T) {
	// Spelled out rather than derived, so that adding a protocol is a deliberate act with a
	// visible diff: this list is what the help offers and what a user is allowed to type.
	assert.Equal(t, []string{
		"ab-eth", "ads", "bacnetip", "c-bus", "eip", "firmata", "knxnet-ip",
		"modbus-ascii", "modbus-rtu", "modbus-tcp", "s7", "slmp",
	}, Names(), "help output depends on this being stable and canonical")

	// And sorted, whatever the list becomes.
	assert.IsIncreasing(t, Names(), "the registry has to stay ordered by canonical name")
}

func TestAllReturnsACopy(t *testing.T) {
	first := All()
	require.NotEmpty(t, first)
	first[0] = Protocol{Name: "clobbered"}
	assert.NotEqual(t, "clobbered", All()[0].Name, "All must not expose the package registry for mutation")
}

// TestEveryProtocolResolvesByItsOwnName guards against adding a protocol to the registry with
// a name that Resolve cannot find.
func TestEveryProtocolResolvesByItsOwnName(t *testing.T) {
	for _, p := range All() {
		t.Run(p.Name, func(t *testing.T) {
			got, err := Resolve(p.Name)
			require.NoError(t, err)
			assert.Equal(t, p.Name, got.Name)
			for _, alias := range p.Aliases {
				gotAlias, err := Resolve(alias)
				require.NoError(t, err, "alias %q must resolve", alias)
				assert.Equal(t, p.Name, gotAlias.Name)
			}
		})
	}
}

// TestNoDuplicateNamesOrAliases catches a copy-paste mistake in the registry that would make
// Resolve's first-match-wins behaviour silently ambiguous.
func TestNoDuplicateNamesOrAliases(t *testing.T) {
	seen := map[string]string{}
	for _, p := range All() {
		for _, spelling := range append([]string{p.Name}, p.Aliases...) {
			if owner, dup := seen[spelling]; dup {
				t.Errorf("spelling %q is claimed by both %q and %q", spelling, owner, p.Name)
			}
			seen[spelling] = p.Name
		}
	}
}

func TestStringIsTheCanonicalName(t *testing.T) {
	assert.Equal(t, "bacnetip", BacnetIP.String())
	assert.Equal(t, "c-bus", CBus.String())
}
