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
	"slices"
	"testing"
	"time"

	plc4go "github.com/apache/plc4x/plc4go/pkg/api"
	apiModel "github.com/apache/plc4x/plc4go/pkg/api/model"
	"github.com/apache/plc4x/plc4go/spi"
	spiTransports "github.com/apache/plc4x/plc4go/spi/transports"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// Compile-time proof that Live satisfies the seam the user interface talks to.
var _ plcsession.Session = (*plcsession.Live)(nil)

// Everything below runs without a PLC and without touching the network. Registering a driver
// only builds objects, and every Connect exercised here is rejected before the driver dials,
// which is exactly the behaviour worth pinning: the operations that DO dial were measured
// blocking for a full minute, so a test must never reach one.

// liveSession returns a live session over a driver manager the test can inspect.
func liveSession(t *testing.T, timeout time.Duration) (*plcsession.Live, plc4go.PlcDriverManager) {
	t.Helper()
	manager := plc4go.NewPlcDriverManager()
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: timeout})
	t.Cleanup(func() {
		assert.NoError(t, live.Close())
		assert.NoError(t, manager.Close())
	})
	return live, manager
}

func TestLiveProtocolsAreSortedAndNotEmpty(t *testing.T) {
	protocols := plcsession.NewLive(plcsession.LiveOptions{}).Protocols()
	require.NotEmpty(t, protocols)
	assert.True(t, slices.IsSorted(protocols), "the protocol list is offered as completions, so it must be stable")
}

// TestLiveProtocolsCannotBeMutatedByTheCaller matters because the UI hands this slice to a
// completion widget, which is free to sort or truncate it in place.
func TestLiveProtocolsCannotBeMutatedByTheCaller(t *testing.T) {
	live := plcsession.NewLive(plcsession.LiveOptions{})
	first := live.Protocols()
	require.NotEmpty(t, first)
	first[0] = "clobbered"
	assert.NotEqual(t, "clobbered", live.Protocols()[0], "Protocols must not expose shared state")
}

// TestLiveEveryAdvertisedProtocolRegistersUnderItsOwnCode is the regression test for the defect
// this implementation replaces: the previous UI advertised "bacnetip", but plc4x registers that
// driver as "bacnet-ip", so the advertised name could never appear in a working connection
// string. Any future drift between the advertised list and the drivers' real codes fails here.
func TestLiveEveryAdvertisedProtocolRegistersUnderItsOwnCode(t *testing.T) {
	for _, protocol := range plcsession.NewLive(plcsession.LiveOptions{}).Protocols() {
		t.Run(protocol, func(t *testing.T) {
			live, _ := liveSession(t, time.Second)
			info, err := live.RegisterDriver(protocol)
			require.NoError(t, err)
			assert.Equal(t, protocol, info.Code,
				"an advertised protocol must be the code its driver answers to")
			assert.NotEmpty(t, info.Name, "the drivers pane shows the name")
		})
	}
}

// TestLiveDriverInfoIsReadOffTheDriver pins that the reported metadata comes from plc4x rather
// than from a table maintained alongside it, which is the mistake that produced the
// bacnetip/bacnet-ip mismatch in the first place.
func TestLiveDriverInfoIsReadOffTheDriver(t *testing.T) {
	live, manager := liveSession(t, time.Second)
	for _, protocol := range live.Protocols() {
		t.Run(protocol, func(t *testing.T) {
			info, err := live.RegisterDriver(protocol)
			require.NoError(t, err)

			driver, err := manager.GetDriver(info.Code)
			require.NoError(t, err, "the driver must be reachable in the manager under the reported code")
			assert.Equal(t, driver.GetProtocolCode(), info.Code)
			assert.Equal(t, driver.GetProtocolName(), info.Name)
			assert.Equal(t, driver.SupportsDiscovery(), info.SupportsDiscovery)
		})
	}
}

func TestLiveRegisterDriverRejectsASecondRegistration(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	_, err := live.RegisterDriver("c-bus")
	require.NoError(t, err)

	_, err = live.RegisterDriver("c-bus")
	require.Error(t, err, "a repeated registration is a typo worth reporting, not a no-op")
	assert.Contains(t, err.Error(), "already registered")
}

