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

# Quickstart

Both tools run against simulated devices with `--demo`, so neither needs hardware to try.
Everything below works on a fresh checkout with no configuration.

```bash
cd plc4go
go run ./tools/plc4xbrowser --demo
go run ./tools/plc4xpcapanalyzer ui --demo
```

`Ctrl+C` quits either one.

## plc4xbrowser --demo

Opens already connected to `demo://plant-1`, with a `DEMO` badge in the status bar so
simulated values can never be mistaken for readings from real hardware.

### 1. See what the device exposes

```
browse-direct demo://plant-1
```

Eight tags appear in **Messages**. Select one to see it in **Detail** — click it, or press
`2` and use `↑`/`↓`.

### 2. Read a tag

```
read-direct demo://plant-1 temp/1
```

Run it a few times. The value moves: the simulator shapes each tag as a sine with a period of
17 readings, so values drift the way a plant reading drifts rather than jumping about. They are
also a pure function of a per-tag counter, so two runs of the demo show the same sequence.

`counter/1` is the one tag that advances on every single read.

### 3. Compose a multi-tag request

`read`, `write`, `browse` and `subscribe` open a form, because a real request has several tags
and a single line cannot express one:

```
read demo://plant-1
```

| Key | Effect |
| --- | --- |
| `Tab` / `Shift+Tab` | move between fields |
| `Ctrl+N` | add a tag row |
| `Ctrl+D` | remove the focused tag row (inside the form only) |
| `Enter` | run the request |
| `Esc` | cancel |

Try submitting with the address left empty: the form refuses and says which tag needs one. A
request that fails at the device keeps the form open with the reason, so it can be corrected
rather than retyped.

### 4. A write that sticks

```
write-direct demo://plant-1 motor/speed 1234
read-direct  demo://plant-1 motor/speed
```

The read returns `1234`. Writing a read-only tag is refused per tag:

```
write-direct demo://plant-1 temp/1 99      # ACCESS_DENIED
```

### 5. A live subscription

```
subscribe-direct demo://plant-1 temp/1
```

New rows arrive once a second until the tool exits.

### 6. Errors stay put

```
read-direct demo://nowhere temp/1
```

The error is pinned above the prompt until `Esc` dismisses it, and it names the connections
that *are* open. Errors used to scroll away in a ten-row console.

### 7. Completion

Type `re` then `Tab`. Type `read-direct ` then `Tab` and it offers the open connection; after a
connection it offers the tag catalogue.

### 8. Resize it

Shrink the terminal below 100 columns: the prompt stays visible and the layout collapses to a
single column, with the sidebar and detail reachable as overlays. Below 60×12 it says the
terminal is too small rather than drawing something broken.

## plc4xpcapanalyzer ui --demo

Generates `cbus-demo.pcap` — ten packets of real C-Bus traffic, eight that round-trip
byte-identically through plc4x's own codec and two deliberately unparseable — opens it, and
analyses it immediately. The file is removed on exit.

### 1. Read the verdicts

`2` focuses the packet pane. Eight packets read `ok`; two are failures.

### 2. Look at the bytes

Select a packet, `3` for **Detail**, then:

| Key | View |
| --- | --- |
| `b` | raw bytes |
| `t` | parsed tree |
| `d` | diff (the default) |

On a mismatch the diff shows the original against the reserialized bytes with the first
differing offset marked. That parse → reserialize → byte-compare loop is the tool's whole
purpose.

### 3. Switch tabs

`]` and `[` cycle **Packets → Log → Findings**. Findings lists only the defects.

### 4. Re-run the analysis

`a` re-analyses. Watch the phase breadcrumb (`index ▸ filter ▸ analyze`), the progress bar and
the live counters. `Esc` aborts a run in progress.

### 5. The command line still works

```
open <path>            # open another capture
analyze                # analyse the open capture
extract                # dump payloads
conf set <key> <value> # change an option
help                   # list every command
```

## Keys, both tools

| Key | Effect |
| --- | --- |
| `Tab` | complete at the prompt |
| `Shift+Tab` | move between the prompt and the panes |
| `1` … `4` | jump straight to a pane — the number is drawn in each pane's title |
| `Alt+1` … `Alt+4` | the same, where the terminal delivers it |
| `:` or `Esc` | return the keyboard to the prompt |
| `↑` / `↓` or `k` / `j` | move within a pane; history at the prompt |
| `g` / `G` | top / end |
| `/` | filter |
| `?` or `F1` | toggle full help |
| `Ctrl+C` | quit |
| `Ctrl+D` | quit, on an empty prompt — it deletes a character otherwise, as in a shell |

A bare letter is deliberately inert while the prompt has the keyboard: otherwise typing `quit`
would fire the `q`, `u`, `i` and `t` pane bindings. Digits are the exception: a digit can only
be a hotkey where it cannot be text, so it jumps from an *empty* prompt and from inside a pane,
and is text once a command is being typed. No command begins with a digit.

`Alt`+digit is also bound, but do not rely on it: GNOME Terminal, Konsole and Windows Terminal
all bind `Alt`+digit to switching terminal tabs and never deliver it to the application. That is
why the bare digit exists, and why each pane draws its number in its title.

**Mouse:** click a pane to focus it, click a row to select it, and use the wheel to scroll
whichever pane the pointer is over — the wheel does not move focus, so the log can be scrolled
without giving up the prompt.

## Flags

| Flag | Effect |
| --- | --- |
| `--demo` | run against simulated data, no hardware needed |
| `--ascii` | force ASCII drawing characters instead of Unicode |
| `--log-level` | `trace`, `debug`, `info`, `warn`, `error` |

`NO_COLOR=1` in the environment strips colour from both tools. The glyph set is otherwise
chosen from the locale, so a terminal without a UTF-8 locale gets the ASCII set automatically.

## What demo mode cannot show

Demo mode simulates the device, so nothing is put on a wire. The browser's byte view
(`b` in the Detail pane) therefore has no frames to show and says so. Connect to a real device
to see captured wire bytes there.
