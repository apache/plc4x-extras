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
	"os"
	"path/filepath"

	"github.com/apache/plc4x/plc4go/spi/errors"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcapfixture"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Demo mode.
//
// The analyzers parse raw captured bytes: there is no connection, no handshake and no device
// anywhere in the path. So demo mode does not need a fake driver, and it does not need a
// capture committed to the repository either — it writes one, from the same fixture builder
// the tests use, containing genuine C-Bus traffic taken from plc4x's own protocol tests.
//
// The session it generates deliberately includes payloads the codec CANNOT parse. A demo where
// everything succeeds shows none of what this tool is for; this one produces findings, so the
// Findings tab, the failure counters and the detail pane all have something real in them.

// DemoCaptureName is the file the generated capture is written as. It is the name the sidebar
// shows, so it says what it is.
const DemoCaptureName = "cbus-demo.pcap"

// Demo is a generated capture, and everything needed to analyse it.
type Demo struct {
	// Dir is the temporary directory holding the capture. Cleanup removes it.
	Dir string
	// Path is the capture.
	Path string
	// Protocol is what the capture contains.
	Protocol protocol.Protocol
	// Client is the address the capture's requests come from, which C-Bus needs in order to
	// tell a request from a response.
	Client string
}

// NewDemo writes a demo capture into a fresh temporary directory.
func NewDemo() (Demo, error) {
	dir, err := os.MkdirTemp("", "plc4xpcapanalyzer-demo-")
	if err != nil {
		return Demo{}, errors.Wrap(err, "error creating the demo directory")
	}
	demo, err := NewDemoIn(dir)
	if err != nil {
		_ = os.RemoveAll(dir)
		return Demo{}, err
	}
	return demo, nil
}

// NewDemoIn writes a demo capture into an existing directory. Tests use it with t.TempDir so
// that nothing has to be cleaned up by hand.
func NewDemoIn(dir string) (Demo, error) {
	path := filepath.Join(dir, DemoCaptureName)
	if err := pcapfixture.WriteCBus(path, pcapfixture.CBusSessionWithFailures()); err != nil {
		return Demo{}, errors.Wrap(err, "error writing the demo capture")
	}
	return Demo{
		Dir:      dir,
		Path:     path,
		Protocol: protocol.CBus,
		Client:   pcapfixture.ClientIP,
	}, nil
}

// Cleanup removes the generated capture. It is safe to call on a zero Demo, and safe to call
// twice, so a deferred call needs no guard.
func (d Demo) Cleanup() error {
	if d.Dir == "" {
		return nil
	}
	return errors.Wrapf(os.RemoveAll(d.Dir), "error removing the demo directory %s", d.Dir)
}
