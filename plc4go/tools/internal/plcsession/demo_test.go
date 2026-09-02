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

package plcsession_test

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// fixedClock returns a stepping clock, so timestamps are predictable without being identical.
func fixedClock() func() time.Time {
	base := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	step := 0
	return func() time.Time {
		step++
		return base.Add(time.Duration(step) * time.Second)
	}
}

// connectedDemo returns a demo session with one device connected.
func connectedDemo(t *testing.T) *plcsession.Demo {
	t.Helper()
	demo := plcsession.NewDemo(plcsession.DemoOptions{
		SubscriptionInterval: time.Millisecond,
		Now:                  fixedClock(),
	})
	t.Cleanup(func() { _ = demo.Close() })

	_, err := demo.RegisterDriver(plcsession.DemoProtocol)
	require.NoError(t, err)
	_, err = demo.Connect(t.Context(), plcsession.DemoDeviceOne)
	require.NoError(t, err)
	return demo
}

func TestDemoRegistersOnlyItsOwnProtocol(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	info, err := demo.RegisterDriver(plcsession.DemoProtocol)
	require.NoError(t, err)
	assert.Equal(t, plcsession.DemoProtocol, info.Code)
	assert.True(t, info.SupportsDiscovery)

	_, err = demo.RegisterDriver("c-bus")
	assert.Error(t, err, "demo mode must not pretend to offer a real driver")

	_, err = demo.RegisterDriver(plcsession.DemoProtocol)
	assert.Error(t, err, "registering twice should be reported, not ignored")
}

// TestDemoRefusesRealConnectionStrings matters because a demo that appeared to connect to
// hardware would be actively misleading.
func TestDemoRefusesRealConnectionStrings(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	_, err := demo.Connect(t.Context(), "c-bus://10.0.0.5:10001")
	require.Error(t, err)
	assert.Contains(t, err.Error(), plcsession.DemoDeviceOne, "the error should point at what does work")
}

func TestDemoConnectAndDisconnect(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	info, err := demo.Connect(t.Context(), plcsession.DemoDeviceOne)
	require.NoError(t, err)
	assert.Equal(t, plcsession.DemoDeviceOne, info.ID)
	assert.Len(t, demo.Connections(), 1)

	_, err = demo.Connect(t.Context(), plcsession.DemoDeviceOne)
	assert.Error(t, err, "connecting twice should be reported")

	require.NoError(t, demo.Disconnect(plcsession.DemoDeviceOne))
	assert.Empty(t, demo.Connections())
	assert.Error(t, demo.Disconnect(plcsession.DemoDeviceOne), "disconnecting twice should be reported")
}

func TestDemoConnectionsAreSortedForAStableList(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	_, err := demo.Connect(t.Context(), plcsession.DemoDeviceTwo)
	require.NoError(t, err)
	_, err = demo.Connect(t.Context(), plcsession.DemoDeviceOne)
	require.NoError(t, err)

	connections := demo.Connections()
	require.Len(t, connections, 2)
	assert.Equal(t, plcsession.DemoDeviceOne, connections[0].ID, "a UI list must not reorder between renders")
	assert.Equal(t, plcsession.DemoDeviceTwo, connections[1].ID)
}

func TestDemoOperationsRequireAConnection(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	tags := []plcsession.TagSpec{{Name: "a", Address: "temp/1"}}

	_, readErr := demo.Read(t.Context(), plcsession.DemoDeviceOne, tags)
	assert.Error(t, readErr)
	_, writeErr := demo.Write(t.Context(), plcsession.DemoDeviceOne, tags)
	assert.Error(t, writeErr)
	_, browseErr := demo.Browse(t.Context(), plcsession.DemoDeviceOne, "")
	assert.Error(t, browseErr)
	_, subscribeErr := demo.Subscribe(t.Context(), plcsession.DemoDeviceOne, tags)
	assert.Error(t, subscribeErr)
}

// TestDemoReadIsDeterministic is the property that lets the same code serve demo mode and the
// tests: two identically-driven sessions produce identical values.
func TestDemoReadIsDeterministic(t *testing.T) {
	tags := []plcsession.TagSpec{
		{Name: "t1", Address: "temp/1"},
		{Name: "p1", Address: "press/1"},
		{Name: "c1", Address: "counter/1"},
	}

	valuesOf := func() []string {
		demo := connectedDemo(t)
		var out []string
		for range 4 {
			result, err := demo.Read(t.Context(), plcsession.DemoDeviceOne, tags)
			require.NoError(t, err)
			for _, tag := range result.Tags {
				out = append(out, tag.Value)
			}
		}
		return out
	}

	assert.Equal(t, valuesOf(), valuesOf(), "the demo value sequence must be reproducible")
}

