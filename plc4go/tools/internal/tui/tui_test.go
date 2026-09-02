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
	"strings"
	"testing"

	"charm.land/lipgloss/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestGlyphSetsHaveMatchingWidths is the property the ASCII fallback depends on. Layout
// arithmetic is done in display cells, so if an ASCII glyph were a different width from the
// Unicode glyph it replaces, switching sets would shift or clip every pane that uses it.
func TestGlyphSetsHaveMatchingWidths(t *testing.T) {
	unicode, ascii := UnicodeGlyphs(), ASCIIGlyphs()

	pairs := map[string][2]string{
		"Selected":      {unicode.Selected, ascii.Selected},
		"Unselected":    {unicode.Unselected, ascii.Unselected},
		"Yes":           {unicode.Yes, ascii.Yes},
		"No":            {unicode.No, ascii.No},
		"Inbound":       {unicode.Inbound, ascii.Inbound},
		"Outbound":      {unicode.Outbound, ascii.Outbound},
		"Ok":            {unicode.Ok, ascii.Ok},
		"Warn":          {unicode.Warn, ascii.Warn},
		"Err":           {unicode.Err, ascii.Err},
		"Ellipsis":      {unicode.Ellipsis, ascii.Ellipsis},
		"Separator":     {unicode.Separator, ascii.Separator},
		"ProgressFull":  {unicode.ProgressFull, ascii.ProgressFull},
		"ProgressEmpty": {unicode.ProgressEmpty, ascii.ProgressEmpty},
	}
	for name, p := range pairs {
		t.Run(name, func(t *testing.T) {
			assert.Equal(t, lipgloss.Width(p[0]), lipgloss.Width(p[1]),
				"%s: unicode %q is %d cells, ascii %q is %d cells",
				name, p[0], lipgloss.Width(p[0]), p[1], lipgloss.Width(p[1]))
		})
	}
}

// TestEveryGlyphIsExactlyOneCell keeps the sets usable in fixed-width columns.
func TestEveryGlyphIsExactlyOneCell(t *testing.T) {
	for setName, set := range map[string]Glyphs{"unicode": UnicodeGlyphs(), "ascii": ASCIIGlyphs()} {
		for name, glyph := range map[string]string{
			"Selected": set.Selected, "Unselected": set.Unselected,
			"Yes": set.Yes, "No": set.No,
			"Inbound": set.Inbound, "Outbound": set.Outbound,
			"Ok": set.Ok, "Warn": set.Warn, "Err": set.Err,
			"Ellipsis": set.Ellipsis, "Separator": set.Separator,
			"ProgressFull": set.ProgressFull, "ProgressEmpty": set.ProgressEmpty,
		} {
			t.Run(setName+"/"+name, func(t *testing.T) {
				assert.Equal(t, 1, lipgloss.Width(glyph), "%q must occupy one cell", glyph)
			})
		}
	}
}

func TestSpinnerFramesAreUniformWidth(t *testing.T) {
	for setName, set := range map[string]Glyphs{"unicode": UnicodeGlyphs(), "ascii": ASCIIGlyphs()} {
		t.Run(setName, func(t *testing.T) {
			require.NotEmpty(t, set.Spinner)
			for _, frame := range set.Spinner {
				assert.Equal(t, 1, lipgloss.Width(frame),
					"spinner frame %q must be one cell or the prompt will jitter", frame)
			}
		})
	}
}

func TestASCIIGlyphsAreActuallyASCII(t *testing.T) {
	set := ASCIIGlyphs()
	all := strings.Join(append([]string{
		set.Selected, set.Unselected, set.Yes, set.No, set.Inbound, set.Outbound,
		set.Ok, set.Warn, set.Err, set.Ellipsis, set.Separator,
		set.ProgressFull, set.ProgressEmpty,
	}, set.Spinner...), "")
	for _, r := range all {
		assert.Less(t, r, rune(128), "the ascii set must contain no multi-byte rune, found %q", r)
	}
}

// TestASCIIBorderIsASCII guards the pane chrome, which is the most visible Unicode in the UI.
func TestASCIIBorderIsASCII(t *testing.T) {
	border := ASCIIGlyphs().Border
	for _, part := range []string{
		border.Top, border.Bottom, border.Left, border.Right,
		border.TopLeft, border.TopRight, border.BottomLeft, border.BottomRight,
	} {
		for _, r := range part {
			assert.Less(t, r, rune(128), "ascii border part %q contains a multi-byte rune %q", part, r)
		}
	}
}

