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
	"bytes"
	"context"
	"encoding/hex"
	"fmt"
	"net"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/apache/plc4x/plc4go/spi"
	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/gopacket/gopacket"
	"github.com/gopacket/gopacket/layers"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/bacnetanalyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/cbusanalyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcaphandler"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// The analysis this UI draws.
//
// internal/analyzer performs the same parse, re-serialize and byte-compare loop, but it
// reports its findings only by logging them: it returns nothing but an error, and its
// per-packet detail — the original payload, the re-serialized payload and where the two first
// differ — reaches the outside world exclusively as a hex dump embedded in a zerolog field.
// A table of packets and a detail pane showing the byte difference cannot be built from that,
// and reconstructing bytes by parsing log lines would be worse than doing the work again. So
// this file walks the capture itself, through the SAME collaborators the analyzer uses —
// pcaphandler for the handle and the packet index, the per-protocol analyzers for parse and
// serialize, common for the packet metadata — and returns values instead of writing them out.
//
// Nothing here touches the Bubble Tea model. Run is called from a tea.Cmd, reports its advance
// through a progress.Reporter, and hands back one Result.

// Phase names the stage of a run. They are what the run panel's breadcrumb shows, and they
// travel to the model as the Description of a progress.Update.
const (
	// PhaseIndex is the pass that opens the capture and numbers its packets. pcaphandler needs
	// this to give a packet the same number Wireshark would show it under, even when a filter
	// has removed the packets before it.
	PhaseIndex = "index"
	// PhaseFilter is the pass that streams the capture through the BPF filter and the
	// protocol's own packet mapping, keeping the payloads that survive.
	PhaseFilter = "filter"
	// PhaseAnalyze is the pass that parses, re-serializes and byte-compares each payload.
	PhaseAnalyze = "analyze"
)

// Phases is the breadcrumb, in order.
var Phases = []string{PhaseIndex, PhaseFilter, PhaseAnalyze}

// Verdict is what an analysed packet turned out to be.
type Verdict int

const (
	// VerdictOK is a packet that parsed, re-serialized and compared equal.
	VerdictOK Verdict = iota
	// VerdictBytesDiffer is the finding this tool exists to produce: the codec parsed the
	// packet and then wrote back something else.
	VerdictBytesDiffer
	// VerdictParseFail is a payload the codec could not read.
	VerdictParseFail
	// VerdictSerializeFail is a message the codec could read but not write.
	VerdictSerializeFail
	// VerdictSkipped is a payload the protocol itself says is not a whole message: a split
	// transmission, an empty packet or an echo. Not a defect, so not a finding.
	VerdictSkipped
	// VerdictFiltered is a packet the protocol mapping removed.
	VerdictFiltered
	// VerdictNoPayload is a packet with no application layer at all.
	VerdictNoPayload
)

// String is the label the packets table shows in its verdict column. The labels are short
// because that column is the first to lose width on a narrow terminal.
func (v Verdict) String() string {
	switch v {
	case VerdictOK:
		return "ok"
	case VerdictBytesDiffer:
		return "bytes"
	case VerdictParseFail:
		return "parse"
	case VerdictSerializeFail:
		return "serial"
	case VerdictSkipped:
		return "skip"
	case VerdictFiltered:
		return "filter"
	case VerdictNoPayload:
		return "empty"
	default:
		return "?"
	}
}

// IsIssue reports whether this verdict belongs in the Findings tab. A skipped, filtered or
// payload-less packet is normal traffic, not a defect, and counting it as one would bury the
// three real findings among two hundred uninteresting ones.
func (v Verdict) IsIssue() bool {
	switch v {
	case VerdictBytesDiffer, VerdictParseFail, VerdictSerializeFail:
		return true
	default:
		return false
	}
}

// Direction says which way a packet travelled, relative to the configured client address.
type Direction int

const (
	// DirectionRequest is a packet sent by the client.
	DirectionRequest Direction = iota
	// DirectionResponse is a packet sent to the client.
	DirectionResponse
	// DirectionUnknown is a packet whose endpoints do not include the configured client, or a
	// capture analysed with no client configured at all.
	DirectionUnknown
)

