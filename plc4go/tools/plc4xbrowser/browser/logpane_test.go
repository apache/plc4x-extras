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
	"strings"
	"testing"
	"time"

	tea "charm.land/bubbletea/v2"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// atInfoLevel holds the global log level for one test and puts it back afterwards.
//
// The level is process-wide and one of the commands cycles it, so a test that emits a line has
// to say which level it is emitting at or it inherits whatever ran before it.
func atInfoLevel(t *testing.T) {
	t.Helper()
	previous := zerolog.GlobalLevel()
	zerolog.SetGlobalLevel(zerolog.InfoLevel)
	t.Cleanup(func() { zerolog.SetGlobalLevel(previous) })
}

// drainLog takes one batch of log lines, or fails the test.
//
// The wait is bounded because the drain blocks by design: an unbounded read turns a redirect
// that delivers nothing into a hung package rather than a failed test.
func drainLog(t *testing.T, model *Model) tea.Msg {
	t.Helper()
	drained := make(chan tea.Msg, 1)
	go func() { drained <- waitForLog(model.logCh)() }()
	select {
	case msg := <-drained:
		return msg
	case <-time.After(5 * time.Second):
		t.Fatal("no log line reached the pane")
		return nil
	}
}

// TestTheModelsLogWriterReachesTheLogPane pins the seam anything writing from another goroutine
// uses to reach the pane.
func TestTheModelsLogWriterReachesTheLogPane(t *testing.T) {
	model := newTestModel(t)

	_, err := model.LogWriter().Write([]byte("hello from a goroutine\n"))
	require.NoError(t, err)

	msg := drainLog(t, model)
	updated, _ := model.Update(msg)
	model = updated.(*Model)

	assert.Contains(t, strings.Join(model.logLines, "\n"), "hello from a goroutine")
}

// TestTheGlobalLoggerReachesTheLogPaneRatherThanTheTerminal is the regression that matters: the
// drivers log through zerolog's global logger, and while the interface owns the screen a write
// straight to the terminal paints over the prompt. Setting the level is not enough -- the
// output has to be redirected too.
func TestTheGlobalLoggerReachesTheLogPaneRatherThanTheTerminal(t *testing.T) {
	atInfoLevel(t)
	model := newTestModel(t)

	restore := redirectGlobalLogger(model.LogWriter(), zerolog.InfoLevel)
	t.Cleanup(restore)

	log.Info().Str("protocolName", "BACnet/IP").Msg("Driver for protocolName registered")

	msg := drainLog(t, model)
	updated, _ := model.Update(msg)
	model = updated.(*Model)

	pane := strings.Join(model.logLines, "\n")
	assert.Contains(t, pane, "Driver for protocolName registered")
	assert.Contains(t, pane, "BACnet/IP", "the fields carry the detail, so they belong in the pane")
}

// TestRedirectingTheGlobalLoggerIsReversible keeps the tool from leaving the process's logger
// pointed at a pane that no longer renders once the interface exits.
func TestRedirectingTheGlobalLoggerIsReversible(t *testing.T) {
	model := newTestModel(t)
	before := log.Logger

	restore := redirectGlobalLogger(model.LogWriter(), zerolog.InfoLevel)
	require.NotEqual(t, before, log.Logger, "the redirect must actually replace the logger")
	restore()

	assert.Equal(t, before, log.Logger)
}

// TestTheModelAdoptsALogChannelTheCallerAlreadyOwns pins the seam the startup ordering needs.
//
// plc4x copies the global logger when a component is constructed, so a redirect installed after
// the session is built never reaches the session, the transports or the drivers. The redirect
// therefore has to happen before any of that exists -- which means the channel behind it has to
// exist before the model does, and the model has to adopt it rather than make its own.
func TestTheModelAdoptsALogChannelTheCallerAlreadyOwns(t *testing.T) {
	early := make(chan string, 8)
	early <- "Transport for transportName registered"

	model := newTestModelWithLogChannel(t, early)

	msg := drainLog(t, model)
	updated, _ := model.Update(msg)
	model = updated.(*Model)

	assert.Contains(t, strings.Join(model.logLines, "\n"), "Transport for transportName registered",
		"a line logged before the model existed must still reach the pane")
}
