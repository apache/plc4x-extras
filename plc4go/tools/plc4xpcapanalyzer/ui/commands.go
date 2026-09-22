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
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strconv"
	"strings"

	plc4xconfig "github.com/apache/plc4x/plc4go/pkg/api/config"
	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/rs/zerolog"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// The command layer.
//
// Every command is a function of the session state that RETURNS what it did. Nothing here
// writes into a widget, formats an escape code or knows that a terminal exists — which is the
// whole difference from the version this replaces, where each action printed markup straight
// into a tview.TextView through a package-level io.Writer, and so could be neither tested nor
// re-rendered when the terminal resized.
//
// Commands run on the Bubble Tea event loop, because they mutate State and State belongs to
// the model. The two commands that can take a long time — analyze and extract — do not do the
// work themselves: they return a Request in their Outcome and the model starts it as a
// tea.Cmd.

// Outcome is what running a command produced.
//
// It is a record of effects rather than a set of callbacks so that a test can assert on it
// directly, and so that the model stays the only place that decides what an effect looks like
// on screen.
type Outcome struct {
	// Lines is the text the command produced, for the log pane.
	Lines []string
	// Err is set when the command failed. A failed command still produces its Lines.
	Err error

	// Quit asks the program to exit.
	Quit bool
	// Abort asks for the running analysis to be cancelled.
	Abort bool
	// ClearPackets, ClearLog and ClearTranscript ask for the corresponding pane to be emptied.
	ClearPackets    bool
	ClearLog        bool
	ClearTranscript bool
	// Analysis and Extraction ask for a long-running run to be started.
	Analysis   *Request
	Extraction *Request
}

// Failed reports whether the command failed.
func (o Outcome) Failed() bool { return o.Err != nil }

// say builds an outcome carrying text.
func say(lines ...string) Outcome { return Outcome{Lines: lines} }

// fail builds a failed outcome.
func fail(err error) Outcome { return Outcome{Err: err} }

// failf builds a failed outcome from a format string.
func failf(format string, args ...any) Outcome { return Outcome{Err: errors.Errorf(format, args...)} }

// Command is one node of the command tree. A node with a Run is executable; a node with Sub
// dispatches to its children; a node may have both, as host and conf do not but clear does.
type Command struct {
	Name        string
	Description string
	// Run performs the command. arg is everything after the command's own name.
	Run func(ctx context.Context, state *State, arg string) Outcome
	// Sub are the subcommands.
	Sub []Command
	// Suggest offers completions for the argument, given what has been typed of it so far.
	// The returned values are argument fragments; the caller prefixes the command path.
	Suggest func(state *State, partial string) []string
}

// child finds the subcommand with this exact name.
func (c Command) child(name string) (Command, bool) {
	for _, sub := range c.Sub {
		if sub.Name == name {
			return sub, true
		}
	}
	return Command{}, false
}

// resolve walks the tree for a command line and returns the node to run together with its
// argument. A word is only treated as a subcommand name once it is finished — followed by a
// space or by more words — so that completing a half-typed name still sees the partial word.
func (c Command) resolve(line string) (Command, string, bool) {
	node := c
	rest := line
	matchedAny := false
	for {
		trimmed := strings.TrimLeft(rest, " ")
		if trimmed == "" {
			break
		}
		name, remainder, _ := strings.Cut(trimmed, " ")
		child, ok := node.child(name)
		if !ok {
			break
		}
		node = child
		rest = remainder
		matchedAny = true
	}
	if !matchedAny {
		return Command{}, "", false
	}
	return node, strings.TrimSpace(rest), true
}

// Execute runs a command line against the session state.
func (c Command) Execute(ctx context.Context, state *State, line string) Outcome {
	line = strings.TrimSpace(line)
	if line == "" {
		return Outcome{}
	}
	node, arg, ok := c.resolve(line)
	if !ok {
		word, _, _ := strings.Cut(line, " ")
		return failf("unknown command %q, try help", word)
	}
	if node.Run == nil {
		return failf("%s needs one of: %s", node.Name, strings.Join(node.childNames(), ", "))
	}
	return node.Run(ctx, state, arg)
}

