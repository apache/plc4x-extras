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
	"maps"
	"slices"
	"testing"
	"time"

	plc4go "github.com/apache/plc4x/plc4go/pkg/api"
	"github.com/apache/plc4x/plc4go/pkg/api/config"
	"github.com/apache/plc4x/plc4go/pkg/api/drivers"
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

// TestLiveRegisterDriverRejectsAnUnknownProtocolActionably uses bare "modbus" deliberately:
// plc4x has three Modbus drivers and no driver answering to "modbus", so it is the plausible
// thing to type and the message has to be the thing that says which three exist.
func TestLiveRegisterDriverRejectsAnUnknownProtocolActionably(t *testing.T) {
	live, _ := liveSession(t, time.Second)
	_, err := live.RegisterDriver("modbus")
	require.Error(t, err)
	assert.Contains(t, err.Error(), `"modbus"`, "the message should quote what was asked for")
	for _, protocol := range live.Protocols() {
		assert.Contains(t, err.Error(), protocol, "the message should list what can be registered instead")
	}
}

// TestLiveSupportsEveryPublicPlc4xDriver pins the protocol list whole: the exact contents, the
// exact order, and the count.
//
// The browser supported five of plc4x's sixteen public drivers, so eleven protocols - every
// Modbus flavour among them - could not be reached from it at all. Pinning the count as well as
// the contents means a plc4x release that adds a driver fails here, rather than quietly
// producing a twelfth unreachable protocol.
func TestLiveSupportsEveryPublicPlc4xDriver(t *testing.T) {
	protocols := plcsession.NewLive(plcsession.LiveOptions{}).Protocols()
	require.Len(t, protocols, 16, "every public plc4go driver must be offered")
	assert.Equal(t, []string{
		"ab-eth",
		"ads",
		"bacnet-ip",
		"c-bus",
		"eip",
		"firmata",
		"iec-60870-5-104",
		"knxnet-ip",
		"logix",
		"modbus-ascii",
		"modbus-rtu",
		"modbus-tcp",
		"opcua",
		"s7",
		"slmp",
		"umas",
	}, protocols, "these are the codes the drivers themselves answer to, in the order the UI lists them")
}

// TestLiveRegisteringADriverRegistersTheTransportItDialsOver checks the session's own idea of
// which transport a protocol needs against the driver's, for every protocol.
//
// The session has to state the transport itself, because ensureTransportLocked needs to know
// which one to have ready before there is a driver to ask. A disagreement between the two is
// invisible until someone connects, and then it surfaces as "couldn't find transport serial",
// which reads as a plc4x fault rather than as this table being wrong.
func TestLiveRegisteringADriverRegistersTheTransportItDialsOver(t *testing.T) {
	for _, protocol := range plcsession.NewLive(plcsession.LiveOptions{}).Protocols() {
		t.Run(protocol, func(t *testing.T) {
			live, manager := liveSession(t, time.Second)
			info, err := live.RegisterDriver(protocol)
			require.NoError(t, err)

			driver, err := manager.GetDriver(info.Code)
			require.NoError(t, err)
			dialsOver := driver.GetDefaultTransport()
			require.NotEmpty(t, dialsOver,
				"plc4x refuses a connection string whose driver has no default transport")
			assert.Contains(t, manager.(spi.TransportAware).ListTransportNames(), dialsOver,
				"%s dials over %s, so that transport has to be registered", protocol, dialsOver)
		})
	}
}

// TestLiveRegisteringADriverRegistersItsNamedTransport spells the three transports out by name
// rather than reading them off the drivers, so that the serial case is stated somewhere a
// reader can see it. serial is the transport the session did not have: without it firmata,
// modbus-rtu and modbus-ascii cannot be connected to at all, whatever the protocol list says.
func TestLiveRegisteringADriverRegistersItsNamedTransport(t *testing.T) {
	for protocol, transport := range map[string]string{
		"firmata":      "serial",
		"modbus-rtu":   "serial",
		"modbus-ascii": "serial",
		"modbus-tcp":   "tcp",
		"knxnet-ip":    "udp",
	} {
		t.Run(protocol, func(t *testing.T) {
			live, manager := liveSession(t, time.Second)
			_, err := live.RegisterDriver(protocol)
			require.NoError(t, err)
			assert.Contains(t, manager.(spi.TransportAware).ListTransportNames(), transport)
		})
	}
}

