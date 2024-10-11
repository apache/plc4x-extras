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
	"fmt"
	"strings"

	plc4go "github.com/apache/plc4x/plc4go/pkg/api"
	"github.com/apache/plc4x/plc4go/pkg/api/drivers"
	"github.com/apache/plc4x/plc4go/pkg/api/transports"
	"github.com/pkg/errors"
	"github.com/rivo/tview"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func InitSubsystem() {
	logLevel := zerolog.InfoLevel
	if configuredLevel := config.LogLevel; configuredLevel != "" {
		if parsedLevel, err := zerolog.ParseLevel(configuredLevel); err != nil {
			panic(err)
		} else {
			logLevel = parsedLevel
		}
	}

	log.Logger = log.
		//// Enable below if you want to see the filenames
		//With().Caller().Logger().
		Output(zerolog.NewConsoleWriter(
			func(w *zerolog.ConsoleWriter) {
				w.Out = tview.ANSIWriter(consoleOutput)
			},
			func(w *zerolog.ConsoleWriter) {
				w.FormatFieldValue = func(i interface{}) string {
					if aString, ok := i.(string); ok && strings.Contains(aString, "\\n") {
						return fmt.Sprintf("\x1b[%dm%v\x1b[0m", 31, "see below")
					}
					return fmt.Sprintf("%s", i)
				}
				w.FormatExtra = func(m map[string]interface{}, buffer *bytes.Buffer) error {
					for key, i := range m {
						if aString, ok := i.(string); ok && strings.Contains(aString, "\n") {
							buffer.WriteString("\n")
							buffer.WriteString(fmt.Sprintf("\x1b[%dm%v\x1b[0m", 32, "field "+key))
							buffer.WriteString(":\n" + aString)
						}
					}
					return nil
				}
			},
		),
		).
		Level(logLevel)

	driverManager = plc4go.NewPlcDriverManager()

	// We offset the commands executed with the last commands
	commandsExecuted = len(config.History.Last10Commands)
	outputCommandHistory()

	for _, driver := range config.AutoRegisterDrivers {
		log.Info().Str("driver", driver).Msg("Auto register driver")
		if err := validateDriverParam(driver); err != nil {
			log.Err(err).Msg("Invalid configuration")
			continue
		}
		_ = registerDriver(driver)
	}
}

func outputCommandHistory() {
	_, _ = fmt.Fprintln(commandOutput, "[#0000ff]Last 10 commands[white]")
	for i, command := range config.History.Last10Commands {
		_, _ = fmt.Fprintf(commandOutput, "   [#00ff00]%d[white]: [\"%d\"]%s[\"\"]\n", i, i, tview.Escape(command))
	}
}

func validateDriverParam(driver string) error {
	for _, protocol := range protocolList {
		if protocol == driver {
			return nil
		}
	}
	return errors.Errorf("protocol %s not found", driver)
}

var tcpRegistered, udpRegistered bool

func registerDriver(driverId string) error {
	if _, ok := registeredDrivers[driverId]; ok {
		return errors.Errorf("%s already registered", driverId)
	}
	var driver plc4go.PlcDriver
	switch driverId {
	case "ads":
		driver = drivers.RegisterAdsDriver(driverManager)
		if !tcpRegistered {
			transports.RegisterTcpTransport(driverManager)
			tcpRegistered = true
		}
	case "bacnetip":
		driver = drivers.RegisterBacnetDriver(driverManager)
		if !udpRegistered {
			transports.RegisterUdpTransport(driverManager)
			udpRegistered = true
		}
	case "c-bus":
		driver = drivers.RegisterCBusDriver(driverManager)
		if !tcpRegistered {
			transports.RegisterTcpTransport(driverManager)
			tcpRegistered = true
		}
	case "s7":
		driver = drivers.RegisterS7Driver(driverManager)
		if !tcpRegistered {
			transports.RegisterTcpTransport(driverManager)
			tcpRegistered = true
		}
	case "opcua":
		driver = drivers.RegisterOpcuaDriver(driverManager)
		if !tcpRegistered {
			transports.RegisterTcpTransport(driverManager)
			tcpRegistered = true
		}
	default:
		return errors.Errorf("Unknown driver %s", driverId)
	}
	registeredDrivers[driverId] = driver
	go driverAdded(driver)
	return nil
}
