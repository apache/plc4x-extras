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
	"fmt"
	"maps"
	"math"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
)

// Demo is a Session backed by a synthetic plant instead of a device.
//
// It answers instantly. That is not merely a convenience: pointing a real plc4x driver at an
// in-memory transport was measured to block for sixty seconds waiting for a protocol handshake
// that nothing answers, which in a demo is indistinguishable from a hang.
//
// Values change from reading to reading, so the UI visibly does something, but they are a pure
// function of a per-tag counter rather than of the clock or a random source. A test can
// therefore assert exact values, and two runs of the demo show the same sequence.
type Demo struct {
	options DemoOptions

	mu sync.Mutex
	// connected holds the connections opened so far, keyed by ID.
	connected map[string]ConnectionInfo
	// registered holds the drivers registered so far, keyed by protocol code.
	registered map[string]DriverInfo
	// reads counts how many times each tag has been read, which is what makes the values
	// advance deterministically.
	reads map[string]int
	// written records the last value written to each tag, so a write is observable by a
	// subsequent read rather than being silently discarded.
	written map[string]string
	// closed marks the session as shut down.
	closed bool
}

// DemoOptions configures the demo session.
type DemoOptions struct {
	// SubscriptionInterval is the gap between subscription events. Tests set it small; the
	// UI leaves it at DefaultSubscriptionInterval so the display is lively but not frantic.
	SubscriptionInterval time.Duration
	// Now supplies timestamps. Tests inject a fixed or stepping clock to keep output stable.
	Now func() time.Time
}

// DefaultSubscriptionInterval is how often a demo subscription emits when unconfigured.
const DefaultSubscriptionInterval = time.Second

// DemoProtocol is the protocol code the demo driver answers to.
const DemoProtocol = "demo"

// The demo plant. Two devices, so the connection list has something to select between.
const (
	DemoDeviceOne = "demo://plant-1"
	DemoDeviceTwo = "demo://plant-2"
)

// demoTag describes one synthetic tag.
type demoTag struct {
	address  string
	name     string
	dataType string
	// unit is appended to a rendered value, when the tag has one.
	unit string
	// base and amplitude shape the value sequence for numeric tags.
	base      float64
	amplitude float64

	readable     bool
	writable     bool
	subscribable bool
}

// demoCatalog is the tag set every demo device exposes. It is deliberately small enough to fit
// a browse pane without scrolling, and varied enough to exercise the value formatting.
var demoCatalog = []demoTag{
	{address: "temp/1", name: "Boiler inlet", dataType: "REAL", unit: "°C", base: 21.5, amplitude: 4, readable: true, subscribable: true},
	{address: "temp/2", name: "Boiler outlet", dataType: "REAL", unit: "°C", base: 68.0, amplitude: 6, readable: true, subscribable: true},
	{address: "press/1", name: "Header pressure", dataType: "DINT", unit: "mbar", base: 1013, amplitude: 12, readable: true, subscribable: true},
	{address: "flow/1", name: "Circuit flow", dataType: "REAL", unit: "l/s", base: 4.2, amplitude: 1.5, readable: true, subscribable: true},
	{address: "motor/run", name: "Pump running", dataType: "BOOL", readable: true, writable: true, subscribable: true},
	{address: "motor/speed", name: "Pump setpoint", dataType: "UINT", unit: "rpm", base: 1450, amplitude: 50, readable: true, writable: true},
	{address: "counter/1", name: "Cycle counter", dataType: "UDINT", base: 84000, amplitude: 0, readable: true, subscribable: true},
	{address: "label/1", name: "Plant label", dataType: "STRING", readable: true, writable: true},
}

// NewDemo creates a demo session. The zero DemoOptions is valid.
func NewDemo(options DemoOptions) *Demo {
	if options.SubscriptionInterval <= 0 {
		options.SubscriptionInterval = DefaultSubscriptionInterval
	}
	if options.Now == nil {
		options.Now = time.Now
	}
	return &Demo{
		options:    options,
		connected:  map[string]ConnectionInfo{},
		registered: map[string]DriverInfo{},
		reads:      map[string]int{},
		written:    map[string]string{},
	}
}