// TestDemoReadValuesAdvance is the other half: the display must not look frozen.
func TestDemoReadValuesAdvance(t *testing.T) {
	demo := connectedDemo(t)
	tags := []plcsession.TagSpec{{Name: "c", Address: "counter/1"}}

	first, err := demo.Read(t.Context(), plcsession.DemoDeviceOne, tags)
	require.NoError(t, err)
	second, err := demo.Read(t.Context(), plcsession.DemoDeviceOne, tags)
	require.NoError(t, err)

	assert.NotEqual(t, first.Tags[0].Value, second.Tags[0].Value,
		"a counter tag must visibly advance between reads")
}

func TestDemoReadReportsTagOutcomes(t *testing.T) {
	demo := connectedDemo(t)
	result, err := demo.Read(t.Context(), plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "good", Address: "temp/1"},
		{Name: "bogus", Address: "no/such/tag"},
	})
	require.NoError(t, err, "an unknown tag is a per-tag outcome, not a failed request")
	require.Len(t, result.Tags, 2)

	assert.True(t, result.Tags[0].Succeeded())
	assert.Equal(t, "REAL", result.Tags[0].DataType)
	assert.NotEmpty(t, result.Tags[0].Value)

	assert.False(t, result.Tags[1].Succeeded())
	assert.Equal(t, "NOT_FOUND", result.Tags[1].Code)
}

func TestDemoReadRejectsAnEmptyRequest(t *testing.T) {
	demo := connectedDemo(t)
	_, err := demo.Read(t.Context(), plcsession.DemoDeviceOne, nil)
	assert.Error(t, err, "an empty read is a mistake worth reporting rather than an empty result")
}

// TestDemoWriteIsObservableByAFollowingRead is what makes the write form worth using in a
// demo: the value sticks.
func TestDemoWriteIsObservableByAFollowingRead(t *testing.T) {
	demo := connectedDemo(t)
	write, err := demo.Write(t.Context(), plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "speed", Address: "motor/speed", Value: "1234"},
	})
	require.NoError(t, err)
	require.Len(t, write.Tags, 1)
	require.True(t, write.Tags[0].Succeeded(), "motor/speed is writable")

	read, err := demo.Read(t.Context(), plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "speed", Address: "motor/speed"},
	})
	require.NoError(t, err)
	assert.Equal(t, "1234", read.Tags[0].Value, "a written value must be visible to a later read")
}

func TestDemoWriteRefusesReadOnlyTags(t *testing.T) {
	demo := connectedDemo(t)
	result, err := demo.Write(t.Context(), plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "t", Address: "temp/1", Value: "99"},
	})
	require.NoError(t, err)
	assert.Equal(t, "ACCESS_DENIED", result.Tags[0].Code, "temp/1 is not writable")
}

func TestDemoBrowseListsTheCatalogue(t *testing.T) {
	demo := connectedDemo(t)
	result, err := demo.Browse(t.Context(), plcsession.DemoDeviceOne, "")
	require.NoError(t, err)
	assert.NotEmpty(t, result.Items)

	addresses := make([]string, 0, len(result.Items))
	for _, item := range result.Items {
		addresses = append(addresses, item.Address)
	}
	assert.ElementsMatch(t, plcsession.DemoTagAddresses(), addresses,
		"browse must agree with the completion suggestions")
}

func TestDemoBrowseFiltersByQuery(t *testing.T) {
	demo := connectedDemo(t)
	for query, wantSome := range map[string]bool{
		"temp":    true,
		"motor":   true,
		"Boiler":  true,
		"*":       true,
		"":        true,
		"nothing": false,
	} {
		t.Run(query, func(t *testing.T) {
			result, err := demo.Browse(t.Context(), plcsession.DemoDeviceOne, query)
			require.NoError(t, err)
			if wantSome {
				assert.NotEmpty(t, result.Items, "query %q should match something", query)
			} else {
				assert.Empty(t, result.Items, "query %q should match nothing", query)
			}
		})
	}
}

func TestDemoBrowseStarMeansEverything(t *testing.T) {
	demo := connectedDemo(t)
	all, err := demo.Browse(t.Context(), plcsession.DemoDeviceOne, "")
	require.NoError(t, err)
	star, err := demo.Browse(t.Context(), plcsession.DemoDeviceOne, "*")
	require.NoError(t, err)
	assert.Equal(t, all.Items, star.Items)
}

