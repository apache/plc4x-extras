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

package common

import (
	"fmt"
	"net"
	"time"

	"github.com/apache/plc4x/plc4go/spi/errors"
	"github.com/gopacket/gopacket"
)

type PacketInformation struct {
	PacketNumber    int
	PacketTimestamp time.Time
	Description     string
	SrcIp           net.IP
	DstIp           net.IP
	// SrcPort and DstPort are the transport ports, zero when the packet has no transport layer.
	//
	// They are here because they answer a question nothing else can answer cheaply: which end
	// of this conversation is the device. A packet arriving at a protocol's registered port is
	// a request and one leaving it is a response, which is how Wireshark decides and which
	// needs nothing from the user. The alternative is being told the client's address, and a
	// user who forgets sees half a capture reported as parse failures.
	SrcPort int
	DstPort int
}

func (p PacketInformation) String() string {
	return fmt.Sprintf("%s (SrcIp:%v, DstIp:%v)", p.Description, p.SrcIp, p.DstIp)
}

// ErrUnterminatedPackage is used when a transmission is incomplete (usually when package is split)
var ErrUnterminatedPackage = errors.New("ErrUnterminatedPackage")

// ErrEmptyPackage is used when there is no payload
var ErrEmptyPackage = errors.New("ErrEmptyPackage")

// ErrEcho is used when the package is a echo from the previous
var ErrEcho = errors.New("ErrEcho")

type FilteredPackage interface {
	gopacket.Packet
	IsFilteredPackage() bool
	FilterReason() error
}

func NewFilteredPackage(err error, packet gopacket.Packet) FilteredPackage {
	return &filteredPackage{Packet: packet, err: err}
}

type filteredPackage struct {
	gopacket.Packet
	err error
}

func (f *filteredPackage) IsFilteredPackage() bool {
	return true
}

func (f *filteredPackage) FilterReason() error {
	return f.err
}
