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

package config

// Defaults is a snapshot of the configuration singletons as the flag registrations left them.
//
// It exists to make one question answerable: did the user pass this flag? The flags bind
// straight to the singletons by pointer, so once a command line has been parsed there is
// nothing left to distinguish a value the user asked for from the default that happens to
// equal it. Without the answer, the session configuration read from disk cannot be given its
// proper place in the order -- losing to a flag and winning over a default -- and the version
// this replaces simply decoded the file over the top of everything, so a value persisted
// months ago silently beat the flag typed a second ago.
//
// Only each config's own scalar fields are meaningful here. The structs embed pointers to one
// another, so a copy shares those; each embedded config is snapshotted in its own right and is
// compared through its own entry.
type Defaults struct {
	Root    RootConfig
	Analyze AnalyzeConfig
	Extract ExtractConfig
	Bacnet  BacnetConfig
	CBus    CBusConfig
	Pcap    PcapConfig
}

// defaults holds the snapshot. Zero until SnapshotDefaults is called.
var defaults Defaults

// snapshotTaken records whether a snapshot was actually taken, so a caller can tell a real
// snapshot of zero-valued defaults from no snapshot at all.
var snapshotTaken bool

// SnapshotDefaults records the singletons as they stand.
//
// It has to be called after every flag registration and before any command line is parsed.
// Execute is that moment: the registrations happen in package initialisers, so they are all
// done, and cobra has not yet been handed the arguments.
func SnapshotDefaults() {
	defaults = Defaults{
		Root:    RootConfigInstance,
		Analyze: AnalyzeConfigInstance,
		Extract: ExtractConfigInstance,
		Bacnet:  BacnetConfigInstance,
		CBus:    CBusConfigInstance,
		Pcap:    PcapConfigInstance,
	}
	snapshotTaken = true
}

// Defaults returns the snapshot, and whether one was taken.
//
// With no snapshot the caller cannot identify which values came from the command line. The
// honest fallback is to treat every non-zero value as deliberate, which leaves the persisted
// configuration filling gaps rather than overriding anything -- conservative, and never
// surprising in the direction that matters.
func DefaultsSnapshot() (Defaults, bool) { return defaults, snapshotTaken }

// ResetDefaults forgets the snapshot. For tests, which have to be able to start from nothing.
func ResetDefaults() {
	defaults, snapshotTaken = Defaults{}, false
}