func TestDemoDiscoverFindsBothDevices(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	var found []plcsession.DiscoveryItem
	require.NoError(t, demo.Discover(t.Context(), plcsession.DemoProtocol, func(item plcsession.DiscoveryItem) {
		found = append(found, item)
	}))
	require.Len(t, found, 2)
	assert.Equal(t, plcsession.DemoDeviceOne, found[0].ConnectionString)
	assert.Equal(t, plcsession.DemoDeviceTwo, found[1].ConnectionString)
}

func TestDemoDiscoverStopsOnACancelledContext(t *testing.T) {
	demo := plcsession.NewDemo(plcsession.DemoOptions{})
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	err := demo.Discover(ctx, plcsession.DemoProtocol, func(plcsession.DiscoveryItem) {})
	assert.Error(t, err)
}

// TestDemoSubscribeEmitsEventsAndStopsOnCancel covers the behaviour the message pane depends
// on, including that cancelling closes the channel rather than leaking the producer.
func TestDemoSubscribeEmitsEventsAndStopsOnCancel(t *testing.T) {
	demo := connectedDemo(t)
	ctx, cancel := context.WithCancel(t.Context())

	events, err := demo.Subscribe(ctx, plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "t", Address: "temp/1"},
	})
	require.NoError(t, err)

	for i := range 3 {
		select {
		case event, ok := <-events:
			require.True(t, ok, "event %d: channel closed early", i)
			assert.Equal(t, plcsession.EventSubscribe, event.Kind)
			assert.Equal(t, plcsession.DemoDeviceOne, event.Connection)
			require.Len(t, event.Tags, 1)
			assert.True(t, event.Tags[0].Succeeded())
			assert.False(t, event.Received.IsZero(), "an event needs a timestamp for the message list")
		case <-time.After(2 * time.Second):
			t.Fatalf("event %d never arrived", i)
		}
	}

	cancel()
	// Drain until closed: the channel must close, or the UI would hold a live goroutine.
	deadline := time.After(2 * time.Second)
	for {
		select {
		case _, ok := <-events:
			if !ok {
				return
			}
		case <-deadline:
			t.Fatal("cancelling the context did not close the event channel")
		}
	}
}

func TestDemoSubscribeRejectsUnsubscribableAndUnknownTags(t *testing.T) {
	demo := connectedDemo(t)
	_, err := demo.Subscribe(t.Context(), plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "l", Address: "label/1"},
	})
	assert.Error(t, err, "label/1 is not subscribable")

	_, err = demo.Subscribe(t.Context(), plcsession.DemoDeviceOne, []plcsession.TagSpec{
		{Name: "x", Address: "no/such/tag"},
	})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "browsing", "the error should suggest how to find a real tag")
}

func TestDemoSubscribeRejectsAnEmptyTagList(t *testing.T) {
	demo := connectedDemo(t)
	_, err := demo.Subscribe(t.Context(), plcsession.DemoDeviceOne, nil)
	assert.Error(t, err)
}

func TestDemoCloseIsIdempotentAndReleasesConnections(t *testing.T) {
	demo := connectedDemo(t)
	require.NotEmpty(t, demo.Connections())

	require.NoError(t, demo.Close())
	assert.Empty(t, demo.Connections())
	require.NoError(t, demo.Close(), "Close must be safe to call twice")

	_, err := demo.Connect(t.Context(), plcsession.DemoDeviceTwo)
	assert.Error(t, err, "a closed session must not accept new connections")
}

// TestDemoValuesCoverEveryDataType guards the value renderer against a data type being added
// to the catalogue without a rendering rule.
func TestDemoValuesCoverEveryDataType(t *testing.T) {
	demo := connectedDemo(t)
	for _, address := range plcsession.DemoTagAddresses() {
		t.Run(address, func(t *testing.T) {
			result, err := demo.Read(t.Context(), plcsession.DemoDeviceOne,
				[]plcsession.TagSpec{{Name: "x", Address: address}})
			require.NoError(t, err)
			require.Len(t, result.Tags, 1)
			assert.True(t, result.Tags[0].Succeeded(), "catalogue tag %s should read", address)
			assert.NotEmpty(t, result.Tags[0].Value, "catalogue tag %s produced no value", address)
			assert.NotEmpty(t, result.Tags[0].DataType)
		})
	}
}

func TestDemoProtocolsOffersOnlyDemo(t *testing.T) {
	assert.Equal(t, []string{plcsession.DemoProtocol}, plcsession.NewDemo(plcsession.DemoOptions{}).Protocols())
}