func TestSupportsUnicodeReadsTheLocale(t *testing.T) {
	tests := map[string]struct {
		env  map[string]string
		want bool
	}{
		"utf-8 in LANG":           {map[string]string{"LANG": "en_US.UTF-8"}, true},
		"utf8 without hyphen":     {map[string]string{"LANG": "en_US.utf8"}, true},
		"LC_ALL wins over LANG":   {map[string]string{"LC_ALL": "C", "LANG": "en_US.UTF-8"}, false},
		"LC_CTYPE utf-8":          {map[string]string{"LC_CTYPE": "de_DE.UTF-8"}, true},
		"plain C locale":          {map[string]string{"LANG": "C"}, false},
		"POSIX locale":            {map[string]string{"LANG": "POSIX"}, false},
		"nothing set at all":      {map[string]string{}, false},
		"empty vars fall through": {map[string]string{"LC_ALL": "", "LANG": "en_GB.UTF-8"}, true},
	}
	for name, tc := range tests {
		t.Run(name, func(t *testing.T) {
			got := SupportsUnicode(func(key string) string { return tc.env[key] })
			assert.Equal(t, tc.want, got)
		})
	}
}

func TestGlyphsForSelectsTheSet(t *testing.T) {
	assert.Equal(t, UnicodeGlyphs().Selected, GlyphsFor(true).Selected)
	assert.Equal(t, ASCIIGlyphs().Selected, GlyphsFor(false).Selected)
}

// TestThemeRendersDifferentlyForLightAndDark proves the palette actually adapts rather than
// being nominally adaptive. The old UIs hardcoded colours such as pure blue, which is close
// to illegible on a dark background.
func TestThemeRendersDifferentlyForLightAndDark(t *testing.T) {
	light := NewTheme(Options{Dark: false})
	dark := NewTheme(Options{Dark: true})

	assert.NotEqual(t, light.Accent.Render("x"), dark.Accent.Render("x"),
		"the accent colour must differ between light and dark backgrounds")
	assert.NotEqual(t, light.Err.Render("x"), dark.Err.Render("x"))
	assert.False(t, light.IsDark())
	assert.True(t, dark.IsDark())
}

// TestNoColorThemeEmitsNoEscapeSequences is the NO_COLOR contract, and it is also what makes
// golden-file tests stable.
func TestNoColorThemeEmitsNoEscapeSequences(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	require.True(t, theme.IsNoColor())

	for name, style := range map[string]lipgloss.Style{
		"Title": theme.Title, "Muted": theme.Muted, "Key": theme.Key, "Value": theme.Value,
		"Accent": theme.Accent, "Ok": theme.Ok, "Warn": theme.Warn, "Err": theme.Err,
		"SelectedRow": theme.SelectedRow, "Badge": theme.Badge,
		"PaneTitle": theme.PaneTitle, "PaneTitleFocused": theme.PaneTitleFocused,
	} {
		t.Run(name, func(t *testing.T) {
			rendered := style.Render("sample")
			assert.NotContains(t, rendered, "\x1b[3", "%s must not set a foreground colour", name)
			assert.NotContains(t, rendered, "\x1b[4", "%s must not set a background colour", name)
			assert.Contains(t, rendered, "sample", "%s must still render its text", name)
		})
	}
}

func TestNoColorPanesKeepTheirBorders(t *testing.T) {
	theme := NewTheme(Options{Dark: true, NoColor: true})
	// Losing colour must not lose structure: the borders are what explain the layout.
	rendered := theme.Pane.Render("content")
	assert.Contains(t, rendered, "content")
	assert.Greater(t, strings.Count(rendered, "\n"), 1, "a bordered pane spans several lines")
}

// TestFocusIsVisuallyDistinct pins the fix for having had no focus indication at all.
func TestFocusIsVisuallyDistinct(t *testing.T) {
	theme := NewTheme(Options{Dark: true})
	assert.NotEqual(t,
		theme.PaneStyle(false).Render("x"),
		theme.PaneStyle(true).Render("x"),
		"the focused pane must be distinguishable from an unfocused one")
	assert.NotEqual(t,
		theme.PaneTitleStyle(false).Render("x"),
		theme.PaneTitleStyle(true).Render("x"))
}

func TestASCIIOptionSelectsTheASCIIBorder(t *testing.T) {
	assert.Equal(t, ASCIIGlyphs().Border, NewTheme(Options{ASCII: true}).Glyphs.Border)
	assert.Equal(t, UnicodeGlyphs().Border, NewTheme(Options{ASCII: false}).Glyphs.Border)
}

func TestOptionsFromEnvHonoursNoColor(t *testing.T) {
	t.Setenv("NO_COLOR", "1")
	assert.True(t, OptionsFromEnv(true).NoColor)

	// An empty NO_COLOR still counts as set, per the convention: presence is the signal.
	t.Setenv("NO_COLOR", "")
	assert.True(t, OptionsFromEnv(true).NoColor)
}

func TestOptionsFromEnvDerivesASCIIFromLocale(t *testing.T) {
	t.Setenv("LC_ALL", "en_US.UTF-8")
	t.Setenv("LANG", "en_US.UTF-8")
	assert.False(t, OptionsFromEnv(true).ASCII, "a UTF-8 locale should keep the Unicode glyphs")

	t.Setenv("LC_ALL", "C")
	assert.True(t, OptionsFromEnv(true).ASCII, "a C locale should fall back to ASCII")
}
