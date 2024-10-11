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

package cbusanalyzer

import (
	"context"
	"fmt"
	"net"
	"reflect"

	readWriteModel "github.com/apache/plc4x/plc4go/protocols/cbus/readwrite/model"
	"github.com/apache/plc4x/plc4go/spi"
	"github.com/gopacket/gopacket"
	"github.com/pkg/errors"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/config"
	"github.com/apache/plc4x-extras/plc4go/tools/plc4xpcapanalyzer/internal/common"
)

type Analyzer struct {
	Client                          net.IP
	requestContext                  readWriteModel.RequestContext
	cBusOptions                     readWriteModel.CBusOptions
	initialized                     bool
	currentInboundPayloads          map[string][]byte
	currentPrefilterInboundPayloads map[string][]byte
	mappedPacketChan                chan gopacket.Packet

	lastParsePayload []byte
	lastMapPayload   []byte
}

func (a *Analyzer) Init() {
	if a.initialized {
		return
	}
	a.requestContext = readWriteModel.NewRequestContext(false)
	a.cBusOptions = readWriteModel.NewCBusOptions(config.CBusConfigInstance.Connect, config.CBusConfigInstance.Smart, config.CBusConfigInstance.Idmon, config.CBusConfigInstance.Exstat, config.CBusConfigInstance.Monitor, config.CBusConfigInstance.Monall, config.CBusConfigInstance.Pun, config.CBusConfigInstance.Pcn, config.CBusConfigInstance.Srchk)
	a.currentInboundPayloads = make(map[string][]byte)
	a.currentPrefilterInboundPayloads = make(map[string][]byte)
	a.initialized = true
}

func (a *Analyzer) PackageParse(packetInformation common.PacketInformation, payload []byte) (spi.Message, error) {
	if !a.initialized {
		log.Warn().Msg("Not initialized... doing that now")
		a.Init()
	}
	cBusOptions := a.cBusOptions
	log.Debug().
		Stringer("packetInformation", packetInformation).
		Stringer("requestContext", a.requestContext).
		Stringer("cBusOptions", cBusOptions).
		Bytes("payload", payload).
		Msg("Parsing")
	isResponse := a.isResponse(packetInformation)
	if isResponse {
		// Responses should have a checksum
		cBusOptions = readWriteModel.NewCBusOptions(
			cBusOptions.GetConnect(),
			cBusOptions.GetSmart(),
			cBusOptions.GetIdmon(),
			cBusOptions.GetExstat(),
			cBusOptions.GetMonitor(),
			cBusOptions.GetMonall(),
			cBusOptions.GetPun(),
			cBusOptions.GetPcn(),
			true,
		)
	}
	mergeCallback := func(index int) {
		log.Warn().Stringer("packetInformation", packetInformation).Int("index", index).Msg("we have a split at index")
	}
	currentPayload, err := a.getCurrentPayload(packetInformation, payload, mergeCallback, a.currentInboundPayloads, &a.lastParsePayload)
	if err != nil {
		return nil, err
	}
	if reflect.DeepEqual(currentPayload, a.lastParsePayload) {
		return nil, common.ErrEcho
	}
	a.lastParsePayload = currentPayload
	parse, err := readWriteModel.CBusMessageParse[readWriteModel.CBusMessage](context.TODO(), currentPayload, isResponse, a.requestContext, cBusOptions)
	if err != nil {
		if secondParse, err := readWriteModel.CBusMessageParse[readWriteModel.CBusMessage](context.TODO(), currentPayload, isResponse, readWriteModel.NewRequestContext(false), readWriteModel.NewCBusOptions(false, false, false, false, false, false, false, false, false)); err != nil {
			log.Debug().Err(err).Msg("Second parse failed too")
			return nil, errors.Wrap(err, "Error parsing CBusCommand")
		} else {
			log.Warn().
				Stringer("packetInformation", packetInformation).
				Stringer("secondParse", secondParse).
				Msg("package got overridden by second parse... probably a MMI")
			parse = secondParse
		}
	}
	a.requestContext = CreateRequestContextWithInfoCallback(parse, func(infoString string) {
		log.Debug().
			Int("packetNumber", packetInformation.PacketNumber).
			Str("infoString", infoString).
			Msg("No.[packetNumber] infoString")
	})
	log.Debug().Stringer("parse", parse).Msg("Parsed c-bus command")
	return parse, nil
}

func (a *Analyzer) isResponse(packetInformation common.PacketInformation) bool {
	isResponse := packetInformation.DstIp.Equal(a.Client)
	log.Debug().Stringer("packetInformation", packetInformation).Bool("isResponse", isResponse).Msg("isResponse")
	return isResponse
}