// TestLiveRegisterDriverAcceptsTheBacnetipAlias keeps the spelling the previous UI, its saved
// configuration files and its auto-register list all used, without letting two spellings become
// two registrations.
func TestLiveRegisterDriverAcceptsTheBacnetipAlias(t *testing.T) {
	for _, alias := range []string{"bacnet", "bacnetip", "BACnetIP", " bacnetip "} {
		t.Run(alias, func(t *testing.T) {
			live, _ := liveSession(t, time.Second)
			info, err := live.RegisterDriver(alias)
			require.NoError(t, err)
			assert.Equal(t, "bacnet-ip", info.Code)

			_, err = live.RegisterDriver("bacnet-ip")
			assert.Error(t, err, "an alias and its canonical code must be one registration")
		})
	}
}

func TestLiveRegisterDriverRejectsAnUnknownProtocolActionably(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	_, err := live.RegisterDriver("modbus-tcp")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "modbus-tcp", "the message should quote what was asked for")
	for _, protocol := range live.Protocols() {
		assert.Contains(t, err.Error(), protocol, "the message should list what can be registered instead")
	}
}

func TestLiveDriversAreSortedForAStableList(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	for _, protocol := range []string{"s7", "ads", "c-bus"} {
		_, err := live.RegisterDriver(protocol)
		require.NoError(t, err)
	}

	codes := make([]string, 0, 3)
	for _, info := range live.Drivers() {
		codes = append(codes, info.Code)
	}
	assert.Equal(t, []string{"ads", "c-bus", "s7"}, codes, "a UI list must not reorder between renders")
}

// countingDriverManager counts transport registrations, so a test can see how many of them a
// driver registration causes. That number is what the browser's console pane pays for: plc4x
// logs "Transport already registered" at Warn level for every registration after the first, and
// the browser puts that log on screen, where a warning for a perfectly ordinary second driver
// reads as a fault.
type countingDriverManager struct {
	plc4go.PlcDriverManager
	registrations map[string]int
}

func newCountingDriverManager() *countingDriverManager {
	return &countingDriverManager{
		PlcDriverManager: plc4go.NewPlcDriverManager(),
		registrations:    map[string]int{},
	}
}

func (c *countingDriverManager) transportAware() spi.TransportAware {
	return c.PlcDriverManager.(spi.TransportAware)
}

// total is how many transport registrations have been attempted, of any transport.
func (c *countingDriverManager) total() int {
	sum := 0
	for _, count := range c.registrations {
		sum += count
	}
	return sum
}

func (c *countingDriverManager) RegisterTransport(transport spiTransports.Transport) {
	c.registrations[transport.GetTransportCode()]++
	c.transportAware().RegisterTransport(transport)
}

func (c *countingDriverManager) ListTransportNames() []string {
	return c.transportAware().ListTransportNames()
}

func (c *countingDriverManager) GetTransport(name, connectionString string, options map[string][]string) (spiTransports.Transport, error) {
	return c.transportAware().GetTransport(name, connectionString, options)
}

// TestLiveAddsNoTransportRegistrationOfItsOwn pins the transport behaviour precisely.
//
// plc4x's drivers.Register*Driver already registers the transport its driver needs, so a
// session that also registers it produces a second, redundant registration - and with it the
// warning line the previous UI's latch was there to avoid and never did avoid. The assertion is
// per-driver rather than a total, so it keeps holding if plc4x changes whether it registers
// transports itself: either way, registering one driver may cost at most one registration.
func TestLiveAddsNoTransportRegistrationOfItsOwn(t *testing.T) {
	manager := newCountingDriverManager()
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: time.Second})
	t.Cleanup(func() {
		assert.NoError(t, live.Close())
		assert.NoError(t, manager.Close())
	})

	// Three TCP drivers and one UDP driver, so both the first and the repeat cases are covered.
	for _, protocol := range []string{"ads", "c-bus", "s7", "bacnet-ip"} {
		before := manager.total()
		_, err := live.RegisterDriver(protocol)
		require.NoError(t, err, protocol)
		assert.LessOrEqual(t, manager.total()-before, 1,
			"registering %s caused more than one transport registration, and every extra one is a warning on screen", protocol)
	}

	assert.Equal(t, []string{"tcp", "udp"}, slices.Sorted(slices.Values(manager.ListTransportNames())),
		"the manager must end up holding exactly the two transports these four drivers dial over")
}

