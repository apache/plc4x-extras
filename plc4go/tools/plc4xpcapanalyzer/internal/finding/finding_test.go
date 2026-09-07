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

package finding

import (
	"testing"

	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
)

// These rules had two implementations before this package existed, and they disagreed. The
// tests are therefore about the rules themselves rather than about either caller.

// TestClassifyDrawsTheLineBetweenADefectAndATruce is the distinction that matters most: a
// payload the protocol says is not a whole message is a skip, and one the codec was handed and
// could not read is a defect. Wrong in one direction a report is noise; wrong in the other it is
// a false clean bill of health.
func TestClassifyDrawsTheLineBetweenADefectAndATruce(t *testing.T) {
	for name, test := range map[string]struct {
		err     error
		verdict Verdict
		reason  string
	}{
		"parsed":       {nil, VerdictOK, ""},
		"unterminated": {common.ErrUnterminatedPackage, VerdictSkipped, "unterminated"},
		"empty":        {common.ErrEmptyPackage, VerdictSkipped, "empty"},
		"echo":         {common.ErrEcho, VerdictSkipped, "echo"},
		"a real error": {errors.New("no command type could be found"), VerdictParseFail, "no command type could be found"},
	} {
		verdict, reason := Classify(test.err)
		assert.Equal(t, test.verdict, verdict, name)
		assert.Equal(t, test.reason, reason, name)
	}

	// Wrapped, because that is how these arrive from a codec.
	verdict, reason := Classify(errors.Wrap(common.ErrEcho, "while reading packet 10"))
	assert.Equal(t, VerdictSkipped, verdict, "a wrapped skip is still a skip")
	assert.Equal(t, "echo", reason)
}

// TestOnlyADefectIsAnIssue guards the taxonomy the findings tab and every report filter on.
func TestOnlyADefectIsAnIssue(t *testing.T) {
	for verdict, want := range map[Verdict]bool{
		VerdictBytesDiffer:   true,
		VerdictParseFail:     true,
		VerdictSerializeFail: true,
		VerdictOK:            false,
		VerdictSkipped:       false,
		VerdictFiltered:      false,
		VerdictNoPayload:     false,
	} {
		assert.Equal(t, want, verdict.IsIssue(), verdict.String())
	}
}

// TestOnlyWhatReachedACodecIsReportable is why a filtered packet is absent from a JUnit report:
// counting it would describe the capture rather than the codec, and inflate a total meant to
// say how much of plc4x was exercised.
func TestOnlyWhatReachedACodecIsReportable(t *testing.T) {
	for verdict, want := range map[Verdict]bool{
		VerdictOK:            true,
		VerdictBytesDiffer:   true,
		VerdictParseFail:     true,
		VerdictSerializeFail: true,
		VerdictSkipped:       true,
		VerdictFiltered:      false,
		VerdictNoPayload:     false,
	} {
		assert.Equal(t, want, verdict.Reportable(), verdict.String())
	}
}

// TestEveryVerdictHasADistinctLabel matters because the label is what a verdict column and a
// JUnit failure type both say. Two verdicts sharing a label would be indistinguishable in both.
func TestEveryVerdictHasADistinctLabel(t *testing.T) {
	all := []Verdict{
		VerdictOK, VerdictBytesDiffer, VerdictParseFail, VerdictSerializeFail,
		VerdictSkipped, VerdictFiltered, VerdictNoPayload,
	}
	seen := map[string]Verdict{}
	for _, verdict := range all {
		label := verdict.String()
		assert.NotEmpty(t, label)
		assert.NotEqual(t, "?", label, "a known verdict must not fall through to the unknown label")
		if other, clash := seen[label]; clash {
			t.Fatalf("%v and %v share the label %q", verdict, other, label)
		}
		seen[label] = verdict
	}
	assert.Equal(t, "?", Verdict(99).String(), "an unknown verdict is visibly unknown")
}

// TestCountingIsOneRule is the arithmetic both paths now share. "Parsed" is the easy one to get
// wrong: a packet that parsed and then failed to serialize did parse, and has to count as both.
func TestCountingIsOneRule(t *testing.T) {
	counters := Counters{}
	for _, verdict := range []Verdict{
		VerdictOK, VerdictOK, VerdictParseFail, VerdictSerializeFail,
		VerdictBytesDiffer, VerdictSkipped, VerdictFiltered, VerdictNoPayload,
	} {
		counters.Count(verdict)
	}

	assert.Equal(t, 8, counters.Walked, "everything counted is walked")
	assert.Equal(t, 4, counters.Parsed, "two clean, one that failed to serialize, one that differed")
	assert.Equal(t, 1, counters.ParseFail)
	assert.Equal(t, 1, counters.SerializeFail)
	assert.Equal(t, 1, counters.CompareFail)
	assert.Equal(t, 3, counters.Skipped, "a skip, a filtered packet and one with no payload")
	assert.Equal(t, 3, counters.Issues())

	// Every walked packet lands in exactly one category, so a report showing the totals cannot
	// imply that some packets went unaccounted for.
	assert.Equal(t, counters.Walked,
		counters.Parsed-counters.SerializeFail-counters.CompareFail+
			counters.ParseFail+counters.SerializeFail+counters.CompareFail+counters.Skipped,
		"the categories have to add up to the packets walked")
}

// TestFirstDifferenceAndDifferingBytesAgree covers the number the detail pane points its caret
// at and the number a report names.
func TestFirstDifferenceAndDifferingBytesAgree(t *testing.T) {
	for name, test := range map[string]struct {
		a, b       []byte
		difference int
		differing  int
	}{
		"identical":    {[]byte("abc"), []byte("abc"), -1, 0},
		"both empty":   {nil, nil, -1, 0},
		"middle":       {[]byte("abcdef"), []byte("abcXef"), 3, 1},
		"first byte":   {[]byte("abc"), []byte("Xbc"), 0, 1},
		"truncated":    {[]byte("abcdef"), []byte("abc"), 3, 3},
		"longer":       {[]byte("abc"), []byte("abcdef"), 3, 3},
		"one is empty": {[]byte("abc"), nil, 0, 3},
	} {
		assert.Equal(t, test.difference, FirstDifference(test.a, test.b), "%s: first difference", name)
		assert.Equal(t, test.differing, DifferingBytes(test.a, test.b), "%s: differing bytes", name)

		// The two have to agree about whether there is a difference at all.
		assert.Equal(t, FirstDifference(test.a, test.b) < 0, DifferingBytes(test.a, test.b) == 0, name)
	}
}
