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
	"slices"
	"strings"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/rs/zerolog"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/plcsession"
)

// The command layer.
//
// Nothing here knows that a terminal exists. A command is data: a name, a description, an
// optional argument summary, subcommands, a completion function and a Run function that takes
// a context and an [Env] and returns a [Result]. The previous implementation instead had each
// action write into package-level tview widgets, which is why not one of its commands could be
// tested and why four of them were never finished at all.
//
// Result is a value rather than a set of callbacks for the same reason: the model applies it,
// so a test can run every command against a plcsession.Demo and assert on what came back
// without constructing a user interface.

// Operation is the kind of request the composer is opened for.
type Operation string

// The operations the request composer can be opened for. These are the names of the four
// commands that the previous implementation advertised but answered with "mode switch not yet
// implemented".
const (
	OperationRead      Operation = "read"
	OperationWrite     Operation = "write"
	OperationBrowse    Operation = "browse"
	OperationSubscribe Operation = "subscribe"
)

// ClearTarget is a set of outputs to empty, as a bit set so that the bare `clear` command can
// name all of them at once.
type ClearTarget uint8

// The clearable outputs. Messages is the message table and its detail; Console is the log
// pane, which is where both the log and command output land.
const (
	ClearMessages ClearTarget = 1 << iota
	ClearConsole
	ClearCommands
)

// ClearAll is every output, which is what the bare `clear` command asks for.
const ClearAll = ClearMessages | ClearConsole | ClearCommands

// Has reports whether t includes target.
func (t ClearTarget) Has(target ClearTarget) bool { return t&target != 0 }

// ComposeSpec asks the model to open the request composer, pre-set for one operation.
type ComposeSpec struct {
	Operation Operation
	// Connection is the connection to pre-select, empty when the user named none.
	Connection string
	// Tags pre-fills the tag rows; a nil slice means start with one empty row.
	Tags []plcsession.TagSpec
	// Query pre-fills the browse query.
	Query string
}

// Stream is a live subscription handed back to the model to drain.
//
// The channel is drained by a tea.Cmd that reads one event and re-arms, never by a goroutine
// that touches model state. Cancel stops the subscription; the model calls it when the
// connection is closed, when the user aborts, and at shutdown.
type Stream struct {
	Connection string
	// Label names the subscription in the UI, typically the tag address.
	Label  string
	Events <-chan plcsession.Event
	Cancel context.CancelFunc
}

// Result is everything a command asks the user interface to do. The zero Result means "the
// command succeeded and there is nothing to show", which is a legitimate outcome.
type Result struct {
	// Lines are console lines to append to the log pane.
	Lines []string
	// Events are entries for the message table.
	Events []plcsession.Event
	// Compose, when set, opens the request composer.
	Compose *ComposeSpec
	// Stream, when set, is a subscription for the model to drain.
	Stream *Stream
	// Quit asks the application to exit.
	Quit bool
	// Clear names outputs to empty.
	Clear ClearTarget
	// LogLevel, when set, is the new log level.
	LogLevel *zerolog.Level
	// ConnectionsChanged and DriversChanged tell the sidebar to re-read the session.
	ConnectionsChanged bool
	DriversChanged     bool
}

// Env is what a command is allowed to reach. It is deliberately small: a session, the
// persisted settings, a clock and a way to obtain a context that outlives the command.
type Env struct {
	// Session is the PLC seam. Never nil.
	Session plcsession.Session
	// Config is the persisted settings, which the connect history and the auto-register list
	// live in. Never nil.
	Config *Config
	// Now supplies timestamps, injected so that tests can pin them.
	Now func() time.Time
	// StreamContext returns a context that outlives the command that created it, for a
	// subscription. A command's own context is cancelled when the command returns, so a
	// subscription started on it would die immediately.
	StreamContext func() (context.Context, context.CancelFunc)
	// Registry is the command tree, so that `help` can enumerate it without a global.
	Registry *Registry
	// Debug mirrors the old plc4xbrowser-debug switch: the browser's own debug logging.
	Debug bool
	// Demo records whether the session is the simulated one, so that suggestions and error
	// messages can name something the user can actually reach.
	Demo bool
}

// Command is one node of the command tree.
type Command struct {
	// Name is the word that selects this command.
	Name string
	// Description is the one-line help text.
	Description string
	// Args summarises the arguments, for the help listing.
	Args string
	// Sub are the subcommands.
	Sub []*Command
	// Run executes the command. A nil Run means the command only groups subcommands.
	Run func(ctx context.Context, env *Env, args string) (Result, error)
	// Suggest returns completion candidates for the argument text typed so far. The candidates
	// are argument text, not whole lines: the registry prepends the command path.
	Suggest func(env *Env, partial string) []string
}

// Registry is a resolved command tree.
type Registry struct {
	root *Command
}

// NewRegistry builds a registry over a root command whose Sub are the top-level commands.
func NewRegistry(root *Command) *Registry { return &Registry{root: root} }

// Root exposes the root command, for the help listing and for tests.
func (r *Registry) Root() *Command { return r.root }

// Names lists the top-level command names in tree order.
func (r *Registry) Names() []string {
	names := make([]string, 0, len(r.root.Sub))
	for _, command := range r.root.Sub {
		names = append(names, command.Name)
	}
	return names
}

// Resolve finds the deepest command the line names, and returns it with the path that was
// consumed and the argument text that was not.
//
// A word is only consumed as a command name when it is followed by a space. That is what keeps
// `read` and `read-direct` apart: on the line "read-direct x" the word "read" is not a
// candidate, because the line does not start with "read ". The previous implementation needed
// a special case for exactly this (acceptsCurrentText refused a match when the text continued
// with a hyphen), which broke for any command name that was a prefix of another with a
// different separator.
func (r *Registry) Resolve(line string) (command *Command, path string, args string) {
	command = r.root
	rest := strings.TrimLeft(line, " ")
	var consumed []string

	for {
		child, remainder, ok := matchChild(command, rest)
		if !ok {
			break
		}
		consumed = append(consumed, child.Name)
		command = child
		rest = remainder
	}
	return command, strings.Join(consumed, " "), strings.TrimSpace(rest)
}

