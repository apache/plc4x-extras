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
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/rs/zerolog"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// The command tree.
//
// Every command the previous implementation advertised is here, with the same name and the
// same meaning, because a user's muscle memory and their saved command history both survive
// the port. Four of them - read, write, browse and subscribe - are new work rather than a
// port: they existed only as an error message, returning "mode switch not yet implemented",
// so the tool's headline capability had never actually shipped.

// NewCommands builds the command tree.
func NewCommands() *Registry {
	root := &Command{Name: rootName}
	root.Sub = []*Command{
		discoverCommand(),
		connectCommand(),
		disconnectCommand(),
		registerCommand(),

		composeCommand(OperationRead, "read", "Compose a read request against a connection"),
		composeCommand(OperationWrite, "write", "Compose a write request against a connection"),
		composeCommand(OperationBrowse, "browse", "Compose a browse request against a connection"),
		composeCommand(OperationSubscribe, "subscribe", "Compose a subscription against a connection"),

		readDirectCommand(),
		writeDirectCommand(),
		browseDirectCommand(),
		subscribeDirectCommand(),

		logCommand(),
		historyCommand(),
		clearCommand(),
		helpCommand(),
		quitCommand(),
	}
	return NewRegistry(root)
}

// rootName labels the synthetic root of the tree.
const rootName = "plc4xbrowser"

// discoverCommand searches for devices speaking a protocol.
func discoverCommand() *Command {
	return &Command{
		Name:        "discover",
		Description: "Discover devices speaking a protocol",
		Args:        "[protocol]",
		Suggest: func(env *Env, partial string) []string {
			return prefixed(env.Session.Protocols(), partial)
		},
		Run: func(ctx context.Context, env *Env, args string) (Result, error) {
			protocol := strings.TrimSpace(args)
			if protocol == "" {
				return Result{}, errors.Errorf("discover needs a protocol, one of %s",
					strings.Join(env.Session.Protocols(), ", "))
			}
			var result Result
			err := env.Session.Discover(ctx, protocol, func(item plcsession.DiscoveryItem) {
				result.Lines = append(result.Lines,
					fmt.Sprintf("found %s (%s)", item.ConnectionString, item.Name))
				result.Events = append(result.Events, plcsession.Event{
					Kind:       plcsession.EventDiscover,
					Connection: item.ConnectionString,
					Received:   env.Now(),
					Summary:    item.Name,
				})
			})
			if err != nil {
				return Result{}, err
			}
			if len(result.Events) == 0 {
				result.Lines = append(result.Lines, "no devices found")
			}
			return result, nil
		},
	}
}

// connectCommand opens a connection.
func connectCommand() *Command {
	return &Command{
		Name:        "connect",
		Description: "Connect to a device",
		Args:        "[connection string]",
		Suggest:     suggestConnectTargets,
		Run: func(ctx context.Context, env *Env, args string) (Result, error) {
			connectionString := strings.TrimSpace(args)
			if connectionString == "" {
				return Result{}, errors.New("connect needs a connection string")
			}
			info, err := env.Session.Connect(ctx, connectionString)
			if err != nil {
				return Result{}, err
			}
			// Remember the host so the next session can suggest it, as the old tool did.
			if parsed, parseErr := url.Parse(connectionString); parseErr == nil && parsed.Host != "" {
				env.Config.AddHost(parsed.Host)
			}
			return Result{
				Lines:              []string{"connected " + info.ID},
				ConnectionsChanged: true,
			}, nil
		},
	}
}

// disconnectCommand closes a connection.
func disconnectCommand() *Command {
	return &Command{
		Name:        "disconnect",
		Description: "Disconnect a connection",
		Args:        "[connection]",
		Suggest:     suggestConnections,
		Run: func(_ context.Context, env *Env, args string) (Result, error) {
			id := strings.TrimSpace(args)
			if id == "" {
				return Result{}, errors.New("disconnect needs a connection")
			}
			if err := env.Session.Disconnect(id); err != nil {
				return Result{}, err
			}
			return Result{
				Lines:              []string{"disconnected " + id},
				ConnectionsChanged: true,
			}, nil
		},
	}
}