// TestLiveDiscoverySupportIsTheDriversOwnAnswer pins discovery support to what each driver says
// and to the values the drivers actually give, both answers included.
//
// Two assertions, because either alone can be passed by a mistake: comparing only against the
// driver would still pass if every driver were asked the wrong question, and pinning only the
// literals would still pass if the answer were restated in a table here instead of read from
// plc4x. The UI greys out `discover` for a driver that reports false, so a guess costs the user
// either a missing feature or an error at the prompt.
func TestLiveDiscoverySupportIsTheDriversOwnAnswer(t *testing.T) {
	discovery := map[string]bool{
		"ads":             true,
		"bacnet-ip":       true,
		"eip":             true,
		"knxnet-ip":       true,
		"logix":           true,
		"ab-eth":          false,
		"c-bus":           false,
		"firmata":         false,
		"iec-60870-5-104": false,
		"modbus-ascii":    false,
		"modbus-rtu":      false,
		"modbus-tcp":      false,
		"opcua":           false,
		"s7":              false,
		"slmp":            false,
		"umas":            false,
	}
	// Every protocol, so a driver added to the registry cannot slip in without its discovery
	// answer being stated - the tab strip greys `discover` out on this flag alone.
	assert.ElementsMatch(t, plcsession.NewLive(plcsession.LiveOptions{}).Protocols(),
		slices.Collect(maps.Keys(discovery)))

	for protocol, discovers := range discovery {
		t.Run(protocol, func(t *testing.T) {
			live, manager := liveSession(t, time.Second)
			info, err := live.RegisterDriver(protocol)
			require.NoError(t, err)

			driver, err := manager.GetDriver(info.Code)
			require.NoError(t, err)
			assert.Equal(t, driver.SupportsDiscovery(), info.SupportsDiscovery,
				"discovery support must be the driver's answer, not a guess")
			assert.Equal(t, discovers, info.SupportsDiscovery)
		})
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

// TestLiveAddsNoTransportRegistrationOfItsOwn pins the transport behaviour precisely: going
// through the session must cost exactly the registrations plc4x would have made anyway.
//
// plc4x's drivers.Register*Driver already registers the transports its driver needs, so a
// session that also registers one produces a redundant registration - and with it the warning
// line the previous UI's latch was there to avoid and never did avoid: plc4x logs "Transport
// already registered" at Warn level, and the browser puts that log on screen, where a warning
// for a perfectly ordinary second driver reads as a fault.
//
// The baseline is measured rather than stated, because the number is not one per driver and
// not knowable from here. RegisterFirmataDriver, RegisterModbusRtuDriver and
// RegisterModbusAsciiDriver each register TWO transports - serial and tcp - since those
// protocols are commonly tunnelled over a socket. Comparing against plc4x itself keeps this
// test honest whatever plc4x decides to register.
func TestLiveAddsNoTransportRegistrationOfItsOwn(t *testing.T) {
	// Three TCP drivers, one UDP and one serial one, so every transport is covered and so are
	// both the first-registration and the repeat cases.
	plc4xRegistrations := map[string]func(plc4go.PlcDriverManager, ...config.WithOption) plc4go.PlcDriver{
		"ads":       drivers.RegisterAdsDriver,
		"c-bus":     drivers.RegisterCBusDriver,
		"s7":        drivers.RegisterS7Driver,
		"bacnet-ip": drivers.RegisterBacnetDriver,
		"firmata":   drivers.RegisterFirmataDriver,
	}

	manager := newCountingDriverManager()
	live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: time.Second})
	t.Cleanup(func() {
		assert.NoError(t, live.Close())
		assert.NoError(t, manager.Close())
	})

	for protocol, registerDirectly := range plc4xRegistrations {
		// A manager of its own, so the baseline is what plc4x costs for this driver alone.
		baseline := newCountingDriverManager()
		registerDirectly(baseline)
		require.NoError(t, baseline.Close())

		before := manager.total()
		_, err := live.RegisterDriver(protocol)
		require.NoError(t, err, protocol)
		assert.Equal(t, baseline.total(), manager.total()-before,
			"registering %s through the session must cost the same registrations as plc4x's own call, and every extra one is a warning on screen",
			protocol)
	}

	assert.Equal(t, []string{"serial", "tcp", "udp"}, slices.Sorted(slices.Values(manager.ListTransportNames())),
		"the manager must end up holding exactly the three transports these five drivers dial over")
}