// Record is one analysed packet, and everything the UI shows about it.
type Record struct {
	// Number is the packet's position in the unfiltered capture, which is the number a user
	// comparing against Wireshark expects to see.
	Number int
	// Offset is how long after the first packet of the capture this one arrived.
	Offset time.Duration
	// Direction is which way it travelled.
	Direction Direction
	// Protocol is the canonical protocol name it was analysed as.
	Protocol string
	// Summary is the message type the codec parsed it into, empty when it did not parse.
	Summary string
	// Verdict is the outcome.
	Verdict Verdict
	// Reason carries the error or the explanation behind a non-OK verdict.
	Reason string
	// Original is the captured application payload.
	Original []byte
	// Reserialized is what the codec wrote back, nil when it never got that far.
	Reserialized []byte
	// DiffOffset is the offset of the first differing byte, or -1 when the two agree or when
	// there was nothing to compare.
	DiffOffset int
	// Tree is the parsed message rendered as text, for the detail pane's tree view.
	Tree string
}

// Counters are the running totals the run panel shows. They are the same four numbers the
// analyzer logs in its summary line, plus the packets walked.
type Counters struct {
	Walked         int
	Parsed         int
	ParseFail      int
	SerializeFail  int
	CompareFail    int
	Skipped        int
	TotalInCapture int
}

// Issues is how many findings the run produced.
func (c Counters) Issues() int { return c.ParseFail + c.SerializeFail + c.CompareFail }

// Request is one analysis, fully specified.
//
// It is a value rather than a read of the config singletons because the singletons are
// process-global: a run has to be reproducible, and a test has to be able to describe a run
// without mutating state that outlives it.
type Request struct {
	Protocol protocol.Protocol
	PcapFile string
	// Client is the address requests originate from. Protocols where a request and a response
	// are encoded differently — C-Bus is one — cannot be parsed without it.
	Client string
	// Filter is a BPF filter expression. Empty means the protocol's own default.
	Filter string
	// NoFilter disables filtering entirely, default included.
	NoFilter bool
	// OnlyParse stops after parsing, skipping the re-serialize and the byte compare.
	OnlyParse bool
	// NoBytesCompare re-serializes but does not compare.
	NoBytesCompare bool
	// StartPacket skips everything before this packet number. Zero starts at the beginning.
	StartPacket uint
	// PacketLimit caps how many packets are walked. Zero means no limit — unlike the analyzer's
	// config singleton, where zero means "walk nothing", which is why the old UI's analyze
	// command produced an empty run unless the CLI's analyze flags had been parsed first.
	PacketLimit uint
}

// Label is the command line that would reproduce this run. It titles the run panel.
func (r Request) Label() string {
	return "analyze " + r.Protocol.Name + " " + filepath.Base(r.PcapFile)
}

// filterExpression resolves the BPF filter this run should use.
func (r Request) filterExpression() string {
	if r.NoFilter {
		return ""
	}
	return r.Filter
}

// Result is the outcome of one run.
type Result struct {
	Request  Request
	Records  []Record
	Counters Counters
	// Err is set when the run could not be performed at all: an unreadable capture, an
	// unsupported protocol. A packet that failed to parse is a Record, not an Err.
	Err error
	// Aborted says the caller's context was cancelled part way through. The records collected
	// before that point are still returned, because a half-finished analysis of a large
	// capture is worth keeping.
	Aborted bool
	// Elapsed is how long the run took.
	Elapsed time.Duration
}

// codec is the per-protocol half of a run.
type codec struct {
	parse     func(common.PacketInformation, []byte) (spi.Message, error)
	serialize func(spi.Message) ([]byte, error)
	// mapPackets wraps the packet stream in whatever re-assembly the protocol needs. C-Bus
	// splits messages across TCP segments, so it merges them here.
	mapPackets func(chan gopacket.Packet, func(gopacket.Packet) common.PacketInformation) chan gopacket.Packet
	// wait blocks until any goroutine the mapping started has finished, so that nothing is
	// still running once Run returns.
	wait func()
}