func (a *Analyzer) getCurrentPayload(packetInformation common.PacketInformation, payload []byte, mergeCallback func(int), currentInboundPayloads map[string][]byte, lastPayload *[]byte) ([]byte, error) {
	srcUip := packetInformation.SrcIp.String()
	payload = filterXOnXOff(payload)
	if len(payload) == 0 {
		return nil, common.ErrEmptyPackage
	}
	currentPayload := currentInboundPayloads[srcUip]
	if currentPayload != nil {
		log.Debug().Func(func(e *zerolog.Event) {
			e.
				Bytes("currentPayload", currentPayload).
				Bytes("actualPayload", payload).
				Interface("allPayload", append(currentPayload, payload...)).
				Msg("Prepending current payload currentPayload to actual payload actualPayload: allPayload")
		})
		currentPayload = append(currentPayload, payload...)
	} else {
		currentPayload = payload
	}
	if len(currentPayload) == 1 && currentPayload[0] == '!' {
		// This is an errormessage from the server
		return currentPayload, nil
	}
	containsError := false
	// We ensure that there are no random ! in the string
	currentPayload, containsError = filterOneServerError(currentPayload)
	if containsError {
		// Save the current inbound payload for the next try
		currentInboundPayloads[srcUip] = currentPayload
		return []byte{'!'}, nil
	}
	// Check if we have a termination in the middle
	isMergedMessage, shouldClearInboundPayload := mergeCheck(&currentPayload, srcUip, mergeCallback, currentInboundPayloads, lastPayload)
	if !isMergedMessage {
		// When we have a merge message we already set the current payload to the tail
		currentInboundPayloads[srcUip] = currentPayload
	} else {
		log.Debug().Stringer("packetInformation", packetInformation).
			Interface("remainder", currentInboundPayloads[srcUip]).
			Msg("Remainder %+q")
	}
	if lastElement := currentPayload[len(currentPayload)-1]; (lastElement != '\r') && (lastElement != '\n') {
		return nil, common.ErrUnterminatedPackage
	} else {
		log.Debug().Stringer("packetInformation", packetInformation).
			Uint8("lastElement", lastElement).
			Msg("Last element")
		if shouldClearInboundPayload {
			if currentSavedPayload := currentInboundPayloads[srcUip]; currentSavedPayload != nil {
				// We remove our current payload from the beginning of the cache
				for i, b := range currentPayload {
					if currentSavedPayload[i] != b {
						panic("programming error... at this point they should start with the identical bytes")
					}
				}
			}
			currentInboundPayloads[srcUip] = nil
		}
	}
	log.Debug().Stringer("packetInformation", packetInformation).
		Bytes("currentPayload", currentPayload).
		Msg("Returning payload")
	return currentPayload, nil
}

func mergeCheck(currentPayload *[]byte, srcUip string, mergeCallback func(int), currentInboundPayloads map[string][]byte, lastPayload *[]byte) (isMergedMessage, shouldClearInboundPayload bool) {
	// Check if we have a merged message
	for i, b := range *currentPayload {
		if i == 0 {
			// we ignore the first byte as this is typical for reset etc... so maybe this is good or bad we will see
			continue
		}
		switch b {
		case 0x0D:
			if i+1 < len(*currentPayload) && (*currentPayload)[i+1] == 0x0A {
				// If we know the next is a newline we jump to that index...
				i++
			}
			// ... other than that the logic is the same
			fallthrough
		case 0x0A:
			// We have a merged message if we are not at the end
			if i < len(*currentPayload)-1 {
				headPayload := (*currentPayload)[:i+1]
				tailPayload := (*currentPayload)[i+1:]
				if reflect.DeepEqual(headPayload, *lastPayload) {
					// This means that we have a merge where the last payload is an echo. In that case we discard that here to not offset all numbers
					*currentPayload = tailPayload
					log.Debug().Bytes("headPayload", headPayload).Msg("We cut the echo message %s out of the response to keep numbering")
					return mergeCheck(currentPayload, srcUip, mergeCallback, currentInboundPayloads, lastPayload)
				} else {
					if mergeCallback != nil {
						mergeCallback(i)
					}
					// In this case we need to put the tail into our "buffer"
					currentInboundPayloads[srcUip] = tailPayload
					// and use the beginning as current payload
					*currentPayload = headPayload
					return true, false
				}
			}
		}
	}
	return false, true
}

func filterXOnXOff(payload []byte) []byte {
	n := 0
	for i, b := range payload {
		switch b {
		case 0x11: // Filter XON
			fallthrough
		case 0x13: // Filter XOFF
			log.Trace().
				Uint8("b", b).
				Int("i", i).
				Bytes("payload", payload).
				Msg("Filtering b at i for payload")
		default:
			payload[n] = b
			n++
		}
	}
	return payload[:n]
}

func filterOneServerError(unfilteredPayload []byte) (filteredPayload []byte, containsError bool) {
	for i, b := range unfilteredPayload {
		if b == '!' {
			return append(unfilteredPayload[:i], unfilteredPayload[i+1:]...), true

		}
	}
	return unfilteredPayload, false
}