// matchChild consumes a leading child-command name from rest.
//
// Only a complete word counts, and only one that is followed by more text: "log" on its own is
// still being typed, so it is left for the completion of the word rather than treated as a
// resolved command with no arguments. Execute puts the word back before dispatching.
func matchChild(parent *Command, rest string) (*Command, string, bool) {
	for _, child := range parent.Sub {
		if after, found := strings.CutPrefix(rest, child.Name+" "); found {
			return child, strings.TrimLeft(after, " "), true
		}
	}
	return nil, "", false
}

// Execute runs the command a line names.
//
// The line is resolved first, so an unknown word is reported as an unknown command rather than
// being handed to some parent as an argument.
func (r *Registry) Execute(ctx context.Context, env *Env, line string) (Result, error) {
	line = strings.TrimSpace(line)
	if line == "" {
		return Result{}, nil
	}

	command, path, args := r.Resolve(line)
	// Resolve leaves a trailing bare word unconsumed so that completion can still work on it.
	// Executing has no such need, so a remaining word that names a child is taken now.
	if child := exactChild(command, args); child != nil {
		command, args = child, ""
		path = strings.TrimSpace(path + " " + child.Name)
	}

	if command == r.root {
		return Result{}, errors.Errorf("unknown command %q, try help", firstWord(line))
	}
	if command.Run == nil {
		return Result{}, errors.Errorf("%s needs one of: %s", path, strings.Join(childNames(command), ", "))
	}
	if args != "" && len(command.Sub) > 0 && exactChild(command, firstWord(args)) == nil && command.Suggest == nil {
		// A grouping command that also runs, such as `clear`, only accepts its own
		// subcommands; anything else is a typo worth naming.
		return Result{}, errors.Errorf("%s does not understand %q, try one of: %s",
			path, args, strings.Join(childNames(command), ", "))
	}
	return command.Run(ctx, env, args)
}

// exactChild returns the child of command whose name is exactly name.
func exactChild(command *Command, name string) *Command {
	for _, child := range command.Sub {
		if child.Name == name {
			return child
		}
	}
	return nil
}

// childNames lists a command's subcommand names.
func childNames(command *Command) []string {
	names := make([]string, 0, len(command.Sub))
	for _, child := range command.Sub {
		names = append(names, child.Name)
	}
	return names
}

// firstWord returns the first space-separated word of s.
func firstWord(s string) string {
	if before, _, ok := strings.Cut(s, " "); ok {
		return before
	}
	return s
}

// Complete returns whole replacement lines for the text typed so far.
//
// Whole lines, not fragments: [tui.Prompt] hands the candidates to bubbles/textinput, which
// matches a candidate by testing it as a case-insensitive prefix of the ENTIRE field value. A
// candidate of "temp/1" for the line "read-direct demo://plant-1 te" would match nothing.
//
// An empty line returns nothing. Offering the whole command list before a single key has been
// pressed pushed a popup over the panes on every start, which the previous implementation
// avoided in the same way.
func (r *Registry) Complete(env *Env, line string) []string {
	if strings.TrimSpace(line) == "" {
		return nil
	}

	command, path, args := r.Resolve(line)
	prefix := ""
	if path != "" {
		prefix = path + " "
	}

	var candidates []string
	// Subcommands whose name continues the word being typed.
	partialWord := firstWord(args)
	for _, child := range command.Sub {
		if strings.HasPrefix(child.Name, partialWord) {
			candidates = append(candidates, prefix+child.Name)
		}
	}
	// Argument suggestions for the command itself, and for a subcommand whose name is complete
	// but which Resolve left unconsumed because no space follows it yet.
	candidates = append(candidates, suggestionsFor(command, env, prefix, args)...)
	if child := exactChild(command, partialWord); child != nil {
		candidates = append(candidates, suggestionsFor(child, env, prefix+child.Name+" ", "")...)
	}

	// Only candidates that continue what the user has already typed are useful, since anything
	// else is filtered out by textinput anyway and merely inflates the "n more" counter.
	lowerLine := strings.ToLower(line)
	out := make([]string, 0, len(candidates))
	for _, candidate := range candidates {
		if !strings.HasPrefix(strings.ToLower(candidate), lowerLine) {
			continue
		}
		if slices.Contains(out, candidate) {
			continue
		}
		out = append(out, candidate)
	}
	return out
}

// suggestionsFor collects one command's argument suggestions, prefixed with its path.
func suggestionsFor(command *Command, env *Env, prefix, partial string) []string {
	if command.Run == nil || command.Suggest == nil {
		return nil
	}
	suggestions := command.Suggest(env, partial)
	out := make([]string, 0, len(suggestions))
	for _, suggestion := range suggestions {
		out = append(out, prefix+suggestion)
	}
	return out
}

// Walk visits every command depth-first, deepest last, with its indent level and full path.
// The root itself is not visited: it has no name a user could type.
func (r *Registry) Walk(visit func(depth int, path string, command *Command)) {
	var recurse func(depth int, path string, command *Command)
	recurse = func(depth int, path string, command *Command) {
		if command != r.root {
			visit(depth, path, command)
		}
		for _, child := range command.Sub {
			childPath := child.Name
			if command != r.root {
				childPath = path + " " + child.Name
			}
			recurse(depth+1, childPath, child)
		}
	}
	recurse(-1, "", r.root)
}
