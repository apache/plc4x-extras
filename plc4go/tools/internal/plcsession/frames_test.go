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
	"sync"
	"testing"
	"time"

	plc4go "github.com/apache/plc4x/plc4go/pkg/api"
	"github.com/apache/plc4x/plc4go/spi"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// at is a timestamp a fixed number of milliseconds into the test's epoch.
func at(millis int) time.Time {
	return time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC).Add(time.Duration(millis) * time.Millisecond)
}

func TestAFrameLogRecordsBothDirections(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameOutbound, at(0), []byte("~~~\r"))
	log.Add(plcsession.FrameInbound, at(1), []byte("322100AD\r\n"))

	frames := log.Frames()
	require.Len(t, frames, 2)
	assert.Equal(t, plcsession.FrameOutbound, frames[0].Direction)
	assert.Equal(t, "~~~\r", string(frames[0].Bytes))
	assert.Equal(t, plcsession.FrameInbound, frames[1].Direction)
}

// TestAFrameLogCopiesTheBytes matters because a driver reuses its read buffer: holding the
// caller's slice would make the display change under the user.
func TestAFrameLogCopiesTheBytes(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	buffer := []byte("original")
	log.Add(plcsession.FrameInbound, at(0), buffer)

	copy(buffer, "clobbered")
	assert.Equal(t, "original", string(log.Frames()[0].Bytes),
		"a recorded frame must not change when the driver reuses its buffer")
}

func TestAFrameLogIsBounded(t *testing.T) {
	log := plcsession.NewFrameLog(4)
	for i := range 20 {
		log.Add(plcsession.FrameInbound, at(i), []byte{byte(i)})
	}
	frames := log.Frames()
	require.Len(t, frames, 4, "a busy connection must not grow the log without limit")
	// The most recent frames are the ones worth keeping.
	assert.Equal(t, byte(19), frames[3].Bytes[0])
	assert.Equal(t, byte(16), frames[0].Bytes[0])
}

func TestAFrameLogIgnoresEmptyReads(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameInbound, at(0), nil)
	log.Add(plcsession.FrameInbound, at(1), []byte{})
	assert.Zero(t, log.Len(), "a zero-length read is not a frame")
}

// TestBetweenSelectsTheFramesOfARequest is how a request is associated with its bytes: the
// transport carries no request identifier, so the association is the time window.
func TestBetweenSelectsTheFramesOfARequest(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameOutbound, at(0), []byte("before"))
	log.Add(plcsession.FrameOutbound, at(10), []byte("request"))
	log.Add(plcsession.FrameInbound, at(15), []byte("reply"))
	log.Add(plcsession.FrameInbound, at(30), []byte("after"))

	during := log.Between(at(10), at(20))
	require.Len(t, during, 2)
	assert.Equal(t, "request", string(during[0].Bytes))
	assert.Equal(t, "reply", string(during[1].Bytes))
}

func TestBetweenIsInclusiveAtBothEnds(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameOutbound, at(10), []byte("start"))
	log.Add(plcsession.FrameInbound, at(20), []byte("end"))
	assert.Len(t, log.Between(at(10), at(20)), 2,
		"a frame exactly on the boundary belongs to the request")
}

func TestFrameLogResetDiscardsEverything(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	log.Add(plcsession.FrameInbound, at(0), []byte("x"))
	log.Reset()
	assert.Zero(t, log.Len())
}

// TestANilFrameLogIsSafe keeps capture optional without a nil check at every call site.
func TestANilFrameLogIsSafe(t *testing.T) {
	var log *plcsession.FrameLog
	assert.NotPanics(t, func() {
		log.Add(plcsession.FrameInbound, at(0), []byte("x"))
		assert.Zero(t, log.Len())
		assert.Nil(t, log.Frames())
		assert.Nil(t, log.Between(at(0), at(1)))
		log.Reset()
	})
}

// TestAFrameLogIsSafeUnderConcurrentUse: the driver writes from its own goroutines while the
// interface reads, so this runs under -race in CI.
func TestAFrameLogIsSafeUnderConcurrentUse(t *testing.T) {
	log := plcsession.NewFrameLog(64)
	var group sync.WaitGroup
	for writer := range 4 {
		group.Add(1)
		go func(writer int) {
			defer group.Done()
			for i := range 50 {
				log.Add(plcsession.FrameInbound, at(i), []byte{byte(writer)})
			}
		}(writer)
	}
	group.Go(func() {
		for range 50 {
			_ = log.Frames()
			_ = log.Len()
		}
	})
	group.Wait()
	assert.LessOrEqual(t, log.Len(), 64)
}

// TestFrameCaptureRegistersTheRecordingTransportsFirst pins the ordering the capture depends
// on. plc4x keeps the FIRST transport registered under a code and skips the rest, so the
// decorator has to be installed before any driver registers its own.
func TestFrameCaptureRegistersTheRecordingTransportsFirst(t *testing.T) {
	manager := plc4go.NewPlcDriverManager()
	log := plcsession.NewFrameLog(0)
	session := plcsession.NewLive(plcsession.LiveOptions{DriverManager: manager, Frames: log})
	t.Cleanup(func() { _ = session.Close() })

	aware, ok := manager.(spi.TransportAware)
	require.True(t, ok)
	names := aware.ListTransportNames()
	assert.Contains(t, names, "tcp", "capture must install a tcp transport up front")
	assert.Contains(t, names, "udp")

	// Registering a driver afterwards must not displace the recording transports.
	_, err := session.RegisterDriver("c-bus")
	require.NoError(t, err)
	assert.ElementsMatch(t, names, aware.ListTransportNames(),
		"a driver's own transport registration must not replace the recording one")
}

func TestFrameCaptureIsOffByDefault(t *testing.T) {
	session := plcsession.NewLive(plcsession.LiveOptions{})
	t.Cleanup(func() { _ = session.Close() })
	assert.Nil(t, session.FrameLog(), "capture costs a copy of every byte, so it is opt-in")
}

func TestFrameLogIsReachableWhenCaptureIsOn(t *testing.T) {
	log := plcsession.NewFrameLog(0)
	session := plcsession.NewLive(plcsession.LiveOptions{Frames: log})
	t.Cleanup(func() { _ = session.Close() })
	assert.Same(t, log, session.FrameLog())
}