// childNames lists the subcommand names, for an error message.
func (c Command) childNames() []string {
	names := make([]string, 0, len(c.Sub))
	for _, sub := range c.Sub {
		names = append(names, sub.Name)
	}
	return names
}

// Completions returns whole replacement lines for what has been typed so far.
//
// Whole lines, not fragments, because that is what bubbles/textinput matches: it tests each
// candidate against the ENTIRE field value as a case-insensitive prefix, so returning "c-bus"
// for the line "analyze c-" would match nothing.
//
// An empty line returns nothing. Offering all twenty-odd commands the moment the prompt is
// focused fills the bottom of the screen with a menu the user did not ask for.
func (c Command) Completions(state *State, line string) []string {
	if strings.TrimSpace(line) == "" {
		return nil
	}

	node := c
	var consumed strings.Builder
	rest := line
	for {
		trimmed := strings.TrimLeft(rest, " ")
		if trimmed == "" {
			break
		}
		name, remainder, hasMore := strings.Cut(trimmed, " ")
		child, ok := node.child(name)
		if !ok {
			break
		}
		// The last word is still being typed unless something follows it. Consuming it would
		// complete against the wrong node: "log" would offer log's subcommands rather than
		// finishing the word "log" itself.
		if !hasMore {
			break
		}
		node = child
		consumed.WriteString(name)
		consumed.WriteString(" ")
		rest = remainder
	}
	prefix := consumed.String()
	partial := strings.TrimLeft(rest, " ")

	var candidates []string
	firstWord, _, _ := strings.Cut(partial, " ")
	for _, sub := range node.Sub {
		if strings.HasPrefix(sub.Name, firstWord) && !strings.Contains(partial, " ") {
			candidates = append(candidates, prefix+sub.Name)
		}
	}
	if node.Suggest != nil {
		for _, suggestion := range node.Suggest(state, partial) {
			candidates = append(candidates, prefix+suggestion)
		}
	}
	return candidates
}

// Walk visits every command in the tree, depth first, carrying the indentation depth.
func (c Command) Walk(depth int, visit func(depth int, command Command)) {
	visit(depth, c)
	for _, sub := range c.Sub {
		sub.Walk(depth+1, visit)
	}
}

// Root builds the command tree.
//
// It is a function rather than a package variable because part of the tree is derived by
// reflection from the CLI configuration singletons, and a package variable would freeze that
// at import time — which is also what made the old tree impossible to exercise from a test.
func Root() Command {
	root := Command{
		Name:        "",
		Description: "plc4xpcapanalyzer commands",
		Sub: []Command{
			commandLs(),
			commandCd(),
			commandPwd(),
			commandOpen(),
			commandAnalyze(),
			commandExtract(),
			commandHost(),
			commandRegister(),
			commandUnregister(),
			commandQuit(),
			commandLog(),
			commandConf(),
			commandPlc4xConf(),
			commandHistory(),
			commandClear(),
			commandAbort(),
		},
	}
	// help closes over the finished tree, so it has to be appended after it is built.
	root.Sub = append(root.Sub, Command{
		Name:        "help",
		Description: "prints out this help",
		Run: func(_ context.Context, _ *State, _ string) Outcome {
			lines := []string{"Available commands"}
			root.Walk(0, func(depth int, command Command) {
				if command.Name == "" {
					return
				}
				description := command.Description
				if description == "" {
					description = command.Name + "s"
				}
				lines = append(lines, strings.Repeat("  ", depth-1)+"  "+command.Name+": "+description)
			})
			return say(lines...)
		},
	})
	return root
}

func commandLs() Command {
	return Command{
		Name:        "ls",
		Description: "list directories",
		Run: func(_ context.Context, state *State, dir string) Outcome {
			if dir == "" {
				dir = state.CurrentDir
			} else if !filepath.IsAbs(dir) {
				dir = filepath.Join(state.CurrentDir, dir)
			}
			entries, err := os.ReadDir(dir)
			if err != nil {
				return fail(errors.Wrapf(err, "error listing %s", dir))
			}
			lines := []string{"contents of " + dir}
			for _, entry := range entries {
				name := entry.Name()
				if entry.IsDir() {
					name += string(os.PathSeparator)
				}
				lines = append(lines, "  "+name)
			}
			if len(entries) == 0 {
				lines = append(lines, "  (empty)")
			}
			return say(lines...)
		},
		Suggest: suggestDirectories,
	}
}

