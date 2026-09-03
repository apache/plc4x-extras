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

package plcsession

import (
	"context"
	"maps"
	"net/url"
	"slices"
	"strings"
	"sync"
	"time"

	plc4go "github.com/apache/plc4x/plc4go/pkg/api"
	"github.com/apache/plc4x/plc4go/pkg/api/drivers"
	apiModel "github.com/apache/plc4x/plc4go/pkg/api/model"
	"github.com/apache/plc4x/plc4go/pkg/api/transports"
	"github.com/apache/plc4x/plc4go/pkg/api/values"
	"github.com/apache/plc4x/plc4go/spi"
	"github.com/apache/plc4x/plc4go/spi/errors"
)

// DefaultOperationTimeout bounds every operation when LiveOptions leaves the timeout unset.
//
// A bound is not optional. A plc4x driver pointed at a host that never completes its protocol
// handshake was measured blocking for sixty seconds, which in a terminal user interface is
// indistinguishable from a hang: the user's only evidence is a spinner. Ten seconds is long
// enough for a slow PLC on a slow link and short enough that a mistyped connection string
// reports itself while the user still remembers typing it.
const DefaultOperationTimeout = 10 * time.Second

// liveTransport names the transport a driver dials over.
type liveTransport int

const (
	transportTCP liveTransport = iota
	transportUDP
)

// liveDriver ties a protocol code to the plc4x call that registers its driver and to the
// transport that driver needs.
type liveDriver struct {
	register  func(plc4go.PlcDriverManager) plc4go.PlcDriver
	transport liveTransport
}

// liveDrivers is the protocol registry: the single place where the set of supported protocols
// and the way to register each one are stated.
//
// The previous UI kept those two facts apart - a comma-separated `protocols` constant in one
// file and a switch statement in another - and they drifted, twice. One copy of the constant
// had lost "opcua" while the switch still handled it, and the constant said "bacnetip" while
// the driver answers to "bacnet-ip" (see liveProtocolAliases). Deriving Protocols() from this
// map makes that particular drift impossible rather than merely unlikely.
var liveDrivers = map[string]liveDriver{
	"ads": {
		register:  func(manager plc4go.PlcDriverManager) plc4go.PlcDriver { return drivers.RegisterAdsDriver(manager) },
		transport: transportTCP,
	},
	"bacnet-ip": {
		register:  func(manager plc4go.PlcDriverManager) plc4go.PlcDriver { return drivers.RegisterBacnetDriver(manager) },
		transport: transportUDP,
	},
	"c-bus": {
		register:  func(manager plc4go.PlcDriverManager) plc4go.PlcDriver { return drivers.RegisterCBusDriver(manager) },
		transport: transportTCP,
	},
	"opcua": {
		register:  func(manager plc4go.PlcDriverManager) plc4go.PlcDriver { return drivers.RegisterOpcuaDriver(manager) },
		transport: transportTCP,
	},
	"s7": {
		register:  func(manager plc4go.PlcDriverManager) plc4go.PlcDriver { return drivers.RegisterS7Driver(manager) },
		transport: transportTCP,
	},
}

// liveProtocolAliases accepts the names the previous UI used for a protocol whose driver
// answers to a different code.
//
// "bacnetip" is the one that mattered. The old browser listed it, keyed its own driver map by
// it, and offered it as a completion - but plc4x registers that driver as "bacnet-ip", so
// `connect bacnetip://...` could only ever fail with "couldn't find driver bacnetip" while
// `discover bacnet-ip` failed the other way round. The same bacnet/bacnetip mismatch is a
// known defect in the pcap analyzer. Canonicalising here means either spelling works and both
// end up at one registration.
var liveProtocolAliases = map[string]string{
	"bacnet":   "bacnet-ip",
	"bacnetip": "bacnet-ip",
}

// browseFlattenDepth caps how deep a browse result tree is flattened. It is a guard, not a
// policy: a driver that answered with a cyclic tree would otherwise spin the UI's command
// goroutine forever, and no real address space is eight levels deep.
const browseFlattenDepth = 8

