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

// Package tui holds the presentation vocabulary shared by the plc4go terminal tools: the
// colour theme, the glyph sets and the pane chrome.
//
// It exists so that styling decisions live in exactly one place. The tools it serves
// previously wrote colours as raw markup literals inline, such as "[#0000ff]" and "[white]",
// which meant the palette could not adapt to the terminal, could not be switched off, and
// was pure blue on a dark background in places where that is barely legible.
package tui

import (
	"image/color"
	"os"
	"strings"

	"charm.land/lipgloss/v2"
)

// Options selects a theme variant.
type Options struct {
	// Dark says whether the terminal background is dark. Under Bubble Tea this comes from
	// tea.BackgroundColorMsg, which arrives during startup and again whenever it changes.
	Dark bool
	// NoColor strips all colour, for NO_COLOR, a dumb terminal or a golden-file test.
	NoColor bool
	// ASCII selects the ASCII glyph set instead of the Unicode one.
	ASCII bool
}

// OptionsFromEnv derives options from the environment, honouring the NO_COLOR convention and
// the locale-based Unicode guess. An explicit --ascii flag should override the result.
func OptionsFromEnv(dark bool) Options {
	_, noColorSet := os.LookupEnv("NO_COLOR")
	return Options{
		Dark:    dark,
		NoColor: noColorSet,
		ASCII:   !SupportsUnicodeFromEnv(),
	}
}

// Theme is the resolved set of styles and glyphs a view renders with.
type Theme struct {
	// Glyphs is the drawing-character set.
	Glyphs Glyphs

	// Title is the application name in the status bar.
	Title lipgloss.Style
	// Badge marks a mode that must be impossible to miss, such as demo mode.
	Badge lipgloss.Style
	// Muted is for secondary text: counts, hints, units.
	Muted lipgloss.Style
	// Key is for the key part of a key/value pair, and for keybinding names.
	Key lipgloss.Style
	// Value is for the value part of a key/value pair.
	Value lipgloss.Style
	// Accent draws attention without implying a problem.
	Accent lipgloss.Style
	// Ok, Warn and Err carry status meaning.
	Ok   lipgloss.Style
	Warn lipgloss.Style
	Err  lipgloss.Style

	// Pane is the chrome around an unfocused pane, PaneFocused around the focused one. The
	// difference has to be visible at a glance: not knowing which pane has the keyboard was
	// one of the worst parts of the previous UIs.
	Pane        lipgloss.Style
	PaneFocused lipgloss.Style
	// Chrome and ChromeFocused colour border glyphs that are drawn by hand, as Pane does when
	// it embeds a title in the top edge. These carry a foreground colour ONLY: a style with a
	// Border set would draw a fresh box around every character it rendered.
	Chrome        lipgloss.Style
	ChromeFocused lipgloss.Style

	// PaneTitle and PaneTitleFocused style the name carried in the pane's top border.
	PaneTitle        lipgloss.Style
	PaneTitleFocused lipgloss.Style

	// SelectedRow highlights the current row of a list or table.
	SelectedRow lipgloss.Style

	// Surface is the raised background of the command bar, and OnSurface puts other styles
	// onto it. A terminal cannot nest a background: an inner style's reset ends the outer
	// background mid-row, so a banded row has to be assembled from styles that each carry
	// the background themselves rather than wrapped in one that has it.
	Surface lipgloss.Style

	// The data-type families. A tag's type is looked up with DataType rather than picked here,
	// so a call site never has to know which family a type name belongs to.
	Numeric  lipgloss.Style
	Boolean  lipgloss.Style
	Text     lipgloss.Style
	Temporal lipgloss.Style

	// dark records which variant produced this theme, for tests and for re-resolving.
	dark bool
	// noColor records whether colour was stripped.
	noColor bool
	// surface is the band's background colour, kept so OnSurface can graft it onto any style.
	surface color.Color
	// accent is the accent colour itself, for the few places that need a colour and not a style.
	accent color.Color
}

// IsDark reports which background variant this theme was built for.
func (t Theme) IsDark() bool { return t.dark }

// IsNoColor reports whether the theme was built without colour.
func (t Theme) IsNoColor() bool { return t.noColor }

