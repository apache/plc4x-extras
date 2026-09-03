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

// Package plcsession is the seam between the browser's user interface and the PLC.
//
// It exists for three reasons that turn out to be the same reason.
//
// The interface can be faked, so the user interface can be tested without a PLC, a network or
// a terminal. The previous UI could not be tested at all: its command actions wrote their
// results straight into global tview widgets, so there was nothing to assert on.
//
// The same fake is what --demo runs on, so demo mode is not a parallel implementation that can
// drift from the real thing; it is the seam the tests already exercise.
//
// And because nothing here mentions a plc4x type, a view can render a result without knowing
// how it was produced. That is what makes it practical to finish the read, write, browse and
// subscribe commands, which the previous UI advertised but answered with "mode switch not yet
// implemented".
package plcsession

import (
	"context"
	"time"
)

// TagSpec identifies one tag in a request. Name is the caller's label for the tag, which comes
// back on the corresponding result; Address is the protocol-specific tag address.
type TagSpec struct {
	Name    string
	Address string
	// Value is the value to write, used only by Write.
	Value string
}

// DriverInfo describes a protocol driver as the UI lists it.
type DriverInfo struct {
	// Code is the protocol code used in a connection string, such as "c-bus".
	Code string
	// Name is the human-readable driver name.
	Name string
	// SupportsDiscovery says whether Discover can be called for this protocol.
	SupportsDiscovery bool
}

// ConnectionInfo describes an open connection.
type ConnectionInfo struct {
	// ID is the canonical "scheme://host" identity the UI and the commands address it by.
	ID string
	// Protocol is the driver code serving this connection.
	Protocol string
	// Transport is the transport the connection runs over, when known.
	Transport string
}

// TagResult is one tag's outcome in a read, write or subscription event.
type TagResult struct {
	Name     string
	Address  string
	DataType string
	Value    string
	// Code is the response code, "OK" when the tag succeeded.
	Code string
}

// Succeeded reports whether this tag's response code indicates success.
func (t TagResult) Succeeded() bool { return t.Code == ResponseCodeOK }

// ResponseCodeOK is the response code denoting success.
const ResponseCodeOK = "OK"

// ReadResult is the outcome of a read request.
type ReadResult struct {
	Tags     []TagResult
	Duration time.Duration
}

// WriteResult is the outcome of a write request.
type WriteResult struct {
	Tags     []TagResult
	Duration time.Duration
}

// BrowseItem is one entry discovered by browsing a connection.
type BrowseItem struct {
	Address  string
	Name     string
	DataType string

	Readable     bool
	Writable     bool
	Subscribable bool
}

// BrowseResult is the outcome of a browse request.
type BrowseResult struct {
	Items    []BrowseItem
	Duration time.Duration
}

// DiscoveryItem is a device found by discovery.
type DiscoveryItem struct {
	// ConnectionString is the string that would connect to the discovered device.
	ConnectionString string
	Protocol         string
	Transport        string
	Name             string
}

// EventKind distinguishes the sources of a message shown in the message list.
type EventKind string

// The event kinds. These are the operations the UI can attribute a message to.
const (
	EventRead      EventKind = "read"
	EventWrite     EventKind = "write"
	EventBrowse    EventKind = "browse"
	EventSubscribe EventKind = "subscribe"
	EventDiscover  EventKind = "discover"
)

// Event is one message to show in the message list. Subscriptions deliver these continuously;
// the one-shot operations produce one each.
type Event struct {
	Kind EventKind
	// Connection is the connection the event came from.
	Connection string
	// Received is when the event was observed.
	Received time.Time
	// Started is when the request that produced the event was issued. Together with Received it
	// is the window a frame log is queried with: a transport carries no request identifier, so
	// the only association available between a request and its bytes is temporal.
	Started time.Time
	// Tags carries the payload for read, write and subscription events.
	Tags []TagResult
	// Summary is a short human-readable description, used when Tags is not the whole story.
	Summary string
}

// Session is everything the browser's user interface needs from a PLC.
//
// Implementations are not required to be safe for concurrent use by several goroutines. The
// user interface calls a Session only from inside a command, and Bubble Tea runs one command
// at a time per message, so serialisation is the caller's job when it fans work out.
type Session interface {
	// Protocols lists the protocol codes that can be registered.
	Protocols() []string

	// Drivers lists the drivers registered so far.
	Drivers() []DriverInfo

	// RegisterDriver registers the driver for a protocol code, along with the transports it
	// needs. Registering an already-registered driver is an error, so that a typo in a
	// configuration file is reported rather than silently ignored.
	RegisterDriver(protocol string) (DriverInfo, error)

	// Connections lists the open connections.
	Connections() []ConnectionInfo

	// Connect opens a connection from a plc4x connection string.
	Connect(ctx context.Context, connectionString string) (ConnectionInfo, error)

	// Disconnect closes the connection with the given ID.
	Disconnect(id string) error

	// Discover searches for devices speaking a protocol, calling sink for each one found.
	Discover(ctx context.Context, protocol string, sink func(DiscoveryItem)) error

	// Read reads tags from a connection.
	Read(ctx context.Context, connectionID string, tags []TagSpec) (ReadResult, error)

	// Write writes tags to a connection. Each TagSpec carries its Value.
	Write(ctx context.Context, connectionID string, tags []TagSpec) (WriteResult, error)

	// Browse enumerates the tags a connection exposes. An empty query means everything.
	Browse(ctx context.Context, connectionID, query string) (BrowseResult, error)

	// Subscribe subscribes to tags. Events arrive on the returned channel until the context is
	// cancelled or the connection is closed, at which point the channel is closed.
	Subscribe(ctx context.Context, connectionID string, tags []TagSpec) (<-chan Event, error)

	// Close releases every connection. It is safe to call more than once.
	Close() error
}
