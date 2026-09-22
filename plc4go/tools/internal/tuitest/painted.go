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

// Package tuitest holds assertions about rendered output that both tools need.
//
// It exists rather than each tool keeping a copy because the logic here is fiddly enough to
// drift: two hand-written SGR parsers that disagree would give two different answers to the
// same question about the same screen.
package tuitest

import (
	"regexp"
	"strconv"
	"strings"

	"charm.land/lipgloss/v2"
)

// Escapes matches one SGR sequence, for tests that need to look at or strip the styling.
var Escapes = regexp.MustCompile("\x1b\\[[0-9;]*m")

// Strip removes all styling from a rendered string.
func Strip(rendered string) string { return Escapes.ReplaceAllString(rendered, "") }

// PaintedCells counts the leading cells of a row that the terminal actually fills in.
//
// A background set once covers every cell after it until a reset or an explicit
// default-background takes it away; a foreground or an attribute leaves it alone. That is why
// this walks cells rather than styled segments: the bug it was written to catch is a bare space
// *between* two styled pieces, which no per-segment check would see.
//
// Reverse video counts as painted. bubbles draws the text cursor that way and its background is
// not settable, so it is the one cell of a command bar carrying no background of its own -- and
// it still paints a solid block, which is all this asks about.
func PaintedCells(line string) int {
	cells, painted := 0, false
	rest := line
	for rest != "" {
		location := Escapes.FindStringIndex(rest)
		if location != nil && location[0] == 0 {
			for param := range strings.SplitSeq(strings.Trim(rest[2:location[1]], "m"), ";") {
				switch param {
				case "", "0", "49":
					painted = false
				case "7", "48", "40", "41", "42", "43", "44", "45", "46", "47",
					"100", "101", "102", "103", "104", "105", "106", "107":
					painted = true
				}
			}
			rest = rest[location[1]:]
			continue
		}

		next := len(rest)
		if location != nil {
			next = location[0]
		}
		if !painted {
			return cells
		}
		cells += lipgloss.Width(rest[:next])
		rest = rest[next:]
	}
	return cells
}

// ColourCodes returns every SGR sequence in a rendered string that sets a colour, so a test
// can assert that a NO_COLOR render sets none. Attributes and resets are not colours: NO_COLOR
// asks for no colour, and bold and reverse video are how a title and a cursor still read
// without one.
//
// The whole sequence is returned rather than the parameter that gave it away, because a
// failure message naming "\x1b[38;2;167;139;250m" says what leaked and one naming "38" does not.
func ColourCodes(rendered string) []string {
	var colours []string
	for _, code := range Escapes.FindAllString(rendered, -1) {
		for param := range strings.SplitSeq(strings.Trim(code[2:], "m"), ";") {
			if !setsColour(param) {
				continue
			}
			colours = append(colours, code)
			// One report per sequence. 38 and 48 are followed by their arguments, which are
			// numbers rather than parameters and would otherwise be read as colours of their
			// own -- an RGB green of 31 is not the SGR red 31.
			break
		}
	}
	return colours
}

// setsColour reports whether one SGR parameter selects a colour, as opposed to setting an
// attribute or resetting one.
func setsColour(param string) bool {
	switch param {
	case "38", "48", "58":
		// Extended colour: 38;2;R;G;B or 38;5;N.
		return true
	}
	number, err := strconv.Atoi(param)
	if err != nil {
		return false
	}
	switch {
	case number >= 30 && number <= 37, number >= 40 && number <= 47,
		number >= 90 && number <= 97, number >= 100 && number <= 107:
		return true
	default:
		// Everything else is an attribute (bold, reverse) or a reset, including 39 and 49,
		// which put the terminal's own colours back.
		return false
	}
}