// liveBrowseQueryName is the name the single browse query is submitted under. plc4x reports
// per-query response codes by name, so it needs one.
const liveBrowseQueryName = "browse"

// LiveOptions configures a live session.
type LiveOptions struct {
	// DriverManager is the plc4x driver manager to register drivers with. A nil manager means
	// the session creates and owns one, which is what the application does; a test or an
	// embedder injects its own.
	DriverManager plc4go.PlcDriverManager
	// Now supplies timestamps and measures durations. Injectable so a test can pin both. It is
	// called from driver-owned goroutines while a subscription is running, so an injected clock
	// has to be safe for concurrent use.
	Now func() time.Time
	// Timeout bounds every single operation. Zero means DefaultOperationTimeout.
	Timeout time.Duration
}

// liveConnection is one open connection together with the identity the UI addresses it by.
type liveConnection struct {
	info       ConnectionInfo
	connection plc4go.PlcConnection
}

// Live is a Session backed by real plc4x drivers talking to real devices.
//
// Every operation runs under a timeout (see DefaultOperationTimeout), including Connect: the
// whole point of the seam is that the user interface stays answerable, and a driver waiting on
// a handshake will not volunteer to give up.
//
// Unlike the Session contract's minimum, Live is safe for concurrent use. A subscription
// delivers events from a driver-owned goroutine while the UI is free to read or disconnect, so
// the maps have to be guarded whether the interface demands it or not.
type Live struct {
	options LiveOptions

	// driverManager is held apart from options because a nil option means "create one".
	driverManager plc4go.PlcDriverManager
	// ownsDriverManager records whether Close should shut the manager down. An injected
	// manager belongs to whoever injected it.
	ownsDriverManager bool

	mu sync.Mutex
	// registered holds what has been registered so far, keyed by canonical protocol code.
	registered map[string]DriverInfo
	// drivers holds the driver objects, which Discover needs.
	drivers map[string]plc4go.PlcDriver
	// connections holds the open connections, keyed by ID.
	connections map[string]liveConnection
	// tcpRegistered and udpRegistered latch the transport registrations, so that at most one
	// registration per transport is ever attempted from here. See ensureTransportLocked for
	// why that matters and why it is not the same latch the previous UI had.
	tcpRegistered bool
	udpRegistered bool
	// closed marks the session as shut down.
	closed bool
}

// NewLive creates a live session. The zero LiveOptions is valid and yields an owned driver
// manager, the wall clock and DefaultOperationTimeout.
func NewLive(options LiveOptions) *Live {
	if options.Now == nil {
		options.Now = time.Now
	}
	if options.Timeout <= 0 {
		options.Timeout = DefaultOperationTimeout
	}
	live := &Live{
		options:       options,
		driverManager: options.DriverManager,
		registered:    map[string]DriverInfo{},
		drivers:       map[string]plc4go.PlcDriver{},
		connections:   map[string]liveConnection{},
	}
	if live.driverManager == nil {
		live.driverManager = plc4go.NewPlcDriverManager()
		live.ownsDriverManager = true
	}
	return live
}

// Protocols lists the protocol codes that can be registered, in the spelling a connection
// string must use.
func (l *Live) Protocols() []string {
	return slices.Sorted(maps.Keys(liveDrivers))
}

// Drivers lists the registered drivers, ordered so the UI list is stable between renders.
func (l *Live) Drivers() []DriverInfo {
	l.mu.Lock()
	defer l.mu.Unlock()
	registered := slices.Collect(maps.Values(l.registered))
	slices.SortFunc(registered, func(a, b DriverInfo) int { return strings.Compare(a.Code, b.Code) })
	return registered
}