// Protocols returns the demo protocol. Demo mode offers exactly one, so that the UI's protocol
// suggestions cannot lead a user towards a driver that has no device behind it.
func (d *Demo) Protocols() []string { return []string{DemoProtocol} }

// Drivers lists the registered drivers.
func (d *Demo) Drivers() []DriverInfo {
	d.mu.Lock()
	defer d.mu.Unlock()
	drivers := slices.Collect(maps.Values(d.registered))
	slices.SortFunc(drivers, func(a, b DriverInfo) int { return strings.Compare(a.Code, b.Code) })
	return drivers
}

// RegisterDriver registers the demo driver.
func (d *Demo) RegisterDriver(protocol string) (DriverInfo, error) {
	if protocol != DemoProtocol {
		return DriverInfo{}, errors.Errorf("demo mode serves only the %q protocol, not %q", DemoProtocol, protocol)
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	if _, exists := d.registered[protocol]; exists {
		return DriverInfo{}, errors.Errorf("%s already registered", protocol)
	}
	info := DriverInfo{Code: DemoProtocol, Name: "Demo Simulator", SupportsDiscovery: true}
	d.registered[protocol] = info
	return info, nil
}

// Connections lists the open connections, ordered so the UI list is stable.
func (d *Demo) Connections() []ConnectionInfo {
	d.mu.Lock()
	defer d.mu.Unlock()
	connections := slices.Collect(maps.Values(d.connected))
	slices.SortFunc(connections, func(a, b ConnectionInfo) int { return strings.Compare(a.ID, b.ID) })
	return connections
}

// Connect opens a demo connection. It accepts the demo devices, and rejects anything else with
// a message naming what is available, since a demo that silently accepts a real connection
// string would look like it had connected to hardware.
func (d *Demo) Connect(_ context.Context, connectionString string) (ConnectionInfo, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.closed {
		return ConnectionInfo{}, errors.New("session is closed")
	}

	id := strings.TrimSpace(connectionString)
	if !strings.HasPrefix(id, DemoProtocol+"://") {
		return ConnectionInfo{}, errors.Errorf(
			"demo mode only connects to the simulated devices, try %s or %s", DemoDeviceOne, DemoDeviceTwo)
	}
	if _, exists := d.connected[id]; exists {
		return ConnectionInfo{}, errors.Errorf("%s already connected", id)
	}
	info := ConnectionInfo{ID: id, Protocol: DemoProtocol, Transport: DemoProtocol}
	d.connected[id] = info
	return info, nil
}

// Disconnect closes a demo connection.
func (d *Demo) Disconnect(id string) error {
	d.mu.Lock()
	defer d.mu.Unlock()
	if _, exists := d.connected[id]; !exists {
		return errors.Errorf("%s not connected", id)
	}
	delete(d.connected, id)
	return nil
}

// Discover reports the demo devices.
func (d *Demo) Discover(ctx context.Context, protocol string, sink func(DiscoveryItem)) error {
	if protocol != DemoProtocol {
		return errors.Errorf("demo mode can only discover the %q protocol", DemoProtocol)
	}
	for _, device := range []string{DemoDeviceOne, DemoDeviceTwo} {
		if err := ctx.Err(); err != nil {
			return err
		}
		sink(DiscoveryItem{
			ConnectionString: device,
			Protocol:         DemoProtocol,
			Transport:        DemoProtocol,
			Name:             "Simulated plant " + strings.TrimPrefix(device, DemoProtocol+"://"),
		})
	}
	return nil
}

// Read returns synthetic values for the requested tags.
func (d *Demo) Read(_ context.Context, connectionID string, tags []TagSpec) (ReadResult, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if err := d.requireConnected(connectionID); err != nil {
		return ReadResult{}, err
	}
	if len(tags) == 0 {
		return ReadResult{}, errors.New("a read request needs at least one tag")
	}

	result := ReadResult{Tags: make([]TagResult, 0, len(tags))}
	for _, tag := range tags {
		result.Tags = append(result.Tags, d.readTagLocked(connectionID, tag))
	}
	// A fixed, non-zero duration: the UI shows it, and a real timing would make golden output
	// unstable for no benefit.
	result.Duration = 4 * time.Millisecond
	return result, nil
}

// Write records values against the tags, so that a following read observes them.
func (d *Demo) Write(_ context.Context, connectionID string, tags []TagSpec) (WriteResult, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if err := d.requireConnected(connectionID); err != nil {
		return WriteResult{}, err
	}
	if len(tags) == 0 {
		return WriteResult{}, errors.New("a write request needs at least one tag")
	}

	result := WriteResult{Tags: make([]TagResult, 0, len(tags))}
	for _, tag := range tags {
		catalogued, found := lookupDemoTag(tag.Address)
		switch {
		case !found:
			result.Tags = append(result.Tags, TagResult{
				Name: tag.Name, Address: tag.Address, Code: "NOT_FOUND",
			})
		case !catalogued.writable:
			result.Tags = append(result.Tags, TagResult{
				Name: tag.Name, Address: tag.Address, DataType: catalogued.dataType, Code: "ACCESS_DENIED",
			})
		default:
			d.written[connectionID+"|"+tag.Address] = tag.Value
			result.Tags = append(result.Tags, TagResult{
				Name: tag.Name, Address: tag.Address, DataType: catalogued.dataType,
				Value: tag.Value, Code: ResponseCodeOK,
			})
		}
	}
	result.Duration = 3 * time.Millisecond
	return result, nil
}

// Browse lists the demo catalog, filtered by a substring query.
func (d *Demo) Browse(_ context.Context, connectionID, query string) (BrowseResult, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if err := d.requireConnected(connectionID); err != nil {
		return BrowseResult{}, err
	}

	query = strings.ToLower(strings.TrimSpace(query))
	// "*" is the conventional "everything" query, and reads more naturally than an empty one.
	if query == "*" {
		query = ""
	}

	result := BrowseResult{Duration: 6 * time.Millisecond}
	for _, tag := range demoCatalog {
		if query != "" &&
			!strings.Contains(strings.ToLower(tag.address), query) &&
			!strings.Contains(strings.ToLower(tag.name), query) {
			continue
		}
		result.Items = append(result.Items, BrowseItem{
			Address:      tag.address,
			Name:         tag.name,
			DataType:     tag.dataType,
			Readable:     tag.readable,
			Writable:     tag.writable,
			Subscribable: tag.subscribable,
		})
	}
	return result, nil
}

// Subscribe streams synthetic events until the context is cancelled.
func (d *Demo) Subscribe(ctx context.Context, connectionID string, tags []TagSpec) (<-chan Event, error) {
	d.mu.Lock()
	if err := d.requireConnected(connectionID); err != nil {
		d.mu.Unlock()
		return nil, err
	}
	d.mu.Unlock()

	if len(tags) == 0 {
		return nil, errors.New("a subscription needs at least one tag")
	}
	for _, tag := range tags {
		catalogued, found := lookupDemoTag(tag.Address)
		if !found {
			return nil, errors.Errorf("unknown tag %q, try browsing the connection first", tag.Address)
		}
		if !catalogued.subscribable {
			return nil, errors.Errorf("tag %q cannot be subscribed to", tag.Address)
		}
	}

	events := make(chan Event)
	go func() {
		defer close(events)
		ticker := time.NewTicker(d.options.SubscriptionInterval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				event := d.nextSubscriptionEvent(connectionID, tags)
				select {
				case events <- event:
				case <-ctx.Done():
					return
				}
			}
		}
	}()
	return events, nil
}

