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

package analyzer

import (
	"context"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"os"
	"strconv"
	"time"

	"github.com/apache/plc4x/plc4go/spi"
	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/gopacket/gopacket"
	"github.com/gopacket/gopacket/layers"
	"github.com/rs/zerolog/log"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/bacnetanalyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/cbusanalyzer"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/codec"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/finding"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcaphandler"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Options carries the collaborators one analysis run needs: where its output goes, who watches
// the messages it parses, and who is told how far it has got.
//
// It is a struct rather than more parameters on AnalyzeWithOutputAndCallback because that
// function's signature is pinned by characterisation tests, and because the next thing to be
// hoisted out of the config singletons will belong here too.
type Options struct {
	// Stdout receives the pretty-printed messages and byte dumps the verbosity flags ask for.
	Stdout io.Writer
	// Stderr is where a progress bar may be drawn, when Progress is unset and drawing is safe.
	Stderr io.Writer
	// MessageCallback, when set, is handed every successfully parsed message.
	MessageCallback func(parsed spi.Message)
	// OnCounters, when set, is handed the run's totals when it ends -- including when it ends
	// early, in which case they describe what was examined rather than the whole capture.
	//
	// A caller could add up the findings itself, and the first version of the report did, but
	// then the packet count in the capture is missing: it is a property of the capture rather
	// than of any finding, and a report saying a ten-packet capture holds zero packets is
	// worse than one saying nothing.
	OnCounters func(finding.Counters)
	// OnFinding, when set, is handed what every analysed packet turned out to be.
	//
	// This is how the analysis reports itself to something other than a log. Until it existed
	// the per-packet detail -- the original payload, the re-serialized payload, and where the
	// two first differ -- reached the outside world exclusively as a hex dump inside a zerolog
	// field, so anything wanting to act on a finding had to either parse log lines or walk the
	// capture a second time. The terminal interface does the latter to this day, and says so.
	OnFinding func(finding.Finding)
	// Progress receives progress. Leaving it nil means "decide from the CLI configuration",
	// which is what the command line wants; a terminal UI passes progress.NewChannel so that
	// it can draw progress inside its own layout instead of over the top of it.
	Progress progress.Reporter
	// Theme styles the default progress bar. Leaving it nil resolves one from the environment.
	Theme *tui.Theme
}

// reporter resolves the reporter this run should use.
func (o Options) reporter() progress.Reporter {
	if o.Progress != nil {
		return o.Progress
	}
	theme := tui.NewTheme(tui.OptionsFromEnv(true))
	if o.Theme != nil {
		theme = *o.Theme
	}
	// ForWriter, not NewCLI: the bar and the zerolog stream share this descriptor, so a bar
	// drawn into a redirected stderr would interleave with the log and corrupt both. That was
	// the measured defect, and this is the one place the decision is now made.
	return progress.ForWriter(o.Stderr, config.RootConfigInstance.HideProgressBar, theme)
}

// Analyze runs the parse, reserialize and compare loop over a capture.
//
// The context is honoured: the loop checks it between packets and stops cleanly, keeping the
// counts it has already gathered. It used to be context.TODO here, which made the check inside
// the loop unreachable -- the abort was implemented and could never be asked for.
func Analyze(ctx context.Context, pcapFile, protocolType string) error {
	return AnalyzeWithOutput(ctx, pcapFile, protocolType, os.Stdout, os.Stderr)
}

func AnalyzeWithOutput(ctx context.Context, pcapFile, protocolType string, stdout, stderr io.Writer) error {
	return AnalyzeWithOutputAndCallback(ctx, pcapFile, protocolType, stdout, stderr, nil)
}

func AnalyzeWithOutputAndCallback(ctx context.Context, pcapFile, protocolType string, stdout, stderr io.Writer, messageCallback func(parsed spi.Message)) error {
	return AnalyzeWithOptions(ctx, pcapFile, protocolType, Options{
		Stdout:          stdout,
		Stderr:          stderr,
		MessageCallback: messageCallback,
	})
}

// AnalyzeWithOptions is the full entry point; the others are conveniences over it.
func AnalyzeWithOptions(ctx context.Context, pcapFile, protocolType string, options Options) error {
	stdout := options.Stdout
	if stdout == nil {
		stdout = io.Discard
	}
	messageCallback := options.MessageCallback
	reporter := options.reporter()
	// Done from a defer as well: every early exit from the loop below - the package limit, a
	// nil packet, a cancelled context - has to release the line the bar is holding.
	defer reporter.Done()

	var filterExpression = config.AnalyzeConfigInstance.Filter
	if filterExpression != "" {
		log.Info().Str("filterExpression", filterExpression).Msg("Using global filter")
	}
	var mapPackets = func(in chan gopacket.Packet, packetInformationCreator func(packet gopacket.Packet) common.PacketInformation) chan gopacket.Packet {
		return in
	}
	var packageParse func(common.PacketInformation, []byte) (spi.Message, error)
	var serializePackage func(spi.Message) ([]byte, error)
	var prettyPrint = func(item spi.Message) {
		_, _ = fmt.Fprintf(stdout, "%v\n", item)
	}
	var byteOutput = hex.Dump
	// Resolve the caller-supplied name onto a canonical one before dispatching. The CLI accepts
	// aliases such as "bacnet"; the branches below only ever see canonical names.
	proto, err := protocol.Resolve(protocolType)
	if err != nil {
		return err
	}
	switch proto.Name {
	case protocol.BacnetIP.Name:
		if !config.AnalyzeConfigInstance.NoFilter {
			if config.AnalyzeConfigInstance.Filter == "" && config.BacnetConfigInstance.BacnetFilter != "" {
				log.Debug().Str("filter", config.BacnetConfigInstance.Filter).Msg("Setting bacnet filter")
				filterExpression = config.BacnetConfigInstance.BacnetFilter
			}
		} else {
			log.Info().Msg("All filtering disabled")
		}
		packageParse = bacnetanalyzer.PackageParse
		serializePackage = bacnetanalyzer.SerializePackage
	case protocol.CBus.Name:
		if !config.AnalyzeConfigInstance.NoFilter {
			if config.AnalyzeConfigInstance.Filter == "" && config.CBusConfigInstance.CBusFilter != "" {
				log.Debug().Str("filter", config.CBusConfigInstance.Filter).Msg("Setting cbus filter")
				filterExpression = config.CBusConfigInstance.CBusFilter
			}
		} else {
			log.Info().Msg("All filtering disabled")
		}
		analyzer := cbusanalyzer.Analyzer{Client: net.ParseIP(config.AnalyzeConfigInstance.Client)}
		analyzer.Init()
		packageParse = analyzer.PackageParse
		serializePackage = analyzer.SerializePackage
		// mappingCtx is cancelled when this function returns, which is what lets the mapping
		// goroutine exit on every early exit from the loop below - the package-number limit,
		// a nil packet, or a cancelled caller context. Without it that goroutine blocks on a
		// send forever and keeps logging.
		mappingCtx, cancelMapping := context.WithCancel(ctx)
		defer func() {
			cancelMapping()
			// Wait for the goroutine to actually finish, not merely to be told to stop, so
			// that nothing is still logging once this function has returned.
			analyzer.WaitForMapping()
		}()
		mapPackets = func(in chan gopacket.Packet, packetInformationCreator func(packet gopacket.Packet) common.PacketInformation) chan gopacket.Packet {
			return analyzer.MapPackets(mappingCtx, in, packetInformationCreator)
		}
		if !config.AnalyzeConfigInstance.NoCustomMapping {
			byteOutput = analyzer.ByteOutput
		} else {
			log.Info().Msg("Custom mapping disabled")
		}
	default:
		// Every other protocol is a codec plus a filter, which is what internal/codec holds.
		// The two branches above are the exceptions rather than the pattern: C-Bus tracks
		// request context across a session and BACnet has an adapter of its own.
		protocolCodec, known := codec.For(proto.Name)
		if !known {
			// Reachable only by adding a protocol to the name registry and nowhere else, which
			// has to fail loudly rather than silently analysing nothing.
			return errors.Errorf("protocol %s is registered but not implemented by the analyzer", proto.Name)
		}
		if !config.AnalyzeConfigInstance.NoFilter {
			if config.AnalyzeConfigInstance.Filter == "" && protocolCodec.DefaultFilter != "" {
				filterExpression = protocolCodec.DefaultFilter
				log.Debug().Str("filter", filterExpression).Str("protocol", proto.Name).
					Msg("Using the protocol's default filter")
			}
		} else {
			log.Info().Msg("All filtering disabled")
		}
		// A protocol whose two directions are encoded differently cannot be read at all without
		// knowing which way a packet went, and the only thing that says so is the client
		// address. Saying so once here is worth more than a capture's worth of parse failures.
		client := net.ParseIP(config.AnalyzeConfigInstance.Client)
		if protocolCodec.NeedsDirection && client == nil {
			// Printed, not merely logged. The default log level is "error", so this warning was
			// invisible in exactly the situation it exists for: pointed at a real Modbus capture
			// with no -c, the run reported half its packets as parse failures and said nothing
			// about why. Advice to the user is not a log line.
			notice(options.Stderr, "%s encodes requests and responses differently, and no client "+
				"address was given: every packet will be read as a request, so every response "+
				"will look like a parse failure. Pass -c <client ip>.", proto.Name)
			log.Warn().Str("protocol", proto.Name).Msg("no client address for a directional protocol")
		}
		packageParse = func(info common.PacketInformation, payload []byte) (spi.Message, error) {
			return protocolCodec.Parse(ctx, payload, isResponse(info, client))
		}
		serializePackage = func(message spi.Message) ([]byte, error) {
			return protocolCodec.Serialize(ctx, message)
		}
	}

	log.Info().
		Str("pcapFile", pcapFile).
		Str("protocolType", proto.Name).
		Str("filterExpression", filterExpression).
		Msg("Analyzing pcap file pcapFile with protocolType and filter filterExpression now")
	handle, numberOfPackage, timestampToIndexMap, err := pcaphandler.GetIndexedPcapHandle(pcapFile, filterExpression)
	if err != nil {
		return errors.Wrap(err, "Error getting handle")
	}
	log.Info().Int("numberOfPackage", numberOfPackage).Msg("Starting to analyze numberOfPackage packages")
	defer handle.Close()
	log.Debug().Interface("handle", handle).Int("numberOfPackage", numberOfPackage).Msg("got handle")
	source := pcaphandler.GetPacketSource(handle)
	reporter.Start(numberOfPackage, "Analyzing packages")
	currentPackageNum := uint(0)
	// One counting rule, shared with the terminal interface, and one place that reports a
	// finding. The three loose integers this replaces were incremented next to a log call, so
	// the totals and the detail could and did diverge from the interface's.
	counters := finding.Counters{TotalInCapture: numberOfPackage}
	report := func(found finding.Finding) {
		if options.OnFinding != nil {
			options.OnFinding(found)
		}
	}
	for packet := range mapPackets(source.Packets(), func(packet gopacket.Packet) common.PacketInformation {
		return createPacketInformation(pcapFile, packet, timestampToIndexMap)
	}) {
		if err := ctx.Err(); err != nil {
			log.Info().Err(err).Uint("currentPackageNum", currentPackageNum).Msg("Aborted after currentPackageNum packages")
			break
		}
		currentPackageNum++
		if currentPackageNum < config.AnalyzeConfigInstance.StartPackageNumber {
			log.Debug().
				Uint("currentPackageNum", currentPackageNum).
				Uint("startPackageNum", config.AnalyzeConfigInstance.StartPackageNumber).
				Msg("Skipping package number currentPackageNum (till no. startPackageNum)")
			continue
		}
		if currentPackageNum > config.AnalyzeConfigInstance.PackageNumberLimit {
			log.Warn().
				Uint("PackageNumberLimit", config.AnalyzeConfigInstance.PackageNumberLimit).
				Msg("Aborting reading packages because we hit the limit of packageNumberLimit")
			break
		}
		if packet == nil {
			log.Debug().Msg("Done reading packages. (nil returned)")
			break
		}
		reporter.Advance(1)
		packetInformation := createPacketInformation(pcapFile, packet, timestampToIndexMap)
		realPacketNumber := packetInformation.PacketNumber
		if filteredPackage, ok := packet.(common.FilteredPackage); ok {
			log.Info().Err(filteredPackage.FilterReason()).
				Int("realPacketNumber", realPacketNumber).Msg("No.[realPacketNumber] was filtered")
			continue
		}

		applicationLayer := packet.ApplicationLayer()
		if applicationLayer == nil {
			log.Info().Stringer("packetInformation", packetInformation).
				Int("realPacketNumber", realPacketNumber).
				Msg("No.[realPacketNumber] No application layer")
			continue
		}
		payload := applicationLayer.Payload()
		if parsed, err := packageParse(packetInformation, payload); err != nil {
			switch {
			case errors.Is(err, common.ErrUnterminatedPackage),
				errors.Is(err, common.ErrEmptyPackage),
				errors.Is(err, common.ErrEcho):
				// Not a defect: the protocol itself says this is not a whole message. It is
				// still reported, because a report that shows only failures cannot say how
				// much of the capture was actually examined.
				verdict, reason := finding.Classify(err)
				counters.Count(verdict)
				report(finding.Finding{
					Number:     realPacketNumber,
					Protocol:   protocolType,
					Verdict:    verdict,
					Reason:     reason,
					Original:   payload,
					DiffOffset: -1,
				})
				log.Info().Stringer("packetInformation", packetInformation).
					Int("realPacketNumber", realPacketNumber).
					Str("reason", reason).
					Msg("No.[realPacketNumber] skipped: reason")
			default:
				counters.Count(finding.VerdictParseFail)
				report(finding.Finding{
					Number:     realPacketNumber,
					Protocol:   protocolType,
					Verdict:    finding.VerdictParseFail,
					Reason:     err.Error(),
					Original:   payload,
					DiffOffset: -1,
				})
				log.Error().
					Err(err).
					Stringer("packetInformation", packetInformation).
					Int("realPacketNumber", realPacketNumber).
					Str("byteOutput", byteOutput(payload)).
					Msg("No.[realPacketNumber] Error parsing package.")
			}
			continue
		} else {
			if messageCallback != nil {
				messageCallback(parsed)
			}
			log.Info().
				Stringer("packetInformation", packetInformation).
				Int("realPacketNumber", realPacketNumber).
				Msg("No.[realPacketNumber] Parsed")
			if config.AnalyzeConfigInstance.Verbosity > 1 {
				prettyPrint(parsed)
			}
			if config.AnalyzeConfigInstance.OnlyParse {
				log.Trace().Msg("only parsing")
				continue
			}
			serializedBytes, err := serializePackage(parsed)
			if err != nil {
				counters.Count(finding.VerdictSerializeFail)
				report(finding.Finding{
					Number:     realPacketNumber,
					Protocol:   protocolType,
					Summary:    finding.MessageName(parsed),
					Verdict:    finding.VerdictSerializeFail,
					Reason:     err.Error(),
					Original:   payload,
					DiffOffset: -1,
				})
				log.Warn().
					Err(err).
					Stringer("packetInformation", packetInformation).
					Int("realPacketNumber", realPacketNumber).
					Msg("No.[realPacketNumber] Error serializing")
				continue
			}
			if config.AnalyzeConfigInstance.NoBytesCompare {
				log.Trace().Msg("not comparing bytes")
				continue
			}
			if offset := finding.FirstDifference(payload, serializedBytes); offset >= 0 {
				counters.Count(finding.VerdictBytesDiffer)
				report(finding.Finding{
					Number:       realPacketNumber,
					Protocol:     protocolType,
					Summary:      finding.MessageName(parsed),
					Verdict:      finding.VerdictBytesDiffer,
					Reason:       strconv.Itoa(finding.DifferingBytes(payload, serializedBytes)) + " bytes differ",
					Original:     payload,
					Reserialized: serializedBytes,
					DiffOffset:   offset,
				})
				log.Warn().
					Stringer("packetInformation", packetInformation).
					Int("realPacketNumber", realPacketNumber).
					Str("byteOutputPayload", byteOutput(payload)).
					Str("byteSerializedBytes", byteOutput(serializedBytes)).
					Msg("No.[realPacketNumber] Bytes don't match.")
				if config.AnalyzeConfigInstance.Verbosity > 0 {
					_, _ = fmt.Fprintf(stdout, "Original bytes\n%s\n%s\n", hex.Dump(payload), hex.Dump(serializedBytes))
				}
			} else {
				counters.Count(finding.VerdictOK)
				report(finding.Finding{
					Number:       realPacketNumber,
					Protocol:     protocolType,
					Summary:      finding.MessageName(parsed),
					Verdict:      finding.VerdictOK,
					Original:     payload,
					Reserialized: serializedBytes,
					DiffOffset:   -1,
				})
			}
		}
	}

	if options.OnCounters != nil {
		options.OnCounters(counters)
	}
	// The field names are the log's contract -- a characterisation test reads them, and so may
	// anything parsing these logs -- so they stay as they are while the values now come from
	// the shared counters.
	log.Info().
		Uint("currentPackageNum", currentPackageNum).
		Int("numberOfPackage", numberOfPackage).
		Int("parseFails", counters.ParseFail).
		Int("serializeFails", counters.SerializeFail).
		Int("compareFails", counters.CompareFail).
		Msg("Done evaluating currentPackageNum of numberOfPackage packages (parseFails failed to parse, serializeFails failed to serialize and compareFails failed in byte comparison)")
	return nil
}

// notice tells the user something they need to act on, whatever the log level is set to.
//
// It goes to the run's stderr rather than through the logger because the two have different
// audiences: the log is for working out afterwards what happened, and a notice is for the person
// waiting at the terminal now. A notice that a log level can hide is not a notice.
func notice(stderr io.Writer, format string, args ...any) {
	if stderr == nil {
		return
	}
	_, _ = fmt.Fprintf(stderr, "warning: "+format+"\n", args...)
}

// isResponse reports whether a packet travelled from the device to the client.
//
// Unknown when there is no client address, and "request" is the honest default then: it is what
// the protocols' own reference vectors mostly are, and guessing the other way would make a
// capture look worse than it is.
func isResponse(info common.PacketInformation, client net.IP) bool {
	if client == nil || info.SrcIp == nil {
		return false
	}
	return !info.SrcIp.Equal(client)
}

func createPacketInformation(pcapFile string, packet gopacket.Packet, timestampToIndexMap map[time.Time]int) common.PacketInformation {
	packetTimestamp := packet.Metadata().Timestamp
	realPacketNumber := timestampToIndexMap[packetTimestamp]
	description := fmt.Sprintf("No.[%d] timestamp: %v, %s", realPacketNumber, packetTimestamp, pcapFile)
	packetInformation := common.PacketInformation{
		PacketNumber:    realPacketNumber,
		PacketTimestamp: packetTimestamp,
		Description:     description,
	}
	if networkLayer, ok := packet.NetworkLayer().(*layers.IPv4); ok {
		packetInformation.SrcIp = networkLayer.SrcIP
		packetInformation.DstIp = networkLayer.DstIP
	}
	return packetInformation
}
