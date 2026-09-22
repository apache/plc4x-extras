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

// Package progress reports the advance of a long-running analysis without deciding how, or
// even whether, it is drawn.
//
// It exists because of a measured defect in the pcap analyser. Both of its analysis loops
// built a progress bar bound to a hardwired ANSI stderr and ignored the io.Writer they had
// been handed, so a caller could only switch the bar off, never redirect it — a terminal UI
// therefore had no way to show progress at all. Worse, that bar and zerolog wrote to the same
// stderr with no coordination, so log lines and bar frames overwrote each other and corrupted
// both. Separating "something advanced" from "how it is shown" is what fixes that: the plain
// command line draws a bar only where a bar can safely be drawn, and the terminal UI receives
// values it can render inside its own layout.
package progress

// Reporter receives progress from long-running analysis.
//
// Implementations must tolerate being driven loosely, because the callers are analysis loops
// that break out early on a packet limit, a nil packet or a cancelled context: Advance may
// overshoot Total, Done may be called more than once, and the whole sequence may be abandoned
// after Start. None of that is an error, and none of it may panic.
type Reporter interface {
	// Start announces a new unit of work of a known size. Calling it again restarts the
	// reporter, which is how a multi-phase run reports each phase.
	Start(total int, description string)
	// Advance adds delta to the amount of work completed.
	Advance(delta int)
	// SetDescription renames the work in flight without disturbing the counters.
	SetDescription(description string)
	// Done announces that the work has finished, successfully or not. It is idempotent.
	Done()
}

// Update is one progress frame, as sent by the reporter returned from NewChannel.
//
// It carries no styling and no widths: it is the model's input, not a rendering. Any type
// satisfies bubbletea's Msg, so a program can receive this value directly.
type Update struct {
	Current, Total int
	Description    string
	Done           bool
}

// Nop returns a Reporter that discards everything.
//
// This is the default throughout, and the one tests use. Progress reporting is a courtesy to
// a human watching a terminal; nothing in the analysis depends on it, so "no reporter" has to
// be an ordinary, safe choice rather than a nil check at every call site.
func Nop() Reporter { return nopReporter{} }

// nopReporter is stateless, so it is safe to share and safe to call in any order or from any
// goroutine.
type nopReporter struct{}

func (nopReporter) Start(int, string)     {}
func (nopReporter) Advance(int)           {}
func (nopReporter) SetDescription(string) {}
func (nopReporter) Done()                 {}