// bareDriverManager implements only PlcDriverManager, without spi.TransportAware.
type bareDriverManager struct{}

func (bareDriverManager) Close() error                    { return nil }
func (bareDriverManager) RegisterDriver(plc4go.PlcDriver) {}
func (bareDriverManager) ListDriverNames() []string       { return nil }
func (bareDriverManager) GetDriver(string) (plc4go.PlcDriver, error) {
	return nil, assert.AnError
}

func (bareDriverManager) GetConnection(context.Context, string) (plc4go.PlcConnection, error) {
	return nil, assert.AnError
}

func (bareDriverManager) Discover(context.Context, func(apiModel.PlcDiscoveryItem), ...plc4go.WithDiscoveryOption) error {
	return assert.AnError
}

// TestLiveRegisterDriverReportsAManagerThatCannotCarryTransports exists because plc4x's
// transport helpers type-assert the driver manager without checking, so the unguarded path is a
// panic inside a UI command goroutine rather than an error the UI can show.
func TestLiveRegisterDriverReportsAManagerThatCannotCarryTransports(t *testing.T) {
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: bareDriverManager{}, Timeout: time.Second})
	t.Cleanup(func() { assert.NoError(t, live.Close()) })

	_, err := live.RegisterDriver("c-bus")
	require.Error(t, err, "this must be an error, not a panic")
	assert.Contains(t, err.Error(), "transport")
}

// TestLiveConnectRejectsABadConnectionStringWithoutWaiting is the behaviour that keeps a typo
// from looking like a hang: the whole request is decided from the string, before any driver is
// given the chance to spend the timeout on it.
func TestLiveConnectRejectsABadConnectionStringWithoutWaiting(t *testing.T) {
	// Long enough that a timeout-driven failure would be unmistakable in the elapsed time.
	const timeout = 30 * time.Second
	for name, connectionString := range map[string]string{
		"empty":              "",
		"blank":              "   ",
		"no scheme":          "10.0.0.5:10001",
		"missing scheme":     "://10.0.0.5:10001",
		"bad percent escape": "c-bus://10.0.0.5:10001/%zz",
		"control character":  "c-bus://10.0.0.5:1\x7f",
	} {
		t.Run(name, func(t *testing.T) {
			live, _ := liveSession(t, timeout)
			_, err := live.RegisterDriver("c-bus")
			require.NoError(t, err)

			started := time.Now()
			_, err = live.Connect(t.Context(), connectionString)
			elapsed := time.Since(started)

			require.Error(t, err)
			assert.NotContains(t, err.Error(), "gave up", "this must fail on inspection, not on the timeout")
			assert.NotContains(t, err.Error(), "deadline exceeded")
			assert.Less(t, elapsed, time.Second,
				"rejecting %q took %s of a %s timeout, so it went to the driver", connectionString, elapsed, timeout)
		})
	}
}

// TestLiveConnectReportsAnUnregisteredProtocolRatherThanTimingOut also pins the connection ID:
// the message quotes the canonical "scheme://host", with the path and query stripped, which is
// the identity every later command addresses the connection by.
func TestLiveConnectReportsAnUnregisteredProtocolRatherThanTimingOut(t *testing.T) {
	live, _ := liveSession(t, 30*time.Second)

	started := time.Now()
	_, err := live.Connect(t.Context(), "c-bus://10.0.0.5:10001/ignored?also=ignored")
	elapsed := time.Since(started)

	require.Error(t, err)
	assert.Contains(t, err.Error(), "c-bus://10.0.0.5:10001", "the error should name the canonical connection id")
	assert.NotContains(t, err.Error(), "ignored", "the path and query are not part of the identity")
	assert.Contains(t, err.Error(), "no driver registered")
	assert.Less(t, elapsed, time.Second, "an unregistered driver is knowable without dialling")
}