// registerCommand registers a protocol driver.
func registerCommand() *Command {
	return &Command{
		Name:        "register",
		Description: "Register a protocol driver",
		Args:        "[protocol]",
		Suggest: func(env *Env, partial string) []string {
			return prefixed(env.Session.Protocols(), partial)
		},
		Run: func(_ context.Context, env *Env, args string) (Result, error) {
			protocol := strings.TrimSpace(args)
			if protocol == "" {
				return Result{}, errors.Errorf("register needs a protocol, one of %s",
					strings.Join(env.Session.Protocols(), ", "))
			}
			info, err := env.Session.RegisterDriver(protocol)
			if err != nil {
				return Result{}, err
			}
			return Result{
				Lines:          []string{"registered " + info.Code + " (" + info.Name + ")"},
				DriversChanged: true,
			}, nil
		},
	}
}

// composeCommand opens the request composer for one operation.
//
// These four are the commands the previous implementation never finished. Rather than
// executing immediately, they hand the model a ComposeSpec: a read or a write is a multi-tag
// request, and typing several tag/address pairs on one line is exactly the interface the
// -direct variants already provide badly.
func composeCommand(operation Operation, name, description string) *Command {
	return &Command{
		Name:        name,
		Description: description,
		Args:        "[connection] [tag address...]",
		Suggest:     suggestConnectionThenTag,
		Run: func(_ context.Context, env *Env, args string) (Result, error) {
			connection, rest := splitFirst(strings.TrimSpace(args))
			if connection != "" {
				if err := requireConnected(env, connection); err != nil {
					return Result{}, err
				}
			}
			spec := &ComposeSpec{Operation: operation, Connection: connection}
			if operation == OperationBrowse {
				spec.Query = strings.TrimSpace(rest)
			} else {
				for i, address := range strings.Fields(rest) {
					spec.Tags = append(spec.Tags, plcsession.TagSpec{
						Name:    "tag" + strconv.Itoa(i+1),
						Address: address,
					})
				}
			}
			return Result{Compose: spec}, nil
		},
	}
}

// readDirectCommand reads one tag without opening the composer.
func readDirectCommand() *Command {
	return &Command{
		Name:        "read-direct",
		Description: "Read one tag from a connection",
		Args:        "[connection] [tag address]",
		Suggest:     suggestConnectionThenTag,
		Run: func(ctx context.Context, env *Env, args string) (Result, error) {
			connection, address, err := connectionAndAddress(env, args, "read-direct")
			if err != nil {
				return Result{}, err
			}
			started := env.Now()
			read, err := env.Session.Read(ctx, connection, []plcsession.TagSpec{
				{Name: "readTag", Address: address},
			})
			if err != nil {
				return Result{}, err
			}
			return resultForTags(env, plcsession.EventRead, connection, read.Tags, read.Duration, started), nil
		},
	}
}

// writeDirectCommand writes one tag without opening the composer.
func writeDirectCommand() *Command {
	return &Command{
		Name:        "write-direct",
		Description: "Write one tag on a connection",
		Args:        "[connection] [tag address] [value]",
		Suggest:     suggestConnectionThenTag,
		Run: func(ctx context.Context, env *Env, args string) (Result, error) {
			fields := strings.Fields(strings.TrimSpace(args))
			if len(fields) < 3 {
				return Result{}, errors.New("write-direct needs a connection, a tag address and a value")
			}
			connection, address, value := fields[0], fields[1], strings.Join(fields[2:], " ")
			if err := requireConnected(env, connection); err != nil {
				return Result{}, err
			}
			started := env.Now()
			write, err := env.Session.Write(ctx, connection, []plcsession.TagSpec{
				{Name: "writeTag", Address: address, Value: value},
			})
			if err != nil {
				return Result{}, err
			}
			return resultForTags(env, plcsession.EventWrite, connection, write.Tags, write.Duration, started), nil
		},
	}
}

