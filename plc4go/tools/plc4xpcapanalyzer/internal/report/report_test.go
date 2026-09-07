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

package report

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
)

// sampleReport is one of each verdict, which is what makes the mapping worth asserting.
func sampleReport() Report {
	return Report{
		Capture:  "/tmp/build-agent-42/captures/cbus.pcap",
		Protocol: "c-bus",
		Started:  time.Date(2024, 1, 2, 3, 4, 5, 0, time.UTC),
		Elapsed:  1500 * time.Millisecond,
		Counters: finding.Counters{Walked: 5, Parsed: 3, ParseFail: 1, CompareFail: 1, Skipped: 1, TotalInCapture: 7},
		Findings: []finding.Finding{
			{Number: 1, Protocol: "c-bus", Summary: "CBusMessageToServer", Verdict: finding.VerdictOK,
				Original: []byte("~~~\r"), Reserialized: []byte("~~~\r"), DiffOffset: -1},
			{Number: 2, Protocol: "c-bus", Summary: "CBusMessageToClient", Verdict: finding.VerdictBytesDiffer,
				Reason: "3 bytes differ", Original: []byte("abcdef"), Reserialized: []byte("abcXYZ"), DiffOffset: 3},
			{Number: 3, Protocol: "c-bus", Verdict: finding.VerdictParseFail,
				Reason: "no command type could be found", Original: []byte("AFFE!"), DiffOffset: -1},
			{Number: 4, Protocol: "c-bus", Verdict: finding.VerdictSkipped, Reason: "echo",
				Original: []byte("!"), DiffOffset: -1},
			{Number: 5, Protocol: "c-bus", Summary: "CBusMessageToServer", Verdict: finding.VerdictSerializeFail,
				Reason: "cannot write", Original: []byte("x"), DiffOffset: -1},
			// Neither of these reached a codec, so neither is the codec's business.
			{Number: 6, Protocol: "c-bus", Verdict: finding.VerdictFiltered, Reason: "not our port", DiffOffset: -1},
			{Number: 7, Protocol: "c-bus", Verdict: finding.VerdictNoPayload, Reason: "no application layer", DiffOffset: -1},
		},
	}
}

// parseJUnit reads a report back with a general-purpose XML parser, which is what makes it a
// test of the document rather than of our own writer.
func parseJUnit(t *testing.T, document []byte) junitSuites {
	t.Helper()
	var parsed junitSuites
	require.NoError(t, xml.Unmarshal(document, &parsed), "the document has to be well-formed XML")
	return parsed
}

// TestTheJUnitDocumentIsWellFormed is the whole point: a report CI cannot parse is worth less
// than no report, because it looks like a passing build.
func TestTheJUnitDocumentIsWellFormed(t *testing.T) {
	out := &bytes.Buffer{}
	require.NoError(t, Write(out, FormatJUnit, sampleReport()))

	assert.True(t, strings.HasPrefix(out.String(), xml.Header),
		"a JUnit consumer expects the declaration")

	// Parsed by a decoder that knows nothing about us, and every token accounted for.
	decoder := xml.NewDecoder(bytes.NewReader(out.Bytes()))
	for {
		_, err := decoder.Token()
		if err == io.EOF {
			break
		}
		require.NoError(t, err, "every token has to parse")
	}
}

// TestEachVerdictBecomesTheRightJUnitOutcome pins the mapping a reader depends on. A skip
// reported as a failure is noise; a failure reported as a skip is a false clean bill of health.
func TestEachVerdictBecomesTheRightJUnitOutcome(t *testing.T) {
	parsed := parseJUnit(t, mustJUnit(t, sampleReport()))
	require.Len(t, parsed.Suites, 1)
	suite := parsed.Suites[0]

	byName := map[string]junitCase{}
	for _, one := range suite.Cases {
		byName[one.Name] = one
	}

	// A clean round trip is a passing test case: no failure, no skip.
	ok := byName["packet 1 CBusMessageToServer"]
	require.NotZero(t, ok.Name, "a clean packet still has to appear, or the report cannot say how much was checked")
	assert.Nil(t, ok.Failure)
	assert.Nil(t, ok.Skipped)

	// The three defects are failures, typed by what went wrong.
	for name, wantType := range map[string]string{
		"packet 2 CBusMessageToClient": "bytes",
		"packet 3":                     "parse",
		"packet 5 CBusMessageToServer": "serial",
	} {
		one := byName[name]
		require.NotNil(t, one.Failure, "%s has to be a failure", name)
		assert.Equal(t, wantType, one.Failure.Type, "%s", name)
		assert.Nil(t, one.Skipped, "%s must not also be a skip", name)
	}

	// The skip is a skip, carrying the reason.
	skipped := byName["packet 4"]
	require.NotNil(t, skipped.Skipped, "a skipped packet has to be a skip, not a failure")
	assert.Nil(t, skipped.Failure)
	assert.Equal(t, "echo", skipped.Skipped.Message)

	// And what never reached a codec is not the codec's business.
	assert.NotContains(t, byName, "packet 6", "a filtered packet must not be reported")
	assert.NotContains(t, byName, "packet 7", "nor one with no application layer")
}