func commandCd() Command {
	return Command{
		Name:        "cd",
		Description: "changes directory",
		Run: func(_ context.Context, state *State, newDir string) Outcome {
			var proposed string
			switch {
			case newDir == "":
				home, err := os.UserHomeDir()
				if err != nil {
					return fail(errors.Wrap(err, "error resolving the home directory"))
				}
				proposed = home
			case filepath.IsAbs(newDir):
				proposed = newDir
			default:
				proposed = filepath.Join(state.CurrentDir, newDir)
			}
			stat, err := os.Stat(proposed)
			if err != nil {
				return fail(errors.Wrapf(err, "error changing to %s", proposed))
			}
			if !stat.IsDir() {
				return failf("%s is not a directory", newDir)
			}
			state.CurrentDir = filepath.Clean(proposed)
			return say("current directory: " + state.CurrentDir)
		},
		Suggest: suggestDirectories,
	}
}

func commandPwd() Command {
	return Command{
		Name:        "pwd",
		Description: "shows current directory",
		Run: func(_ context.Context, state *State, _ string) Outcome {
			return say("current directory: " + state.CurrentDir)
		},
	}
}

func commandOpen() Command {
	return Command{
		Name:        "open",
		Description: "open a pcap file",
		Run: func(_ context.Context, state *State, pcapFile string) Outcome {
			capture, err := state.Open(pcapFile)
			if err != nil {
				return fail(err)
			}
			return say("opened " + capture.Path)
		},
		Suggest: suggestCaptureFiles,
	}
}

func commandAnalyze() Command {
	return Command{
		Name:        "analyze",
		Description: "analyzes a pcap file using a driver",
		Run: func(_ context.Context, state *State, arg string) Outcome {
			request, err := analysisRequest(state, arg)
			if err != nil {
				return fail(err)
			}
			return Outcome{Analysis: &request}
		},
		Suggest: suggestProtocolAndFile,
	}
}

func commandExtract() Command {
	return Command{
		Name:        "extract",
		Description: "extract a pcap file using a driver",
		Run: func(_ context.Context, state *State, arg string) Outcome {
			request, err := analysisRequest(state, arg)
			if err != nil {
				return fail(err)
			}
			return Outcome{Extraction: &request}
		},
		Suggest: suggestProtocolAndFile,
	}
}

// analysisRequest parses "<protocol> <pcapfile>" into a request, falling back to the session's
// current protocol and capture when either half is omitted.
//
// The fallback is what makes the sidebar and the "a" shortcut work: they run "analyze" with
// nothing after it, and the user has already said which capture is selected.
func analysisRequest(state *State, arg string) (Request, error) {
	arg = strings.TrimSpace(arg)
	if arg == "" {
		return state.Request()
	}
	name, file, hasFile := strings.Cut(arg, " ")
	proto, err := protocol.Resolve(name)
	if err != nil {
		return Request{}, err
	}
	if !hasFile || strings.TrimSpace(file) == "" {
		capture, ok := state.CurrentCapture()
		if !ok {
			return Request{}, errors.Errorf("analyze %s needs a file: analyze %s <pcapfile>", name, name)
		}
		return state.RequestFor(proto, capture.Path), nil
	}
	file = strings.TrimSpace(file)
	if !filepath.IsAbs(file) {
		file = filepath.Join(state.CurrentDir, file)
	}
	if _, err := os.Stat(file); err != nil {
		return Request{}, errors.Wrapf(err, "error reading %s", file)
	}
	return state.RequestFor(proto, file), nil
}