// browseDirectCommand browses a connection without opening the composer.
func browseDirectCommand() *Command {
	return &Command{
		Name:        "browse-direct",
		Description: "Browse the tags a connection exposes",
		Args:        "[connection] [query]",
		Suggest:     suggestConnections,
		Run: func(ctx context.Context, env *Env, args string) (Result, error) {
			connection, query := splitFirst(strings.TrimSpace(args))
			if connection == "" {
				return Result{}, errors.New("browse-direct needs a connection")
			}
			if err := requireConnected(env, connection); err != nil {
				return Result{}, err
			}
			started := env.Now()
			browse, err := env.Session.Browse(ctx, connection, strings.TrimSpace(query))
			if err != nil {
				return Result{}, err
			}
			result := Result{Lines: []string{
				fmt.Sprintf("browse %s found %d tags in %s", connection, len(browse.Items), browse.Duration),
			}}
			// One message for the whole browse, not one per tag. Emitting a message per tag
			// meant browsing a device with eight tags pushed eight rows into the list, all with
			// the same timestamp, and shoved the events the user was actually watching off the
			// screen. The tags belong in the detail pane, which is what it is for.
			if len(browse.Items) > 0 {
				tags := make([]plcsession.TagResult, 0, len(browse.Items))
				for _, item := range browse.Items {
					tags = append(tags, plcsession.TagResult{
						Name:     item.Name,
						Address:  item.Address,
						DataType: item.DataType,
						Value:    browseAccess(item),
						Code:     plcsession.ResponseCodeOK,
					})
				}
				result.Events = append(result.Events, plcsession.Event{
					Kind:       plcsession.EventBrowse,
					Connection: connection,
					Started:    started,
					Received:   env.Now(),
					Tags:       tags,
					Summary:    fmt.Sprintf("%d tags", len(tags)),
				})
			}
			if len(browse.Items) == 0 {
				result.Lines = append(result.Lines, "no tags matched")
			}
			return result, nil
		},
	}
}

// subscribeDirectCommand subscribes to one tag without opening the composer.
func subscribeDirectCommand() *Command {
	return &Command{
		Name:        "subscribe-direct",
		Description: "Subscribe to one tag on a connection",
		Args:        "[connection] [tag address]",
		Suggest:     suggestConnectionThenTag,
		Run: func(_ context.Context, env *Env, args string) (Result, error) {
			connection, address, err := connectionAndAddress(env, args, "subscribe-direct")
			if err != nil {
				return Result{}, err
			}
			// A subscription outlives the command that started it, so it needs a context that
			// is not the command's own; the command's is cancelled the moment it returns.
			streamCtx, cancel := env.StreamContext()
			events, err := env.Session.Subscribe(streamCtx, connection, []plcsession.TagSpec{
				{Name: "subscriptionTag", Address: address},
			})
			if err != nil {
				cancel()
				return Result{}, err
			}
			return Result{
				Lines: []string{"subscribed " + connection + " " + address},
				Stream: &Stream{
					Connection: connection,
					Label:      address,
					Events:     events,
					Cancel:     cancel,
				},
			}, nil
		},
	}
}

// logCommand groups the log-related subcommands.
func logCommand() *Command {
	return &Command{
		Name:        "log",
		Description: "Log related operations",
		Sub: []*Command{
			{
				Name:        "level",
				Description: "Set the log level",
				Args:        "[level]",
				Suggest: func(_ *Env, partial string) []string {
					return prefixed(logLevelNames(), partial)
				},
				Run: func(_ context.Context, env *Env, args string) (Result, error) {
					name := strings.TrimSpace(args)
					level, err := zerolog.ParseLevel(name)
					if err != nil {
						return Result{}, errors.Errorf("unknown log level %q, one of %s",
							name, strings.Join(logLevelNames(), ", "))
					}
					env.Config.LogLevel = level.String()
					return Result{
						Lines:    []string{"log level " + level.String()},
						LogLevel: &level,
					}, nil
				},
			},
			{
				Name:        "clear",
				Description: "Clear the log pane",
				Run: func(_ context.Context, _ *Env, _ string) (Result, error) {
					return Result{Clear: ClearConsole}, nil
				},
			},
		},
	}
}

