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

package tui

import (
	"os"
	"strings"

	"charm.land/lipgloss/v2"
)

// Glyphs is the set of drawing characters a UI uses.
//
// It exists so that no call site ever hardcodes a box-drawing character. The tools default to
// the Unicode set because it looks considerably better, but a terminal, font or console that
// cannot render it would show mojibake and, worse, miscount cell widths and break the layout.
// Every glyph therefore has an ASCII counterpart of the SAME display width.
type Glyphs struct {
	// Border is the pane border style.
	Border lipgloss.Border

	// Selected marks the highlighted row of a list or table.
	Selected string
	// Unselected occupies the same width as Selected for rows that are not selected, so that
	// text does not shift when the selection moves.
	Unselected string

	// Yes and No mark a boolean capability, such as whether a driver supports discovery.
	Yes string
	No  string

	// Inbound and Outbound mark the direction a message travelled.
	Inbound  string
	Outbound string

	// Ok, Warn and Err prefix a status.
	Ok   string
	Warn string
	Err  string

	// Ellipsis truncates overlong text.
	Ellipsis string

	// Separator joins items on the status line.
	Separator string

	// ProgressFull and ProgressEmpty draw a progress bar.
	ProgressFull  string
	ProgressEmpty string

	// Spinner frames are cycled while work is in flight.
	Spinner []string
}

// UnicodeGlyphs is the default, better-looking set.
func UnicodeGlyphs() Glyphs {
	return Glyphs{
		Border:        lipgloss.RoundedBorder(),
		Selected:      "▸",
		Unselected:    " ",
		Yes:           "●",
		No:            "○",
		Inbound:       "←",
		Outbound:      "→",
		Ok:            "✓",
		Warn:          "!",
		Err:           "✖",
		Ellipsis:      "…",
		Separator:     "·",
		ProgressFull:  "█",
		ProgressEmpty: "░",
		Spinner:       []string{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"},
	}
}

// ASCIIGlyphs is the fallback set. Every entry has the same display width as its Unicode
// counterpart, so switching sets cannot change a layout's arithmetic.
func ASCIIGlyphs() Glyphs {
	return Glyphs{
		Border:        lipgloss.ASCIIBorder(),
		Selected:      ">",
		Unselected:    " ",
		Yes:           "*",
		No:            "-",
		Inbound:       "<",
		Outbound:      ">",
		Ok:            "+",
		Warn:          "!",
		Err:           "x",
		Ellipsis:      ".",
		Separator:     "|",
		ProgressFull:  "#",
		ProgressEmpty: "-",
		Spinner:       []string{"|", "/", "-", "\\"},
	}
}

// GlyphsFor returns the Unicode set when unicode is true and the ASCII set otherwise.
func GlyphsFor(unicode bool) Glyphs {
	if unicode {
		return UnicodeGlyphs()
	}
	return ASCIIGlyphs()
}

// SupportsUnicode guesses whether the terminal can render the Unicode glyph set, by looking
// for a UTF-8 locale in the usual environment variables.
//
// lookup is the environment accessor, so this is testable; pass os.Getenv in production.
// The guess is deliberately conservative in only one direction: a terminal that can render
// Unicode but advertises no UTF-8 locale merely gets the plainer set, whereas guessing wrong
// the other way corrupts the display.
func SupportsUnicode(lookup func(string) string) bool {
	for _, key := range []string{"LC_ALL", "LC_CTYPE", "LANG"} {
		value := strings.ToUpper(lookup(key))
		if value == "" {
			continue
		}
		// The first variable that is set decides, mirroring how locales resolve.
		return strings.Contains(value, "UTF-8") || strings.Contains(value, "UTF8")
	}
	// Windows Terminal and most modern emulators cope, but a bare Windows console historically
	// did not, and no locale variable is set there.
	return false
}

// SupportsUnicodeFromEnv applies SupportsUnicode to the real environment.
func SupportsUnicodeFromEnv() bool {
	return SupportsUnicode(os.Getenv)
}