// RegisterDriver registers a protocol's plc4x driver and the transport it dials over.
//
// The DriverInfo is read back off the driver rather than restated here, so what the UI shows
// is what the driver will actually answer to.
func (l *Live) RegisterDriver(protocol string) (DriverInfo, error) {
	code := canonicalLiveProtocol(protocol)
	definition, known := liveDrivers[code]
	if !known {
		return DriverInfo{}, errors.Errorf("unknown protocol %q, one of %s can be registered",
			protocol, strings.Join(l.Protocols(), ", "))
	}

	l.mu.Lock()
	defer l.mu.Unlock()
	if l.closed {
		return DriverInfo{}, errors.New("session is closed")
	}
	if _, exists := l.registered[code]; exists {
		return DriverInfo{}, errors.Errorf("%s already registered", code)
	}
	// Checked before registering anything: plc4x's transport helpers type-assert the driver
	// manager to spi.TransportAware without checking, and drivers.Register*Driver calls one of
	// those helpers itself, so an injected manager that cannot carry transports would panic
	// deep inside plc4x rather than in a UI command goroutine that could report it.
	if _, aware := l.driverManager.(spi.TransportAware); !aware {
		return DriverInfo{}, errors.Errorf("can't register %s: the driver manager cannot register transports", code)
	}

	driver := definition.register(l.driverManager)
	l.ensureTransportLocked(definition.transport)
	info := DriverInfo{
		Code:              driver.GetProtocolCode(),
		Name:              driver.GetProtocolName(),
		SupportsDiscovery: driver.SupportsDiscovery(),
	}
	l.registered[code] = info
	l.drivers[code] = driver
	return info, nil
}

// ensureTransportLocked makes sure the transport a driver dials over is registered, and does
// nothing when it already is. Callers hold l.mu and have already established that the driver
// manager is spi.TransportAware.
//
// This is a safety net, not the normal path. As of plc4x v0.0.0-20260902093211,
// drivers.Register*Driver registers the driver's transport itself, which is why the previous
// UI's tcpRegistered/udpRegistered latch never did what its name suggested: it suppressed only
// the browser's own duplicate call, while plc4x logged "Transport already registered" once per
// additional driver anyway. Checking the manager instead of registering unconditionally means
// a normal registration is silent, and a future plc4x that stops registering transports for us
// is still handled here rather than failing at connect time with "couldn't find transport tcp".
func (l *Live) ensureTransportLocked(transport liveTransport) {
	code := "tcp"
	registered := &l.tcpRegistered
	register := transports.RegisterTcpTransport
	if transport == transportUDP {
		code = "udp"
		registered = &l.udpRegistered
		register = transports.RegisterUdpTransport
	}
	if *registered || slices.Contains(l.driverManager.(spi.TransportAware).ListTransportNames(), code) {
		*registered = true
		return
	}
	register(l.driverManager)
	*registered = true
}

// Connections lists the open connections, ordered so the UI list is stable between renders.
func (l *Live) Connections() []ConnectionInfo {
	l.mu.Lock()
	defer l.mu.Unlock()
	open := make([]ConnectionInfo, 0, len(l.connections))
	for _, held := range l.connections {
		open = append(open, held.info)
	}
	slices.SortFunc(open, func(a, b ConnectionInfo) int { return strings.Compare(a.ID, b.ID) })
	return open
}

