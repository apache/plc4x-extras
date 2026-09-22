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

package extractor

import (
	"context"
	"fmt"
	"io"
	"net"
	"os"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/gopacket/gopacket/layers"
	"github.com/rs/zerolog/log"

	"github.com/apache/plc4x-extras/plc4go/tools/internal/progress"
	"github.com/apache/plc4x-extras/plc4go/tools/internal/tui"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/pcaphandler"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/protocol"
)

// Options carries the collaborators one extraction run needs: where its output goes, who is
// told how far it has got, and what it styles its direction markers with.
//
// It is a struct rather than more parameters on ExtractWithOutput because that function's
// signature is part of the surface the pcap tool's tests pin.
type Options struct {
	// Stdout receives the extracted payloads. This is routinely a pipe or a file.
	Stdout io.Writer
	// Stderr receives the direction markers and may carry a progress bar.
	Stderr io.Writer
	// Progress receives progress. Leaving it nil means "decide from the CLI configuration";
	// a terminal UI passes progress.NewChannel.
	Progress progress.Reporter
	// Theme styles the direction markers and the default progress bar. Leaving it nil resolves
	// one per writer, so that a redirected stream gets no escape sequences.
	Theme *tui.Theme
}

// themeFor resolves the theme to style writes to w with.
//
// The colour is switched off when w is not a terminal. That is deliberately the behaviour of
// the fatih/color writers this replaces: extract's payload output is routinely piped into a
// file or another tool, and escape sequences in it corrupt the transcript.
func (o Options) themeFor(w io.Writer) tui.Theme {
	if o.Theme != nil {
		return *o.Theme
	}
	themeOptions := tui.OptionsFromEnv(true)
	themeOptions.NoColor = themeOptions.NoColor || !progress.IsTerminal(w)
	return tui.NewTheme(themeOptions)
}

// reporter resolves the reporter this run should use.
func (o Options) reporter() progress.Reporter {
	if o.Progress != nil {
		return o.Progress
	}
	// ForWriter, not NewCLI: the bar and the zerolog stream share this descriptor, so a bar
	// drawn into a redirected stderr would interleave with the log and corrupt both.
	return progress.ForWriter(o.Stderr, config.RootConfigInstance.HideProgressBar, o.themeFor(o.Stderr))
}

// Extract dumps the application payloads of a capture.
//
// The context is honoured: the loop checks it between packets and stops cleanly. It used to be
// context.TODO here, which made the check inside the loop unreachable -- the abort was
// implemented and could never be asked for.
//
// os.Stdout and os.Stderr, rather than the go-ansi wrappers that used to be here: those
// wrappers hid the file descriptor, so nothing downstream could tell whether it was writing
// to a terminal, which is how the bar came to be drawn over the log in the first place.
func Extract(ctx context.Context, pcapFile, protocolType string) error {
	return ExtractWithOutput(ctx, pcapFile, protocolType, os.Stdout, os.Stderr)
}

func ExtractWithOutput(ctx context.Context, pcapFile, protocolType string, stdout, stderr io.Writer) error {
	return ExtractWithOptions(ctx, pcapFile, protocolType, Options{Stdout: stdout, Stderr: stderr})
}

