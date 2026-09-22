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
	"regexp"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestDataTypeGroupsByFamily pins the mapping from IEC 61131-3 type names onto the four
// families a reader actually distinguishes. The names are matched by suffix, so the sized and
// unsized spellings of one type have to land together.
func TestDataTypeGroupsByFamily(t *testing.T) {
	theme := NewTheme(Options{Dark: true})

	families := map[string][]string{
		"numeric":  {"INT", "UINT", "SINT", "USINT", "DINT", "UDINT", "LINT", "ULINT", "REAL", "LREAL", "BYTE", "WORD", "DWORD", "LWORD"},
		"boolean":  {"BOOL", "BIT"},
		"text":     {"STRING", "WSTRING", "CHAR", "WCHAR"},
		"temporal": {"TIME", "LTIME", "DATE", "LDATE", "TIME_OF_DAY", "TOD", "LTOD", "DATE_AND_TIME", "LDATE_AND_TIME", "DT", "LDT"},
	}
	want := map[string]string{
		"numeric":  theme.Numeric.Render("x"),
		"boolean":  theme.Boolean.Render("x"),
		"text":     theme.Text.Render("x"),
		"temporal": theme.Temporal.Render("x"),
	}

	// The four families have to be visually distinct, or grouping by them buys nothing.
	seen := map[string]string{}
	for family, rendered := range want {
		if other, clash := seen[rendered]; clash {
			t.Fatalf("families %q and %q render identically", family, other)
		}
		seen[rendered] = family
	}

	for family, types := range families {
		for _, dataType := range types {
			assert.Equal(t, want[family], theme.DataType(dataType).Render("x"),
				"%s should be %s", dataType, family)
			// Case is not part of the contract: a driver may report either.
			assert.Equal(t, want[family], theme.DataType(strings.ToLower(dataType)).Render("x"),
				"%s should be %s in lower case too", dataType, family)
		}
	}
}

// TestDataTypeLeavesUnknownTypesUncategorised is the deliberate choice not to guess: a type
// this does not know about must read as uncategorised rather than as a wrong family.
func TestDataTypeLeavesUnknownTypesUncategorised(t *testing.T) {
	theme := NewTheme(Options{Dark: true})
	for _, dataType := range []string{"", "STRUCT", "ARRAY", "RAW_BYTES", "unknown"} {
		assert.Equal(t, theme.Muted.Render("x"), theme.DataType(dataType).Render("x"), dataType)
	}
}

// TestLogLevelDistinguishesTheLevelsThatMatter checks that a warning and an error are each
// visually separable from an ordinary line -- the only reason to colour a log at all.
func TestLogLevelDistinguishesTheLevelsThatMatter(t *testing.T) {
	theme := NewTheme(Options{Dark: true})

	info := theme.LogLevel("info").Render("x")
	warn := theme.LogLevel("warn").Render("x")
	err := theme.LogLevel("error").Render("x")

	assert.NotEqual(t, info, warn)
	assert.NotEqual(t, info, err)
	assert.NotEqual(t, warn, err)

	// trace and debug are the noise you turned the level up to see: they are quieter than an
	// info line, not louder, and an unknown level is treated as noise rather than as an alarm.
	quiet := theme.Muted.Render("x")
	for _, level := range []string{"", "trace", "debug", "nonsense"} {
		assert.Equal(t, quiet, theme.LogLevel(level).Render("x"), level)
		assert.NotEqual(t, warn, theme.LogLevel(level).Render("x"), level)
		assert.NotEqual(t, err, theme.LogLevel(level).Render("x"), level)
	}
	assert.NotEqual(t, quiet, info, "an info line should read as more than noise")

	// Spelling and case vary between loggers; the aliases must land on the same style.
	assert.Equal(t, err, theme.LogLevel("ERROR").Render("x"))
	assert.Equal(t, err, theme.LogLevel("fatal").Render("x"))
	assert.Equal(t, err, theme.LogLevel(" panic ").Render("x"))
	assert.Equal(t, warn, theme.LogLevel("warning").Render("x"))
	assert.Equal(t, warn, theme.LogLevel("WARN").Render("x"))
}