// codecFor builds the codec for a request. cancel is called when the run finishes, to release
// the mapping goroutine; the caller must arrange for it to run on every exit path.
func codecFor(ctx context.Context, request Request) (codec, context.CancelFunc, error) {
	switch request.Protocol.Name {
	case protocol.BacnetIP.Name:
		return codec{
			parse:      bacnetanalyzer.PackageParse,
			serialize:  bacnetanalyzer.SerializePackage,
			mapPackets: passthroughMapping,
			wait:       func() {},
		}, func() {}, nil

	case protocol.CBus.Name:
		analyzer := &cbusanalyzer.Analyzer{Client: net.ParseIP(request.Client)}
		analyzer.Init()
		// A context of our own so the mapping goroutine is released on every early exit — the
		// packet limit, an abort, a read error. Without it that goroutine blocks on a send for
		// the life of the process and keeps logging into the UI's log pane.
		mappingCtx, cancel := context.WithCancel(ctx)
		return codec{
			parse:     analyzer.PackageParse,
			serialize: analyzer.SerializePackage,
			mapPackets: func(in chan gopacket.Packet, info func(gopacket.Packet) common.PacketInformation) chan gopacket.Packet {
				return analyzer.MapPackets(mappingCtx, in, info)
			},
			wait: analyzer.WaitForMapping,
		}, cancel, nil

	default:
		return codec{}, func() {}, errors.Errorf("protocol %s is registered but not implemented by the analyzer", request.Protocol.Name)
	}
}

// passthroughMapping is the mapping for protocols that need no re-assembly.
func passthroughMapping(in chan gopacket.Packet, _ func(gopacket.Packet) common.PacketInformation) chan gopacket.Packet {
	return in
}

// collected is one packet that survived the filter pass, held until the analyze pass.
type collected struct {
	info    common.PacketInformation
	payload []byte
	// filtered is the reason the protocol mapping rejected the packet, nil when it did not.
	filtered error
}

// Sink is where a run publishes what it learns while it is still running.
//
// Every member is optional and every member is called from the analysis goroutine, so a
// consumer inside a Bubble Tea program must do nothing in them but hand the value to a
// channel. Nothing here may touch model state.
type Sink struct {
	// Progress receives the advance of each phase.
	Progress progress.Reporter
	// Observe is handed each record as it is produced, in packet order, so that the table
	// fills while the run is in flight rather than all at once at the end.
	Observe func(Record)
	// Clock measures the elapsed time. Tests inject one they control.
	Clock func() time.Time
}

// Analyze performs one analysis and returns what it found.
//
// It is deliberately synchronous and free of any UI type: the model calls it from a tea.Cmd
// and receives the Result as a message.
func Analyze(ctx context.Context, request Request, sink Sink) Result {
	reporter := sink.Progress
	if reporter == nil {
		reporter = progress.Nop()
	}
	clock := sink.Clock
	if clock == nil {
		clock = time.Now
	}
	started := clock()
	result := Result{Request: request}
	finish := func() Result {
		result.Elapsed = clock().Sub(started)
		return result
	}

	defer reporter.Done()

	// aborted records a cancellation and says whether to stop. It is checked between the
	// phases as well as inside their loops: a context cancelled before a phase starts makes
	// that phase's producer close its channel immediately, so the loop body never runs and
	// the cancellation would otherwise go unnoticed and the run would look complete.
	aborted := func() bool {
		if ctx.Err() == nil {
			return false
		}
		result.Aborted = true
		return true
	}

	codec, cancelMapping, err := codecFor(ctx, request)
	if err != nil {
		result.Err = err
		return finish()
	}
	defer func() {
		cancelMapping()
		codec.wait()
	}()

	if aborted() {
		return finish()
	}

	// Index. The total is not known until this pass has run, so the bar starts indeterminate.
	reporter.Start(0, PhaseIndex)
	handle, total, timestampToIndex, err := pcaphandler.GetIndexedPcapHandle(request.PcapFile, request.filterExpression())
	if err != nil {
		result.Err = errors.Wrapf(err, "error opening capture %s", request.PcapFile)
		return finish()
	}
	defer handle.Close()
	result.Counters.TotalInCapture = total

	// Filter. Everything that survives is copied out of the capture buffer, because the analyze
	// pass reads it after the handle has moved on.
	reporter.Start(total, PhaseFilter)
	packetInformationFor := func(packet gopacket.Packet) common.PacketInformation {
		return packetInformation(request.PcapFile, packet, timestampToIndex)
	}
	var packets []collected
	walked := uint(0)
	if aborted() {
		return finish()
	}
	for packet := range codec.mapPackets(pcaphandler.GetPacketSource(handle).Packets(), packetInformationFor) {
		if aborted() {
			break
		}
		if packet == nil {
			break
		}
		walked++
		// The same boundary the analyzer uses: StartPacket is the first packet KEPT, not the
		// last one skipped.
		if request.StartPacket > 0 && walked < request.StartPacket {
			continue
		}
		if request.PacketLimit > 0 && walked > request.PacketLimit {
			break
		}
		reporter.Advance(1)

		info := packetInformationFor(packet)
		if filtered, ok := packet.(common.FilteredPackage); ok {
			packets = append(packets, collected{info: info, filtered: filtered.FilterReason()})
			continue
		}
		applicationLayer := packet.ApplicationLayer()
		if applicationLayer == nil {
			packets = append(packets, collected{info: info})
			continue
		}
		packets = append(packets, collected{info: info, payload: bytes.Clone(applicationLayer.Payload())})
	}

	// Analyze.
	if aborted() {
		return finish()
	}
	reporter.Start(len(packets), PhaseAnalyze)
	var base time.Time
	for i, packet := range packets {
		if aborted() {
			break
		}
		reporter.Advance(1)
		if i == 0 {
			base = packet.info.PacketTimestamp
		}
		record := analyseOne(request, codec, packet, base)
		result.Counters.Walked++
		switch record.Verdict {
		case VerdictParseFail:
			result.Counters.ParseFail++
		case VerdictSerializeFail:
			result.Counters.Parsed++
			result.Counters.SerializeFail++
		case VerdictBytesDiffer:
			result.Counters.Parsed++
			result.Counters.CompareFail++
		case VerdictOK:
			result.Counters.Parsed++
		default:
			result.Counters.Skipped++
		}
		result.Records = append(result.Records, record)
		if sink.Observe != nil {
			sink.Observe(record)
		}
	}
	return finish()
}

