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

package progress

import "sync"

// NewChannel returns a Reporter that sends an Update per change instead of drawing anything.
//
// This is the terminal-UI half of the package. A Bubble Tea model cannot be written to from an
// analysis goroutine — its state belongs to the event loop — so the analysis publishes values
// and the program turns them into messages, typically with a command that reads ch once and
// re-arms itself.
//
// The channel should be buffered. An unbuffered one still works but will drop nearly
// everything, since a send only succeeds while the program happens to be waiting.
//
// The channel is bidirectional rather than send-only so that the terminal frame can displace a
// stale update instead of being dropped; see send.
func NewChannel(ch chan Update) Reporter {
	return &channelReporter{ch: ch}
}

// channelReporter publishes progress to a Bubble Tea program.
type channelReporter struct {
	ch chan Update

	mu          sync.Mutex
	total       int
	current     int
	description string
	done        bool
	dropped     int
}

func (r *channelReporter) Start(total int, description string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.total = total
	r.current = 0
	r.description = description
	r.done = false
	r.send(false)
}

func (r *channelReporter) Advance(delta int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.current += delta
	r.send(false)
}

func (r *channelReporter) SetDescription(description string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.description = description
	r.send(false)
}

func (r *channelReporter) Done() {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.done {
		return
	}
	r.done = true
	r.send(true)
}

// Dropped reports how many updates were discarded because nobody was ready to receive them.
//
// It is diagnostic, not part of Reporter: reach it with a type assertion for
// interface{ Dropped() int }. It exists so that a test can prove the drop actually happens
// rather than inferring it from a test that merely did not hang.
func (r *channelReporter) Dropped() int {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.dropped
}

// send publishes the current state, or drops it.
//
// The send is deliberately non-blocking. The alternative — waiting for the UI to catch up —
// would let the display throttle the analysis, and a progress frame that arrives late is
// worthless anyway: the next one supersedes it entirely, because Update carries absolute
// counters rather than a delta. Dropping is therefore lossless in the only sense that matters.
//
// The terminal frame is the exception. Dropping it strands the bar at whatever fraction it
// last showed -- a run that finished but reads as stuck at 99% -- and unlike an intermediate
// frame nothing supersedes it. It is also bounded: it happens once. So when the buffer is
// full, the final update displaces the oldest queued one rather than being discarded. That
// still cannot block, because both operations are non-blocking.
//
// A UI should nonetheless treat the analysis function returning as the authoritative signal
// that the run ended; this frame just gets the bar to its final position sooner.
//
// The caller must hold r.mu.
func (r *channelReporter) send(done bool) {
	current := max(r.current, 0)
	// Clamp for the same reason the CLI reporter does: a model dividing Current by Total must
	// not be handed a ratio above one.
	if r.total > 0 && current > r.total {
		current = r.total
	}
	update := Update{
		Current:     current,
		Total:       r.total,
		Description: r.description,
		Done:        done,
	}
	// A nil channel is never ready, so this degenerates to "drop everything" rather than
	// blocking forever, which is the right answer for a reporter wired up wrongly.
	select {
	case r.ch <- update:
		return
	default:
	}

	if !done {
		r.dropped++
		return
	}

	// Make room for the final frame by discarding the oldest queued update, then retry. If the
	// receiver drained the channel in between, the retry simply succeeds on an empty buffer.
	select {
	case <-r.ch:
		r.dropped++
	default:
	}
	select {
	case r.ch <- update:
	default:
		// A nil or zero-capacity channel with no reader: nothing can be delivered, and the
		// analysis must not wait on a display.
		r.dropped++
	}
}