// TestNoColorThemeHasNoBandAtAll matters because the band is a background, and a background is
// exactly what a NO_COLOR terminal must not receive: it would print as solid blocks.
func TestNoColorThemeHasNoBandAtAll(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})

	assert.Equal(t, "  ", theme.Surface.Render("  "), "Surface must be inert without colour")
	assert.NotContains(t, theme.Surface.Render("  "), "\x1b")

	// OnSurface and Surfaced are the identity, so a call site needs no NoColor branch.
	assert.Equal(t, theme.Accent.Render("x"), theme.OnSurface(theme.Accent).Render("x"))
	assert.Equal(t, theme.Muted.Render("x"), theme.Surfaced().Muted.Render("x"))
	for _, rendered := range []string{
		theme.Surfaced().Title.Render("x"),
		theme.Surfaced().Err.Render("x"),
		theme.Surfaced().Numeric.Render("x"),
	} {
		assert.NotContains(t, rendered, "\x1b[4", "no background may reach a NO_COLOR terminal")
	}
}

// TestSurfacedKeepsForegroundAndAddsBackground is the property the banded rows rely on: the
// meaning of a style is its foreground, and putting it on the band must not change that.
func TestSurfacedKeepsForegroundAndAddsBackground(t *testing.T) {
	for _, dark := range []bool{true, false} {
		theme := NewTheme(Options{Dark: dark})
		surfaced := theme.Surfaced()
		band := theme.Surface.Render(" ")
		require.Contains(t, band, "\x1b[4", "the band must set a background")
		background := backgroundOf(t, band)

		for name, pair := range map[string][2]string{
			"Title":    {theme.Title.Render("x"), surfaced.Title.Render("x")},
			"Muted":    {theme.Muted.Render("x"), surfaced.Muted.Render("x")},
			"Accent":   {theme.Accent.Render("x"), surfaced.Accent.Render("x")},
			"Err":      {theme.Err.Render("x"), surfaced.Err.Render("x")},
			"Numeric":  {theme.Numeric.Render("x"), surfaced.Numeric.Render("x")},
			"Temporal": {theme.Temporal.Render("x"), surfaced.Temporal.Render("x")},
		} {
			plain, onBand := pair[0], pair[1]
			assert.NotEqual(t, plain, onBand, "%s should differ on the band", name)
			assert.Contains(t, onBand, background, "%s should carry the band background", name)
			for _, code := range foregroundsOf(plain) {
				assert.Contains(t, onBand, code, "%s should keep its foreground", name)
			}
		}

		// The badge sets a background of its own and reads by contrasting with what it sits
		// on, so Surfaced must leave it alone.
		assert.Equal(t, theme.Badge.Render("DEMO"), surfaced.Badge.Render("DEMO"))
	}
}

// trueColour matches one truecolour SGR parameter run, foreground (38) or background (48).
// lipgloss merges a style's parameters into a single sequence, so the run and not the whole
// sequence is what one style's contribution to another's rendering looks like.
var trueColour = regexp.MustCompile(`(38|48);2;[0-9]+;[0-9]+;[0-9]+`)

// backgroundOf extracts the background parameter run from a rendered string.
func backgroundOf(t *testing.T, rendered string) string {
	t.Helper()
	for _, run := range trueColour.FindAllString(rendered, -1) {
		if strings.HasPrefix(run, "48;") {
			return run
		}
	}
	t.Fatalf("no background in %q", rendered)
	return ""
}

// foregroundsOf extracts the foreground parameter runs from a rendered string.
func foregroundsOf(rendered string) []string {
	var codes []string
	for _, run := range trueColour.FindAllString(rendered, -1) {
		if strings.HasPrefix(run, "38;") {
			codes = append(codes, run)
		}
	}
	return codes
}
