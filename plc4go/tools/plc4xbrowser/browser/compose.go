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

package browser

import (
	"context"
	"strconv"
	"strings"

	"charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
)

// The request composer.
//
// This is the interface behind read, write, browse and subscribe -- the four commands the
// previous implementation advertised in its help and then answered with "mode switch not yet
// implemented". They were never finished because a read is a multi-tag request and the
// single-line prompt cannot express one: the -direct variants exist precisely because the real
// form did not. A modal form is the missing piece.

// composeField identifies a focusable field in the form.
type composeField int

const (
	fieldConnection composeField = iota
	fieldFirstTag
)

// composeRow is one tag row of the form.
type composeRow struct {
	name    string
	address string
	value   string
}

// composer is the modal request form.
type composer struct {
	operation Operation
	// connection is the connection the request runs against.
	connection string
	// query is the browse query; browse has no tag rows.
	query string
	rows  []composeRow

	// field is the focused field index: 0 is the connection, then two or three cells per tag
	// row depending on whether the operation writes.
	field int
	// submitting suppresses further edits while the request is in flight.
	submitting bool
	// problem is the validation message shown at the foot of the form.
	problem string
}

// cellsPerRow is how many editable cells a tag row has: name and address, plus a value for a
// write.
func (c *composer) cellsPerRow() int {
	if c.operation == OperationWrite {
		return 3
	}
	return 2
}

// fieldCount is the number of focusable cells, including the connection and the trailing
// "add tag" affordance.
func (c *composer) fieldCount() int {
	if c.operation == OperationBrowse {
		// Connection and query.
		return 2
	}
	return 1 + len(c.rows)*c.cellsPerRow() + 1
}

// isAddRow reports whether the focus is on the "+ add tag" affordance.
func (c *composer) isAddRow() bool {
	return c.operation != OperationBrowse && c.field == c.fieldCount()-1
}

// rowAndCell maps the focused field onto a tag row and the cell within it.
func (c *composer) rowAndCell() (row, cell int, ok bool) {
	if c.operation == OperationBrowse || c.field == 0 || c.isAddRow() {
		return 0, 0, false
	}
	offset := c.field - 1
	return offset / c.cellsPerRow(), offset % c.cellsPerRow(), true
}

// openComposer opens the form for a spec.
func (m *Model) openComposer(spec ComposeSpec) {
	form := &composer{
		operation:  spec.Operation,
		connection: spec.Connection,
		query:      spec.Query,
	}
	for _, tag := range spec.Tags {
		form.rows = append(form.rows, composeRow{name: tag.Name, address: tag.Address})
	}
	if len(form.rows) == 0 && spec.Operation != OperationBrowse {
		form.rows = append(form.rows, composeRow{name: "tag1"})
	}
	if form.connection == "" {
		// Nothing was named on the command line, so start on the connection field; otherwise
		// start where the user still has work to do.
		form.field = int(fieldConnection)
	} else {
		form.field = int(fieldFirstTag)
	}
	m.composer = form
	m.prompt.Blur()
}

// closeComposer dismisses the form and returns the keyboard to the prompt.
func (m *Model) closeComposer() {
	m.composer = nil
	m.focusPrompt()
}

// updateComposer handles a key while the form is open.
func (m *Model) updateComposer(msg tea.KeyPressMsg) (tea.Model, tea.Cmd) {
	form := m.composer
	if form.submitting {
		// The request is in flight. Only escape is honoured, so a stray keystroke cannot edit
		// a form that has already been sent.
		if msg.String() == "esc" {
			m.closeComposer()
		}
		return m, nil
	}

	switch msg.String() {
	case "esc":
		m.closeComposer()
		return m, nil

	case "tab", "down":
		form.field = (form.field + 1) % form.fieldCount()
		return m, nil

	case "shift+tab", "up":
		form.field = (form.field - 1 + form.fieldCount()) % form.fieldCount()
		return m, nil

	case "enter":
		if form.isAddRow() {
			form.addRow()
			return m, nil
		}
		return m.submitComposer()

	case "ctrl+n":
		form.addRow()
		return m, nil

	case "ctrl+d":
		form.removeRow()
		return m, nil

	case "backspace":
		form.edit(func(current string) string {
			if current == "" {
				return current
			}
			runes := []rune(current)
			return string(runes[:len(runes)-1])
		})
		return m, nil
	}

	// Any other printable key types into the focused cell.
	if text := msg.String(); len([]rune(text)) == 1 {
		form.edit(func(current string) string { return current + text })
	}
	return m, nil
}