func commandHost() Command {
	return Command{
		Name:        "host",
		Description: "the host assumed to be the sender (matters for directional protocols)",
		Sub: []Command{
			{
				Name:        "set",
				Description: "sets the client address",
				Run: func(_ context.Context, state *State, host string) Outcome {
					host = strings.TrimSpace(host)
					if host == "" {
						return failf("host set needs an address: host set <ip>")
					}
					state.HostIP = host
					state.Config.HostIp = host
					return say("client address: " + host)
				},
			},
			{
				Name:        "get",
				Description: "shows the client address",
				Run: func(_ context.Context, state *State, _ string) Outcome {
					if state.HostIP == "" {
						return say("no client address set: host set <ip>")
					}
					return say("client address: " + state.HostIP)
				},
			},
		},
	}
}

func commandRegister() Command {
	return Command{
		Name:        "register",
		Description: "register a driver in the subsystem",
		Run: func(_ context.Context, state *State, driver string) Outcome {
			driver = strings.TrimSpace(driver)
			if err := state.RegisterDriver(driver); err != nil {
				return fail(err)
			}
			return say("registered driver " + driver)
		},
		Suggest: suggestDrivers,
	}
}

// commandUnregister is new. The sidebar lists the drivers and lets the cursor act on a row, so
// there has to be something for the row of an already-registered driver to do; leaving it
// inert would be exactly the dead affordance this port set out to remove.
func commandUnregister() Command {
	return Command{
		Name:        "unregister",
		Description: "forget a driver registered in the subsystem",
		Run: func(_ context.Context, state *State, driver string) Outcome {
			driver = strings.TrimSpace(driver)
			if err := ValidateDriver(driver); err != nil {
				return fail(err)
			}
			if !state.IsRegistered(driver) {
				return failf("%s is not registered", driver)
			}
			remaining := make([]string, 0, len(state.Registered))
			for _, registered := range state.Registered {
				if registered != driver {
					remaining = append(remaining, registered)
				}
			}
			// plc4x offers no way to remove a driver from a manager, so the manager is rebuilt
			// from what is left. Saying so here is cheaper than surprising the next reader.
			state.Registered, state.DriverManager = nil, nil
			for _, registered := range remaining {
				_ = state.RegisterDriver(registered)
			}
			return say("unregistered driver " + driver)
		},
		Suggest: suggestDrivers,
	}
}

func commandQuit() Command {
	return Command{
		Name:        "quit",
		Description: "quits the application",
		Run: func(_ context.Context, _ *State, _ string) Outcome {
			return Outcome{Quit: true}
		},
	}
}

func commandLog() Command {
	return Command{
		Name:        "log",
		Description: "log related operations",
		Sub: []Command{
			{
				Name:        "get",
				Description: "get the log level",
				Run: func(_ context.Context, state *State, _ string) Outcome {
					return say("current log level " + state.LogLevel.String())
				},
			},
			{
				Name:        "set",
				Description: "sets the log level",
				Run: func(_ context.Context, state *State, level string) Outcome {
					parsed, err := zerolog.ParseLevel(strings.TrimSpace(level))
					if err != nil {
						return fail(errors.Wrapf(err, "error setting log level"))
					}
					state.LogLevel = parsed
					state.Config.LogLevel = parsed.String()
					return say("log level " + parsed.String())
				},
				Suggest: func(_ *State, partial string) []string {
					var levels []string
					for _, level := range logLevels {
						if strings.HasPrefix(level, partial) {
							levels = append(levels, level)
						}
					}
					return levels
				},
			},
		},
	}
}

// logLevels are the levels log set accepts.
var logLevels = []string{
	zerolog.LevelTraceValue,
	zerolog.LevelDebugValue,
	zerolog.LevelInfoValue,
	zerolog.LevelWarnValue,
	zerolog.LevelErrorValue,
	zerolog.LevelFatalValue,
	zerolog.LevelPanicValue,
}