// analyseOne turns one collected packet into a record.
func analyseOne(request Request, codec codec, packet collected, base time.Time) Record {
	record := Record{
		Number:     packet.info.PacketNumber,
		Offset:     packet.info.PacketTimestamp.Sub(base),
		Direction:  directionOf(packet.info, request.Client),
		Protocol:   request.Protocol.Name,
		Original:   packet.payload,
		DiffOffset: -1,
	}
	switch {
	case packet.filtered != nil:
		record.Verdict = VerdictFiltered
		record.Reason = packet.filtered.Error()
		return record
	case len(packet.payload) == 0:
		record.Verdict = VerdictNoPayload
		record.Reason = "no application layer"
		return record
	}

	parsed, err := codec.parse(packet.info, packet.payload)
	if err != nil {
		record.Reason = err.Error()
		switch {
		case errors.Is(err, common.ErrUnterminatedPackage):
			record.Verdict, record.Reason = VerdictSkipped, "unterminated"
		case errors.Is(err, common.ErrEmptyPackage):
			record.Verdict, record.Reason = VerdictSkipped, "empty"
		case errors.Is(err, common.ErrEcho):
			record.Verdict, record.Reason = VerdictSkipped, "echo"
		default:
			record.Verdict = VerdictParseFail
		}
		return record
	}

	record.Summary = messageName(parsed)
	record.Tree = fmt.Sprintf("%v", parsed)
	if request.OnlyParse {
		record.Verdict = VerdictOK
		return record
	}

	serialized, err := codec.serialize(parsed)
	if err != nil {
		record.Verdict = VerdictSerializeFail
		record.Reason = err.Error()
		return record
	}
	record.Reserialized = serialized
	if request.NoBytesCompare {
		record.Verdict = VerdictOK
		return record
	}
	if offset := FirstDifference(record.Original, record.Reserialized); offset >= 0 {
		record.Verdict = VerdictBytesDiffer
		record.DiffOffset = offset
		record.Reason = strconv.Itoa(differingBytes(record.Original, record.Reserialized)) + " bytes differ"
		return record
	}
	record.Verdict = VerdictOK
	return record
}

// FirstDifference returns the offset of the first byte at which a and b disagree, treating a
// prefix as differing at the point where the shorter one ends. It returns -1 when the two are
// identical.
//
// This is the number the detail pane points its caret at, and it is the single most useful
// piece of information the tool produces, so it is a named, tested function rather than an
// expression buried in a loop.
func FirstDifference(a, b []byte) int {
	limit := min(len(a), len(b))
	for i := range limit {
		if a[i] != b[i] {
			return i
		}
	}
	if len(a) != len(b) {
		return limit
	}
	return -1
}

