<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# plc4go tools

Two terminal tools built on [PLC4X](https://plc4x.apache.org/):

- **plc4xbrowser** — connect to PLCs, browse their tags, and read, write and subscribe to them.
- **plc4xpcapanalyzer** — run captured protocol traffic back through plc4x's codecs and report
  what fails to parse, fails to reserialize, or reserializes to different bytes.

New here? Start with [QUICKSTART.md](QUICKSTART.md); both tools run against simulated devices
with `--demo` and need no hardware.

## Building and running

```bash
cd plc4go
go run ./tools/plc4xbrowser --demo
go run ./tools/plc4xpcapanalyzer ui --demo

go build -o plc4xbrowser       ./tools/plc4xbrowser
go build -o plc4xpcapanalyzer  ./tools/plc4xpcapanalyzer
```

`plc4xpcapanalyzer` **requires cgo and libpcap**, because it reads captures through
`gopacket/pcap`. `CGO_ENABLED=0 go build ./tools/plc4xpcapanalyzer/...` fails. `plc4xbrowser`
has no such requirement and builds without cgo.

## plc4xpcapanalyzer

A pcap forensics tool. For each packet it feeds the application payload into a real plc4x codec,
re-serializes the parsed message and compares the bytes against the original. That loop is a
regression harness for the codecs themselves: a mismatch means plc4x's reader and writer
disagree about the same message.

```
plc4xpcapanalyzer analyze <protocol> <capture>   # parse, reserialize and compare
plc4xpcapanalyzer analyze bacnet   <capture>     # the same, per protocol
plc4xpcapanalyzer analyze cbus     <capture>
plc4xpcapanalyzer extract <protocol> <capture>   # dump application payloads
plc4xpcapanalyzer ui [capture]                   # the terminal interface
```

Protocols are `bacnetip` and `c-bus`. `bacnet` and `cbus` are accepted as aliases, and every
layer resolves names through one registry so the command line, the analyzer, the extractor and
the interface cannot disagree about them.

`extract` prints payloads only at verbosity 2 or above: pass `-vv`.

## plc4xbrowser

An interactive client. Register a driver, connect, then browse, read, write or subscribe.

```
plc4xbrowser              # against real devices
plc4xbrowser --demo       # against simulated devices
```

Commands, as `help` lists them:

| Command | Purpose |
| --- | --- |
| `discover <protocol>` | find devices on the network |
| `connect <connection string>` | open a connection |
| `disconnect <connection>` | close one |
| `register <protocol>` | register a driver and its transports |
| `read` / `write` / `browse` / `subscribe` | open the request composer |
| `read-direct <connection> <tag>` | one-shot single-tag read |
| `write-direct <connection> <tag> <value>` | one-shot single-tag write |
| `browse-direct <connection> [query]` | list the tags a connection exposes |
| `subscribe-direct <connection> <tag>` | subscribe to one tag |
| `log level <level>` / `log clear` | logging |
| `history`, `clear`, `help`, `quit` | housekeeping |

Configuration lives in the user config directory as YAML and remembers the last ten commands,
the last ten hosts, the log level and which drivers to auto-register.

### Wire bytes

With a real connection, the browser records the bytes crossing the transport, and the Detail
pane's `b` view shows the frames captured while the selected request was in flight.

Two honest caveats. A frame is one transport read or write, **not** one protocol message: a
message may span several frames and one frame may carry several messages. And the association
between a request and its bytes is **temporal**, because a transport carries no request
identifier — on a connection that is also carrying a subscription, the window will include
unrelated traffic. The pane says both of these rather than implying otherwise.

Demo mode records nothing, because a simulated device puts nothing on a wire.

## Layout

```
tools/
  internal/
    tui/          theme, glyph sets, responsive layout, pane chrome, keymap, prompt
    plcsession/   the seam between a UI and a PLC: Session, Live, Demo, frame capture
    progress/     progress reporting, for a plain CLI and for a Bubble Tea UI alike
  plc4xbrowser/
    browser/      command tree, root model, request composer, views, mouse
  plc4xpcapanalyzer/
    cmd/          the cobra command line
    config/       the CLI configuration singletons
    internal/
      protocol/     the protocol-name registry
      analyzer/     the parse, reserialize and compare loop
      extractor/    payload extraction
      pcaphandler/  libpcap access
      pcapfixture/  small deterministic captures from real protocol bytes
      common/       shared packet types
    ui/           the terminal interface
```

### The session seam

`plcsession.Session` is the boundary between the browser's interface and a PLC. Nothing behind
it mentions a plc4x type, which is what makes three things possible at once: the interface can
be tested without a device, a network or a terminal; `--demo` is the same seam the tests
exercise rather than a parallel implementation that can drift; and a view can render a result
without knowing how it was produced.

`Live` talks to real drivers. Every operation is bounded by a timeout, defaulting to ten
seconds, because an unbounded plc4x call waiting on a handshake blocks for sixty — long enough
to be indistinguishable from a hang.

`Demo` answers instantly from a synthetic plant. Values are a pure function of a per-tag
counter rather than of the clock or a random source, so they visibly move while remaining
exactly reproducible.

## Testing

```bash
cd plc4go
go test ./tools/...
go test -race ./tools/...

# what CI runs, via the maven build
go tool -modfile=tools.mod gotestsum -- ./...
```

Tests are deterministic: no sleeps as synchronisation, no network, no real terminal, and
injected clocks wherever time is observable. The terminal interfaces are driven by sending
typed messages to `Update` and asserting on the rendered output, which needs no terminal;
`teatest` covers the end-to-end path.

Protocol tests use real bytes. `internal/pcapfixture` builds small captures from sequences
taken from plc4x's own protocol tests, each verified to parse and re-serialize
byte-identically, plus sequences verified *not* to parse so the failure counters can be
exercised. Captures are written with the pure-Go `pcapgo` writer, so building a fixture needs no
cgo even though reading one back does, and with a fixed timestamp so they are byte-stable.

## Development

Tool dependencies live in `tools.mod`, keeping them out of `go.mod`:

```bash
go tool -modfile=tools.mod gotestsum
go tool -modfile=tools.mod goimports-reviser -excludes .idea,.git ./...
go tool -modfile=tools.mod mockery
```

Note that `go generate` does not accept `-modfile`. This module has no `//go:generate`
directives today; if one is added, its tool has to move back to `go.mod`.

Before committing:

```bash
gofmt -l tools/            # must print nothing
go vet ./...
go fix -diff ./...         # must print nothing: the 1.27 modernizers
go test -race ./...
```

Every source file needs the Apache licence header — `apache-rat` runs in the maven build and a
missing header fails the release. Generated checksum files (`go.sum`, `tools.sum`) and binary
test fixtures are excluded in the root `pom.xml`.