func commandConf() Command {
	return Command{
		Name:        "conf",
		Description: "various settings for plc4xpcapanalyzer",
		Sub: []Command{
			{
				Name:        "list",
				Description: "list config values with their current settings",
				Run: func(_ context.Context, _ *State, _ string) Outcome {
					return say(describeConfigs(CliConfigInstances())...)
				},
			},
			commandConfSet(),
			{
				Name:        "plc4xpcapanalyzer-debug",
				Description: "prints out debug information of the pcap analyzer itself",
				Sub: []Command{
					{
						Name:        "on",
						Description: "debug on",
						Run: func(_ context.Context, state *State, _ string) Outcome {
							state.Debug = true
							return say("plc4xpcapanalyzer debug on")
						},
					},
					{
						Name:        "off",
						Description: "debug off",
						Run: func(_ context.Context, state *State, _ string) Outcome {
							state.Debug = false
							return say("plc4xpcapanalyzer debug off")
						},
					},
				},
			},
			commandAutoRegister(),
		},
	}
}

func commandAutoRegister() Command {
	return Command{
		Name:        "auto-register",
		Description: "autoregister drivers at startup",
		Sub: []Command{
			{
				Name:        "list",
				Description: "lists the drivers registered at startup",
				Run: func(_ context.Context, state *State, _ string) Outcome {
					if len(state.Config.AutoRegisterDrivers) == 0 {
						return say("no drivers auto-registered: conf auto-register enable <driver>")
					}
					lines := []string{"auto-register enabled drivers:"}
					for _, driver := range state.Config.AutoRegisterDrivers {
						lines = append(lines, "  "+driver)
					}
					return say(lines...)
				},
			},
			{
				Name:        "enable",
				Description: "auto-registers a driver at startup",
				Run: func(_ context.Context, state *State, driver string) Outcome {
					driver = strings.TrimSpace(driver)
					if err := ValidateDriver(driver); err != nil {
						return fail(err)
					}
					if err := state.Config.EnableAutoRegister(driver); err != nil {
						return fail(err)
					}
					return say("auto-register enabled for " + driver)
				},
				Suggest: suggestDrivers,
			},
			{
				Name:        "disable",
				Description: "stops auto-registering a driver at startup",
				Run: func(_ context.Context, state *State, driver string) Outcome {
					driver = strings.TrimSpace(driver)
					if err := ValidateDriver(driver); err != nil {
						return fail(err)
					}
					if err := state.Config.DisableAutoRegister(driver); err != nil {
						return fail(err)
					}
					return say("auto-register disabled for " + driver)
				},
				Suggest: suggestDrivers,
			},
		},
	}
}

func commandPlc4xConf() Command {
	toggle := func(name, description string, set func(bool)) Command {
		return Command{
			Name:        name,
			Description: description,
			Sub: []Command{
				{
					Name:        "on",
					Description: "trace on",
					Run: func(_ context.Context, _ *State, _ string) Outcome {
						set(true)
						return say(name + " on")
					},
				},
				{
					Name:        "off",
					Description: "trace off",
					Run: func(_ context.Context, _ *State, _ string) Outcome {
						set(false)
						return say(name + " off")
					},
				},
			},
		}
	}
	return Command{
		Name:        "plc4x-conf",
		Description: "plc4x related settings",
		Sub: []Command{
			toggle("TraceTransactionManagerWorkers", "print information about transaction manager workers",
				func(on bool) { plc4xconfig.TraceTransactionManagerWorkers = on }),
			toggle("TraceTransactionManagerTransactions", "print information about transaction manager transactions",
				func(on bool) { plc4xconfig.TraceTransactionManagerTransactions = on }),
			toggle("TraceDefaultMessageCodecWorker", "print information about message codec workers",
				func(on bool) { plc4xconfig.TraceDefaultMessageCodecWorker = on }),
		},
	}
}

func commandHistory() Command {
	return Command{
		Name:        "history",
		Description: "outputs the last commands",
		Run: func(_ context.Context, state *State, _ string) Outcome {
			if len(state.Config.History.Last10Commands) == 0 {
				return say("no commands remembered yet")
			}
			lines := []string{"last commands"}
			for i, command := range state.Config.History.Last10Commands {
				lines = append(lines, "  "+strconv.Itoa(i)+": "+command)
			}
			return say(lines...)
		},
	}
}

