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

package browser

import (
	"context"
	"io"

	tea "charm.land/bubbletea/v2"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/charmbracelet/fang"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"github.com/spf13/cobra"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// The command line.
//
// plc4xbrowser previously had none at all: main called straight into the tview application, so
// there was nowhere to put a flag. It gains a cobra root here, wrapped in fang for the same
// styled help and errors the pcap analyser now uses.

// Settings are the command-line settings.
type Settings struct {
	// Demo runs against the simulated devices instead of real hardware.
	Demo bool
	// ASCII forces the ASCII glyph set for terminals that cannot render box-drawing characters.
	ASCII bool
	// LogLevel overrides the persisted log level.
	LogLevel string
	// Version labels the status bar.
	Version string
}

// Execute runs the browser's command line.
//
// WithoutVersion is deliberate: fang offers a version flag, and adding one is out of scope, so
// it is suppressed rather than acquired as a side effect of adopting fang.
func Execute(version string) error {
	settings := Settings{Version: version}
	root := NewRootCommand(&settings)
	return fang.Execute(context.Background(), root, fang.WithoutVersion())
}

// NewRootCommand builds the cobra root.
func NewRootCommand(settings *Settings) *cobra.Command {
	root := &cobra.Command{
		Use:   "plc4xbrowser",
		Short: "Browse and exercise PLC connections from the terminal",
		Long: "plc4xbrowser connects to PLCs through plc4x and lets you discover devices, browse\n" +
			"their tags, and read, write and subscribe to them from a terminal interface.\n\n" +
			"Run it with --demo to try it against simulated devices, with no hardware attached.",
		Example: "  plc4xbrowser --demo\n" +
			"  plc4xbrowser --log-level debug\n" +
			"  plc4xbrowser --ascii",
		SilenceUsage: true,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return Run(cmd.Context(), *settings)
		},
	}
	root.Flags().BoolVar(&settings.Demo, "demo", false,
		"run against simulated devices, for demos and manual debugging with no hardware attached")
	root.Flags().BoolVar(&settings.ASCII, "ascii", false,
		"force ASCII drawing characters instead of Unicode, for terminals that cannot render them")
	root.Flags().StringVar(&settings.LogLevel, "log-level", "",
		"log level (trace, debug, info, warn, error); defaults to the saved setting")
	return root
}

// Run starts the terminal interface.
func Run(ctx context.Context, settings Settings) error {
	if ctx == nil {
		ctx = context.Background()
	}

	config := NewConfig()
	if path, err := ConfigPath(); err == nil {
		if loaded, loadErr := LoadConfig(path); loadErr == nil {
			config = loaded
		}
	}
	if settings.LogLevel != "" {
		config.LogLevel = settings.LogLevel
	}
	applyLogLevel(config.LogLevel)

	// The redirect has to be in place before anything else is constructed. plc4x copies the
	// global logger into each component as it is built, so a component built beforehand keeps
	// writing to the terminal for its whole life however the global logger is changed later --
	// and the interface owns that terminal. The channel outlives the redirect and is handed to
	// the model below, so the registrations logged here still reach the pane.
	logCh := make(chan string, logChannelDepth)
	restoreLogger := redirectGlobalLogger(tui.NewLineWriter(tui.ChannelSink(logCh)), zerolog.GlobalLevel())
	defer restoreLogger()

	session, frames, demo, err := newSession(ctx, settings, &config)
	if err != nil {
		return err
	}

	model := NewModel(Options{
		Session:    session,
		Config:     &config,
		Demo:       demo,
		Version:    settings.Version,
		ForceASCII: settings.ASCII,
		Frames:     frames,
		LogCh:      logCh,
	})
	if demo {
		model.AppendLog("demo mode: simulated devices, no hardware attached")
		model.AppendLog("connected " + plcsession.DemoDeviceOne + " — try: browse-direct " + plcsession.DemoDeviceOne)
	}

	program := tea.NewProgram(model, tea.WithContext(ctx))
	_, err = program.Run()
	return err
}

// newSession builds the session the interface runs on.
//
// Demo mode swaps this one value and nothing else: the model, the commands and the tests all
// run against the same seam, so demo mode cannot drift away from the real thing.
func newSession(ctx context.Context, settings Settings, config *Config) (plcsession.Session, *plcsession.FrameLog, bool, error) {
	if settings.Demo {
		session := plcsession.NewDemo(plcsession.DemoOptions{})
		if _, err := session.RegisterDriver(plcsession.DemoProtocol); err != nil {
			return nil, nil, false, errors.Wrap(err, "error preparing the demo session")
		}
		// Open a connection at startup so the tool has something to show immediately; a demo
		// that opens on an empty screen demonstrates nothing.
		if _, err := session.Connect(ctx, plcsession.DemoDeviceOne); err != nil {
			return nil, nil, false, errors.Wrap(err, "error opening the demo connection")
		}
		// No frame log: a simulated device puts nothing on a wire, and inventing bytes for the
		// byte view would be worse than the view saying there are none.
		return session, nil, true, nil
	}

	frames := plcsession.NewFrameLog(0)
	session := plcsession.NewLive(plcsession.LiveOptions{Frames: frames})
	for _, protocol := range config.AutoRegisterDrivers {
		if _, err := session.RegisterDriver(protocol); err != nil {
			log.Warn().Err(err).Str("protocol", protocol).Msg("Could not auto-register driver")
		}
	}
	return session, frames, false, nil
}

// redirectGlobalLogger points zerolog's global logger at writer and returns a function that
// puts the previous one back.
//
// The drivers log through the global logger, and while the interface is running the terminal
// belongs to it: a line written straight to stdout lands on top of the prompt and the panes.
// Setting the level is not enough, because a level still writes somewhere.
//
// Colour is off and the timestamp is dropped, because the pane renders both itself. The level
// is kept, so a line arrives carrying its own marker.
func redirectGlobalLogger(writer io.Writer, level zerolog.Level) func() {
	previous := log.Logger
	log.Logger = zerolog.New(zerolog.ConsoleWriter{
		Out:          writer,
		NoColor:      true,
		PartsExclude: []string{zerolog.TimestampFieldName},
	}).Level(level)
	return func() { log.Logger = previous }
}

// applyLogLevel sets the global log level, falling back to info rather than exiting.
//
// The pcap analyser's equivalent called log.Fatal on an unparseable level, which kills the
// process over a typo in a configuration file. A bad level here is worth a warning, not a
// refusal to start.
func applyLogLevel(level string) {
	if level == "" {
		zerolog.SetGlobalLevel(zerolog.InfoLevel)
		return
	}
	parsed, err := zerolog.ParseLevel(level)
	if err != nil {
		log.Warn().Str("level", level).Msg("Unknown log level, using info")
		parsed = zerolog.InfoLevel
	}
	zerolog.SetGlobalLevel(parsed)
}
