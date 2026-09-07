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

The analyzer handles twelve protocols, and `plc4xpcapanalyzer analyze --help` lists them with
their aliases -- generated from the registry, so it cannot go stale the way a hand-written list
does. Every layer resolves names through that one registry, so the command line, the analyzer,
the extractor and the interface cannot disagree about them, and the names are the same driver
codes the browser uses: a user who connected with `modbus-tcp` does not have to learn a second
spelling to analyse its traffic.

Ten of them are a codec plus a filter, held in `internal/codec`: the parse function, the byte
order its buffers use, and the BPF expression that selects its packets. Adding one is a data
change. BACnet and C-Bus stay outside it, because they need more -- C-Bus tracks request
context across a session, and BACnet has an adapter that predates this.

Three of the twelve are serial protocols -- Modbus RTU, Modbus ASCII, Firmata -- so they only
reach a capture through a gateway, and analysing one means passing `--filter` for whichever port
that gateway uses. They are supported because the framing is the same wherever it is carried,
not because a serial line can be captured.

Two protocols the browser drives are **not** analysable yet, and the reasons differ. **OPC UA**
has no wire-level PDU in its reference suite, so an adapter could be written but not verified
against anything; that one wants a real capture. **UMAS** needs a request-function key carried
from a request to its response -- stateful correlation, like C-Bus -- rather than a codec.
**IEC 60870-5-104** has only a driver test suite rather than a parser-serializer one, so its
vectors are in a different shape and want separate work.

### Why the byte order is in the codec

Because it is not decoration. EtherNet/IP is little-endian, and parsed through the default
big-endian buffer **not one** of Apache's twenty-seven EtherNet/IP reference vectors survives
the round trip -- they fail at the first field. Wired up without it, the analyzer would have
reported that plc4x cannot parse its own protocol. ADS and SLMP declare little-endian too but
round-trip either way, because their generated code sets the order per field; EtherNet/IP's
expects it on the buffer. Nothing here is inferable from the protocol name, which is why each
codec states it and a test proves it.

`--demo` needs no capture and no hardware: it generates a small C-Bus capture of ten packets,
eight of which round-trip, analyses that, and removes it afterwards. It works on `analyze`,
`extract` and the `c-bus` subcommand, and combines with `--report`, so a machine-readable report
can be produced on a machine that has never seen a capture:

```bash
plc4xpcapanalyzer analyze --demo --report report.xml
```

It also supplies the client address, which matters more than it looks: C-Bus encodes a request
differently from a response, so without one most of the capture is read the wrong way round and
the same ten packets report six failures instead of one. An address given with `-c` still wins.
Asking for a protocol the demo does not speak is refused rather than obeyed -- analysing C-Bus
traffic as BACnet produces a screenful of failures that say nothing about either.

`extract` prints payloads only at verbosity 2 or above: pass `-vv`. Without it the command walks
the capture and prints nothing, which looks like a failure and is not one.

`analyze bacnet <capture>` and `analyze c-bus <capture>` reach the *subcommands*, which carry
their own filter and protocol-option flags. Reaching `analyze`'s own body takes a protocol name
that is not also a subcommand -- `analyze bacnetip <capture>` -- and the two paths do the same
work.

### Reports

`--report <path>` writes what the run found in a form something other than a person can read:

```bash
plc4xpcapanalyzer analyze c-bus capture.pcap -c 192.168.0.10 \
  --report target/surefire-reports/analyzer.xml
```

JUnit XML unless the name ends in `.json`. JUnit because it is what this repository already
speaks -- the Go tests run through `gotestsum --junitfile target/surefire-reports`, and
`go-platform-test-report.yml` renders that with `dorny/test-reporter` as `java-junit` -- so a
capture becomes a suite, a packet becomes a test case, and a codec that stops round-tripping a
message arrives in CI as a named failing test with the offending bytes and the first differing
offset attached, tracked across builds for free.