// Connect opens a connection from a plc4x connection string.
//
// Everything that can be decided without touching the network is decided first - the string
// parses, it names a protocol, that protocol is registered, that connection is not already
// open - because each of those is a mistake the user can fix, and none of them is worth
// waiting a timeout to be told about.
func (l *Live) Connect(ctx context.Context, connectionString string) (ConnectionInfo, error) {
	connectionString = strings.TrimSpace(connectionString)
	connectionUrl, err := url.Parse(connectionString)
	if err != nil {
		return ConnectionInfo{}, errors.Wrapf(err, "can't parse connection url %s", connectionString)
	}
	if connectionUrl.Scheme == "" {
		return ConnectionInfo{}, errors.Errorf(
			"%q names no protocol, a connection string looks like c-bus://10.0.0.5:10001", connectionString)
	}
	code := canonicalLiveProtocol(connectionUrl.Scheme)
	// The alias is rewritten in the string handed to plc4x, not merely resolved for the
	// lookup here: plc4x's driver manager finds its driver by the connection string's own
	// scheme, so leaving "bacnetip://..." alone fails with "couldn't find driver bacnetip"
	// even with the driver registered - the very defect the aliases exist to remove. Spliced
	// rather than re-rendered from the parsed url, so a query string is passed on byte for
	// byte; url.Parse only lower-cases the scheme, so the lengths match.
	if code != connectionUrl.Scheme {
		connectionString = code + connectionString[len(connectionUrl.Scheme):]
	}
	id, transport := liveConnectionID(code, connectionUrl)

	l.mu.Lock()
	if l.closed {
		l.mu.Unlock()
		return ConnectionInfo{}, errors.New("session is closed")
	}
	if _, exists := l.connections[id]; exists {
		l.mu.Unlock()
		return ConnectionInfo{}, errors.Errorf("%s already connected", id)
	}
	driver, registered := l.drivers[code]
	l.mu.Unlock()

	if !registered {
		return ConnectionInfo{}, errors.Errorf("%s can't connect: no driver registered for %s, register it first", id, code)
	}
	if transport == "" {
		transport = driver.GetDefaultTransport()
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, l.options.Timeout)
	defer cancel()
	connection, err := l.driverManager.GetConnection(timeoutCtx, connectionString)
	if err != nil {
		return ConnectionInfo{}, errors.Wrapf(err, "%s can't connect", id)
	}

	info := ConnectionInfo{ID: id, Protocol: driver.GetProtocolCode(), Transport: transport}
	l.mu.Lock()
	// The session may have been closed, or the same connection opened, while the driver was
	// dialling. Dropping the winner's connection on the floor would leak a live socket, so the
	// loser closes what it just opened.
	_, raced := l.connections[id]
	if l.closed || raced {
		l.mu.Unlock()
		if closeErr := connection.Close(); closeErr != nil {
			return ConnectionInfo{}, errors.Wrapf(closeErr, "%s connected too late and could not be closed", id)
		}
		if raced {
			return ConnectionInfo{}, errors.Errorf("%s already connected", id)
		}
		return ConnectionInfo{}, errors.New("session is closed")
	}
	l.connections[id] = liveConnection{info: info, connection: connection}
	l.mu.Unlock()
	return info, nil
}

// Disconnect closes the connection with the given ID.
func (l *Live) Disconnect(id string) error {
	l.mu.Lock()
	held, exists := l.connections[id]
	if !exists {
		l.mu.Unlock()
		return errors.Errorf("%s not connected", id)
	}
	delete(l.connections, id)
	l.mu.Unlock()

	// Closed outside the lock: a driver's Close talks to the device and can take as long as
	// the device likes, and holding l.mu through it would stall every pane that reads the
	// connection list.
	if err := held.connection.Close(); err != nil {
		return errors.Wrapf(err, "%s can't close", id)
	}
	return nil
}

// Discover searches for devices speaking a protocol.
//
// sink is called from the driver's own goroutine, once per device, before Discover returns.
func (l *Live) Discover(ctx context.Context, protocol string, sink func(DiscoveryItem)) error {
	code := canonicalLiveProtocol(protocol)

	l.mu.Lock()
	closed := l.closed
	driver, registered := l.drivers[code]
	l.mu.Unlock()

	if closed {
		return errors.New("session is closed")
	}
	if !registered {
		return errors.Errorf("%s not registered, register it first", code)
	}
	if !driver.SupportsDiscovery() {
		return errors.Errorf("%s doesn't support discovery", code)
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, l.options.Timeout)
	defer cancel()
	return driver.Discover(timeoutCtx, func(item apiModel.PlcDiscoveryItem) {
		sink(DiscoveryItem{
			ConnectionString: item.GetConnectionUrl(),
			Protocol:         item.GetProtocolCode(),
			Transport:        item.GetTransportCode(),
			Name:             item.GetName(),
		})
	})
}