// historyCommand shows the recent command history.
func historyCommand() *Command {
	return &Command{
		Name:        "history",
		Description: "Show the recent command history",
		Run: func(_ context.Context, env *Env, _ string) (Result, error) {
			recent := env.Config.History.Last10Commands
			if len(recent) == 0 {
				return Result{Lines: []string{"no commands yet"}}, nil
			}
			lines := make([]string, 0, len(recent)+1)
			lines = append(lines, "recent commands:")
			for i, command := range recent {
				lines = append(lines, fmt.Sprintf("  %d: %s", i, command))
			}
			return Result{Lines: lines}, nil
		},
	}
}

// clearCommand empties the output panes.
func clearCommand() *Command {
	return &Command{
		Name:        "clear",
		Description: "Clear the output panes",
		Args:        "[messages|console|all]",
		Suggest: func(_ *Env, partial string) []string {
			return prefixed([]string{"messages", "console", "all"}, partial)
		},
		Run: func(_ context.Context, _ *Env, args string) (Result, error) {
			switch strings.TrimSpace(args) {
			case "", "all":
				return Result{Clear: ClearAll}, nil
			case "messages":
				return Result{Clear: ClearMessages}, nil
			case "console":
				return Result{Clear: ClearConsole}, nil
			default:
				return Result{}, errors.Errorf("clear takes messages, console or all")
			}
		},
	}
}

// helpCommand lists the command tree.
func helpCommand() *Command {
	return &Command{
		Name:        "help",
		Description: "List the available commands",
		Run: func(_ context.Context, env *Env, _ string) (Result, error) {
			var lines []string
			// Walk skips the root by identity, so every command it visits belongs in the
			// listing; depth 0 is a top-level command, not the root.
			env.Registry.Walk(func(depth int, path string, command *Command) {
				entry := strings.Repeat("  ", depth) + path
				if command.Args != "" {
					entry += " " + command.Args
				}
				if command.Description != "" {
					entry += " — " + command.Description
				}
				lines = append(lines, entry)
			})
			return Result{Lines: lines}, nil
		},
	}
}

// quitCommand exits the application.
func quitCommand() *Command {
	return &Command{
		Name:        "quit",
		Description: "Quit the application",
		Run: func(_ context.Context, _ *Env, _ string) (Result, error) {
			return Result{Quit: true}, nil
		},
	}
}

// --- shared helpers ---

// requireConnected reports a useful error when a command names a connection that is not open.
func requireConnected(env *Env, id string) error {
	for _, connection := range env.Session.Connections() {
		if connection.ID == id {
			return nil
		}
	}
	open := connectionIDs(env)
	if len(open) == 0 {
		return errors.Errorf("%s is not connected, and nothing else is either; connect first", id)
	}
	return errors.Errorf("%s is not connected; open connections are %s", id, strings.Join(open, ", "))
}

// connectionAndAddress parses the "[connection] [address]" argument shape the -direct commands
// share, reporting which command complained so the message is actionable.
func connectionAndAddress(env *Env, args, command string) (connection, address string, err error) {
	fields := strings.Fields(strings.TrimSpace(args))
	if len(fields) != 2 {
		return "", "", errors.Errorf("%s needs exactly two arguments: a connection and a tag address", command)
	}
	if err := requireConnected(env, fields[0]); err != nil {
		return "", "", err
	}
	return fields[0], fields[1], nil
}

// resultForTags turns a read or write outcome into console lines and a message-table entry.
func resultForTags(env *Env, kind plcsession.EventKind, connection string, tags []plcsession.TagResult, duration time.Duration, started time.Time) Result {
	failed := 0
	for _, tag := range tags {
		if !tag.Succeeded() {
			failed++
		}
	}
	summary := fmt.Sprintf("%s %s: %d tags in %s", kind, connection, len(tags), duration)
	if failed > 0 {
		summary += fmt.Sprintf(", %d failed", failed)
	}
	return Result{
		Lines: []string{summary},
		Events: []plcsession.Event{{
			Kind:       kind,
			Connection: connection,
			Started:    started,
			Received:   env.Now(),
			Tags:       tags,
			Summary:    summary,
		}},
	}
}

