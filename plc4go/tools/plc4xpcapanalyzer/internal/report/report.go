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

// Package report writes what a run found in a form something other than a person can read.
//
// The analyzer is a regression harness for plc4x's codecs: run it over a corpus of captures and
// a codec that stops round-tripping a message shows up as a failure. That is only useful if the
// failure survives the run, and until now it did not -- a finding existed as a log line among
// info lines for every packet, and as a number in a summary, so you could learn that three
// packets failed but not which or why without grepping.
//
// JUnit XML is the default because it is what this repository already speaks: the Go tests are
// run through gotestsum --junitfile into target/surefire-reports, and the workflow renders that
// with dorny/test-reporter as java-junit. A capture becomes a suite and a packet becomes a test
// case, so a codec regression arrives in CI as a named failing test with the offending bytes
// attached, and is tracked across builds for free.
package report

import (
	"encoding/hex"
	"encoding/json"
	"encoding/xml"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/pkg/errors"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
)

// Report is one run, as something reading it needs to see it.
type Report struct {
	// Capture is the path of the capture that was analysed.
	Capture string
	// Protocol is the canonical protocol name it was analysed as.
	Protocol string
	// Started is when the run began, and Elapsed how long it took.
	Started time.Time
	Elapsed time.Duration
	// Counters are the totals.
	Counters finding.Counters
	// Findings is what each analysed packet turned out to be, in capture order.
	Findings []finding.Finding
	// Aborted says the run was interrupted, so the findings are a prefix of the capture rather
	// than all of it. A report that did not say so would read as a clean bill of health for
	// packets nobody looked at.
	Aborted bool
}

// Format is a report format.
type Format string

const (
	// FormatJUnit is JUnit XML, which is what CI consumes.
	FormatJUnit Format = "junit"
	// FormatJSON is the same content as JSON, for anything that would rather not read XML.
	FormatJSON Format = "json"
)

// FormatFor picks a format from a path's extension. Anything but .json is JUnit XML, since that
// is the point of the feature and .xml is what a user will write.
func FormatFor(path string) Format {
	if strings.EqualFold(filepath.Ext(path), ".json") {
		return FormatJSON
	}
	return FormatJUnit
}

// WriteFile writes a report to path, in the format its extension implies, creating the
// directory if it is missing.
//
// The directory is created because the natural place to write one is
// target/surefire-reports/<something>.xml, and asking a user to create a directory before a
// flag will work is a papercut with no upside.
func WriteFile(path string, report Report) error {
	if directory := filepath.Dir(path); directory != "" {
		if err := os.MkdirAll(directory, 0o755); err != nil {
			return errors.Wrapf(err, "error creating %s", directory)
		}
	}
	file, err := os.Create(path)
	if err != nil {
		return errors.Wrapf(err, "error creating %s", path)
	}
	defer func() { _ = file.Close() }()

	if err := Write(file, FormatFor(path), report); err != nil {
		return err
	}
	return errors.Wrapf(file.Close(), "error closing %s", path)
}

// Write writes a report in the given format.
func Write(w io.Writer, format Format, report Report) error {
	if format == FormatJSON {
		return writeJSON(w, report)
	}
	return writeJUnit(w, report)
}

// writeJSON writes the report as JSON, with the byte payloads hex-encoded so the document is
// text a human can also read.
func writeJSON(w io.Writer, report Report) error {
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	return errors.Wrap(encoder.Encode(asJSON(report)), "error writing the JSON report")
}

// writeJUnit writes the report as JUnit XML.
func writeJUnit(w io.Writer, report Report) error {
	if _, err := io.WriteString(w, xml.Header); err != nil {
		return errors.Wrap(err, "error writing the report header")
	}
	encoder := xml.NewEncoder(w)
	encoder.Indent("", "  ")
	if err := encoder.Encode(asJUnit(report)); err != nil {
		return errors.Wrap(err, "error writing the JUnit report")
	}
	if err := encoder.Flush(); err != nil {
		return errors.Wrap(err, "error flushing the JUnit report")
	}
	_, err := io.WriteString(w, "\n")
	return errors.Wrap(err, "error writing the report")
}

// suiteName is what CI shows as the suite. The capture's base name, because the full path is
// usually a build-agent temporary directory that means nothing to a reader.
func (r Report) suiteName() string {
	if r.Capture == "" {
		return "capture"
	}
	return filepath.Base(r.Capture)
}

// detail is the body of a failure: what went wrong, and the bytes it went wrong on.
//
// The bytes are the reason this feature exists. A report saying "packet 9 failed to parse" sends
// the reader back to the capture; one carrying the payload lets them see the problem, and for a
// byte mismatch it names the offset the two first differ at, which is the single most useful
// number the tool produces.
func detail(found finding.Finding) string {
	var out strings.Builder
	if found.Reason != "" {
		out.WriteString(found.Reason + "\n")
	}
	if found.Verdict == finding.VerdictBytesDiffer && found.DiffOffset >= 0 {
		out.WriteString("first difference at offset " + strconv.Itoa(found.DiffOffset) + "\n")
	}
	if len(found.Original) > 0 {
		out.WriteString("\noriginal (" + strconv.Itoa(len(found.Original)) + " bytes)\n")
		out.WriteString(hex.Dump(found.Original))
	}
	if len(found.Reserialized) > 0 {
		out.WriteString("\nreserialized (" + strconv.Itoa(len(found.Reserialized)) + " bytes)\n")
		out.WriteString(hex.Dump(found.Reserialized))
	}
	return sanitise(out.String())
}

// sanitise replaces the characters XML 1.0 cannot carry with a readable stand-in.
//
// It is about legibility rather than validity, and the distinction is worth stating because it
// is easy to assume otherwise. encoding/xml never emits an invalid document: handed a control
// character it substitutes U+FFFD and reports no error (verified against Go 1.27). So the file
// parses either way -- what it does not do is read, because a codec error mentioning a control
// byte arrives as three bytes of mojibake in the middle of the sentence explaining the failure.
// A full stop says the same thing and costs nothing.
//
// The payloads are hex-encoded and never need this; a codec's error message is arbitrary text
// and does.
func sanitise(text string) string {
	return strings.Map(func(r rune) rune {
		switch {
		case r == '\t' || r == '\n' || r == '\r':
			return r
		case r < 0x20, r == 0x7F:
			return '.'
		// The XML 1.0 forbidden ranges beyond the C0 controls.
		case r >= 0x80 && r <= 0x9F, r == 0xFFFE, r == 0xFFFF:
			return '.'
		default:
			return r
		}
	}, text)
}