// addRow appends an empty tag row and moves to its first cell.
func (c *composer) addRow() {
	if c.operation == OperationBrowse {
		return
	}
	c.rows = append(c.rows, composeRow{name: "tag" + strconv.Itoa(len(c.rows)+1)})
	c.field = 1 + (len(c.rows)-1)*c.cellsPerRow()
}

// removeRow deletes the focused tag row, keeping at least one.
func (c *composer) removeRow() {
	row, _, ok := c.rowAndCell()
	if !ok || len(c.rows) <= 1 {
		return
	}
	c.rows = append(c.rows[:row], c.rows[row+1:]...)
	if c.field >= c.fieldCount()-1 {
		c.field = c.fieldCount() - 2
	}
}

// edit applies a change to the focused cell.
func (c *composer) edit(change func(string) string) {
	if c.field == 0 {
		c.connection = change(c.connection)
		return
	}
	if c.operation == OperationBrowse {
		c.query = change(c.query)
		return
	}
	row, cell, ok := c.rowAndCell()
	if !ok {
		return
	}
	switch cell {
	case 0:
		c.rows[row].name = change(c.rows[row].name)
	case 1:
		c.rows[row].address = change(c.rows[row].address)
	case 2:
		c.rows[row].value = change(c.rows[row].value)
	}
}

// validate reports why the form cannot be submitted, or an empty string when it can.
func (c *composer) validate() string {
	if strings.TrimSpace(c.connection) == "" {
		return "a connection is required"
	}
	if c.operation == OperationBrowse {
		return ""
	}
	for i, row := range c.rows {
		if strings.TrimSpace(row.address) == "" {
			return "tag " + strconv.Itoa(i+1) + " needs an address"
		}
		if c.operation == OperationWrite && strings.TrimSpace(row.value) == "" {
			return "tag " + strconv.Itoa(i+1) + " needs a value to write"
		}
	}
	return ""
}

// tagSpecs turns the form's rows into a request.
func (c *composer) tagSpecs() []plcsession.TagSpec {
	specs := make([]plcsession.TagSpec, 0, len(c.rows))
	for i, row := range c.rows {
		name := strings.TrimSpace(row.name)
		if name == "" {
			name = "tag" + strconv.Itoa(i+1)
		}
		specs = append(specs, plcsession.TagSpec{
			Name:    name,
			Address: strings.TrimSpace(row.address),
			Value:   strings.TrimSpace(row.value),
		})
	}
	return specs
}

// submitComposer validates and runs the composed request.
func (m *Model) submitComposer() (tea.Model, tea.Cmd) {
	form := m.composer
	if problem := form.validate(); problem != "" {
		form.problem = problem
		return m, nil
	}
	form.problem = ""
	form.submitting = true

	session := m.options.Session
	env := m.env
	operation := form.operation
	connection := strings.TrimSpace(form.connection)
	query := strings.TrimSpace(form.query)
	tags := form.tagSpecs()

	return m, func() tea.Msg {
		ctx := context.Background()
		started := env.Now()
		switch operation {
		case OperationRead:
			result, err := session.Read(ctx, connection, tags)
			if err != nil {
				return commandDoneMsg{err: err}
			}
			return commandDoneMsg{result: resultForTags(env, plcsession.EventRead, connection, result.Tags, result.Duration, started)}

		case OperationWrite:
			result, err := session.Write(ctx, connection, tags)
			if err != nil {
				return commandDoneMsg{err: err}
			}
			return commandDoneMsg{result: resultForTags(env, plcsession.EventWrite, connection, result.Tags, result.Duration, started)}

		case OperationBrowse:
			result, err := session.Browse(ctx, connection, query)
			if err != nil {
				return commandDoneMsg{err: err}
			}
			out := Result{Lines: []string{
				"browse " + connection + " found " + strconv.Itoa(len(result.Items)) + " tags",
			}}
			for _, item := range result.Items {
				out.Events = append(out.Events, plcsession.Event{
					Kind:       plcsession.EventBrowse,
					Connection: connection,
					Started:    started,
					Received:   env.Now(),
					Summary:    browseSummary(item),
					Tags: []plcsession.TagResult{{
						Name:     item.Name,
						Address:  item.Address,
						DataType: item.DataType,
						Code:     plcsession.ResponseCodeOK,
					}},
				})
			}
			return commandDoneMsg{result: out}

		case OperationSubscribe:
			streamCtx, cancel := env.StreamContext()
			events, err := session.Subscribe(streamCtx, connection, tags)
			if err != nil {
				cancel()
				return commandDoneMsg{err: err}
			}
			label := tags[0].Address
			if len(tags) > 1 {
				label = tags[0].Address + " +" + strconv.Itoa(len(tags)-1)
			}
			return commandDoneMsg{result: Result{
				Lines:  []string{"subscribed " + connection + " " + label},
				Stream: &Stream{Connection: connection, Label: label, Events: events, Cancel: cancel},
			}}
		}
		return commandDoneMsg{}
	}
}