// TestTheTotalsMatchTheCases is the arithmetic a CI dashboard shows. Attributes that disagree
// with the cases beneath them make the report untrustworthy in a way nobody investigates.
func TestTheTotalsMatchTheCases(t *testing.T) {
	parsed := parseJUnit(t, mustJUnit(t, sampleReport()))
	suite := parsed.Suites[0]

	failures, skips := 0, 0
	for _, one := range suite.Cases {
		if one.Failure != nil {
			failures++
		}
		if one.Skipped != nil {
			skips++
		}
	}

	assert.Equal(t, len(suite.Cases), suite.Tests, "tests has to count the cases")
	assert.Equal(t, failures, suite.Failures)
	assert.Equal(t, skips, suite.Skipped)
	assert.Equal(t, 3, failures, "the sample has three defects in it")
	assert.Equal(t, 1, skips)

	// And the top level agrees with its only suite.
	assert.Equal(t, suite.Tests, parsed.Tests)
	assert.Equal(t, suite.Failures, parsed.Failures)
	assert.Equal(t, suite.Skipped, parsed.Skipped)
}

// TestAFailureCarriesTheBytes is the reason to write a report at all. "Packet 2 failed" sends
// the reader back to the capture; the payloads and the offset let them see the problem.
func TestAFailureCarriesTheBytes(t *testing.T) {
	parsed := parseJUnit(t, mustJUnit(t, sampleReport()))

	var mismatch *junitFailure
	for _, one := range parsed.Suites[0].Cases {
		if one.Name == "packet 2 CBusMessageToClient" {
			mismatch = one.Failure
		}
	}
	require.NotNil(t, mismatch)

	assert.Contains(t, mismatch.Message, "offset 3", "the message names where they differ")
	assert.Contains(t, mismatch.Detail, "first difference at offset 3")
	assert.Contains(t, mismatch.Detail, "original (6 bytes)")
	assert.Contains(t, mismatch.Detail, "reserialized (6 bytes)")
	assert.Contains(t, mismatch.Detail, "61 62 63 64 65 66", "the captured bytes, in hex")
	assert.Contains(t, mismatch.Detail, "61 62 63 58 59 5a", "and what the codec wrote back")
}

// TestAControlCharacterStaysReadable is about legibility, not validity: encoding/xml never
// emits an invalid document, and handed a control character it substitutes U+FFFD without
// complaint. That leaves three bytes of mojibake in the middle of the sentence explaining a
// failure, so a control byte becomes a full stop instead, which says the same thing.
func TestAControlCharacterStaysReadable(t *testing.T) {
	hostile := sampleReport()
	hostile.Findings = []finding.Finding{{
		Number:     1,
		Protocol:   "c-bus",
		Verdict:    finding.VerdictParseFail,
		Reason:     "bad byte \x00 then \x07 then \x1b[31m and a form feed \x0c",
		Original:   []byte{0x00, 0x1b, 0xff},
		DiffOffset: -1,
	}}

	document := mustJUnit(t, hostile)
	parsed := parseJUnit(t, document)
	require.Len(t, parsed.Suites[0].Cases, 1)

	failure := parsed.Suites[0].Cases[0].Failure
	require.NotNil(t, failure)
	for _, forbidden := range []string{"\x00", "\x07", "\x1b", "\x0c"} {
		assert.NotContains(t, failure.Message, forbidden, "the message must not carry %q", forbidden)
		assert.NotContains(t, failure.Detail, forbidden, "the detail must not carry %q", forbidden)
	}
	// And not the replacement character either, which is what encoding/xml would have left
	// behind and is the thing this exists to avoid.
	assert.NotContains(t, failure.Message, "\uFFFD",
		"a control byte should read as a full stop, not as mojibake")
	assert.NotContains(t, failure.Detail, "\uFFFD")
	assert.Contains(t, failure.Message, "bad byte . then . then .[31m",
		"the sentence explaining the failure has to survive intact")
	assert.Contains(t, failure.Detail, "[31m", "an escape's payload is text and stays")
}