func (a *Analyzer) SerializePackage(message spi.Message) ([]byte, error) {
	if message, ok := message.(readWriteModel.CBusMessage); !ok {
		log.Fatal().Type("message", message).Msg("Unsupported type supplied")
		panic("unreachable statement")
	} else {
		theBytes, err := message.Serialize()
		if err != nil {
			return nil, errors.Wrap(err, "Error serializing")
		}
		return theBytes, nil
	}
}

// MapPackets reorders the packages as they were not split
func (a *Analyzer) MapPackets(in chan gopacket.Packet, packetInformationCreator func(packet gopacket.Packet) common.PacketInformation) chan gopacket.Packet {
	if a.mappedPacketChan == nil {
		a.mappedPacketChan = make(chan gopacket.Packet)
		go func() {
			defer close(a.mappedPacketChan)
		mappingLoop:
			for packet := range in {
				switch {
				case packet == nil:
					log.Debug().Msg("Done reading packages. (nil returned)")
					a.mappedPacketChan <- nil
					break mappingLoop
				case packet.ApplicationLayer() == nil:
					a.mappedPacketChan <- packet
				default:
					packetInformation := packetInformationCreator(packet)
					mergeCallback := func(index int) {
						log.Warn().Stringer("packetInformation", packetInformation).
							Int("index", index).
							Msg("we have a split at index")
					}
					if payload, err := a.getCurrentPayload(packetInformation, packet.ApplicationLayer().Payload(), mergeCallback, a.currentPrefilterInboundPayloads, &a.lastMapPayload); err != nil {
						log.Debug().Err(err).Stringer("packetInformation", packetInformation).Msg("Filtering message")
						a.mappedPacketChan <- common.NewFilteredPackage(err, packet)
					} else {
						currentApplicationLayer := packet.ApplicationLayer()
						newPayload := gopacket.Payload(payload)
						if !reflect.DeepEqual(currentApplicationLayer.Payload(), payload) {
							log.Debug().
								Bytes("currentPayload", currentApplicationLayer.Payload()).
								Bytes("payload", payload).
								Msg("Replacing payload currentPayload with payload")
							packet = &manipulatedPackage{Packet: packet, newApplicationLayer: newPayload}
						}
						a.lastMapPayload = payload
						a.mappedPacketChan <- packet
					}
				}
			}
		}()
	}
	return a.mappedPacketChan
}

// ByteOutput returns the string representation as usually this is ASCII over serial... so this output is much more useful in that context
func (a *Analyzer) ByteOutput(data []byte) string {
	return fmt.Sprintf("%+q\n", data)
}

type manipulatedPackage struct {
	gopacket.Packet
	newApplicationLayer gopacket.ApplicationLayer
}

func (p *manipulatedPackage) SetApplicationLayer(l gopacket.ApplicationLayer) {
	p.newApplicationLayer = l
}

func (p *manipulatedPackage) ApplicationLayer() gopacket.ApplicationLayer {
	return p.newApplicationLayer
}

func CreateRequestContextWithInfoCallback(cBusMessage readWriteModel.CBusMessage, infoCallBack func(string)) readWriteModel.RequestContext {
	if infoCallBack == nil {
		infoCallBack = func(_ string) {}
	}
	switch cBusMessage := cBusMessage.(type) {
	case readWriteModel.CBusMessageToServer:
		switch request := cBusMessage.GetRequest().(type) {
		case readWriteModel.RequestDirectCommandAccess:
			sendIdentifyRequestBefore := false
			infoCallBack("CAL request detected")
			switch request.GetCalData().(type) {
			case readWriteModel.CALDataIdentify:
				sendIdentifyRequestBefore = true
			}
			return readWriteModel.NewRequestContext(sendIdentifyRequestBefore)
		case readWriteModel.RequestCommand:
			switch command := request.GetCbusCommand().(type) {
			case readWriteModel.CBusCommandPointToPoint:
				sendIdentifyRequestBefore := false
				infoCallBack("CAL request detected")
				switch command.GetCommand().GetCalData().(type) {
				case readWriteModel.CALDataIdentify:
					sendIdentifyRequestBefore = true
				}
				return readWriteModel.NewRequestContext(sendIdentifyRequestBefore)
			}
		case readWriteModel.RequestObsolete:
			sendIdentifyRequestBefore := false
			infoCallBack("CAL request detected")
			switch request.GetCalData().(type) {
			case readWriteModel.CALDataIdentify:
				sendIdentifyRequestBefore = true
			}
			return readWriteModel.NewRequestContext(sendIdentifyRequestBefore)
		}
	case readWriteModel.CBusMessageToClient:
		// We received a request, so we need to reset our flags
		return readWriteModel.NewRequestContext(false)
	}
	return readWriteModel.NewRequestContext(false)
}
