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
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// The command layer is tested against the demo session rather than a bespoke mock: it is the
// same implementation --demo runs on, so these tests exercise the path a user actually takes.

// testEnv builds an environment over a demo session, connected to the first demo device.
func testEnv(t *testing.T) (*Env, *Registry) {
	t.Helper()

	session := plcsession.NewDemo(plcsession.DemoOptions{
		SubscriptionInterval: time.Millisecond,
		Now:                  steppingClock(),
	})
	t.Cleanup(func() { _ = session.Close() })
	_, err := session.RegisterDriver(plcsession.DemoProtocol)
	require.NoError(t, err)
	_, err = session.Connect(t.Context(), plcsession.DemoDeviceOne)
	require.NoError(t, err)

	config := NewConfig()
	registry := NewCommands()
	env := &Env{
		Session:  session,
		Config:   &config,
		Now:      steppingClock(),
		Registry: registry,
		Demo:     true,
		StreamContext: func() (context.Context, context.CancelFunc) {
			return context.WithCancel(context.Background())
		},
	}
	return env, registry
}

// steppingClock returns timestamps that advance, so ordering is observable without wall time.
func steppingClock() func() time.Time {
	base := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	step := 0
	return func() time.Time {
		step++
		return base.Add(time.Duration(step) * time.Second)
	}
}

// TestEveryAdvertisedCommandIsReachable guards the inventory. The port had to preserve every
// command the previous tool offered, and a command that the registry cannot resolve is a
// command the user's saved history can no longer run.
func TestEveryAdvertisedCommandIsReachable(t *testing.T) {
	_, registry := testEnv(t)
	for _, name := range []string{
		"discover", "connect", "disconnect", "register",
		"read", "write", "browse", "subscribe",
		"read-direct", "write-direct", "browse-direct", "subscribe-direct",
		"log level", "log clear",
		"history", "clear", "help", "quit",
	} {
		t.Run(name, func(t *testing.T) {
			// A trailing space matters: Resolve deliberately leaves a bare final word
			// unconsumed, because a word still being typed belongs to completion rather than
			// to dispatch. That is what keeps "read" and "read-direct" apart.
			command, path, _ := registry.Resolve(name + " ")
			require.NotNil(t, command, "%q must resolve to a command", name)
			assert.Equal(t, name, strings.TrimSpace(path))
			assert.NotNil(t, command.Run, "%q must be runnable, not just a grouping node", name)
		})
	}
}

// TestLogIsAGroupingCommand documents the one node that is deliberately not runnable.
func TestLogIsAGroupingCommand(t *testing.T) {
	_, registry := testEnv(t)
	command, path, _ := registry.Resolve("log ")
	require.NotNil(t, command)
	assert.Equal(t, "log", path)
	assert.Nil(t, command.Run, "log only groups its subcommands")
	assert.NotEmpty(t, command.Sub)
}

func TestEveryRunnableCommandHasHelpText(t *testing.T) {
	_, registry := testEnv(t)
	registry.Walk(func(depth int, path string, command *Command) {
		if depth == 0 {
			return
		}
		assert.NotEmpty(t, command.Description, "%s needs a description; it appears in help", path)
	})
}

// TestTheFourPreviouslyUnimplementedCommandsNowDoSomething is the regression test for the
// headline defect: read, write, browse and subscribe each answered "mode switch not yet
// implemented" and did nothing at all.
func TestTheFourPreviouslyUnimplementedCommandsNowDoSomething(t *testing.T) {
	for _, name := range []string{"read", "write", "browse", "subscribe"} {
		t.Run(name, func(t *testing.T) {
			env, registry := testEnv(t)
			result, err := registry.Execute(t.Context(), env, name+" "+plcsession.DemoDeviceOne)
			require.NoError(t, err, "%s must not fail", name)
			require.NotNil(t, result.Compose, "%s must open the request composer", name)
			assert.Equal(t, Operation(name), result.Compose.Operation)
			assert.Equal(t, plcsession.DemoDeviceOne, result.Compose.Connection)

			// The old implementation's error text must be gone entirely.
			assert.NotContains(t, strings.Join(result.Lines, " "), "not yet implemented")
		})
	}
}

func TestComposeCommandsRejectAnUnknownConnection(t *testing.T) {
	env, registry := testEnv(t)
	_, err := registry.Execute(t.Context(), env, "read demo://nowhere")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "not connected")
}

func TestComposeCommandPrefillsTagsFromTheCommandLine(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "read "+plcsession.DemoDeviceOne+" temp/1 press/1")
	require.NoError(t, err)
	require.NotNil(t, result.Compose)
	require.Len(t, result.Compose.Tags, 2)
	assert.Equal(t, "temp/1", result.Compose.Tags[0].Address)
	assert.Equal(t, "press/1", result.Compose.Tags[1].Address)
}