// browseAccess renders a browsed tag's access flags as rws, which is what a user needs before
// choosing to read, write or subscribe to it.
func browseAccess(item plcsession.BrowseItem) string {
	var access strings.Builder
	for _, flag := range []struct {
		enabled bool
		letter  string
	}{{item.Readable, "r"}, {item.Writable, "w"}, {item.Subscribable, "s"}} {
		if flag.enabled {
			access.WriteString(flag.letter)
		} else {
			access.WriteString("-")
		}
	}
	// The address and the data type are already columns of their own wherever this is shown, so
	// repeating them here rendered each tag twice over.
	if item.Name != "" {
		return access.String() + " " + item.Name
	}
	return access.String()
}

// suggestConnections offers the open connections.
func suggestConnections(env *Env, partial string) []string {
	return prefixed(connectionIDs(env), partial)
}

// suggestConnectTargets offers protocols, and the hosts this user connected to before.
//
// The recalled hosts are what makes the prompt worth using on a second run: the old tool kept
// the same history and this port keeps reading it.
func suggestConnectTargets(env *Env, partial string) []string {
	var candidates []string
	for _, protocol := range env.Session.Protocols() {
		candidates = append(candidates, protocol+"://")
		for _, host := range env.Config.History.Last10Hosts {
			candidates = append(candidates, protocol+"://"+host)
		}
	}
	if env.Demo {
		// In demo mode the only reachable devices are the simulated ones, so offer those first
		// rather than a protocol prefix that cannot connect to anything.
		candidates = append([]string{plcsession.DemoDeviceOne, plcsession.DemoDeviceTwo}, candidates...)
	}
	return prefixed(candidates, partial)
}

// suggestConnectionThenTag offers connections until one has been typed, then tag addresses.
func suggestConnectionThenTag(env *Env, partial string) []string {
	connection, rest := splitFirst(partial)
	if rest == "" && !strings.HasSuffix(partial, " ") {
		return prefixed(connectionIDs(env), partial)
	}
	// A tag address is being typed. In demo mode the catalogue is known, so offer it; against a
	// real device the addresses are protocol-specific and browsing is how you find them.
	var addresses []string
	if env.Demo {
		addresses = plcsession.DemoTagAddresses()
	}
	out := make([]string, 0, len(addresses))
	for _, address := range addresses {
		candidate := connection + " " + address
		if strings.HasPrefix(candidate, partial) {
			out = append(out, candidate)
		}
	}
	return out
}

// connectionIDs lists the open connection identifiers.
func connectionIDs(env *Env) []string {
	connections := env.Session.Connections()
	ids := make([]string, 0, len(connections))
	for _, connection := range connections {
		ids = append(ids, connection.ID)
	}
	return ids
}

// prefixed filters candidates by prefix, which is the matching bubbles/textinput performs.
func prefixed(candidates []string, partial string) []string {
	if partial == "" {
		return candidates
	}
	out := make([]string, 0, len(candidates))
	for _, candidate := range candidates {
		if strings.HasPrefix(candidate, partial) {
			out = append(out, candidate)
		}
	}
	return out
}

// splitFirst splits off the first whitespace-separated word.
func splitFirst(s string) (first, rest string) {
	trimmed := strings.TrimLeft(s, " ")
	index := strings.IndexAny(trimmed, " \t")
	if index < 0 {
		return trimmed, ""
	}
	return trimmed[:index], strings.TrimLeft(trimmed[index:], " ")
}

// logLevelNames lists the levels `log level` accepts.
func logLevelNames() []string {
	return []string{"trace", "debug", "info", "warn", "error", "fatal", "panic", "disabled"}
}
