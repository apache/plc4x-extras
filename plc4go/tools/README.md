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

The analyzer handles `bacnetip` and `c-bus`. `bacnet` and `cbus` are accepted as aliases, and
every layer resolves names through one registry so the command line, the analyzer, the
extractor and the interface cannot disagree about them.

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

### Protocols

Every public plc4go driver is available. The code is what the driver itself answers to, so it
is also what a connection string must use:

| Code | Driver | Transport | Discovery |
| --- | --- | --- | --- |
| `ab-eth` | Allen Bradley ETH | tcp | |
| `ads` | Beckhoff TwinCat ADS | tcp | yes |
| `bacnet-ip` | BACnet/IP | udp | yes |
| `c-bus` | Clipsal Bus | tcp | |
| `eip` | EthernetIP | tcp | yes |
| `firmata` | Firmata | serial | |
| `iec-60870-5-104` | IEC 60870-5-104 | tcp | |
| `knxnet-ip` | KNXNet/IP | udp | yes |
| `logix` | Logix CIP | tcp | yes |
| `modbus-ascii` | Modbus ASCII | serial | |
| `modbus-rtu` | Modbus RTU | serial | |
| `modbus-tcp` | Modbus TCP | tcp | |
| `opcua` | Opcua | tcp | |
| `s7` | Siemens S7 (Basic) | tcp | |
| `slmp` | SLMP (MELSEC) 3E | tcp | |
| `umas` | UMAS (Schneider Electric) | tcp | |

A test registers every one and asserts the driver answers to the code it is listed under, so a
name cannot drift out of step. `bacnetip` is accepted as an alias for `bacnet-ip`, because the
previous interface advertised that spelling and saved command histories still contain it.

A serial connection names its port in the path rather than the host, since a serial connection
string has no host:

```
modbus-rtu:///dev/ttyUSB0
firmata:///dev/ttyACM0
```

### Configuration precedence

The analyzer's settings come from three places, in the conventional order: a built-in default is
overridden by the persisted session configuration, and that is overridden by a flag on the
command line. `conf set` writes to the persisted layer, so it survives a restart without ever
beating a flag.

Getting that order right needs one fact that is otherwise unrecoverable. The cobra flags bind
straight to the configuration singletons by pointer, so once a command line has been parsed
there is nothing left to distinguish a value the user asked for from the default that happens
to equal it. `config.SnapshotDefaults`, called from `Execute` after every registration and
before any parsing, records the defaults; a field that no longer holds its recorded default is
one a flag set. The session file is decoded into detached structs and then applied only to the
fields that still hold their defaults.

Two limits, both deliberate. A flag passed with exactly its default value looks unset, so the
persisted value wins for that field -- the benign direction. And with no snapshot at all, which
in practice means a test rather than the tool, every non-zero value is treated as deliberate
and the persisted settings only fill in fields that are still zero.

Before this, the file was decoded straight through the shared pointers, so it overwrote whatever
the command line had just put there: a value persisted months ago beat the flag typed a second
ago, silently and with no way to override it.

### Stopping things

`Ctrl+C` stops what is happening rather than ending the session. A user whose read is hanging on
an unreachable device, or who is watching an analysis grind through a large capture, reaches for
it to get back control -- and losing every open connection, or every record collected so far, is
not a reasonable answer to that keystroke. With nothing running it offers to quit instead;
`y`, `Enter` or a second `Ctrl+C` confirms, and any other key keeps the session and is swallowed
rather than also doing its usual job. `q` and `Ctrl+D` go through the same question.

Once an abort is under way, `Ctrl+C` means the session again, so a run that is slow to stop
cannot trap the user in a tool that will not exit.

The typed `quit` command still exits directly: four letters and a return are their own
confirmation.

A cancelled context does not stop the goroutine that will eventually deliver the command's
outcome, so each run carries a sequence number and an outcome whose number no longer matches is
dropped. Without that, an abort was immediately followed by the aborted command's own error
appearing anyway.

### Reading the screen

Colour carries meaning rather than decoration, so a glance answers a question without reading:

| What | How it reads |
| --- | --- |
| A value's data type | by family -- numbers cyan, flags amber, text lime, times violet |
| An unknown data type | muted, so it is visibly *uncategorised* rather than miscategorised |
| An outcome | `OK` green, a failure red, in the message list and in Detail alike |
| A log line | its level marker coloured: `ERR` red, `WRN` amber, `INF` accent, trace and debug muted |
| A count worth glancing at | the number in the accent, its label muted; a failure count red |
| The top and bottom bars | a raised band, framing the panes between them |

In the analyzer specifically:

| What | How it reads |
| --- | --- |
| A verdict | `ok` green, a defect red, and a skip or a filtered packet muted -- those are not findings and must not read as though they were |
| A direction | request and reply in different colours, so a exchange can be followed down the column rather than a row at a time |
| A hex dump | the offset and the text pane muted, the bytes at full contrast: only the middle column is the data |
| A parse tree | plc4x draws it as nested boxes; the frames drop to the chrome colour so the field names and values are the only thing at full contrast |

Two implementation notes, because both have already caused bugs:

A terminal cannot nest a background. An inner style's reset ends the outer background
mid-row, so a banded row is assembled from styles that each carry the band themselves --
`Theme.OnSurface` and `Theme.Surfaced` exist for that, and every space in such a row lives
*inside* a styled run rather than beside one. A bare space between two styled pieces shows the
terminal's own background through the band and stripes the bar.

For the same reason the analyzer builds its packet rows from styled cells rather than styling
the row, and `bubbles/table` gets a bare cell style. It styles each cell and then styles
the selected row around them, so any foreground on the cell style ends with a reset that wipes
the selection from every cell after the first, leaving the selected row indistinguishable.

`NO_COLOR=1` strips all of it. Bold and reverse video stay: they are how a title and a cursor
still read without colour, and a background is the one thing that cannot degrade gracefully,
because it prints as a solid block.

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
    tuitest/      assertions about rendered output that both tools share
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

Most view tests render with a colourless ASCII theme, so an assertion on the content is an
assertion on the content and a width is a count of characters. The `appearance_test.go` files
are the deliberate exception: the coloured themes are what a user actually sees, and a band that
stops a cell short of the edge, a style whose reset punches a hole in one, a selection that
vanishes after the first cell, and a background reaching a `NO_COLOR` terminal are all
invisible in plain text. `internal/tuitest` holds the escape-sequence assertions both tools
need, so there is one answer to what the terminal paints rather than two that can drift.

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