// ExtractWithOptions is the full entry point; the others are conveniences over it.
func ExtractWithOptions(ctx context.Context, pcapFile, protocolType string, options Options) error {
	stdout := options.Stdout
	if stdout == nil {
		stdout = io.Discard
	}
	stderr := options.Stderr
	if stderr == nil {
		stderr = io.Discard
	}
	reporter := options.reporter()
	// Done from a defer as well: every early exit from the loop below has to release the line
	// the bar is holding.
	defer reporter.Done()

	var printPayload = func(packetInformation common.PacketInformation, item []byte) {
		_, _ = fmt.Fprintf(stdout, "%x\n", item)
	}
	proto, err := protocol.Resolve(protocolType)
	if err != nil {
		return err
	}
	switch proto.Name {
	case protocol.BacnetIP.Name:
		// nothing special as this is byte based
	case protocol.CBus.Name:
		// c-bus is string based so we consume the string and print it
		clientIp := net.ParseIP(config.ExtractConfigInstance.Client)
		payloadTheme := options.themeFor(stdout)
		markerTheme := options.themeFor(stderr)
		// Red for what the PCI said, green for what the client asked - the same coding the
		// fatih/color writers used, now resolved through the theme so that it adapts to the
		// background, honours NO_COLOR and degrades to bold on a colourless terminal.
		serverResponseStyle := payloadTheme.Err
		clientRequestStyle := payloadTheme.Ok
		serverResponseMarkerStyle := markerTheme.Err
		clientRequestMarkerStyle := markerTheme.Ok
		// Bold only where styling is wanted at all. A colourless theme must emit NO escape
		// sequence, not merely no colour: this stream is routinely redirected to a file, and a
		// stray "\x1b[1m" corrupts the transcript just as surely as a colour code would. That
		// was the behaviour of the fatih/color writers this replaces.
		if !markerTheme.IsNoColor() {
			serverResponseMarkerStyle = serverResponseMarkerStyle.Bold(true)
			clientRequestMarkerStyle = clientRequestMarkerStyle.Bold(true)
		}
		printPayload = func(packetInformation common.PacketInformation, payload []byte) {
			payloadString := ""
			suffix := ""
			extraInformation := ""
			if config.ExtractConfigInstance.Verbosity > 2 {
				extraInformation = fmt.Sprintf("(No.[%d])", packetInformation.PacketNumber)
			}
			if len(payload) > 0 {
				if properTerminated := payload[len(payload)-1] == 0x0D || payload[len(payload)-1] == 0x0A; properTerminated {
					suffix = "\n"
				}
				quotedPayload := fmt.Sprintf("%+q", payload)
				unquotedPayload := quotedPayload[1 : len(quotedPayload)-1]
				payloadString = unquotedPayload
			}
			// The arrow comes from the glyph set rather than being written as "<--" here, so
			// that a terminal without Unicode gets the ASCII arrow of the same display width
			// instead of mojibake.
			if isResponse := packetInformation.DstIp.Equal(clientIp); isResponse {
				if config.ExtractConfigInstance.ShowDirectionalIndicators {
					marker := fmt.Sprintf("%s(%spci)", extraInformation, markerTheme.Glyphs.Inbound)
					_, _ = fmt.Fprint(stderr, serverResponseMarkerStyle.Render(marker))
				}
				_, _ = fmt.Fprint(stdout, serverResponseStyle.Render(payloadString)+suffix)
			} else {
				if config.ExtractConfigInstance.ShowDirectionalIndicators {
					marker := fmt.Sprintf("%s(%spci)", extraInformation, markerTheme.Glyphs.Outbound)
					_, _ = fmt.Fprint(stderr, clientRequestMarkerStyle.Render(marker))
				}
				_, _ = fmt.Fprint(stdout, clientRequestStyle.Render(payloadString)+suffix)
			}
		}
	}
	filterExpression := config.ExtractConfigInstance.Filter
	log.Info().
		Str("pcapFile", pcapFile).
		Str("protocolType", protocolType).
		Str("filterExpression", filterExpression).
		Msg("Analyzing pcap file pcapFile with protocolType protocolType and filter filterExpression now")

	handle, numberOfPackage, timestampToIndexMap, err := pcaphandler.GetIndexedPcapHandle(pcapFile, filterExpression)
	if err != nil {
		return errors.Wrap(err, "Error getting handle")
	}
	log.Info().Int("numberOfPackage", numberOfPackage).Msg("Starting to analyze numberOfPackage packages")
	defer handle.Close()
	log.Debug().Interface("handle", handle).Int("numberOfPackage", numberOfPackage).Msg("got handle")
	source := pcaphandler.GetPacketSource(handle)
	// "Extracting", not the "Analyzing packages..." this bar was copied from: the description
	// is now a parameter, so it may as well say what is actually running.
	reporter.Start(numberOfPackage, "Extracting packages")
	currentPackageNum := uint(0)
	parseFails := 0
	serializeFails := 0
	compareFails := 0
	for packet := range source.Packets() {
		if errors.Is(ctx.Err(), context.Canceled) {
			log.Info().
				Uint("currentPackageNum", currentPackageNum).
				Msg("Aborted after currentPackageNum packages")
			break
		}
		currentPackageNum++
		if currentPackageNum < config.ExtractConfigInstance.StartPackageNumber {
			log.Debug().
				Uint("currentPackageNum", currentPackageNum).
				Uint("startPackageNumber", config.ExtractConfigInstance.StartPackageNumber).
				Msg("Skipping package number currentPackageNum (till no. startPackageNumber)")
			continue
		}
		if currentPackageNum > config.ExtractConfigInstance.PackageNumberLimit {
			log.Warn().
				Uint("packageNumberLimit", config.ExtractConfigInstance.PackageNumberLimit).
				Msg("Aborting reading packages because we hit the limit of packageNumberLimit")
			break
		}
		if packet == nil {
			log.Debug().Msg("Done reading packages. (nil returned)")
			break
		}
		reporter.Advance(1)
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

		var payload []byte
		applicationLayer := packet.ApplicationLayer()
		if applicationLayer == nil {
			log.Info().Stringer("packetInformation", packetInformation).Int("realPacketNumber", realPacketNumber).Msg("No.[realPacketNumber] No application layer")
		} else {
			payload = applicationLayer.Payload()
		}

		log.Debug().Hex("payload", payload).Msg("Got payload")
		if config.ExtractConfigInstance.Verbosity > 1 {
			printPayload(packetInformation, payload)
		}
	}
	_, _ = fmt.Fprintf(stdout, "\n")

	log.Info().
		Uint("currentPackageNum", currentPackageNum).
		Int("numberOfPackage", numberOfPackage).
		Int("parseFails", parseFails).
		Int("serializeFails", serializeFails).
		Int("compareFails", compareFails).
		Msg("Done evaluating currentPackageNum of numberOfPackage packages (parseFails failed to parse, serializeFails failed to serialize and compareFails failed in byte comparison)")
	return nil
}