// The palette. Each entry is a light/dark pair, chosen so that contrast holds on both. These
// are the only colour literals in the tools.
var (
	paletteAccent = pair{light: "#7D56F4", dark: "#A78BFA"}
	paletteOk     = pair{light: "#03713B", dark: "#4ADE80"}
	paletteWarn   = pair{light: "#8A5A00", dark: "#FBBF24"}
	paletteErr    = pair{light: "#B3261E", dark: "#F87171"}
	paletteMuted  = pair{light: "#5C5C66", dark: "#9CA3AF"}
	paletteText   = pair{light: "#1F2933", dark: "#E5E7EB"}
	paletteChrome = pair{light: "#9AA0AA", dark: "#4B5563"}
	// Raised just enough to read as a distinct band against either background, and no more:
	// the command bar should look like a surface, not like a selection.
	paletteSurface = pair{light: "#E4E4E9", dark: "#26262F"}

	// Data-type hues. A tag list is read by shape as much as by name -- "which of these are
	// numbers" is a faster question to answer by colour than by reading each type -- so the
	// families get their own hues rather than all sharing the muted grey.
	paletteNumeric  = pair{light: "#0E7490", dark: "#22D3EE"}
	paletteBoolean  = pair{light: "#B45309", dark: "#F59E0B"}
	paletteTypeText = pair{light: "#3F6212", dark: "#A3E635"}
	paletteTemporal = pair{light: "#6D28D9", dark: "#C4B5FD"}
)

// pair is a light/dark colour pair.
type pair struct {
	light string
	dark  string
}

// NewTheme resolves a theme for the given options.
func NewTheme(options Options) Theme {
	// lightDark picks the member of a pair that suits the background. When colour is off,
	// every style is left unset instead, which renders as the terminal's own default.
	lightDark := lipgloss.LightDark(options.Dark)
	pick := func(p pair) color.Color {
		return lightDark(lipgloss.Color(p.light), lipgloss.Color(p.dark))
	}

	// foreground builds a style, or a bare style when colour is disabled.
	foreground := func(p pair) lipgloss.Style {
		if options.NoColor {
			return lipgloss.NewStyle()
		}
		return lipgloss.NewStyle().Foreground(pick(p))
	}

	glyphs := GlyphsFor(!options.ASCII)

	theme := Theme{
		Glyphs:  glyphs,
		dark:    options.Dark,
		noColor: options.NoColor,

		Title:  foreground(paletteText).Bold(true),
		Muted:  foreground(paletteMuted),
		Key:    foreground(paletteMuted),
		Value:  foreground(paletteText),
		Accent: foreground(paletteAccent),
		Ok:     foreground(paletteOk),
		Warn:   foreground(paletteWarn),
		Err:    foreground(paletteErr),
	}

	// The badge has to read as a badge even with colour off, so it keeps its brackets there.
	if options.NoColor {
		theme.Badge = lipgloss.NewStyle().Bold(true)
	} else {
		theme.Badge = lipgloss.NewStyle().
			Foreground(lipgloss.Color("#FFFFFF")).
			Background(pick(paletteAccent)).
			Bold(true).
			Padding(0, 1)
	}

	theme.Pane = lipgloss.NewStyle().Border(glyphs.Border)
	theme.PaneFocused = lipgloss.NewStyle().Border(glyphs.Border)
	if !options.NoColor {
		theme.Pane = theme.Pane.BorderForeground(pick(paletteChrome))
		theme.PaneFocused = theme.PaneFocused.BorderForeground(pick(paletteAccent))
	}

	theme.Numeric = foreground(paletteNumeric)
	theme.Boolean = foreground(paletteBoolean)
	theme.Text = foreground(paletteTypeText)
	theme.Temporal = foreground(paletteTemporal)

	theme.Chrome = foreground(paletteChrome)
	theme.ChromeFocused = foreground(paletteAccent)

	theme.PaneTitle = foreground(paletteMuted)
	theme.PaneTitleFocused = foreground(paletteAccent).Bold(true)

	// Without colour, the selected row is marked by the Selected glyph alone, which the views
	// draw regardless; reverse video would fight the glyph and hurt readability.
	if options.NoColor {
		theme.SelectedRow = lipgloss.NewStyle().Bold(true)
	} else {
		theme.SelectedRow = lipgloss.NewStyle().Foreground(pick(paletteAccent)).Bold(true)
	}

	// With colour off there is no band, so Surface is bare and OnSurface is the identity.
	if !options.NoColor {
		theme.accent = pick(paletteAccent)
		theme.surface = pick(paletteSurface)
		theme.Surface = lipgloss.NewStyle().Background(theme.surface)
	}

	return theme
}

// CursorColor is the colour of the text cursor, or nil when colour is off. It is the accent,
// because the cursor marks the one place on the screen that takes typing.
func (t Theme) CursorColor() color.Color {
	if t.noColor {
		return nil
	}
	return t.accent
}

// OnSurface returns style as it should be drawn on the command bar: the same foreground, with
// the band's background. It is the identity when colour is off.
func (t Theme) OnSurface(style lipgloss.Style) lipgloss.Style {
	if t.noColor || t.surface == nil {
		return style
	}
	return style.Background(t.surface)
}