// differingBytes counts how many byte positions disagree, counting the tail of the longer
// slice as differing.
func differingBytes(a, b []byte) int {
	limit := min(len(a), len(b))
	count := max(len(a), len(b)) - limit
	for i := range limit {
		if a[i] != b[i] {
			count++
		}
	}
	return count
}

// directionOf decides which way a packet travelled.
func directionOf(info common.PacketInformation, client string) Direction {
	if client == "" || info.SrcIp == nil {
		return DirectionUnknown
	}
	clientIP := net.ParseIP(client)
	if clientIP == nil {
		return DirectionUnknown
	}
	switch {
	case info.SrcIp.Equal(clientIP):
		return DirectionRequest
	case info.DstIp != nil && info.DstIp.Equal(clientIP):
		return DirectionResponse
	default:
		return DirectionUnknown
	}
}

// messageName is the short type name of a parsed message, which is what the packets table
// shows. The codecs return pointers to generated types whose names carry an underscore
// prefix, so both are trimmed.
func messageName(message spi.Message) string {
	name := fmt.Sprintf("%T", message)
	if index := strings.LastIndex(name, "."); index >= 0 {
		name = name[index+1:]
	}
	return strings.TrimPrefix(strings.TrimPrefix(name, "*"), "_")
}

// packetInformation builds the metadata the codecs need, the same way the analyzer does.
func packetInformation(pcapFile string, packet gopacket.Packet, timestampToIndex map[time.Time]int) common.PacketInformation {
	timestamp := packet.Metadata().Timestamp
	number := timestampToIndex[timestamp]
	information := common.PacketInformation{
		PacketNumber:    number,
		PacketTimestamp: timestamp,
		Description:     fmt.Sprintf("No.[%d] timestamp: %v, %s", number, timestamp, pcapFile),
	}
	if network, ok := packet.NetworkLayer().(*layers.IPv4); ok {
		information.SrcIp = network.SrcIP
		information.DstIp = network.DstIP
	}
	return information
}

// HexDumpLines renders data as offset-prefixed rows of bytesPerRow bytes, with the printable
// rendering on the right. It is hex.Dump's shape at a configurable row width, because the
// detail pane has to fit two dumps side by side in a pane that is often under sixty cells.
func HexDumpLines(data []byte, bytesPerRow, maxRows int) []string {
	if bytesPerRow < 1 {
		bytesPerRow = 8
	}
	var lines []string
	for offset := 0; offset < len(data) && len(lines) < maxRows; offset += bytesPerRow {
		end := min(offset+bytesPerRow, len(data))
		lines = append(lines, hexRow(data, offset, end, bytesPerRow))
	}
	if len(lines) == 0 {
		lines = append(lines, "(empty)")
	}
	return lines
}

// hexRow renders one row of a hex dump.
func hexRow(data []byte, from, to, bytesPerRow int) string {
	var row strings.Builder
	row.WriteString(fmt.Sprintf("%08x  ", from))
	for i := range bytesPerRow {
		if from+i < to {
			row.WriteString(hex.EncodeToString(data[from+i : from+i+1]))
			row.WriteString(" ")
		} else {
			row.WriteString("   ")
		}
	}
	row.WriteString(" |")
	for i := from; i < to; i++ {
		row.WriteByte(printable(data[i]))
	}
	row.WriteString("|")
	return row.String()
}

// hexRowCaretColumn is the column a caret has to sit in to point at the byte at offset within
// its row, given the row layout hexRow produces.
func hexRowCaretColumn(offset, bytesPerRow int) int {
	const offsetPrefix = len("00000000  ")
	return offsetPrefix + (offset%bytesPerRow)*3
}

// printable maps a byte onto something safe to draw in a terminal.
func printable(b byte) byte {
	if b < 0x20 || b > 0x7e {
		return '.'
	}
	return b
}

// Preview renders a payload the way the detail pane's first two rows show it: as a quoted Go
// string, truncated to width cells. Quoting rather than dumping is what makes a C-Bus
// exchange readable at a glance, since C-Bus is an ASCII protocol.
func Preview(data []byte, width int, ellipsis string) string {
	if len(data) == 0 {
		return `""`
	}
	quoted := strconv.Quote(string(data))
	if width <= 0 || len([]rune(quoted)) <= width {
		return quoted
	}
	runes := []rune(quoted)
	keep := max(width-len([]rune(ellipsis)), 0)
	return string(runes[:keep]) + ellipsis
}