A defect is a `<failure>`, typed `parse`, `serial` or `bytes`. A skip is a `<skipped>`, because
a payload the protocol says is not a whole message is not a defect. A packet that never reached
a codec at all -- filtered out, or with no application layer -- is left out of the JUnit
document entirely: counting it would describe the capture rather than the codec and inflate a
total meant to say how much of plc4x was exercised. The JSON form keeps everything, since it is
not a test report and has no reason to hide anything.

An interrupted run still writes its report, marked `aborted`. What was examined is evidence, and
discarding it would make `Ctrl+C` cost more than it saves.

### Stopping a run

`Ctrl+C` stops a run and reports `Aborted` rather than `Done`, keeping the counts gathered so
far. The interrupt handler is installed per command rather than on the root, deliberately: `ui`
runs a terminal interface that reads `Ctrl+C` as a key press and asks before it exits, and
cancelling its context from underneath would take that decision away.

Both loops have always checked their context between packets. What was missing was any way to
reach the check: the entry points passed `context.TODO` and the root passes
`context.Background`, so the abort was implemented and unreachable, and a capture of any size
had exactly one way to stop -- for the process to be killed part way through a report.

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
      finding/      what an analysed packet turned out to be, and the one place that decides
      report/       JUnit XML and JSON reports of what a run found
      analyzer/     the parse, reserialize and compare loop
      extractor/    payload extraction
      pcaphandler/  libpcap access
      codec/        the parse-and-reserialize pair for each protocol, with its byte order and filter
      pcapfixture/  small deterministic captures from real protocol bytes
      common/       shared packet types
    ui/           the terminal interface
```

### One notion of a finding

There are two implementations of the same analysis, and that is deliberate. `internal/analyzer`
walks a capture once; the terminal interface walks it in three phases so it can report progress
and stream records as they arrive. They cannot be collapsed without giving one of those two
things up.

What they must not have is two ideas of what they found, and for a long time they did. The
analyzer classified a packet inline and reported the result only by logging it, so the interface
walked the capture again and classified it a second time -- reasonably, since bytes cannot be
recovered from a log line. Two implementations of one taxonomy drift, and these did: the
interface counted a skipped packet as normal traffic while the documentation called it a
failure, and no test could catch the disagreement because there was nothing shared for the two
to disagree with.

`internal/finding` is now that shared thing. It owns the verdicts, the classification of a parse
error, the byte comparison and the offset of the first difference, the message name, and the
counting rule. `Record` in the interface embeds `finding.Finding` and adds only what a screen
needs -- the arrival offset, the direction, the parsed tree. The analyzer reports findings
through `Options.OnFinding`, which is what makes a report possible at all and what removed the
`TODO: write report to xml or something` that sat on each of its three failure counters.

`TestBothAnalysisPathsAgree` runs one capture through both and compares verdict by verdict,
payload by payload, and counter by counter. That test is the point of the exercise: it is the
one thing that can catch the drift, and it could not have been written before.

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

Protocol tests use real bytes, and not bytes anyone here invented. `internal/pcapfixture`
carries the reference vectors from each protocol's own `ParserSerializerTestsuite` in the Apache
PLC4X repository -- ninety-four payloads across ten protocols -- which plc4x asserts round-trip:
parse the raw bytes, serialize the result, get the same bytes back. That is precisely the
property the analyzer measures, so a capture built from them is one the analyzer must report as
entirely clean, and `TestEveryProtocolRoundTripsItsOwnReferenceVectors` requires exactly that.
A failure there means either an adapter here is wrong or plc4x has regressed, which are the only
two things worth knowing.

The direction of each fixture packet is the `response` parser argument its test case declares,
not a guess from its name: several protocols encode the two directions differently and the
analyzer derives the direction from the client address, so a packet on the wrong side of the
wire is read the wrong way round. The vectors were generated from those suites and committed,
so the tests stay hermetic rather than depending on a plc4x checkout beside this one.

There are also sequences verified *not* to parse, so the failure counters can be exercised. Captures are written with the pure-Go `pcapgo` writer, so building a fixture needs no
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