// Read reads tags from a connection.
func (l *Live) Read(ctx context.Context, connectionID string, tags []TagSpec) (ReadResult, error) {
	connection, err := l.connection(connectionID)
	if err != nil {
		return ReadResult{}, err
	}
	if len(tags) == 0 {
		return ReadResult{}, errors.New("a read request needs at least one tag")
	}

	builder := connection.ReadRequestBuilder()
	for _, tag := range tags {
		builder = builder.AddTagAddress(tag.Name, tag.Address)
	}
	request, err := builder.Build()
	if err != nil {
		return ReadResult{}, errors.Wrapf(err, "%s can't read", connectionID)
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, l.options.Timeout)
	defer cancel()
	started := l.options.Now()
	result, err := awaitResult(timeoutCtx, request.Execute(timeoutCtx), connectionID, "read", l.options.Timeout)
	if err != nil {
		return ReadResult{}, err
	}
	if err := result.GetErr(); err != nil {
		return ReadResult{}, errors.Wrapf(err, "%s error reading", connectionID)
	}
	response := result.GetResponse()
	if response == nil {
		return ReadResult{}, errors.Errorf("%s answered a read with neither a response nor an error", connectionID)
	}

	// Iterated in request order rather than over response.GetTagNames(): plc4x returns those
	// out of a map, and a results table that reshuffles itself between reads is unreadable.
	out := ReadResult{Tags: make([]TagResult, 0, len(tags))}
	for _, tag := range tags {
		value := response.GetValue(tag.Name)
		out.Tags = append(out.Tags, TagResult{
			Name:     tag.Name,
			Address:  tag.Address,
			DataType: plcValueType(value),
			Value:    plcValueString(value),
			Code:     response.GetResponseCode(tag.Name).GetName(),
		})
	}
	out.Duration = l.options.Now().Sub(started)
	return out, nil
}

// Write writes tags to a connection.
func (l *Live) Write(ctx context.Context, connectionID string, tags []TagSpec) (WriteResult, error) {
	connection, err := l.connection(connectionID)
	if err != nil {
		return WriteResult{}, err
	}
	if len(tags) == 0 {
		return WriteResult{}, errors.New("a write request needs at least one tag")
	}

	builder := connection.WriteRequestBuilder()
	for _, tag := range tags {
		// The value goes over as the string the user typed. Each driver's tag handler coerces
		// it to the tag's own type, which is the only place that knows what the type is.
		builder = builder.AddTagAddress(tag.Name, tag.Address, tag.Value)
	}
	request, err := builder.Build()
	if err != nil {
		return WriteResult{}, errors.Wrapf(err, "%s can't write", connectionID)
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, l.options.Timeout)
	defer cancel()
	started := l.options.Now()
	result, err := awaitResult(timeoutCtx, request.Execute(timeoutCtx), connectionID, "write", l.options.Timeout)
	if err != nil {
		return WriteResult{}, err
	}
	if err := result.GetErr(); err != nil {
		return WriteResult{}, errors.Wrapf(err, "%s error writing", connectionID)
	}
	response := result.GetResponse()
	if response == nil {
		return WriteResult{}, errors.Errorf("%s answered a write with neither a response nor an error", connectionID)
	}

	out := WriteResult{Tags: make([]TagResult, 0, len(tags))}
	for _, tag := range tags {
		out.Tags = append(out.Tags, TagResult{
			Name:    tag.Name,
			Address: tag.Address,
			// A write response carries no value back, so the value shown is the one asked for.
			Value: tag.Value,
			Code:  response.GetResponseCode(tag.Name).GetName(),
		})
	}
	out.Duration = l.options.Now().Sub(started)
	return out, nil
}

