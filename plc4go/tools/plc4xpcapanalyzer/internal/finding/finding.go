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

// Package finding is what an analysed packet turned out to be, and the one place that decides.
//
// It exists because there were two answers to that question. internal/analyzer classified a
// packet inline and reported the result only by logging it; the terminal interface walked the
// capture again and classified it a second time, because bytes cannot be recovered from a log
// line. Two implementations of the same taxonomy drift, and this one did: the interface counted
// a skipped packet as normal traffic while the documentation described it as a failure, and
// nothing could have caught that because there was no shared definition to disagree with.
//
// Everything that produces a verdict now goes through Classify and Compare, and everything that
// counts them goes through Counters. A carrier may add fields of its own -- the interface adds
// the arrival offset, the direction and the parsed tree, which only a screen needs -- but the
// facts a finding consists of, and the rules for reaching them, live here.
package finding

import (
	"fmt"
	"strings"

	"github.com/apache/plc4x/plc4go/spi"
	"github.com/pkg/errors"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
)

// Verdict is what an analysed packet turned out to be.
type Verdict int

const (
	// VerdictOK is a packet that parsed, re-serialized and compared equal.
	VerdictOK Verdict = iota
	// VerdictBytesDiffer is the finding this tool exists to produce: the codec parsed the
	// packet and then wrote back something else.
	VerdictBytesDiffer
	// VerdictParseFail is a payload the codec could not read.
	VerdictParseFail
	// VerdictSerializeFail is a message the codec could read but not write.
	VerdictSerializeFail
	// VerdictSkipped is a payload the protocol itself says is not a whole message: a split
	// transmission, an empty packet or an echo. Not a defect, so not a finding.
	VerdictSkipped
	// VerdictFiltered is a packet the protocol mapping removed.
	VerdictFiltered
	// VerdictNoPayload is a packet with no application layer at all.
	VerdictNoPayload
)

// String is the label the packets table shows in its verdict column. The labels are short
// because that column is the first to lose width on a narrow terminal.
func (v Verdict) String() string {
	switch v {
	case VerdictOK:
		return "ok"
	case VerdictBytesDiffer:
		return "bytes"
	case VerdictParseFail:
		return "parse"
	case VerdictSerializeFail:
		return "serial"
	case VerdictSkipped:
		return "skip"
	case VerdictFiltered:
		return "filter"
	case VerdictNoPayload:
		return "empty"
	default:
		return "?"
	}
}

// IsIssue reports whether this verdict is a defect: something the codec got wrong, as opposed
// to traffic there was nothing to check in.
//
// A skipped, filtered or payload-less packet is normal traffic, not a defect, and counting it
// as one would bury the three real findings among two hundred uninteresting ones. It is also
// what decides whether a packet appears in the findings tab, and whether it is reported as a
// failure or a skip to anything reading a report.
func (v Verdict) IsIssue() bool {
	switch v {
	case VerdictBytesDiffer, VerdictParseFail, VerdictSerializeFail:
		return true
	default:
		return false
	}
}

// Reportable reports whether the packet was one the codec was actually asked about. A filtered
// packet or one with no application layer never reached a codec, so a report that counted it
// would be describing the capture rather than the codec.
func (v Verdict) Reportable() bool {
	return v != VerdictFiltered && v != VerdictNoPayload
}

// Finding is what one analysed packet turned out to be.
type Finding struct {
	// Number is the packet's position in the unfiltered capture, which is the number a user
	// comparing against Wireshark expects to see.
	Number int
	// Protocol is the canonical protocol name it was analysed as.
	Protocol string
	// Summary is the message type the codec parsed it into, empty when it did not parse.
	Summary string
	// Verdict is the outcome.
	Verdict Verdict
	// Reason carries the error or the explanation behind a non-OK verdict.
	Reason string
	// Original is the captured application payload.
	Original []byte
	// Reserialized is what the codec wrote back, nil when it never got that far.
	Reserialized []byte
	// DiffOffset is the offset of the first differing byte, or -1 when the two agree or when
	// there was nothing to compare.
	DiffOffset int
}

// Classify turns a parse error into a verdict and a reason.
//
// The distinction it draws is the one that matters most in a report: a payload the protocol
// itself says is not a whole message is a skip, and a payload the codec was handed and could
// not read is a defect. Getting that wrong in either direction makes a report useless -- as
// noise in one direction, and as a false clean bill of health in the other.
func Classify(err error) (Verdict, string) {
	switch {
	case err == nil:
		return VerdictOK, ""
	case errors.Is(err, common.ErrUnterminatedPackage):
		return VerdictSkipped, "unterminated"
	case errors.Is(err, common.ErrEmptyPackage):
		return VerdictSkipped, "empty"
	case errors.Is(err, common.ErrEcho):
		return VerdictSkipped, "echo"
	default:
		return VerdictParseFail, err.Error()
	}
}

// FirstDifference returns the offset of the first byte at which a and b disagree, treating a
// prefix as differing at the point where the shorter one ends. It returns -1 when the two are
// identical.
//
// This is the number the detail pane points its caret at and the offset a report names, and it
// is the single most useful piece of information the tool produces, so it is a named, tested
// function rather than an expression buried in a loop.
func FirstDifference(a, b []byte) int {
	limit := min(len(a), len(b))
	for i := range limit {
		if a[i] != b[i] {
			return i
		}
	}
	if len(a) != len(b) {
		return limit
	}
	return -1
}

// DifferingBytes counts how many byte positions disagree, counting the tail of the longer slice
// as differing. It is what a report and a detail pane both say out loud: "3 bytes differ".
func DifferingBytes(a, b []byte) int {
	limit := min(len(a), len(b))
	count := max(len(a), len(b)) - limit
	for i := range limit {
		if a[i] != b[i] {
			count++
		}
	}
	return count
}

// MessageName is the short name of a parsed message, which is what a finding's Summary holds
// and what both a packet table and a report identify a message by.
//
// It is here rather than beside either caller because it is part of what a finding says: two
// implementations would eventually disagree about whether the name keeps its package
// qualifier, and a report and a screen naming the same message differently is a bug in both.
func MessageName(message spi.Message) string {
	name := fmt.Sprintf("%T", message)
	if index := strings.LastIndex(name, "."); index >= 0 {
		name = name[index+1:]
	}
	return strings.TrimPrefix(strings.TrimPrefix(name, "*"), "_")
}

// Counters are the totals a run reports.
type Counters struct {
	Walked         int
	Parsed         int
	ParseFail      int
	SerializeFail  int
	CompareFail    int
	Skipped        int
	TotalInCapture int
}

// Count folds one verdict into the totals.
//
// One rule for all of them, because the alternative was two: the analyzer incremented three
// integers inline and the interface ran a switch of its own, and "parsed" in particular is
// easy to disagree about -- a packet that parsed and then failed to serialize did parse, and
// has to be counted as both.
func (c *Counters) Count(verdict Verdict) {
	c.Walked++
	switch verdict {
	case VerdictOK:
		c.Parsed++
	case VerdictParseFail:
		c.ParseFail++
	case VerdictSerializeFail:
		c.Parsed++
		c.SerializeFail++
	case VerdictBytesDiffer:
		c.Parsed++
		c.CompareFail++
	default:
		// Skipped, filtered and no-payload alike. Every walked packet lands in exactly one
		// category, so the totals add up and a report that shows them cannot imply that some
		// packets went unaccounted for.
		c.Skipped++
	}
}

// Issues is how many defects the run found.
func (c Counters) Issues() int { return c.ParseFail + c.SerializeFail + c.CompareFail }