// forgetfulDriverManager accepts transport registrations, records the order they arrived in,
// and reports that it holds none.
//
// It stands in for a future plc4x whose drivers.Register*Driver no longer registers transports
// itself - the case ensureTransportLocked exists for, and the only case in which the session's
// own choice of transport is observable. Today plc4x registers a driver's transports before the
// session gets a chance to, so a wrong entry in the session's table would be masked.
type forgetfulDriverManager struct {
	plc4go.PlcDriverManager
	registered []string
}

func (f *forgetfulDriverManager) RegisterTransport(transport spiTransports.Transport) {
	f.registered = append(f.registered, transport.GetTransportCode())
}

// ListTransportNames reports nothing, whatever has been registered. That is the forgetting.
func (f *forgetfulDriverManager) ListTransportNames() []string { return nil }

func (f *forgetfulDriverManager) GetTransport(string, string, map[string][]string) (spiTransports.Transport, error) {
	return nil, assert.AnError
}

// TestLiveRegistersTheTransportADriverDialsOverWhenPlc4xHasNot pins the session's own table of
// which transport each protocol needs, for every protocol.
//
// The last registration is the session's: RegisterDriver hands the driver to plc4x first and
// calls ensureTransportLocked afterwards. So the last code recorded here is the session's
// answer to "what does this driver dial over", and it has to be the driver's own answer.
// Without a driver manager that forgets, this is untestable - a serial driver's serial
// transport is registered by plc4x itself, so the session claiming tcp instead would still
// leave "serial" in ListTransportNames and connect perfectly well until the day plc4x stops.
func TestLiveRegistersTheTransportADriverDialsOverWhenPlc4xHasNot(t *testing.T) {
	for _, protocol := range plcsession.NewLive(plcsession.LiveOptions{}).Protocols() {
		t.Run(protocol, func(t *testing.T) {
			manager := &forgetfulDriverManager{PlcDriverManager: plc4go.NewPlcDriverManager()}
			live := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Timeout: time.Second})
			t.Cleanup(func() {
				assert.NoError(t, live.Close())
				assert.NoError(t, manager.Close())
			})

			info, err := live.RegisterDriver(protocol)
			require.NoError(t, err)
			driver, err := manager.GetDriver(info.Code)
			require.NoError(t, err)

			require.NotEmpty(t, manager.registered, "the session must register the transport itself")
			assert.Equal(t, driver.GetDefaultTransport(), manager.registered[len(manager.registered)-1],
				"the session must register the transport %s actually dials over", protocol)
		})
	}
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

// TestLiveConnectIdentifiesSerialConnectionsByTheirDevice covers the third connection-string
// shape, the one the serial drivers brought: "modbus-rtu:///dev/ttyUSB0" has no host at all,
// and plc4x reads the port name out of url.Path.
//
// Keyed on the host alone every serial port collapses onto the single ID "modbus-rtu://", so
// two ports could not be open at once - the second is refused as already connected - and the
// connections pane would name the one that was without saying which device it is. Both the
// plain and the transport-explicit spelling are covered, because the path arrives in a
// different field in each.
func TestLiveConnectIdentifiesSerialConnectionsByTheirDevice(t *testing.T) {
	live, _ := liveSession(t, 30*time.Second)
	for connectionString, wantID := range map[string]string{
		"modbus-rtu:///dev/ttyUSB0":        "modbus-rtu:///dev/ttyUSB0",
		"modbus-rtu:///dev/ttyUSB1":        "modbus-rtu:///dev/ttyUSB1",
		"modbus-rtu:serial:///dev/ttyUSB2": "modbus-rtu:///dev/ttyUSB2",
	} {
		t.Run(connectionString, func(t *testing.T) {
			_, err := live.Connect(t.Context(), connectionString)
			require.Error(t, err, "no driver is registered, so this never reaches a port")
			assert.Contains(t, err.Error(), wantID, "a serial connection is identified by its device")
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