// Surfaced returns the theme as it should be drawn on a band: every foreground-only style
// carries the band's background, so a whole row can be assembled without any segment punching
// a hole in it. Badge is left alone, because it sets a background of its own and reads as a
// badge precisely by contrasting with whatever it sits on.
func (t Theme) Surfaced() Theme {
	if t.noColor || t.surface == nil {
		return t
	}
	on := func(style lipgloss.Style) lipgloss.Style { return style.Background(t.surface) }
	surfaced := t
	surfaced.Title = on(t.Title)
	surfaced.Muted = on(t.Muted)
	surfaced.Key = on(t.Key)
	surfaced.Value = on(t.Value)
	surfaced.Accent = on(t.Accent)
	surfaced.Ok = on(t.Ok)
	surfaced.Warn = on(t.Warn)
	surfaced.Err = on(t.Err)
	surfaced.Chrome = on(t.Chrome)
	surfaced.Numeric = on(t.Numeric)
	surfaced.Boolean = on(t.Boolean)
	surfaced.Text = on(t.Text)
	surfaced.Temporal = on(t.Temporal)
	return surfaced
}

// DataType returns the style for a PLC data type, by family.
//
// The families are what a reader actually distinguishes: numbers, flags, text and times. An
// unknown type falls back to Muted rather than to an arbitrary hue, so a type this does not
// know about is visibly uncategorised instead of miscategorised.
func (t Theme) DataType(dataType string) lipgloss.Style {
	switch normaliseDataType(dataType) {
	case dataNumeric:
		return t.Numeric
	case dataBoolean:
		return t.Boolean
	case dataText:
		return t.Text
	case dataTemporal:
		return t.Temporal
	default:
		return t.Muted
	}
}

// dataFamily groups the PLC data types by how they are read.
type dataFamily int

const (
	dataUnknown dataFamily = iota
	dataNumeric
	dataBoolean
	dataText
	dataTemporal
)

// normaliseDataType maps a plc4x type name onto its family.
//
// The names come from IEC 61131-3, so they are a closed and well-known set; matching on the
// suffix rather than an exhaustive list keeps the sized variants (LREAL, ULINT, WSTRING) in the
// right family without listing every one.
func normaliseDataType(dataType string) dataFamily {
	name := strings.ToUpper(strings.TrimSpace(dataType))
	switch {
	case name == "":
		return dataUnknown
	case name == "BOOL" || name == "BIT":
		return dataBoolean
	case strings.HasSuffix(name, "STRING") || name == "CHAR" || name == "WCHAR":
		return dataText
	// IEC 61131-3 spells every long temporal type as the base name with an L in front --
	// LTIME, LDATE, LTOD, LDT, LDATE_AND_TIME -- so the L is dropped before matching rather
	// than each spelling being listed. That is safe here only because this branch runs before
	// the numeric one: LINT, LREAL and LWORD all reduce to names no temporal prefix matches.
	case isTemporalName(strings.TrimPrefix(name, "L")):
		return dataTemporal
	case strings.HasSuffix(name, "REAL") || strings.HasSuffix(name, "INT") ||
		strings.HasSuffix(name, "WORD") || name == "BYTE":
		return dataNumeric
	default:
		return dataUnknown
	}
}

// isTemporalName reports whether an L-stripped type name is a time or a date.
func isTemporalName(name string) bool {
	return strings.HasPrefix(name, "TIME") || strings.HasPrefix(name, "DATE") ||
		strings.HasPrefix(name, "TOD") || name == "DT"
}

// LogLevel returns the style for a log level, so a wall of log lines has shape.
func (t Theme) LogLevel(level string) lipgloss.Style {
	switch strings.ToLower(strings.TrimSpace(level)) {
	case "error", "err", "fatal", "panic":
		return t.Err
	case "warn", "warning":
		return t.Warn
	case "info":
		return t.Accent
	default:
		// trace and debug are the noise you turned the level up to see; they stay quiet.
		return t.Muted
	}
}

// PaneStyle returns the chrome for a pane, focused or not.
func (t Theme) PaneStyle(focused bool) lipgloss.Style {
	if focused {
		return t.PaneFocused
	}
	return t.Pane
}

// ChromeStyle returns the border-glyph colour for a pane, focused or not.
func (t Theme) ChromeStyle(focused bool) lipgloss.Style {
	if focused {
		return t.ChromeFocused
	}
	return t.Chrome
}

// PaneTitleStyle returns the title style for a pane, focused or not.
func (t Theme) PaneTitleStyle(focused bool) lipgloss.Style {
	if focused {
		return t.PaneTitleFocused
	}
	return t.PaneTitle
}