func commandClear() Command {
	return Command{
		Name:        "clear",
		Description: "clear all outputs",
		Run: func(_ context.Context, _ *State, _ string) Outcome {
			return Outcome{ClearPackets: true, ClearLog: true, ClearTranscript: true}
		},
		Sub: []Command{
			{
				Name:        "message",
				Description: "clears the analysed packets",
				Run: func(_ context.Context, _ *State, _ string) Outcome {
					return Outcome{ClearPackets: true}
				},
			},
			{
				Name:        "console",
				Description: "clears the log",
				Run: func(_ context.Context, _ *State, _ string) Outcome {
					return Outcome{ClearLog: true}
				},
			},
			{
				Name:        "command",
				Description: "clears the command transcript",
				Run: func(_ context.Context, _ *State, _ string) Outcome {
					return Outcome{ClearTranscript: true}
				},
			},
		},
	}
}

func commandAbort() Command {
	return Command{
		Name:        "abort",
		Description: "abort currently running jobs",
		Run: func(_ context.Context, _ *State, _ string) Outcome {
			return Outcome{Abort: true}
		},
	}
}

// commandConfSet builds "conf set <Config> <Field> <value>" by reflecting over the CLI
// configuration singletons, the way the previous version did.
//
// Unlike the previous version it does not call reflect.Value.SetString unconditionally. Every
// non-string field — and most of them are bool or uint — made that call panic, and the panic
// was swallowed by a recover in the command dispatcher and reported as "panic occurred". Each
// kind is now converted properly and an unsupported one is a plain error.
func commandConfSet() Command {
	set := Command{
		Name:        "set",
		Description: "sets a config value",
	}
	configs := reflect.ValueOf(CliConfigInstances())
	for i := range configs.NumField() {
		configValue := configs.Field(i)
		configType := configs.Type().Field(i)
		fields := elemOf(reflect.ValueOf(configValue.Interface()))
		if !fields.IsValid() || fields.Kind() != reflect.Struct {
			continue
		}
		group := Command{
			Name:        configType.Name,
			Description: "setting for " + configType.Name,
		}
		for j := range fields.NumField() {
			field := fields.Field(j)
			fieldType := fields.Type().Field(j)
			if fieldType.Tag.Get("json") == "-" || !field.CanSet() {
				continue
			}
			group.Sub = append(group.Sub, Command{
				Name:        fieldType.Name,
				Description: "sets " + configType.Name + "." + fieldType.Name,
				Run: func(_ context.Context, _ *State, argument string) Outcome {
					if err := assign(field, strings.TrimSpace(argument)); err != nil {
						return fail(errors.Wrapf(err, "error setting %s.%s", configType.Name, fieldType.Name))
					}
					return say(fmt.Sprintf("%s.%s = %v", configType.Name, fieldType.Name, field.Interface()))
				},
			})
		}
		if len(group.Sub) > 0 {
			set.Sub = append(set.Sub, group)
		}
	}
	return set
}

// elemOf dereferences a pointer value, leaving anything else alone.
func elemOf(value reflect.Value) reflect.Value {
	if value.Kind() == reflect.Pointer {
		return value.Elem()
	}
	return value
}

// assign writes a textual value into a config field, converting it to the field's type.
func assign(field reflect.Value, value string) error {
	switch field.Kind() {
	case reflect.String:
		field.SetString(value)
		return nil
	case reflect.Bool:
		parsed, err := strconv.ParseBool(value)
		if err != nil {
			return errors.Errorf("%q is not a boolean, use true or false", value)
		}
		field.SetBool(parsed)
		return nil
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		parsed, err := strconv.ParseInt(value, 10, 64)
		if err != nil {
			return errors.Errorf("%q is not a number", value)
		}
		field.SetInt(parsed)
		return nil
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		parsed, err := strconv.ParseUint(value, 10, 64)
		if err != nil {
			return errors.Errorf("%q is not a positive number", value)
		}
		field.SetUint(parsed)
		return nil
	default:
		return errors.Errorf("fields of type %s cannot be set from the prompt", field.Type())
	}
}