// Browse enumerates the tags a connection exposes.
func (l *Live) Browse(ctx context.Context, connectionID, query string) (BrowseResult, error) {
	connection, err := l.connection(connectionID)
	if err != nil {
		return BrowseResult{}, err
	}

	query = strings.TrimSpace(query)
	if query == "" {
		// plc4x has no universal "everything" query - each driver defines its own address
		// syntax - so an empty query goes over as the conventional wildcard and the driver
		// decides. A driver that rejects it says so in its own words, which is more use than
		// a refusal invented here.
		query = "*"
	}

	request, err := connection.BrowseRequestBuilder().AddQuery(liveBrowseQueryName, query).Build()
	if err != nil {
		return BrowseResult{}, errors.Wrapf(err, "%s can't browse %s", connectionID, query)
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, l.options.Timeout)
	defer cancel()
	started := l.options.Now()
	result, err := awaitResult(timeoutCtx, request.Execute(timeoutCtx), connectionID, "browse", l.options.Timeout)
	if err != nil {
		return BrowseResult{}, err
	}
	if err := result.GetErr(); err != nil {
		return BrowseResult{}, errors.Wrapf(err, "%s error browsing", connectionID)
	}
	response := result.GetResponse()
	if response == nil {
		return BrowseResult{}, errors.Errorf("%s answered a browse with neither a response nor an error", connectionID)
	}
	if code := response.GetResponseCode(liveBrowseQueryName); code != apiModel.PlcResponseCode_OK {
		return BrowseResult{}, errors.Errorf("%s refused the browse query %s: %s", connectionID, query, code.GetName())
	}

	out := BrowseResult{}
	for _, item := range response.GetQueryResults(liveBrowseQueryName) {
		out.Items = appendBrowseItems(out.Items, item, browseFlattenDepth)
	}
	out.Duration = l.options.Now().Sub(started)
	return out, nil
}

// Subscribe subscribes to tags, streaming events until the context is cancelled.
func (l *Live) Subscribe(ctx context.Context, connectionID string, tags []TagSpec) (<-chan Event, error) {
	connection, err := l.connection(connectionID)
	if err != nil {
		return nil, err
	}
	if len(tags) == 0 {
		return nil, errors.New("a subscription needs at least one tag")
	}

	// received is written by the driver's consumer goroutines and read by the forwarder below.
	// It is deliberately never closed: only the forwarder closes a channel, and only the one
	// it owns. Sharing one channel between a driver-owned producer and a cancellation-owned
	// closer is how this repository last acquired a send-on-closed-channel race, in the packet
	// mapper.
	received := make(chan Event)
	consumer := func(tag TagSpec) apiModel.PlcSubscriptionEventConsumer {
		return func(event apiModel.PlcSubscriptionEvent) {
			mapped := l.subscriptionEvent(connectionID, tag, event)
			select {
			case received <- mapped:
			case <-ctx.Done():
				// Dropped rather than blocked. A driver keeps calling its consumers after the
				// UI has stopped listening, and a consumer parked on a send holds a driver
				// goroutine for the rest of the process's life.
			}
		}
	}

	builder := connection.SubscriptionRequestBuilder()
	for _, tag := range tags {
		// Event subscriptions, as the previous UI used: they are what the monitor-style
		// addresses the browser suggests (c-bus salmonitor/mmimonitor) actually are.
		builder = builder.AddEventTagAddress(tag.Name, tag.Address)
		builder = builder.AddPreRegisteredConsumer(tag.Name, consumer(tag))
	}
	request, err := builder.Build()
	if err != nil {
		return nil, errors.Wrapf(err, "%s can't subscribe", connectionID)
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, l.options.Timeout)
	defer cancel()
	result, err := awaitResult(timeoutCtx, request.Execute(timeoutCtx), connectionID, "subscribe", l.options.Timeout)
	if err != nil {
		return nil, err
	}
	if err := result.GetErr(); err != nil {
		return nil, errors.Wrapf(err, "%s can't subscribe", connectionID)
	}
	response := result.GetResponse()
	if response == nil {
		return nil, errors.Errorf("%s answered a subscription with neither a response nor an error", connectionID)
	}
	for _, tag := range tags {
		if code := response.GetResponseCode(tag.Name); code != apiModel.PlcResponseCode_OK {
			return nil, errors.Errorf("%s refused the subscription to %s: %s", connectionID, tag.Address, code.GetName())
		}
	}

	events := make(chan Event)
	handles := response.GetSubscriptionHandles()
	go l.forwardSubscription(ctx, connection, handles, received, events)
	return events, nil
}