// TestLiveConnectIdentifiesTransportExplicitConnectionsByTheirHost covers the second plc4x
// connection-string shape, "c-bus:tcp://host", which net/url leaves in Opaque with an empty
// Host. Deriving the ID from Host alone - what the previous UI did - collapsed every such
// connection onto the single ID "c-bus://".
func TestLiveConnectIdentifiesTransportExplicitConnectionsByTheirHost(t *testing.T) {
	live, _ := liveSession(t, 30*time.Second)
	for connectionString, wantID := range map[string]string{
		"c-bus:tcp://10.0.0.5:10001": "c-bus://10.0.0.5:10001",
		"c-bus:tcp://10.0.0.6:10001": "c-bus://10.0.0.6:10001",
	} {
		t.Run(connectionString, func(t *testing.T) {
			_, err := live.Connect(t.Context(), connectionString)
			require.Error(t, err)
			assert.Contains(t, err.Error(), wantID)
		})
	}
}

// recordingDriverManager captures the connection string Connect hands to plc4x and never
// dials, so the string can be asserted on without a socket. Driver registration still goes to
// a real manager, so the drivers under test are the real ones.
type recordingDriverManager struct {
	plc4go.PlcDriverManager
	requested []string
}

func newRecordingDriverManager() *recordingDriverManager {
	return &recordingDriverManager{PlcDriverManager: plc4go.NewPlcDriverManager()}
}

func (r *recordingDriverManager) transportAware() spi.TransportAware {
	return r.PlcDriverManager.(spi.TransportAware)
}

func (r *recordingDriverManager) RegisterTransport(transport spiTransports.Transport) {
	r.transportAware().RegisterTransport(transport)
}

func (r *recordingDriverManager) ListTransportNames() []string {
	return r.transportAware().ListTransportNames()
}

func (r *recordingDriverManager) GetTransport(name, connectionString string, options map[string][]string) (spiTransports.Transport, error) {
	return r.transportAware().GetTransport(name, connectionString, options)
}

func (r *recordingDriverManager) GetConnection(_ context.Context, connectionString string) (plc4go.PlcConnection, error) {
	r.requested = append(r.requested, connectionString)
	return nil, assert.AnError
}

// TestLiveConnectRewritesAnAliasedSchemeForPlc4x is the other half of the bacnetip defect, and
// the half that bites at the prompt: plc4x looks its driver up by the scheme in the connection
// string, so a session that resolves the alias only for its own bookkeeping still fails with
// "couldn't find driver bacnetip" on a driver it has just registered.
func TestLiveConnectRewritesAnAliasedSchemeForPlc4x(t *testing.T) {
	manager := newRecordingDriverManager()
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: time.Second})
	t.Cleanup(func() {
		assert.NoError(t, live.Close())
		assert.NoError(t, manager.Close())
	})
	_, err := live.RegisterDriver("bacnetip")
	require.NoError(t, err)

	_, err = live.Connect(t.Context(), "bacnetip://10.0.0.5:47808?foo=bar")
	require.Error(t, err, "the recording manager refuses every connection")
	assert.NotContains(t, err.Error(), "couldn't find driver",
		"the alias must be rewritten before plc4x looks the driver up")
	assert.Equal(t, []string{"bacnet-ip://10.0.0.5:47808?foo=bar"}, manager.requested,
		"plc4x must be given the canonical code, with the rest of the string untouched")
	assert.Contains(t, err.Error(), "bacnet-ip://10.0.0.5:47808",
		"and the connection is identified by the canonical code, so one device is one connection")
}

func TestLiveOperationsReportAnUnknownConnection(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	tags := []plcsession.TagSpec{{Name: "a", Address: "info/*/*"}}
	const unknown = "c-bus://10.0.0.5:10001"

	_, readErr := live.Read(t.Context(), unknown, tags)
	assert.ErrorContains(t, readErr, "not connected")
	_, writeErr := live.Write(t.Context(), unknown, tags)
	assert.ErrorContains(t, writeErr, "not connected")
	_, browseErr := live.Browse(t.Context(), unknown, "")
	assert.ErrorContains(t, browseErr, "not connected")
	_, subscribeErr := live.Subscribe(t.Context(), unknown, tags)
	assert.ErrorContains(t, subscribeErr, "not connected")
	assert.ErrorContains(t, live.Disconnect(unknown), "not connected")
}

func TestLiveDiscoverReportsAnUnregisteredProtocol(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	err := live.Discover(t.Context(), "bacnetip", func(plcsession.DiscoveryItem) {
		t.Fatal("nothing can be discovered through an unregistered driver")
	})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "bacnet-ip", "the message should name the canonical protocol")
	assert.Contains(t, err.Error(), "not registered")
}