// nextSubscriptionEvent builds one subscription event, advancing the value sequence.
func (d *Demo) nextSubscriptionEvent(connectionID string, tags []TagSpec) Event {
	d.mu.Lock()
	defer d.mu.Unlock()
	event := Event{
		Kind:       EventSubscribe,
		Connection: connectionID,
		Received:   d.options.Now(),
		Tags:       make([]TagResult, 0, len(tags)),
	}
	for _, tag := range tags {
		event.Tags = append(event.Tags, d.readTagLocked(connectionID, tag))
	}
	return event
}

// Close releases the demo connections.
func (d *Demo) Close() error {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.connected = map[string]ConnectionInfo{}
	d.closed = true
	return nil
}

// requireConnected reports an error unless the connection is open. Callers hold d.mu.
func (d *Demo) requireConnected(id string) error {
	if d.closed {
		return errors.New("session is closed")
	}
	if _, exists := d.connected[id]; !exists {
		return errors.Errorf("%s not connected", id)
	}
	return nil
}

// readTagLocked produces one tag's value, advancing that tag's read counter. Callers hold d.mu.
func (d *Demo) readTagLocked(connectionID string, tag TagSpec) TagResult {
	catalogued, found := lookupDemoTag(tag.Address)
	if !found {
		return TagResult{Name: tag.Name, Address: tag.Address, Code: "NOT_FOUND"}
	}
	if !catalogued.readable {
		return TagResult{Name: tag.Name, Address: tag.Address, DataType: catalogued.dataType, Code: "ACCESS_DENIED"}
	}

	key := connectionID + "|" + tag.Address
	// A value written earlier wins, so that a write is visibly effective.
	if written, ok := d.written[key]; ok {
		return TagResult{
			Name: tag.Name, Address: tag.Address, DataType: catalogued.dataType,
			Value: written, Code: ResponseCodeOK,
		}
	}

	sequence := d.reads[key]
	d.reads[key] = sequence + 1
	return TagResult{
		Name:     tag.Name,
		Address:  tag.Address,
		DataType: catalogued.dataType,
		Value:    demoValue(catalogued, sequence),
		Code:     ResponseCodeOK,
	}
}