// ComposerOpen reports whether the form is showing, for tests.
func (m *Model) ComposerOpen() bool { return m.composer != nil }

// ComposerProblem exposes the validation message, for tests.
func (m *Model) ComposerProblem() string {
	if m.composer == nil {
		return ""
	}
	return m.composer.problem
}

// renderComposer draws the form.
func (m *Model) renderComposer(width, height int) string {
	form := m.composer
	theme := m.theme
	glyphs := theme.Glyphs

	// The form is a fixed, readable width rather than the full pane: a form stretched across a
	// wide terminal is harder to read, not easier.
	formWidth := min(max(width-8, 40), 68)

	title := strings.ToUpper(string(form.operation)[:1]) + string(form.operation)[1:] + " request"
	if form.connection != "" {
		title += " " + glyphs.Separator + " " + form.connection
	}

	var lines []string
	lines = append(lines, m.composeCell("connection", form.connection, 0, formWidth-4))

	if form.operation == OperationBrowse {
		lines = append(lines, m.composeCell("query", form.query, 1, formWidth-4))
	} else {
		for i, row := range form.rows {
			base := 1 + i*form.cellsPerRow()
			cells := []string{
				m.composeInline("tag name", row.name, base, 20),
				m.composeInline("address", row.address, base+1, 22),
			}
			if form.operation == OperationWrite {
				cells = append(cells, m.composeInline("value", row.value, base+2, 12))
			}
			lines = append(lines, "  "+strings.Join(cells, "  "))
		}
		add := "  + add tag"
		if form.isAddRow() {
			add = theme.SelectedRow.Render("  " + glyphs.Selected + " add tag")
		}
		lines = append(lines, add)
	}

	lines = append(lines, "")
	switch {
	case form.submitting:
		lines = append(lines, "  "+theme.Muted.Render("running…"))
	case form.problem != "":
		lines = append(lines, "  "+theme.Err.Render(glyphs.Err+" "+form.problem))
	default:
		lines = append(lines, "  "+theme.Ok.Render(glyphs.Ok+" ready")+
			"   "+theme.Muted.Render("enter run "+glyphs.Separator+" tab next "+glyphs.Separator+" esc cancel"))
	}

	body := strings.Join(lines, "\n")
	pane := tui.Pane{Title: title, Width: formWidth, Height: len(lines) + 2, Focused: true}
	return pane.Render(theme, body)
}

// composeCell renders a full-width labelled field.
func (m *Model) composeCell(label, value string, field, width int) string {
	return "  " + m.composeInline(label, value, field, width-len(label)-4)
}

// composeInline renders one labelled field, marking it when focused.
func (m *Model) composeInline(label, value string, field, width int) string {
	theme := m.theme
	shown := value
	if m.composer.field == field && !m.composer.submitting {
		// A block cursor, so the focused cell is unmistakable even with colour off.
		shown = value + "█"
	}
	shown = pad(shown, width)
	if m.composer.field == field {
		return theme.Muted.Render(label+" ") + theme.SelectedRow.Render(shown)
	}
	return theme.Muted.Render(label+" ") + theme.Value.Render(shown)
}

// pad right-pads text to a width, measured in display cells.
func pad(text string, width int) string {
	actual := lipgloss.Width(text)
	if actual >= width {
		return text
	}
	return text + strings.Repeat(" ", width-actual)
}