// describeConfigs renders every configuration value, for conf list.
func describeConfigs(configs AllCliConfigs) []string {
	value := reflect.ValueOf(configs)
	var lines []string
	for i := range value.NumField() {
		lines = append(lines, value.Type().Field(i).Name+":")
		fields := elemOf(reflect.ValueOf(value.Field(i).Interface()))
		if !fields.IsValid() || fields.Kind() != reflect.Struct {
			continue
		}
		for j := range fields.NumField() {
			fieldType := fields.Type().Field(j)
			if fieldType.Tag.Get("json") == "-" {
				continue
			}
			lines = append(lines, fmt.Sprintf("  %s: %s = %v", fieldType.Name, fieldType.Type, fields.Field(j).Interface()))
		}
	}
	return lines
}

// suggestDrivers completes a driver name.
func suggestDrivers(_ *State, partial string) []string {
	var out []string
	for _, driver := range DriverNames {
		if strings.HasPrefix(driver, partial) {
			out = append(out, driver)
		}
	}
	return out
}

// suggestDirectories completes a directory, relative to the session's current directory unless
// the partial is already absolute.
func suggestDirectories(state *State, partial string) []string {
	base, prefix := completionBase(state, partial)
	entries, err := os.ReadDir(base)
	if err != nil {
		return nil
	}
	var out []string
	for _, entry := range entries {
		if !entry.IsDir() || !strings.HasPrefix(entry.Name(), prefix) {
			continue
		}
		out = append(out, completionJoin(partial, prefix, entry.Name()))
	}
	sort.Strings(out)
	return out
}

// captureExtensions are the file types open completes.
var captureExtensions = []string{".pcap", ".pcapng", ".cap"}

// suggestCaptureFiles completes a capture file, offering the recently opened ones first
// because those are what a returning session usually wants.
func suggestCaptureFiles(state *State, partial string) []string {
	var out []string
	for _, recent := range state.Config.History.Last10Files {
		if strings.HasPrefix(recent, partial) {
			out = append(out, recent)
		}
	}
	base, prefix := completionBase(state, partial)
	entries, err := os.ReadDir(base)
	if err != nil {
		return out
	}
	for _, entry := range entries {
		name := entry.Name()
		if !strings.HasPrefix(name, prefix) {
			continue
		}
		if entry.IsDir() {
			out = append(out, completionJoin(partial, prefix, name))
			continue
		}
		for _, extension := range captureExtensions {
			if strings.HasSuffix(strings.ToLower(name), extension) {
				out = append(out, completionJoin(partial, prefix, name))
				break
			}
		}
	}
	return out
}

// suggestProtocolAndFile completes the "<protocol> <pcapfile>" argument analyze and extract
// take, offering the open captures once a protocol has been chosen.
func suggestProtocolAndFile(state *State, partial string) []string {
	name, file, hasFile := strings.Cut(partial, " ")
	if !hasFile {
		var out []string
		for _, proto := range protocol.All() {
			if strings.HasPrefix(proto.Name, name) {
				out = append(out, proto.Name)
			}
		}
		return out
	}
	proto, err := protocol.Resolve(name)
	if err != nil {
		return nil
	}
	var out []string
	for _, capture := range state.Captures {
		if strings.HasPrefix(capture.Path, file) || strings.HasPrefix(capture.Name, file) {
			out = append(out, proto.Name+" "+capture.Path)
		}
	}
	for _, candidate := range suggestCaptureFiles(state, file) {
		out = append(out, proto.Name+" "+candidate)
	}
	return out
}

// completionBase splits a partial path into the directory to read and the name prefix to
// match inside it.
func completionBase(state *State, partial string) (base, prefix string) {
	if partial == "" {
		return state.CurrentDir, ""
	}
	dir, name := filepath.Split(partial)
	switch {
	case dir == "":
		return state.CurrentDir, name
	case filepath.IsAbs(dir):
		return dir, name
	default:
		return filepath.Join(state.CurrentDir, dir), name
	}
}

// completionJoin rebuilds a completion so that it replaces exactly what the user typed,
// keeping any directory part they had already entered.
func completionJoin(partial, prefix, name string) string {
	return strings.TrimSuffix(partial, prefix) + name
}