// forwardSubscription owns the event channel: it is the only writer and the only closer, which
// is what keeps a driver's consumer from sending on a closed channel.
//
// On cancellation it also unsubscribes. Without that the driver goes on producing events that
// nothing will ever read, which is a slow leak rather than a visible fault.
func (l *Live) forwardSubscription(
	ctx context.Context,
	connection plc4go.PlcConnection,
	handles []apiModel.PlcSubscriptionHandle,
	received <-chan Event,
	events chan<- Event,
) {
	defer close(events)
	defer l.unsubscribe(ctx, connection, handles)
	for {
		select {
		case <-ctx.Done():
			return
		case event := <-received:
			select {
			case events <- event:
			case <-ctx.Done():
				return
			}
		}
	}
}

// unsubscribe tells the device to stop sending. It runs after ctx is already done, so it needs
// a context of its own; the cancelled one would be refused immediately.
func (l *Live) unsubscribe(ctx context.Context, connection plc4go.PlcConnection, handles []apiModel.PlcSubscriptionHandle) {
	if len(handles) == 0 {
		return
	}
	request, err := connection.UnsubscriptionRequestBuilder().AddHandles(handles...).Build()
	if err != nil {
		return
	}
	timeoutCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), l.options.Timeout)
	defer cancel()
	select {
	case <-request.Execute(timeoutCtx):
	case <-timeoutCtx.Done():
	}
}

// subscriptionEvent maps one plc4x subscription event onto the UI's Event.
func (l *Live) subscriptionEvent(connectionID string, tag TagSpec, event apiModel.PlcSubscriptionEvent) Event {
	mapped := Event{
		Kind:       EventSubscribe,
		Connection: connectionID,
		Received:   l.options.Now(),
	}
	// Sorted, because plc4x hands the names out of a map and the message pane renders them in
	// the order given.
	names := slices.Sorted(slices.Values(event.GetTagNames()))
	for _, name := range names {
		address := event.GetAddress(name)
		if address == "" {
			// Not every event is directly addressable; some only carry the source that sent
			// them, and some carry neither, in which case the subscribed address is the best
			// available label.
			address = event.GetSource(name)
		}
		if address == "" {
			address = tag.Address
		}
		value := event.GetValue(name)
		mapped.Tags = append(mapped.Tags, TagResult{
			Name:     name,
			Address:  address,
			DataType: plcValueType(value),
			Value:    plcValueString(value),
			Code:     event.GetResponseCode(name).GetName(),
		})
	}
	return mapped
}

// Close releases every connection, and the driver manager when this session created it.
func (l *Live) Close() error {
	l.mu.Lock()
	if l.closed {
		l.mu.Unlock()
		return nil
	}
	l.closed = true
	held := slices.Collect(maps.Values(l.connections))
	l.connections = map[string]liveConnection{}
	l.registered = map[string]DriverInfo{}
	l.drivers = map[string]plc4go.PlcDriver{}
	l.mu.Unlock()

	// Sorted so a multi-connection failure reports the same way twice.
	slices.SortFunc(held, func(a, b liveConnection) int { return strings.Compare(a.info.ID, b.info.ID) })
	var closeErrors []error
	for _, connection := range held {
		if err := connection.connection.Close(); err != nil {
			closeErrors = append(closeErrors, errors.Wrapf(err, "%s can't close", connection.info.ID))
		}
	}
	if l.ownsDriverManager {
		if err := l.driverManager.Close(); err != nil {
			closeErrors = append(closeErrors, errors.Wrap(err, "can't close the driver manager"))
		}
	}
	return errors.Join(closeErrors...)
}