// TestLiveDiscoverReportsADriverWithoutDiscovery uses c-bus deliberately: it is registerable
// offline and does not support discovery, so the check is reached without any device.
func TestLiveDiscoverReportsADriverWithoutDiscovery(t *testing.T) {
	live, manager := liveSession(t, time.Second)
	info, err := live.RegisterDriver("c-bus")
	require.NoError(t, err)

	driver, err := manager.GetDriver(info.Code)
	require.NoError(t, err)
	require.False(t, driver.SupportsDiscovery(), "this test is only meaningful for a driver without discovery")

	err = live.Discover(t.Context(), "c-bus", func(plcsession.DiscoveryItem) {
		t.Fatal("a driver without discovery must not be asked to discover")
	})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "doesn't support discovery")
}

func TestLiveCloseIsIdempotent(t *testing.T) {
	manager := plc4go.NewPlcDriverManager()
	t.Cleanup(func() { assert.NoError(t, manager.Close()) })
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: time.Second})
	_, err := live.RegisterDriver("c-bus")
	require.NoError(t, err)

	require.NoError(t, live.Close())
	require.NoError(t, live.Close(), "Close must be safe to call twice")
}

// TestLiveClosedSessionRefusesEverything keeps a session that has been shut down from handing
// the UI a half-working connection.
func TestLiveClosedSessionRefusesEverything(t *testing.T) {
	manager := plc4go.NewPlcDriverManager()
	t.Cleanup(func() { assert.NoError(t, manager.Close()) })
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: time.Second})
	_, err := live.RegisterDriver("c-bus")
	require.NoError(t, err)
	require.NoError(t, live.Close())

	assert.Empty(t, live.Connections())
	assert.Empty(t, live.Drivers())

	_, connectErr := live.Connect(t.Context(), "c-bus://10.0.0.5:10001")
	assert.ErrorContains(t, connectErr, "session is closed")
	_, registerErr := live.RegisterDriver("s7")
	assert.ErrorContains(t, registerErr, "session is closed")
	_, readErr := live.Read(t.Context(), "c-bus://10.0.0.5:10001", []plcsession.TagSpec{{Address: "x"}})
	assert.ErrorContains(t, readErr, "session is closed")
	discoverErr := live.Discover(t.Context(), "ads", func(plcsession.DiscoveryItem) {})
	assert.ErrorContains(t, discoverErr, "session is closed")
}

// TestLiveClosesTheDriverManagerItCreatedButNotAnInjectedOne pins the ownership rule: closing
// an injected manager would shut down drivers the caller is still using.
func TestLiveClosesTheDriverManagerItCreatedButNotAnInjectedOne(t *testing.T) {
	injected := plc4go.NewPlcDriverManager()
	t.Cleanup(func() { assert.NoError(t, injected.Close()) })
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: injected, Timeout: time.Second})
	_, err := live.RegisterDriver("c-bus")
	require.NoError(t, err)
	require.NoError(t, live.Close())

	assert.Equal(t, []string{"c-bus"}, injected.ListDriverNames(),
		"an injected manager keeps its drivers when the session closes")

	owned := plcsession.NewLive(plcsession.LiveOptions{Timeout: time.Second})
	_, err = owned.RegisterDriver("c-bus")
	require.NoError(t, err)
	assert.NoError(t, owned.Close(), "an owned manager is closed with the session")
}

// TestLiveReadNamesTheConnectionBeforeTheTags pins the order of the two complaints a malformed
// request can draw: the connection is the more useful of them, and it is the one Demo reports
// first as well, so the UI shows the same message whichever session is behind it.
func TestLiveReadNamesTheConnectionBeforeTheTags(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	_, err := live.Read(t.Context(), "c-bus://10.0.0.5:10001", nil)
	assert.ErrorContains(t, err, "not connected")
}

// TestLiveDefaultTimeoutIsBounded guards the one option that must never be zero: an unbounded
// operation is the sixty-second hang this seam exists to prevent.
func TestLiveDefaultTimeoutIsBounded(t *testing.T) {
	assert.Positive(t, plcsession.DefaultOperationTimeout)
	assert.LessOrEqual(t, plcsession.DefaultOperationTimeout, 30*time.Second,
		"a timeout a user will sit through is not a timeout")
}