func TestBrowseComposePrefillsTheQueryRatherThanTags(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "browse "+plcsession.DemoDeviceOne+" temp")
	require.NoError(t, err)
	require.NotNil(t, result.Compose)
	assert.Equal(t, "temp", result.Compose.Query)
	assert.Empty(t, result.Compose.Tags)
}

func TestReadDirectProducesAMessageEntry(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "read-direct "+plcsession.DemoDeviceOne+" temp/1")
	require.NoError(t, err)
	require.Len(t, result.Events, 1)

	event := result.Events[0]
	assert.Equal(t, plcsession.EventRead, event.Kind)
	assert.Equal(t, plcsession.DemoDeviceOne, event.Connection)
	require.Len(t, event.Tags, 1)
	assert.True(t, event.Tags[0].Succeeded())
	assert.NotEmpty(t, event.Tags[0].Value, "a read that shows no value is not worth showing")
	assert.NotEmpty(t, result.Lines)
}

func TestWriteDirectIsObservableByAFollowingRead(t *testing.T) {
	env, registry := testEnv(t)
	_, err := registry.Execute(t.Context(), env, "write-direct "+plcsession.DemoDeviceOne+" motor/speed 1234")
	require.NoError(t, err)

	result, err := registry.Execute(t.Context(), env, "read-direct "+plcsession.DemoDeviceOne+" motor/speed")
	require.NoError(t, err)
	require.Len(t, result.Events, 1)
	assert.Equal(t, "1234", result.Events[0].Tags[0].Value)
}

func TestBrowseDirectListsTags(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "browse-direct "+plcsession.DemoDeviceOne)
	require.NoError(t, err)
	assert.NotEmpty(t, result.Events, "browsing the demo device should find its catalogue")
	assert.Contains(t, strings.Join(result.Lines, " "), "found")
}

func TestSubscribeDirectReturnsAStreamTheModelCanDrain(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "subscribe-direct "+plcsession.DemoDeviceOne+" temp/1")
	require.NoError(t, err)
	require.NotNil(t, result.Stream)
	require.NotNil(t, result.Stream.Cancel)
	t.Cleanup(result.Stream.Cancel)

	select {
	case event, ok := <-result.Stream.Events:
		require.True(t, ok, "the stream closed before delivering anything")
		assert.Equal(t, plcsession.EventSubscribe, event.Kind)
	case <-time.After(2 * time.Second):
		t.Fatal("the subscription delivered no event")
	}

	// Cancelling must close the channel: a stream nobody closes is a leak in the model.
	result.Stream.Cancel()
	deadline := time.After(2 * time.Second)
	for {
		select {
		case _, ok := <-result.Stream.Events:
			if !ok {
				return
			}
		case <-deadline:
			t.Fatal("cancelling the stream did not close it")
		}
	}
}

func TestDirectCommandsRejectTheWrongArgumentCount(t *testing.T) {
	env, registry := testEnv(t)
	for _, line := range []string{
		"read-direct",
		"read-direct " + plcsession.DemoDeviceOne,
		"read-direct " + plcsession.DemoDeviceOne + " a b",
		"write-direct " + plcsession.DemoDeviceOne + " motor/speed",
		"subscribe-direct " + plcsession.DemoDeviceOne,
	} {
		t.Run(line, func(t *testing.T) {
			_, err := registry.Execute(t.Context(), env, line)
			assert.Error(t, err)
		})
	}
}

// TestNotConnectedErrorsNameTheOpenConnections keeps the failure actionable, rather than the
// bare "%s not connected" the old tool produced.
func TestNotConnectedErrorsNameTheOpenConnections(t *testing.T) {
	env, registry := testEnv(t)
	_, err := registry.Execute(t.Context(), env, "read-direct demo://nowhere temp/1")
	require.Error(t, err)
	assert.Contains(t, err.Error(), plcsession.DemoDeviceOne, "the error should say what IS connected")
}

func TestConnectAndDisconnectUpdateTheSidebar(t *testing.T) {
	env, registry := testEnv(t)

	result, err := registry.Execute(t.Context(), env, "connect "+plcsession.DemoDeviceTwo)
	require.NoError(t, err)
	assert.True(t, result.ConnectionsChanged, "the sidebar must be told to re-read")

	result, err = registry.Execute(t.Context(), env, "disconnect "+plcsession.DemoDeviceTwo)
	require.NoError(t, err)
	assert.True(t, result.ConnectionsChanged)
}

func TestConnectRemembersTheHostForNextTime(t *testing.T) {
	env, registry := testEnv(t)
	_, err := registry.Execute(t.Context(), env, "connect "+plcsession.DemoDeviceTwo)
	require.NoError(t, err)
	assert.Contains(t, env.Config.History.Last10Hosts, "plant-2",
		"the connect history is what makes the prompt useful on a second run")
}

