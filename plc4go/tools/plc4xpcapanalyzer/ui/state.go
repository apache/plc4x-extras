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
	"slices"
	"strings"

	plc4go "github.com/apache/plc4x/plc4go/pkg/api"
	"github.com/apache/plc4x/plc4go/pkg/api/drivers"
	"github.com/apache/plc4x/plc4go/spi"
	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/apache/plc4x/plc4go/spi/transports/pcap"
	"github.com/rs/zerolog"

	cliConfig "github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// The session state the commands read and write.
//
// It holds no view type and no Bubble Tea type on purpose. Commands are values-in, values-out
// functions over this struct, so the whole command layer can be tested without a terminal,
// and the model is the only thing that knows how any of it is drawn.
//
// State is owned by the model and is only ever touched on the event loop. The long-running
// analysis runs off the loop and therefore gets a Request value, not a pointer to this.

// DriverNames are the plc4x drivers that can be registered in the subsystem.
//
// This is deliberately a superset of the protocols the analyzer can parse: registering the ADS
// or S7 driver is still useful for the plc4x internals that the trace settings expose, and the
// previous version offered exactly this list.
var DriverNames = []string{"ads", "bacnetip", "c-bus", "s7"}

// Capture is a pcap file the session has open.
type Capture struct {
	// Name is the file's base name, which is what the sidebar shows.
	Name string
	// Path is the absolute path.
	Path string
	// Packets is how many packets the last analysis of this capture walked, -1 when it has
	// not been analysed yet.
	Packets int
	// Issues is how many findings the last analysis produced.
	Issues int
}

// AnalysisOptions are the analysis settings the sidebar's OPTIONS block shows, the conf
// command edits and a Request is seeded from.
//
// They are READ from the CLI configuration singletons rather than kept alongside them. Those
// singletons are what the cobra layer's analyze flags write to and what conf set edits, so
// keeping a second copy here would give the sidebar and the prompt two different answers to
// the same question.
type AnalysisOptions struct {
	Filter         string
	NoFilter       bool
	OnlyParse      bool
	NoBytesCompare bool
	StartPacket    uint
	PacketLimit    uint
}

// analysisOptions reads the analysis settings out of the CLI configuration singletons.
func analysisOptions() AnalysisOptions {
	analyze := &cliConfig.AnalyzeConfigInstance
	return AnalysisOptions{
		Filter:         analyze.Filter,
		NoFilter:       analyze.NoFilter,
		OnlyParse:      analyze.OnlyParse,
		NoBytesCompare: analyze.NoBytesCompare,
		StartPacket:    analyze.StartPackageNumber,
		PacketLimit:    analyze.PackageNumberLimit,
	}
}

// State is everything a command may read or change.
type State struct {
	// CurrentDir is what ls, cd and the file completions are relative to.
	CurrentDir string
	// Captures are the files the session has open, and Current indexes the selected one, or is
	// -1 when nothing is open.
	Captures []Capture
	Current  int
	// Protocol is the protocol analyses run as.
	Protocol protocol.Protocol
	// HostIP is the address treated as the client. Protocols where a request and a response
	// are encoded differently cannot be parsed without it.
	HostIP string
	// Config is the configuration that survives between sessions.
	Config Config
	// Demo says the session is running against a generated capture.
	Demo bool
	// Debug turns on this tool's own trace logging, which the conf command toggles.
	Debug bool
	// LogLevel is the level the log pane is filtered at.
	LogLevel zerolog.Level

	// DriverManager is the plc4x subsystem the register command registers drivers in. It is
	// created lazily, because a session that only analyses captures never needs one.
	DriverManager plc4go.PlcDriverManager
	// Registered are the drivers registered so far, in registration order.
	Registered []string
}

// NewState builds the session state for a fresh run.
func NewState(currentDir string, config Config) *State {
	state := &State{
		CurrentDir: currentDir,
		Current:    -1,
		Protocol:   protocol.CBus,
		HostIP:     config.HostIp,
		Config:     config,
		LogLevel:   zerolog.InfoLevel,
	}
	if level, err := zerolog.ParseLevel(config.LogLevel); err == nil && config.LogLevel != "" {
		state.LogLevel = level
	}
	return state
}

// CurrentCapture returns the selected capture, and false when nothing is open.
func (s *State) CurrentCapture() (Capture, bool) {
	if s.Current < 0 || s.Current >= len(s.Captures) {
		return Capture{}, false
	}
	return s.Captures[s.Current], true
}