// demoValue renders the value of a tag at a point in its sequence.
//
// The sequence is a plain counter and the shape is a sine, so the values move the way a plant
// reading moves rather than jumping about, and they are exactly reproducible. The period is 17,
// a prime, so that two tags read together do not appear to move in lockstep.
func demoValue(tag demoTag, sequence int) string {
	switch tag.dataType {
	case "BOOL":
		// Alternate on a longer cycle than the numeric tags, so the pane is not a flicker.
		if (sequence/3)%2 == 0 {
			return "true"
		}
		return "false"
	case "STRING":
		return "plant-" + strconv.Itoa(sequence%2+1)
	case "REAL":
		value := tag.base + tag.amplitude*math.Sin(2*math.Pi*float64(sequence)/17)
		return withUnit(strconv.FormatFloat(math.Round(value*10)/10, 'f', 1, 64), tag.unit)
	case "UDINT":
		// A monotonic counter: the one tag that should obviously advance every read.
		return withUnit(strconv.Itoa(int(tag.base)+sequence), tag.unit)
	default:
		value := tag.base + tag.amplitude*math.Sin(2*math.Pi*float64(sequence)/17)
		return withUnit(strconv.Itoa(int(math.Round(value))), tag.unit)
	}
}

// withUnit appends a unit when there is one.
func withUnit(value, unit string) string {
	if unit == "" {
		return value
	}
	return fmt.Sprintf("%s %s", value, unit)
}

// lookupDemoTag finds a catalogued tag by address.
func lookupDemoTag(address string) (demoTag, bool) {
	for _, tag := range demoCatalog {
		if tag.address == address {
			return tag, true
		}
	}
	return demoTag{}, false
}

// DemoTagAddresses lists the demo catalog's addresses, for completion suggestions.
func DemoTagAddresses() []string {
	addresses := make([]string, 0, len(demoCatalog))
	for _, tag := range demoCatalog {
		addresses = append(addresses, tag.address)
	}
	return addresses
}

// Compile-time proof that Demo satisfies the seam.
var _ Session = (*Demo)(nil)
