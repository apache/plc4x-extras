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
	// PaneTitle and PaneTitleFocused style the name carried in the pane's top border.
	PaneTitle        lipgloss.Style
	PaneTitleFocused lipgloss.Style

	// SelectedRow highlights the current row of a list or table.
	SelectedRow lipgloss.Style

	// dark records which variant produced this theme, for tests and for re-resolving.
	dark bool
	// noColor records whether colour was stripped.
	noColor bool
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

	theme.PaneTitle = foreground(paletteMuted)
	theme.PaneTitleFocused = foreground(paletteAccent).Bold(true)

	// Without colour, the selected row is marked by the Selected glyph alone, which the views
	// draw regardless; reverse video would fight the glyph and hurt readability.
	if options.NoColor {
		theme.SelectedRow = lipgloss.NewStyle().Bold(true)
	} else {
		theme.SelectedRow = lipgloss.NewStyle().Foreground(pick(paletteAccent)).Bold(true)
	}

	return theme
}

// PaneStyle returns the chrome for a pane, focused or not.
func (t Theme) PaneStyle(focused bool) lipgloss.Style {
	if focused {
		return t.PaneFocused
	}
	return t.Pane
}

// PaneTitleStyle returns the title style for a pane, focused or not.
func (t Theme) PaneTitleStyle(focused bool) lipgloss.Style {
	if focused {
		return t.PaneTitleFocused
	}
	return t.PaneTitle
}