// Open adds a capture to the session and selects it.
//
// A relative path is resolved against the session's current directory, which is what makes
// "open cbus.pcap" work after a cd.
func (s *State) Open(pcapFile string) (Capture, error) {
	if strings.TrimSpace(pcapFile) == "" {
		return Capture{}, errors.New("open needs a file: open <pcapfile>")
	}
	if !filepath.IsAbs(pcapFile) {
		pcapFile = filepath.Join(s.CurrentDir, pcapFile)
	}
	stat, err := os.Stat(pcapFile)
	if err != nil {
		return Capture{}, errors.Wrapf(err, "error opening %s", pcapFile)
	}
	if stat.IsDir() {
		return Capture{}, errors.Errorf("%s is a directory", pcapFile)
	}
	for i, existing := range s.Captures {
		if existing.Path == pcapFile {
			// Already open is not a failure: select it, which is what the user wanted.
			s.Current = i
			return existing, nil
		}
	}
	capture := Capture{Name: stat.Name(), Path: pcapFile, Packets: -1}
	s.Captures = append(s.Captures, capture)
	s.Current = len(s.Captures) - 1
	s.Config.RememberFile(pcapFile)
	return capture, nil
}

// Request builds the analysis request the current selection and options describe.
func (s *State) Request() (Request, error) {
	capture, ok := s.CurrentCapture()
	if !ok {
		return Request{}, errors.New("no capture open: open <pcapfile> first")
	}
	return s.RequestFor(s.Protocol, capture.Path), nil
}

// RequestFor builds the analysis request for an explicit protocol and file.
//
// PacketLimit is deliberately NOT copied from the config singleton when that singleton still
// holds its zero value. The analyzer reads a limit of zero as "stop before the first packet",
// and the ui subcommand never parses the analyze flags that would otherwise set it to
// MaxUint — so the old UI's analyze command walked nothing at all and reported success.
func (s *State) RequestFor(proto protocol.Protocol, pcapFile string) Request {
	options := analysisOptions()
	return Request{
		Protocol:       proto,
		PcapFile:       pcapFile,
		Client:         s.HostIP,
		Filter:         options.Filter,
		NoFilter:       options.NoFilter,
		OnlyParse:      options.OnlyParse,
		NoBytesCompare: options.NoBytesCompare,
		StartPacket:    options.StartPacket,
		PacketLimit:    options.PacketLimit,
	}
}

// RecordAnalysis folds a finished run's totals back into the capture it analysed, so that the
// sidebar can show a packet count next to the file.
func (s *State) RecordAnalysis(result Result) {
	for i, capture := range s.Captures {
		if capture.Path == result.Request.PcapFile {
			s.Captures[i].Packets = result.Counters.Walked
			s.Captures[i].Issues = result.Counters.Issues()
			return
		}
	}
}

// IsRegistered reports whether a driver has been registered in the subsystem.
func (s *State) IsRegistered(driver string) bool {
	return slices.Contains(s.Registered, driver)
}

// RegisterDriver registers a plc4x driver, along with the pcap transport it needs to replay a
// capture through.
func (s *State) RegisterDriver(driver string) error {
	if err := ValidateDriver(driver); err != nil {
		return err
	}
	if s.IsRegistered(driver) {
		return errors.Errorf("%s is already registered", driver)
	}
	if s.DriverManager == nil {
		s.DriverManager = plc4go.NewPlcDriverManager()
	}
	switch driver {
	case "ads":
		drivers.RegisterAdsDriver(s.DriverManager)
	case "bacnetip":
		drivers.RegisterBacnetDriver(s.DriverManager)
	case "c-bus":
		drivers.RegisterCBusDriver(s.DriverManager)
	case "s7":
		drivers.RegisterS7Driver(s.DriverManager)
	default:
		return errors.Errorf("unknown driver %s", driver)
	}
	if transportAware, ok := s.DriverManager.(spi.TransportAware); ok {
		transportAware.RegisterTransport(pcap.NewTransport())
	}
	s.Registered = append(s.Registered, driver)
	return nil
}

// ValidateDriver reports whether a name is one of the registerable drivers. The error names
// them all, because that is what a user needs in order to recover from a typo.
func ValidateDriver(driver string) error {
	if slices.Contains(DriverNames, driver) {
		return nil
	}
	return errors.Errorf("unknown driver %q, known drivers are %s", driver, strings.Join(DriverNames, ", "))
}
