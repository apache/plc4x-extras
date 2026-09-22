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
	"net/url"
	"sync"
	"time"

	"github.com/apache/plc4x/plc4go/spi/options"
	"github.com/apache/plc4x/plc4go/spi/transports"
)

// Raw frame capture.
//
// The browser can show the bytes a request actually put on the wire, which is the one thing it
// could never do before and the reason the pcap analyzer exists as a separate tool. plc4x has
// no public byte tap: spi/tracer records operations, but its TraceEntry.Message is a string, so
// it carries "read" and "SUCCESS" rather than octets.
//
// The bytes are therefore taken where they exist, at the transport. Transport and
// TransportInstance are both interfaces, so a decorator that embeds one and overrides only
// Read and Write is small and cannot drift as the interface grows.

// FrameDirection says which way a captured frame travelled.
type FrameDirection string

// The frame directions, named from the tool's point of view.
const (
	// FrameOutbound is a frame this tool sent.
	FrameOutbound FrameDirection = "out"
	// FrameInbound is a frame this tool received.
	FrameInbound FrameDirection = "in"
)

// Frame is one chunk of bytes as it crossed the transport.
//
// A transport is a byte stream, not a sequence of messages, so a Frame is one Read or Write
// rather than one protocol message: a single message may span several frames and one frame may
// carry several messages. The UI says so rather than implying a framing that is not there.
type Frame struct {
	// At is when the bytes crossed the transport.
	At time.Time
	// Direction says which way they went.
	Direction FrameDirection
	// Bytes is a copy of the payload, so a later reuse of the driver's buffer cannot change
	// what the UI displays.
	Bytes []byte
}

// FrameLog is a bounded, concurrency-safe record of captured frames.
//
// It is written from the driver's goroutines and read by the user interface, so it is guarded.
// It is bounded because a busy connection would otherwise grow it without limit: a terminal
// can show a few hundred frames and a debugging session needs the most recent ones.
type FrameLog struct {
	mu     sync.Mutex
	frames []Frame
	limit  int
}

// DefaultFrameLimit is how many frames a FrameLog keeps when unconfigured.
const DefaultFrameLimit = 500

// NewFrameLog creates a frame log. A limit of zero or less means DefaultFrameLimit.
func NewFrameLog(limit int) *FrameLog {
	if limit <= 0 {
		limit = DefaultFrameLimit
	}
	return &FrameLog{limit: limit}
}

// Add records a frame, copying the bytes.
func (l *FrameLog) Add(direction FrameDirection, at time.Time, data []byte) {
	if l == nil || len(data) == 0 {
		return
	}
	copied := make([]byte, len(data))
	copy(copied, data)

	l.mu.Lock()
	defer l.mu.Unlock()
	l.frames = append(l.frames, Frame{At: at, Direction: direction, Bytes: copied})
	if len(l.frames) > l.limit {
		l.frames = l.frames[len(l.frames)-l.limit:]
	}
}

// Frames returns a copy of everything recorded.
func (l *FrameLog) Frames() []Frame {
	if l == nil {
		return nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	return append([]Frame(nil), l.frames...)
}

// Between returns the frames captured in a time window, inclusive at both ends.
//
// This is how a request is associated with its bytes. A transport carries no request
// identifier, so the association is temporal: the frames that crossed while the request was in
// flight. That is an approximation, and on a connection carrying a subscription it will include
// unrelated traffic, which is why the UI presents it as "frames during this request" rather
// than as the request's own bytes.
func (l *FrameLog) Between(from, to time.Time) []Frame {
	if l == nil {
		return nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()

	var out []Frame
	for _, frame := range l.frames {
		if frame.At.Before(from) || frame.At.After(to) {
			continue
		}
		out = append(out, frame)
	}
	return out
}

// Len reports how many frames are held.
func (l *FrameLog) Len() int {
	if l == nil {
		return 0
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	return len(l.frames)
}

// Reset discards everything recorded.
func (l *FrameLog) Reset() {
	if l == nil {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	l.frames = nil
}

// recordingTransport wraps a transport so that every instance it creates is recorded.
//
// It reports the wrapped transport's own code and name, because the driver looks a transport up
// by code: a decorator that renamed itself would simply not be found.
type recordingTransport struct {
	transports.Transport
	log   *FrameLog
	clock func() time.Time
}

// newRecordingTransport wraps inner so that the bytes it carries are recorded into log.
func newRecordingTransport(inner transports.Transport, log *FrameLog, clock func() time.Time) transports.Transport {
	if clock == nil {
		clock = time.Now
	}
	return &recordingTransport{Transport: inner, log: log, clock: clock}
}

// CreateTransportInstance wraps the created instance.
func (t *recordingTransport) CreateTransportInstance(transportUrl url.URL, opts map[string][]string, _options ...options.WithOption) (transports.TransportInstance, error) {
	inner, err := t.Transport.CreateTransportInstance(transportUrl, opts, _options...)
	if err != nil || inner == nil {
		return inner, err
	}
	return &recordingInstance{TransportInstance: inner, log: t.log, clock: t.clock}, nil
}

// recordingInstance records the bytes crossing one transport instance.
//
// Only Read and Write are overridden. Everything else, including the buffer-filling and
// peeking that a codec uses to find a message boundary, is delegated: peeking does not consume
// bytes, so recording it would double-count them.
type recordingInstance struct {
	transports.TransportInstance
	log   *FrameLog
	clock func() time.Time
}

// Read records the bytes it returns.
func (i *recordingInstance) Read(ctx context.Context, numBytes uint32) ([]byte, error) {
	data, err := i.TransportInstance.Read(ctx, numBytes)
	i.log.Add(FrameInbound, i.clock(), data)
	return data, err
}

// Write records the bytes it sends.
//
// The frame is recorded even when the write fails: a request that went out and was rejected is
// exactly the case someone reaches for a byte view to understand.
func (i *recordingInstance) Write(ctx context.Context, data []byte) error {
	err := i.TransportInstance.Write(ctx, data)
	i.log.Add(FrameOutbound, i.clock(), data)
	return err
}
