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

// Package protocol is the single source of truth for the protocol names the pcap analyzer
// understands.
//
// It exists because the name accepted at the command line, the name the analyzer switched on
// and the name the extractor switched on used to disagree: the CLI accepted "bacnet", the
// analyzer only matched "bacnetip" and the extractor only matched "bacnet". Every route into
// the analyzer for BACnet therefore failed. Resolving names here, once, keeps the CLI, the
// analyzer, the extractor and the TUI from drifting apart again.
package protocol

import (
	"slices"
	"sort"
	"strings"

	"github.com/apache/plc4x/plc4go/spi/errors"
)

// Protocol is a protocol the analyzer can parse, serialize and byte-compare.
type Protocol struct {
	// Name is the canonical name. It matches the plc4x driver protocol code, so it is also
	// what a plc4x connection string would use.
	Name string
	// Aliases are additional names accepted from users, for convenience and for backwards
	// compatibility with names earlier versions accepted.
	Aliases []string
	// Description is a short human-readable label used in help output.
	Description string
}

// String returns the canonical name, so a Protocol can be passed anywhere a name is logged.
func (p Protocol) String() string { return p.Name }

// Matches reports whether name refers to this protocol, by canonical name or by alias.
// Comparison is case-insensitive and ignores surrounding whitespace.
func (p Protocol) Matches(name string) bool {
	name = normalize(name)
	if name == p.Name {
		return true
	}
	return slices.Contains(p.Aliases, name)
}

// The supported protocols. BACnet/IP is canonically "bacnetip", matching the plc4x driver
// protocol code, with "bacnet" accepted as an alias because the CLI has always advertised it.
var (
	BacnetIP = Protocol{
		Name:        "bacnetip",
		Aliases:     []string{"bacnet", "bacnet-ip"},
		Description: "BACnet/IP",
	}
	CBus = Protocol{
		Name:        "c-bus",
		Aliases:     []string{"cbus"},
		Description: "Clipsal C-Bus",
	}

	// The rest, examined through internal/codec. The names are the plc4x driver codes, which
	// is deliberately the same vocabulary the browser uses: a user who connected to a device
	// with "modbus-tcp" should not have to learn a second spelling to analyse its traffic.
	ModbusTcp   = Protocol{Name: "modbus-tcp", Description: "Modbus TCP"}
	ModbusRtu   = Protocol{Name: "modbus-rtu", Description: "Modbus RTU (needs --filter: serial unless tunnelled)"}
	ModbusAscii = Protocol{Name: "modbus-ascii", Description: "Modbus ASCII (needs --filter: serial unless tunnelled)"}
	S7          = Protocol{Name: "s7", Description: "Siemens S7"}
	Eip         = Protocol{Name: "eip", Aliases: []string{"logix"}, Description: "EtherNet/IP"}
	KnxNetIp    = Protocol{Name: "knxnet-ip", Aliases: []string{"knxnetip"}, Description: "KNXNet/IP"}
	Ads         = Protocol{Name: "ads", Description: "Beckhoff TwinCAT ADS"}
	AbEth       = Protocol{Name: "ab-eth", Aliases: []string{"abeth"}, Description: "Allen Bradley ETH"}
	Slmp        = Protocol{Name: "slmp", Description: "SLMP (MELSEC)"}
	Firmata     = Protocol{Name: "firmata", Description: "Firmata (needs --filter: serial unless tunnelled)"}
)

// all is the registry. Keep it ordered by canonical name so help output is stable.
var all = []Protocol{
	AbEth, Ads, BacnetIP, CBus, Eip, Firmata, KnxNetIp,
	ModbusAscii, ModbusRtu, ModbusTcp, S7, Slmp,
}

// All returns every supported protocol.
func All() []Protocol {
	out := make([]Protocol, len(all))
	copy(out, all)
	return out
}

// Catalogue is the protocols as help text: one per line, canonical name, aliases and
// description.
//
// Generated rather than written, because a hand-written list is a list that goes stale. The
// help said "bacnetip or c-bus" and would have kept saying it after ten more were added.
func Catalogue() string {
	var out strings.Builder
	for _, p := range all {
		name := p.Name
		if len(p.Aliases) > 0 {
			name += " (" + strings.Join(p.Aliases, ", ") + ")"
		}
		out.WriteString("  " + name)
		if p.Description != "" {
			out.WriteString(strings.Repeat(" ", max(1, 30-len(name))) + p.Description)
		}
		out.WriteString("\n")
	}
	return out.String()
}

// Names returns the canonical names, sorted, for help and error messages.
func Names() []string {
	names := make([]string, 0, len(all))
	for _, p := range all {
		names = append(names, p.Name)
	}
	sort.Strings(names)
	return names
}

// Resolve maps a user-supplied name, canonical or alias, onto a supported protocol.
// The returned error names every accepted spelling, since that is what a user needs to
// recover from a typo.
func Resolve(name string) (Protocol, error) {
	for _, p := range all {
		if p.Matches(name) {
			return p, nil
		}
	}
	return Protocol{}, errors.Errorf("unsupported protocol %q, supported protocols are %s (accepted aliases: %s)",
		name, strings.Join(Names(), ", "), strings.Join(aliases(), ", "))
}

// aliases returns every accepted alias, sorted, for error messages.
func aliases() []string {
	var out []string
	for _, p := range all {
		out = append(out, p.Aliases...)
	}
	sort.Strings(out)
	return out
}

// normalize puts a user-supplied name into the form the registry compares against.
func normalize(name string) string {
	return strings.ToLower(strings.TrimSpace(name))
}
