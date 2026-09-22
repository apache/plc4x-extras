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
	"context"
	"io"
	"os"
	"time"

	tea "charm.land/bubbletea/v2"
	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Starting the program.
//
// This is the only place that touches the process: the temporary demo capture, the global
// logger, the terminal. Everything below it is a value the tests construct directly.

// RunOptions configure a session.
type RunOptions struct {
	// PcapFile is the capture named on the command line, if any.
	PcapFile string
	// Protocol is the protocol to analyse it as, empty for the default.
	//
	// Without it the interface starts on C-Bus, so opening a Modbus capture analysed it as
	// C-Bus and filled the screen with parse failures that said nothing about either protocol.
	// The protocol is switchable in the sidebar, but a capture named on the command line should
	// not need a correction before it can be read.
	Protocol string
	// Demo generates a capture and analyses it, so the tool can be demonstrated and debugged
	// with no capture and no device to hand.
	Demo bool
	// Ascii forces the ASCII glyph set, for a terminal that cannot draw the Unicode one.
	Ascii bool
	// Output is where the program draws. Leaving it nil means the real terminal.
	Output io.Writer
	// Clock is the time source, injected by tests.
	Clock func() time.Time
}

// Run starts the terminal UI and blocks until it exits.
//
// It returns errors rather than panicking. The version this replaces panicked from an init
// function when the user's configuration directory could not be created, and panicked again
// from the command layer whenever a config value of a non-string type was set.
func Run(ctx context.Context, options RunOptions) error {
	if ctx == nil {
		ctx = context.Background()
	}

	var demo Demo
	if options.Demo {
		generated, err := NewDemo()
		if err != nil {
			return err
		}
		demo = generated
		// The capture is temporary, so it goes away with the session. A demo that leaves files
		// behind in the user's temp directory is a demo that gets run once.
		defer func() { _ = demo.Cleanup() }()
	}

	currentDir, err := os.Getwd()
	if err != nil {
		currentDir = "."
	}

	config, configErr := loadSessionConfig()
	state := NewState(currentDir, config)
	state.Demo = options.Demo

	if options.Protocol != "" {
		resolved, err := protocol.Resolve(options.Protocol)
		if err != nil {
			return err
		}
		state.Protocol = resolved
	}

	var startupErrors []string
	if configErr != nil {
		startupErrors = append(startupErrors, configErr.Error())
	}

	if options.Demo {
		state.Protocol = demo.Protocol
		state.HostIP = demo.Client
		if _, err := state.Open(demo.Path); err != nil {
			return err
		}
	}
	if options.PcapFile != "" {
		if _, err := state.Open(options.PcapFile); err != nil {
			// Not fatal. The session is still usable, and the message says exactly what failed.
			startupErrors = append(startupErrors, err.Error())
		}
	}
	for _, driver := range state.Config.AutoRegisterDrivers {
		if err := state.RegisterDriver(driver); err != nil {
			startupErrors = append(startupErrors, err.Error())
		}
	}

	themeOptions := tui.OptionsFromEnv(true)
	if options.Ascii {
		themeOptions.ASCII = true
	}

	modelOptions := Options{Theme: themeOptions, State: state, Clock: options.Clock}
	if options.Demo {
		// Demo mode analyses its capture straight away, so that what the user sees when the
		// program starts is the tool doing its job rather than an empty frame.
		request := state.RequestFor(demo.Protocol, demo.Path)
		modelOptions.AutoRun = &request
	}
	model := NewModel(modelOptions)
	for _, message := range startupErrors {
		model.appendLog("startup: " + message)
	}

	// zerolog writes from whichever goroutine is logging, so it goes to a writer that does
	// nothing but split lines and put them on a channel. The model drains that channel with a
	// command; nothing reaches its state from outside the event loop.
	restoreLogger := redirectLogger(model.LogWriter(), state.LogLevel)
	defer restoreLogger()

	programOptions := []tea.ProgramOption{tea.WithContext(ctx)}
	if options.Output != nil {
		programOptions = append(programOptions, tea.WithOutput(options.Output))
	}
	if _, err := tea.NewProgram(model, programOptions...).Run(); err != nil {
		return errors.Wrap(err, "error running the terminal ui")
	}

	if path, err := ConfigPath(); err == nil {
		if err := SaveConfigTo(path, state.Config, time.Now()); err != nil {
			return err
		}
	}
	return nil
}

// LogWriter is an io.Writer whose lines end up in the log pane.
func (m Model) LogWriter() io.Writer {
	return newLineWriter(channelSink(m.logCh))
}

// loadSessionConfig reads the session configuration, tolerating every way that can fail: a
// machine with no configuration directory, and a file that will not parse.
func loadSessionConfig() (Config, error) {
	path, err := ConfigPath()
	if err != nil {
		return NewConfig(), err
	}
	return LoadConfigFrom(path)
}

// redirectLogger points the global logger at the UI and returns a function that puts it back.
//
// Console writing with colour switched off: the log pane applies the UI's own styling, and
// escape sequences arriving from underneath would fight it and corrupt the pane's widths.
func redirectLogger(writer io.Writer, level zerolog.Level) func() {
	previous := log.Logger
	log.Logger = zerolog.New(zerolog.ConsoleWriter{Out: writer, NoColor: true, TimeFormat: "15:04:05"}).
		With().Timestamp().Logger().
		Level(level)
	return func() { log.Logger = previous }
}