// connection resolves a connection ID, reporting the two ways it can fail to resolve.
func (l *Live) connection(id string) (plc4go.PlcConnection, error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.closed {
		return nil, errors.New("session is closed")
	}
	held, exists := l.connections[id]
	if !exists {
		return nil, errors.Errorf("%s not connected", id)
	}
	return held.connection, nil
}

// awaitResult waits for a plc4x request result under a context.
//
// plc4x's request channels are buffered, so abandoning one on a timeout costs nothing; what it
// buys is that a driver which fails to honour its own context cannot hold the UI. That is not
// hypothetical - a driver waiting on a handshake was measured blocking for a full minute.
func awaitResult[T any](ctx context.Context, results <-chan T, connectionID, operation string, timeout time.Duration) (T, error) {
	select {
	case result := <-results:
		return result, nil
	case <-ctx.Done():
		var zero T
		return zero, errors.Wrapf(ctx.Err(), "%s gave up on the %s after %s", connectionID, operation, timeout)
	}
}

// canonicalLiveProtocol resolves an alias to the protocol code its driver answers to.
func canonicalLiveProtocol(protocol string) string {
	protocol = strings.ToLower(strings.TrimSpace(protocol))
	if canonical, aliased := liveProtocolAliases[protocol]; aliased {
		return canonical
	}
	return protocol
}

// liveConnectionID derives the canonical "code://host" identity of a connection string,
// together with the transport code when the string names one. code is the canonical protocol
// code, so that an alias and the code it resolves to are one connection rather than two.
//
// plc4x connection strings come in two shapes: "c-bus://host:port", and the transport-explicit
// "c-bus:tcp://host:port", where net/url parses everything after the first colon as opaque
// data and leaves Host empty. The previous UI derived the ID from Host alone, so every
// transport-explicit connection collapsed onto the single ID "c-bus://" and the second one
// opened was rejected as already connected.
func liveConnectionID(code string, connectionUrl *url.URL) (id string, transport string) {
	host := connectionUrl.Host
	if connectionUrl.Opaque != "" {
		if opaque, err := url.Parse(connectionUrl.Opaque); err == nil {
			transport = opaque.Scheme
			host = opaque.Host
		}
	}
	return code + "://" + host, transport
}

// appendBrowseItems flattens a browse item and its children into the result list.
//
// plc4x returns a tree; the browse pane is a table. Flattening keeps the whole address space
// reachable without the UI having to learn a second shape.
func appendBrowseItems(items []BrowseItem, item apiModel.PlcBrowseItem, depth int) []BrowseItem {
	if item == nil || depth <= 0 {
		return items
	}
	mapped := BrowseItem{
		Name:         item.GetName(),
		Readable:     item.IsReadable(),
		Writable:     item.IsWritable(),
		Subscribable: item.IsSubscribable(),
	}
	if tag := item.GetTag(); tag != nil {
		mapped.Address = tag.GetAddressString()
		mapped.DataType = tag.GetValueType().String()
	}
	items = append(items, mapped)

	children := item.GetChildren()
	// Sorted, because the children arrive in a map and a browse listing that reorders itself
	// between two identical queries is not a listing.
	for _, name := range slices.Sorted(maps.Keys(children)) {
		items = appendBrowseItems(items, children[name], depth-1)
	}
	return items
}

// plcValueString renders a plc4x value for display, tolerating the nil that comes back with a
// failed tag.
func plcValueString(value values.PlcValue) string {
	if value == nil || value.IsNull() {
		return ""
	}
	return value.String()
}

// plcValueType names a plc4x value's type, tolerating the nil that comes back with a failed
// tag.
func plcValueType(value values.PlcValue) string {
	if value == nil {
		return ""
	}
	return value.GetPlcValueType().String()
}

// Compile-time proof that Live satisfies the seam.
var _ Session = (*Live)(nil)