// TestAnAbortedRunSaysSo matters because the findings are then a prefix of the capture. A report
// that did not say so would read as a clean bill of health for packets nobody looked at.
func TestAnAbortedRunSaysSo(t *testing.T) {
	for _, aborted := range []bool{true, false} {
		partial := sampleReport()
		partial.Aborted = aborted

		parsed := parseJUnit(t, mustJUnit(t, partial))
		properties := map[string]string{}
		for _, property := range parsed.Suites[0].Properties {
			properties[property.Name] = property.Value
		}

		assert.Equal(t, strconv.FormatBool(aborted), properties["aborted"],
			"the report has to say whether the findings are the whole capture")
	}
}

// TestThePropertiesDescribeTheRun covers the context a reader needs and no finding carries: how
// much of the capture there was, and how much of it was examined.
func TestThePropertiesDescribeTheRun(t *testing.T) {
	parsed := parseJUnit(t, mustJUnit(t, sampleReport()))
	properties := map[string]string{}
	for _, property := range parsed.Suites[0].Properties {
		properties[property.Name] = property.Value
	}

	assert.Equal(t, "c-bus", properties["protocol"])
	assert.Equal(t, "/tmp/build-agent-42/captures/cbus.pcap", properties["capture"])
	assert.Equal(t, "7", properties["packetsInCapture"], "a property of the capture, not of a finding")
	assert.Equal(t, "5", properties["packetsWalked"])

	// The suite is named for the capture's base name: the full path is usually a build agent's
	// temporary directory and means nothing to a reader.
	assert.Equal(t, "cbus.pcap", parsed.Suites[0].Name)
	assert.Equal(t, "1.500", parsed.Suites[0].Time, "JUnit wants seconds")
	assert.Equal(t, "2024-01-02T03:04:05", parsed.Suites[0].Timestamp)
}

// TestTheFormatFollowsTheExtension is the whole interface to choosing one.
func TestTheFormatFollowsTheExtension(t *testing.T) {
	for path, want := range map[string]Format{
		"report.xml":                     FormatJUnit,
		"target/surefire-reports/go.xml": FormatJUnit,
		"report.json":                    FormatJSON,
		"REPORT.JSON":                    FormatJSON,
		"report":                         FormatJUnit,
		"report.txt":                     FormatJUnit,
	} {
		assert.Equal(t, want, FormatFor(path), path)
	}
}

// TestTheJSONReportCarriesTheSameFacts is the alternative for anything that would rather not
// read XML. Hex-encoded payloads, so the file stays text.
func TestTheJSONReportCarriesTheSameFacts(t *testing.T) {
	out := &bytes.Buffer{}
	require.NoError(t, Write(out, FormatJSON, sampleReport()))

	var parsed jsonReport
	require.NoError(t, json.Unmarshal(out.Bytes(), &parsed), "the document has to be valid JSON")

	assert.Equal(t, "c-bus", parsed.Protocol)
	assert.Equal(t, 7, parsed.Counters.TotalInCapture)
	// Everything, including what never reached a codec: unlike JUnit this is not a test report,
	// so there is no reason to hide packets from it.
	require.Len(t, parsed.Findings, 7)
	assert.Equal(t, "bytes", parsed.Findings[1].Verdict)
	assert.True(t, parsed.Findings[1].Issue)
	assert.Equal(t, 3, parsed.Findings[1].DiffOffset)
	assert.Equal(t, "616263646566", parsed.Findings[1].Original)
	assert.False(t, parsed.Findings[3].Issue, "a skip is not an issue")
}

// TestWriteFileCreatesTheDirectory covers the papercut: the natural place to write a report is
// target/surefire-reports, which does not exist until something makes it.
func TestWriteFileCreatesTheDirectory(t *testing.T) {
	path := filepath.Join(t.TempDir(), "target", "surefire-reports", "analyzer.xml")
	require.NoError(t, WriteFile(path, sampleReport()))

	written, err := os.ReadFile(path)
	require.NoError(t, err)
	assert.NotEmpty(t, parseJUnit(t, written).Suites)
}

// mustJUnit renders a report and fails the test if it cannot.
func mustJUnit(t *testing.T, report Report) []byte {
	t.Helper()
	out := &bytes.Buffer{}
	require.NoError(t, Write(out, FormatJUnit, report))
	return out.Bytes()
}
