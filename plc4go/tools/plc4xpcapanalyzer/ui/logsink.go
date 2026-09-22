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

package ui

import (
	"bytes"
	"strings"
	"sync"
)

// Getting text out of a writer and into the log pane.
//
// Everything that writes text here — zerolog, the extractor's output — writes it from a
// goroutine the model does not own. So neither of these types touches the model: they split
// what they are given into lines and hand each one to a callback, and the model's callback
// does nothing but put it on a channel that a re-arming command drains.

// lineWriter turns an io.Writer into a stream of lines.
//
// It buffers a partial line rather than emitting it, because zerolog's console writer emits a
// record in several Write calls and a pane full of fragments is worse than one that is a beat
// behind. Flush releases whatever is left.
type lineWriter struct {
	emit lineWriterFunc

	mu      sync.Mutex
	partial bytes.Buffer
}

// newLineWriter builds a lineWriter that hands each complete line to emit.
func newLineWriter(emit lineWriterFunc) *lineWriter {
	if emit == nil {
		emit = func(string) {}
	}
	return &lineWriter{emit: emit}
}

// Write implements io.Writer. It is safe to call from any goroutine, because the writers it is
// handed to make no such promise about which one they use.
func (w *lineWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	lines := w.consume(p)
	w.mu.Unlock()
	for _, line := range lines {
		w.emit(line)
	}
	return len(p), nil
}

// consume appends p to the buffer and returns the complete lines it now holds. The caller must
// hold w.mu; the emitting is done outside the lock so a slow consumer cannot deadlock a
// concurrent writer.
func (w *lineWriter) consume(p []byte) []string {
	w.partial.Write(p)
	buffered := w.partial.String()
	index := strings.LastIndexByte(buffered, '\n')
	if index < 0 {
		return nil
	}
	complete := buffered[:index]
	w.partial.Reset()
	w.partial.WriteString(buffered[index+1:])
	return strings.Split(strings.TrimSuffix(complete, "\r"), "\n")
}

// Flush emits any partial line still buffered.
func (w *lineWriter) Flush() {
	w.mu.Lock()
	rest := strings.TrimSuffix(w.partial.String(), "\r")
	w.partial.Reset()
	w.mu.Unlock()
	if rest != "" {
		w.emit(rest)
	}
}

// channelSink is the model's line callback: it puts a line on a channel and nothing else.
//
// The send is non-blocking. A log line that cannot be delivered is dropped rather than allowed
// to throttle whatever produced it — an analysis must not run at the speed of a display, and
// the pane is bounded anyway.
func channelSink(ch chan string) lineWriterFunc {
	return func(line string) {
		select {
		case ch <- line:
		default:
		}
	}
}
