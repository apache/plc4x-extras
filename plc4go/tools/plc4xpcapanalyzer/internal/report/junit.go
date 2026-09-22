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
	"encoding/hex"
	"encoding/xml"
	"strconv"
	"time"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
)

// The JUnit document. The element and attribute names are the format's, not ours, which is why
// they are spelled the way they are.

type junitSuites struct {
	XMLName  xml.Name     `xml:"testsuites"`
	Name     string       `xml:"name,attr"`
	Tests    int          `xml:"tests,attr"`
	Failures int          `xml:"failures,attr"`
	Skipped  int          `xml:"skipped,attr"`
	Time     string       `xml:"time,attr"`
	Suites   []junitSuite `xml:"testsuite"`
}

type junitSuite struct {
	Name       string          `xml:"name,attr"`
	Tests      int             `xml:"tests,attr"`
	Failures   int             `xml:"failures,attr"`
	Skipped    int             `xml:"skipped,attr"`
	Time       string          `xml:"time,attr"`
	Timestamp  string          `xml:"timestamp,attr,omitempty"`
	Properties []junitProperty `xml:"properties>property,omitempty"`
	Cases      []junitCase     `xml:"testcase"`
}

type junitProperty struct {
	Name  string `xml:"name,attr"`
	Value string `xml:"value,attr"`
}

type junitCase struct {
	Name      string        `xml:"name,attr"`
	ClassName string        `xml:"classname,attr"`
	Time      string        `xml:"time,attr"`
	Failure   *junitFailure `xml:"failure,omitempty"`
	Skipped   *junitSkipped `xml:"skipped,omitempty"`
}

type junitFailure struct {
	Type    string `xml:"type,attr"`
	Message string `xml:"message,attr"`
	Detail  string `xml:",chardata"`
}

type junitSkipped struct {
	Message string `xml:"message,attr"`
}

// asJUnit turns a report into the document.
//
// One suite per capture rather than one per protocol, because a capture is the unit a person
// re-runs and a suite is the unit CI groups by. Filtered packets and packets with no
// application layer are left out: they never reached a codec, so counting them would describe
// the capture rather than the codec, and would inflate a "tests" total that is meant to say how
// much of plc4x was exercised.
func asJUnit(report Report) junitSuites {
	suite := junitSuite{
		Name:      report.suiteName(),
		Time:      seconds(report.Elapsed),
		Timestamp: timestamp(report.Started),
		Properties: []junitProperty{
			{Name: "protocol", Value: report.Protocol},
			{Name: "capture", Value: report.Capture},
			{Name: "packetsInCapture", Value: strconv.Itoa(report.Counters.TotalInCapture)},
			{Name: "packetsWalked", Value: strconv.Itoa(report.Counters.Walked)},
			{Name: "aborted", Value: strconv.FormatBool(report.Aborted)},
		},
	}

	for _, found := range report.Findings {
		if !found.Verdict.Reportable() {
			continue
		}
		testCase := junitCase{
			Name:      caseName(found),
			ClassName: className(report, found),
			Time:      "0",
		}
		switch {
		case found.Verdict.IsIssue():
			testCase.Failure = &junitFailure{
				Type:    found.Verdict.String(),
				Message: sanitise(message(found)),
				Detail:  detail(found),
			}
			suite.Failures++
		case found.Verdict == finding.VerdictSkipped:
			testCase.Skipped = &junitSkipped{Message: sanitise(found.Reason)}
			suite.Skipped++
		}
		suite.Cases = append(suite.Cases, testCase)
		suite.Tests++
	}

	return junitSuites{
		Name:     report.suiteName(),
		Tests:    suite.Tests,
		Failures: suite.Failures,
		Skipped:  suite.Skipped,
		Time:     suite.Time,
		Suites:   []junitSuite{suite},
	}
}

// caseName identifies the packet. The number is what a user comparing against Wireshark looks
// for, and the message name is what makes a failure recognisable in a list of a thousand.
func caseName(found finding.Finding) string {
	name := "packet " + strconv.Itoa(found.Number)
	if found.Summary != "" {
		name += " " + found.Summary
	}
	return sanitise(name)
}

// className is the group CI files the case under: the protocol, so that a run over a corpus of
// captures shows which protocol's codec is unhappy.
func className(report Report, found finding.Finding) string {
	if found.Protocol != "" {
		return found.Protocol
	}
	return report.Protocol
}

// message is the one-line summary of a failure, which is what a CI list shows before anyone
// clicks into the detail.
func message(found finding.Finding) string {
	switch found.Verdict {
	case finding.VerdictParseFail:
		return "could not be parsed: " + found.Reason
	case finding.VerdictSerializeFail:
		return "parsed but could not be re-serialized: " + found.Reason
	case finding.VerdictBytesDiffer:
		if found.DiffOffset >= 0 {
			return "re-serialized to different bytes, first differing at offset " +
				strconv.Itoa(found.DiffOffset)
		}
		return "re-serialized to different bytes"
	default:
		return found.Reason
	}
}

// seconds is a duration as JUnit wants it.
func seconds(d time.Duration) string {
	return strconv.FormatFloat(d.Seconds(), 'f', 3, 64)
}

// timestamp is a start time as JUnit wants it, and empty for a zero time rather than the year 1.
func timestamp(t time.Time) string {
	if t.IsZero() {
		return ""
	}
	return t.UTC().Format("2006-01-02T15:04:05")
}

// asJSON is the report as a JSON document, with the payloads hex-encoded so that the file stays
// text and can be read by a person as well as a program.
func asJSON(report Report) jsonReport {
	out := jsonReport{
		Capture:  report.Capture,
		Protocol: report.Protocol,
		Elapsed:  report.Elapsed.String(),
		Aborted:  report.Aborted,
		Counters: report.Counters,
	}
	if !report.Started.IsZero() {
		out.Started = report.Started.UTC().Format(time.RFC3339)
	}
	for _, found := range report.Findings {
		out.Findings = append(out.Findings, jsonFinding{
			Number:       found.Number,
			Protocol:     found.Protocol,
			Summary:      found.Summary,
			Verdict:      found.Verdict.String(),
			Issue:        found.Verdict.IsIssue(),
			Reason:       found.Reason,
			Original:     hex.EncodeToString(found.Original),
			Reserialized: hex.EncodeToString(found.Reserialized),
			DiffOffset:   found.DiffOffset,
		})
	}
	return out
}

type jsonReport struct {
	Capture  string           `json:"capture"`
	Protocol string           `json:"protocol"`
	Started  string           `json:"started,omitempty"`
	Elapsed  string           `json:"elapsed"`
	Aborted  bool             `json:"aborted"`
	Counters finding.Counters `json:"counters"`
	Findings []jsonFinding    `json:"findings"`
}

type jsonFinding struct {
	Number       int    `json:"number"`
	Protocol     string `json:"protocol"`
	Summary      string `json:"summary,omitempty"`
	Verdict      string `json:"verdict"`
	Issue        bool   `json:"issue"`
	Reason       string `json:"reason,omitempty"`
	Original     string `json:"original,omitempty"`
	Reserialized string `json:"reserialized,omitempty"`
	DiffOffset   int    `json:"diffOffset"`
}