func TestRegisterReportsTheDriver(t *testing.T) {
	env, registry := testEnv(t)
	// The demo driver is already registered by the fixture, so registering again must complain.
	_, err := registry.Execute(t.Context(), env, "register "+plcsession.DemoProtocol)
	assert.Error(t, err, "registering twice should be reported rather than silently ignored")
}

func TestLogLevelSetsTheLevelAndPersistsIt(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "log level debug")
	require.NoError(t, err)
	require.NotNil(t, result.LogLevel)
	assert.Equal(t, "debug", result.LogLevel.String())
	assert.Equal(t, "debug", env.Config.LogLevel, "the level must survive into the saved config")
}

func TestLogLevelRejectsAnUnknownLevelAndSaysWhatIsValid(t *testing.T) {
	env, registry := testEnv(t)
	_, err := registry.Execute(t.Context(), env, "log level shouty")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "debug")
}

func TestClearTargetsTheRightPanes(t *testing.T) {
	env, registry := testEnv(t)
	for line, want := range map[string]ClearTarget{
		"clear":          ClearAll,
		"clear all":      ClearAll,
		"clear messages": ClearMessages,
		"clear console":  ClearConsole,
		"log clear":      ClearConsole,
	} {
		t.Run(line, func(t *testing.T) {
			result, err := registry.Execute(t.Context(), env, line)
			require.NoError(t, err)
			assert.Equal(t, want, result.Clear)
		})
	}
}

func TestQuitAsksTheApplicationToExit(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "quit")
	require.NoError(t, err)
	assert.True(t, result.Quit)
}

func TestHelpListsEveryCommand(t *testing.T) {
	env, registry := testEnv(t)
	result, err := registry.Execute(t.Context(), env, "help")
	require.NoError(t, err)
	joined := strings.Join(result.Lines, "\n")
	for _, name := range []string{"connect", "read-direct", "subscribe", "quit"} {
		assert.Contains(t, joined, name)
	}
}

func TestUnknownCommandIsReportedRatherThanIgnored(t *testing.T) {
	env, registry := testEnv(t)
	_, err := registry.Execute(t.Context(), env, "frobnicate the widget")
	assert.Error(t, err)
}

func TestEmptyLineIsNotAnError(t *testing.T) {
	env, registry := testEnv(t)
	for _, line := range []string{"", "   ", "\t"} {
		result, err := registry.Execute(t.Context(), env, line)
		assert.NoError(t, err, "an empty prompt submission is a no-op, not a failure")
		assert.Empty(t, result.Lines)
	}
}

// --- completion ---

func TestCompletionOffersTopLevelCommands(t *testing.T) {
	env, registry := testEnv(t)
	suggestions := registry.Complete(env, "co")
	assert.Contains(t, suggestions, "connect")
}

func TestCompletionOffersSubcommands(t *testing.T) {
	env, registry := testEnv(t)
	suggestions := registry.Complete(env, "log ")
	assert.Contains(t, suggestions, "log level")
	assert.Contains(t, suggestions, "log clear")
}

func TestCompletionOffersOpenConnectionsAsArguments(t *testing.T) {
	env, registry := testEnv(t)
	suggestions := registry.Complete(env, "read-direct ")
	joined := strings.Join(suggestions, " ")
	assert.Contains(t, joined, plcsession.DemoDeviceOne,
		"the open connection is the only sensible first argument")
}

func TestCompletionOffersDemoTagsOnceAConnectionIsTyped(t *testing.T) {
	env, registry := testEnv(t)
	suggestions := registry.Complete(env, "read-direct "+plcsession.DemoDeviceOne+" te")
	joined := strings.Join(suggestions, " ")
	assert.Contains(t, joined, "temp/1", "demo mode knows its catalogue, so it should offer it")
}

func TestCompletionOffersTheDemoDevicesToConnectTo(t *testing.T) {
	env, registry := testEnv(t)
	suggestions := registry.Complete(env, "connect ")
	joined := strings.Join(suggestions, " ")
	assert.Contains(t, joined, plcsession.DemoDeviceOne,
		"in demo mode the reachable devices should lead, not a protocol prefix that connects to nothing")
}

func TestCompletionReturnsWholeLinesSoThePromptCanAcceptThemDirectly(t *testing.T) {
	env, registry := testEnv(t)
	for _, suggestion := range registry.Complete(env, "read-direct ") {
		assert.True(t, strings.HasPrefix(suggestion, "read-direct "),
			"a suggestion is substituted for the whole line, so it must include the command: %q", suggestion)
	}
}

func TestCompletionOfAnUnknownCommandOffersNothing(t *testing.T) {
	env, registry := testEnv(t)
	assert.Empty(t, registry.Complete(env, "frobnicate "))
}
