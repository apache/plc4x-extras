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

package main

import (
	"context"
	"fmt"

	"github.com/apache/plc4x/plc4go/pkg/api"
	"github.com/apache/plc4x/plc4go/pkg/api/drivers"
	apiModel "github.com/apache/plc4x/plc4go/pkg/api/model"
)

func main() {
	ctx := context.Background()
	driverManager := plc4go.NewPlcDriverManager()
	defer func() {
		if err := driverManager.Close(); err != nil {
			panic(err)
		}
	}()
	drivers.RegisterModbusTcpDriver(driverManager)

	// Get a connection to a remote PLC
	connection, err := driverManager.GetConnection(ctx, "modbus-tcp://192.168.23.30")

	// Wait for the driver to connect (or not)
	if err != nil {
		fmt.Printf("error connecting to PLC: %s", err.Error())
		return
	}

	// Make sure the connection is closed at the end
	defer connection.Close()

	// Prepare a write-request
	writeRequest, err := connection.WriteRequestBuilder().
		AddTagAddress("tag", "holding-register:26:REAL", 2.7182818284).
		Build()
	if err != nil {
		fmt.Printf("error preparing read-request: %s", err.Error())
		return
	}

	// Execute a read-request
	wrc := writeRequest.Execute(ctx)

	// Wait for the response to finish
	wrr := <-wrc
	if wrr.GetErr() != nil {
		fmt.Printf("error executing write-request: %s", wrr.GetErr().Error())
		return
	}

	if wrr.GetResponse().GetResponseCode("tag") != apiModel.PlcResponseCode_OK {
		fmt.Printf("error an non-ok return code: %s", wrr.GetResponse().GetResponseCode("tag").GetName())
		return
	}
	fmt.Print("Result: SUCCESS\n")
}
