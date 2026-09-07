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

package pcapfixture

// Reference vectors, one session per protocol.
//
// Every payload here is taken from the protocol's own ParserSerializerTestsuite in the Apache
// PLC4X repository -- protocols/<protocol>/src/test/resources/protocols/<protocol>/ -- which
// plc4x asserts round-trips: parse the raw bytes, serialize the result, get the same bytes.
// That is exactly the property the analyzer measures, so a capture built from them is a capture
// the analyzer must report as entirely clean. A failure here means either the analyzer's
// adapter is wrong or plc4x has regressed, and those are the only two things worth knowing.
//
// The direction of each packet is the "response" parser argument its test case declares, not a
// guess from its name: several of these protocols encode the two directions differently, and
// the analyzer derives the direction from the client address, so a packet placed on the wrong
// side would be read the wrong way round.
//
// Generated from those suites rather than hand-copied, then committed, so the tests stay
// hermetic and do not depend on a plc4x checkout sitting beside this one.

// AbEthWire is where ab-eth traffic is written for a fixture.
var AbEthWire = Wire{Transport: TCP, Port: AbEthPort}

// AbEthSession is the ab-eth reference vectors, 4 packets that all round-trip.
func AbEthSession() []Packet {
	return []Packet{
		{Payload: mustHex("01010000000000000000000000040005000000000000000000000000"), Direction: FromClient, Comment: "Connection Request"},
		{Payload: mustHex("02010000000003320000000000040005000000000000000000000000"), Direction: FromClient, Comment: "Connection Response"},
		{Payload: mustHex("0107000e000003320000000040000000000000000000000000000000080500000f000401a21800640000"), Direction: FromClient, Comment: "Protected Typed Logical Read Request"},
		{Payload: mustHex("02070020000003320000000040000000000000000000000000000000000508004f000401910101000900040405001f02010003000404050000024000"), Direction: FromClient, Comment: "Protected Typed Logical Read Response"},
	}
}

// AdsWire is where ads traffic is written for a fixture.
var AdsWire = Wire{Transport: TCP, Port: AdsPort}

// AdsSession is the ads reference vectors, 8 packets that all round-trip.
func AdsSession() []Packet {
	return []Packet{
		{Payload: mustHex("00002c000000c0a8171401015303c0a817c801015303020004000c000000000000000200000005f000000000801a01000000"), Direction: FromClient, Comment: "Ams-Single-Item-Read-Request"},
		{Payload: mustHex("000029000000c0a817c801015303c0a817140101530302000500090000000000000002000000000000000100000001"), Direction: FromClient, Comment: "Ams-Single-Item-Read-Response"},
		{Payload: mustHex("00004a000000c0a8171401015303c0a817cd0101feff090004002a000000000000000100000003f0000000000000040000001a0000006d61696e2e665f74726967446174656947656c6573656e2e4d00"), Direction: FromClient, Comment: "Ams-Resolve-Symbolic-Address-Request"},
		{Payload: mustHex("00002c000000c0a817cd0101feffc0a8171401015303090005000c000000000000000100000000000000040000000100801b"), Direction: FromClient, Comment: "Ams-Resolve-Symbolic-Address-Response"},
		{Payload: mustHex("00002c000000c0a8171401015303c0a817cd0101feff020004000c000000000000000100000005f000000100801b04000000"), Direction: FromClient, Comment: "Ams-Read-Symbolic-Address-Request"},
		{Payload: mustHex("000029000000c0a817cd0101feffc0a817140101530302000500090000000000000001000000000000000100000001"), Direction: FromClient, Comment: "Ams-Read-Symbolic-Address-Response"},
		{Payload: mustHex("000030000000c0a8171401015303c0a817cd0101feff0300040010000000000000000100000006f0000000000000040000000100801b"), Direction: FromClient, Comment: "Ams-Release-Symbolic-Address-Handle-Request"},
		{Payload: mustHex("000024000000c0a817cd0101feffc0a81714010153030300050004000000000000000100000000000000"), Direction: FromClient, Comment: "Ams-Release-Symbolic-Address-Handle-Response"},
	}
}

// EipWire is where eip traffic is written for a fixture.
var EipWire = Wire{Transport: TCP, Port: EipPort}

// EipSession is the eip reference vectors, 27 packets that all round-trip.
func EipSession() []Packet {
	return []Packet{
		{Payload: mustHex("70003800c9070440000000005765277265000000000000000000000000000200a10004005942feffb10024000100520d91124b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b910370707000010000000000"), Direction: FromClient, Comment: "CIP Connected Read Request"},
		{Payload: mustHex("6f0044001d5e074000000000000000000000000000000000000000000000020000000000b20034005b02200624010a0e02000020b4a500002bcb37132a0000000300000034122000a20f004201402000a20f0042a303010020022401"), Direction: FromClient, Comment: "CIP Connection Manager Open Large Forward Request"},
		{Payload: mustHex("6f002e001d5e074000000000000000000000000000000000000000000000020000000000b2001e00db0000005b6bfeffb4a500002bcb37132a00000034122000014020000000"), Direction: FromServer, Comment: "CIP Connection Manager Open Large Forward Response"},
		{Payload: mustHex("70002e001d5e0740000000005765277265000000000000000000000000000200a10004005b6bfeffb1001a0001005208910d5a5a5a5f5a5a5a5f415252415900010000000000"), Direction: FromClient, Comment: "CIP Connected Write Request"},
		{Payload: mustHex("700020001d5e0740000000000000000000000000000000000000000000000200a1000400b4a50000b1000c000100d2000000c40000000000"), Direction: FromServer, Comment: "CIP Connected Write Response"},
		{Payload: mustHex("700032001d5e0740000000006e6f000000000000000000000000000000000200a10004005b6bfeffb1001e0002004d09910d5a5a5a5f5a5a5a5f4152524159002801c400010001000000"), Direction: FromClient, Comment: "CIP Connected Write Request 4D"},
		{Payload: mustHex("700032001d5e0740000000006e6f000000000000000000000000000000000200a10004005b6bfeffb1001e0002004d09910d5a5a5a5f5a5a5a5f4152524159002801c400010001000000"), Direction: FromServer, Comment: "CIP Connected Write Response 4D"},
		{Payload: mustHex("6f0028001d5e074000000000000000000000000000000000000000000000020000000000b20018004e02200624010a0e2bcb37132a0000000300010020022401"), Direction: FromClient, Comment: "CIP Connection Manager Close Request"},
		{Payload: mustHex("6f001e00c907044000000000000000000000000000000000000000000000020000000000b2000e00ce000000902137132a0000000000"), Direction: FromServer, Comment: "CIP Connection Manager Close Response"},
		{Payload: mustHex("650004000000000000000000302e382e332020200000000001000000"), Direction: FromClient, Comment: "ENIP Register Session Request"},
		{Payload: mustHex("650004001d5e074000000000302e382e332020200000000001000000"), Direction: FromServer, Comment: "ENIP Register Session Response"},
		{Payload: mustHex("660000001d5e074000000000000000000000000000000000"), Direction: FromClient, Comment: "ENIP Un-Register Session Request"},
		{Payload: mustHex("6f002c002a01004000000000000000000000000000000000000000000000020000000000b2001c00520220062401059d0e004c0591084d79537472696e67010001000100"), Direction: FromClient, Comment: "CIP Unconnected Send Request"},
		{Payload: mustHex("6f0070002a01004000000000000000000000000000000000000000000000020000000000b2006000cc000000a002ce0f2b000000556d6d2c2049206e6f772073656520796f7520696e207468652077697265736861726b20636170747572650000000000000000000000000000000000000000000000000000000000000000000000000000000000"), Direction: FromServer, Comment: "CIP Unconnected Send Response"},
		{Payload: mustHex("6500040081926a3500000000504c4334582020200000000001000000"), Direction: FromServer, Comment: "CIP Register Response - Simulator"},
		{Payload: mustHex("700020005e5dccfd00000000504c433458202020000000000000000000000200a1000400c3a45cf2b1000c000100cc000000ca0000000000"), Direction: FromServer, Comment: "CIP Read Response 4C"},
		{Payload: mustHex("04000000ec9a149000000000000000000000000000000000"), Direction: FromClient, Comment: "EIP List Services Request"},
		{Payload: mustHex("04001900ec9a14900000000000000000000000000000000001000001130001002000436f6d6d756e69636174696f6e7300"), Direction: FromServer, Comment: "EIP List Services Response"},
		{Payload: mustHex("040019000000000000000000504c4334582020200000000001000001130001002000436f6d6d756e69636174696f6e7300"), Direction: FromServer, Comment: "EIP List Services Response 2"},
		{Payload: mustHex("6f00160045000040000000001400000030145b0300000000000000002000020000000000b2000600010220022401"), Direction: FromClient, Comment: "EIP Get Attribute List Request - Message Router"},
		{Payload: mustHex("6f00b60045000040000000001400000030145b0300000000000000000000020000000000b200a600810000004e003a03770066004300f6003700f500ac035f005d005e000003ab033703a503040348004203a4038b002f031204b603b203b303b003b10330034f004e00aa03a803a703a6036e037003320331032d031703b20049033503710072007803ac00b0002b03b100730067006b0068007d038d008c006d006a0038031a0369004500f20074006e008e0070006c0002006a036400a100f4000100c100c000060000010500"), Direction: FromServer, Comment: "EIP Get Attribute List Response - Message Router"},
		{Payload: mustHex("6f004c08b181852100000000504c43345820202000000000000000000000020000000000b2003c088100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"), Direction: FromServer, Comment: "EIP Get Attribute List Response - Message Router - Multiple Attributes - Bad Response Handling"},
		{Payload: mustHex("6f0014000100000000000000504c43345820202000000000000000000000020000000000b200040081000800"), Direction: FromServer, Comment: "EIP Get Attribute List Response - Message Router - Unknown Service Response Handling"},
		{Payload: mustHex("6f0014000100000000000000504c43345820202000000000000000000000020000000000b2000400d2000800"), Direction: FromServer, Comment: "CIP Read Response - Unknown Service Response Handling"},
		{Payload: mustHex("6f0018001d5e074000000000504c43345820202000000000000000000000020000000000b20008000e03200124013001"), Direction: FromClient, Comment: "CIP Get Attribute Single Request"},
		{Payload: mustHex("6f0016001d5e074000000000504c43345820202000000000000000000000020000000000b20006008e0000000100"), Direction: FromServer, Comment: "CIP Get Attribute Single Response"},
		{Payload: mustHex("6f0020001d5e074000000000504c43345820202000000000000000000000020000000000b2001000db0001010001341237132a0000000200"), Direction: FromServer, Comment: "CIP Connection Manager Forward Open rejected"},
	}
}

// FirmataWire is where firmata traffic is written for a fixture.
var FirmataWire = Wire{Transport: TCP, Port: FirmataPort}

// FirmataSession is the firmata reference vectors, 13 packets that all round-trip.
func FirmataSession() []Packet {
	return []Packet{
		{Payload: mustHex("ff"), Direction: FromClient, Comment: "Firmata Reset"},
		{Payload: mustHex("f90205"), Direction: FromServer, Comment: "Firmata Report Version"},
		{Payload: mustHex("f07902055300740061006e0064006100720064004600690072006d006100740061002e0069006e006f00f7"), Direction: FromServer, Comment: "Firmata Report Version And Name"},
		{Payload: mustHex("f079f7"), Direction: FromClient, Comment: "FirmataMessageCommand->FirmataCommandSysex->SysexCommandReportFirmware (Request)"},
		{Payload: mustHex("f07902055300740061006e0064006100720064004600690072006d006100740061002e0069006e006f00f7"), Direction: FromServer, Comment: "FirmataMessageCommand->FirmataCommandSysex->SysexCommandReportFirmware (Response)"},
		{Payload: mustHex("f069f7"), Direction: FromClient, Comment: "FirmataMessageCommand->FirmataCommandSysex->SysexCommandAnalogMappingQuery (Request)"},
		{Payload: mustHex("f06bf7"), Direction: FromClient, Comment: "FirmataMessageCommand->FirmataCommandSysex->SysexCommandCapabilityQuery (Request)"},
		{Payload: mustHex("c001"), Direction: FromClient, Comment: "FirmataMessageSubscribeAnalogPinValue (Pin 0)"},
		{Payload: mustHex("d001"), Direction: FromClient, Comment: "FirmataMessageSubscribeDigitalPinValue (Pin 0)"},
		{Payload: mustHex("e05403"), Direction: FromServer, Comment: "FirmataMessageAnalogIO (Pin 0)"},
		{Payload: mustHex("900000"), Direction: FromServer, Comment: "FirmataMessageDigitalIO (Pin 0)"},
		{Payload: mustHex("f06d02f7"), Direction: FromClient, Comment: "FirmataMessageCommand->FirmataCommandSysex->SysexCommandPinStateQuery (Pin 2)"},
		{Payload: mustHex("f06e020100f7"), Direction: FromServer, Comment: "FirmataMessageCommand->FirmataCommandSysex->SysexCommandPinStateResponse"},
	}
}

// KnxWire is where knxnet-ip traffic is written for a fixture.
var KnxWire = Wire{Transport: UDP, Port: KnxPort}

// KnxSession is the knxnet-ip reference vectors, 16 packets that all round-trip.
func KnxSession() []Packet {
	return []Packet{
		{Payload: mustHex("06100201000e0801c0a82ac8d6b4"), Direction: FromClient, Comment: "Search Request"},
		{Payload: mustHex("06100202004c0801c0a82a0b0e5736010200ffff000000082d409852e000170c000ab327553647697261204b4e582f49502d5363686e6974747374656c6c6500000000000802020103010401"), Direction: FromClient, Comment: "Search Response"},
		{Payload: mustHex("06100203000e0801000000000000"), Direction: FromClient, Comment: "Description Request"},
		{Payload: mustHex("06100204004436010200ffff000000082d409852e000170c000ab327553647697261204b4e582f49502d5363686e6974747374656c6c6500000000000802020103010401"), Direction: FromClient, Comment: "Description Response"},
		{Payload: mustHex("06100205001a0801c0a82ac8d6b40801c0a82ac8d6b404040200"), Direction: FromClient, Comment: "Connect Request"},
		{Payload: mustHex("06100206001402000801c0a82a0b0e570404fffe"), Direction: FromClient, Comment: "Connect Response"},
		{Payload: mustHex("06100207001002000801c0a82ac8d6b4"), Direction: FromClient, Comment: "Connection State Request"},
		{Payload: mustHex("0610020800080200"), Direction: FromClient, Comment: "Connection State Response"},
		{Payload: mustHex("06100310001104670000fc000001531001"), Direction: FromClient, Comment: "Device Configuration Request"},
		{Payload: mustHex("06100311000a04670000"), Direction: FromClient, Comment: "Device Configuration Ack"},
		{Payload: mustHex("06100209001001000801c0a82a0b0e57"), Direction: FromClient, Comment: "Disconnect Request"},
		{Payload: mustHex("0610020a00086600"), Direction: FromClient, Comment: "Disconnect Response"},
		{Payload: mustHex("061004200015040200002900bce0220a120c010081"), Direction: FromClient, Comment: "Tunneling Request"},
		{Payload: mustHex("06100420001c046b00002b0703010504024502bc360a1e0ce100810d"), Direction: FromClient, Comment: "Tunneling Request (Busmon)"},
		{Payload: mustHex("06100421000a046b0000"), Direction: FromClient, Comment: "Tunneling Response"},
		{Payload: mustHex("0610020500180801c0a82a46c4090801c0a82a46c40a0203"), Direction: FromClient, Comment: "Default"},
	}
}

// ModbusWire is where modbus-tcp traffic is written for a fixture.
var ModbusWire = Wire{Transport: TCP, Port: ModbusPort}

// ModbusSession is the modbus-tcp reference vectors, 6 packets that all round-trip.
func ModbusSession() []Packet {
	return []Packet{
		{Payload: mustHex("000000000006ff0408d20002"), Direction: FromClient, Comment: "Read Input Registers Request"},
		{Payload: mustHex("7cfe000000c9ff04c600000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000004000000000000000000000000000001db000001d600004a380000000000000000000000000000000000000000000000000000000000006461696d006e0000000000000000000000000000303100300000000000000000000000000000000000000000000000000000000000000000000000000000"), Direction: FromServer, Comment: "Read Input Registers Response"},
		{Payload: mustHex("000a0000001101140e060003270e000206000400000008"), Direction: FromClient, Comment: "Read Extended Registers Request Split File Record"},
		{Payload: mustHex("000a0000001b011418050600000000110600000000000000000000000000000000"), Direction: FromServer, Comment: "Read Extended Registers Response Split File Record"},
		{Payload: mustHex("000a0000000c011509060002000000010008"), Direction: FromClient, Comment: "Write Extended Registers Request File Record"},
		{Payload: mustHex("000a00000015011512060001270f00010000060002000000010000"), Direction: FromClient, Comment: "Write Extended Registers Request Split File Record"},
	}
}

// ModbusAsciiWire is where modbus-ascii traffic is written for a fixture.
var ModbusAsciiWire = Wire{Transport: TCP, Port: ModbusPort}

// ModbusAsciiSession is the modbus-ascii reference vectors, 2 packets that all round-trip.
func ModbusAsciiSession() []Packet {
	return []Packet{
		{Payload: mustHex("01030000000af2"), Direction: FromClient, Comment: "Read Holding Registers Request"},
		{Payload: mustHex("0103140000000000000000000000000000000000000000e8"), Direction: FromServer, Comment: "Read Holding Registers Response"},
	}
}

// ModbusRtuWire is where modbus-rtu traffic is written for a fixture.
var ModbusRtuWire = Wire{Transport: TCP, Port: ModbusPort}

// ModbusRtuSession is the modbus-rtu reference vectors, 2 packets that all round-trip.
func ModbusRtuSession() []Packet {
	return []Packet{
		{Payload: mustHex("01030000000ac5cd"), Direction: FromClient, Comment: "Read Holding Registers Request"},
		{Payload: mustHex("0103140000000000000000000000000000000000000000a367"), Direction: FromServer, Comment: "Read Holding Registers Response"},
	}
}

// S7Wire is where s7 traffic is written for a fixture.
var S7Wire = Wire{Transport: TCP, Port: S7Port}

// S7Session is the s7 reference vectors, 11 packets that all round-trip.
func S7Session() []Packet {
	return []Packet{
		{Payload: mustHex("0300001611e00000000f00c2020100c1020311c0010a"), Direction: FromClient, Comment: "COTP Connection Request"},
		{Payload: mustHex("0300001611d0000f000b00c0010ac1020311c2020100"), Direction: FromClient, Comment: "COTP Connection Response"},
		{Payload: mustHex("0300001902f08132010000000000080000f0000008000803f0"), Direction: FromClient, Comment: "S7 Setup Communication Request"},
		{Payload: mustHex("0300001b02f080320300000000000800000000f0000003000300f0"), Direction: FromClient, Comment: "S7 Setup Communication Response"},
		{Payload: mustHex("0300002102f082320700000001000800080001120411440100ff09000400110000"), Direction: FromClient, Comment: "S7 Read PLC Type Request"},
		{Payload: mustHex("0300007d02f080320700000001000c0060000112081284010100000000ff09005c00110000001c0003000136455337203231322d31424433302d3058423020202000012020000636455337203231322d31424433302d3058423020202000012020000736455337203231322d31424433302d3058423020202056020002"), Direction: FromClient, Comment: "S7 Read PLC Type Response"},
		{Payload: mustHex("0300004302f08b32010000000b003200000404120a10010001000082000000120a10010001000082000000120a10010001000082000000120a10010001000082000000"), Direction: FromClient, Comment: "S7 Read Request"},
		{Payload: mustHex("0300002c02f08032030000000b0002001700000404ff0300010100ff0300010100ff0300010100ff03000101"), Direction: FromClient, Comment: "S7 Read Response"},
		{Payload: mustHex("0300001302f08032020000000a000000008500"), Direction: FromClient, Comment: "S7 Read Error Response"},
		{Payload: mustHex("0300005a02f08e32010000000e003200170504120a10010001000082000000120a10010001000082000001120a10010001000082000002120a10010001000082000003ff0300010100ff0300010100ff0300010100ff03000101"), Direction: FromClient, Comment: "S7 Write Request"},
		{Payload: mustHex("0300001902f08032030000000e0002000400000504ffffffff"), Direction: FromClient, Comment: "S7 Write Response"},
	}
}

// SlmpWire is where slmp traffic is written for a fixture.
var SlmpWire = Wire{Transport: TCP, Port: SlmpPort}

// SlmpSession is the slmp reference vectors, 5 packets that all round-trip.
func SlmpSession() []Packet {
	return []Packet{
		{Payload: mustHex("500000ffff03000c000000010400005e0100a80200"), Direction: FromClient, Comment: "3E Batch Read Request - D350, 2 words"},
		{Payload: mustHex("d00000ffff030006000000ab560f17"), Direction: FromClient, Comment: "3E Batch Read Response - D350=0x56AB, D351=0x170F"},
		{Payload: mustHex("500000ffff030024000000030400000403000000a8000000c2640000902000009cdc0500a86001009d57040090"), Direction: FromClient, Comment: "3E Random Read Request - D0,T0,M100,X20 word + D1500,Y160,M1111 dword"},
		{Payload: mustHex("d00000ffff03001600000095190212302049484e4f544cafb9dec3b7bcddba"), Direction: FromClient, Comment: "3E Random Read Response - 4 word + 3 dword values"},
		{Payload: mustHex("500000ffff030026000000060400000203000000a80400000100b40800000000900200800000900200000100a00300"), Direction: FromClient, Comment: "3E Multi-block Read Request - 2 word blocks (D0-D3, W100-W107) + 3 bit blocks (M0-M31, M128-M159, B100-B12F)"},
	}
}

// ModbusExceptionSession is a Modbus exchange whose responses are exceptions.
//
// This used to be the one fixture that deliberately did not round-trip. plc4x lost the function
// code of an exception response: Modbus flags an error by setting the top bit of the original
// function code -- 0x03 becomes 0x83, 0x08 becomes 0x88 -- and ModbusPDUError captured only the
// flag, so it wrote back 0x80 whatever went in, its own parser having read the function correctly
// and its own serializer having then discarded it.
//
// The cause was in the code generator rather than in the Go binding, which is why every language
// binding did it: the type switch in modbus.mspec matches ModbusPDUError on the error bit alone,
// leaving the generator with no constant for the function code. It now retains a discriminator
// value that no sub type pins, and these round-trip exactly.
//
// The payloads are real. The 0x88 exchange is from a public capture of 2004 shipped in
// gopacket's own testdata, where thirteen packets show it; the 0x83 one came from a live
// capture. plc4x's own Modbus test suites contained no exception response at all -- the error
// flag was false in every case across TCP, RTU and ASCII -- which is why nobody had noticed.
func ModbusExceptionSession() []Packet {
	return []Packet{
		{Payload: mustHex("00000000000600010000000a"), Direction: FromClient, Comment: "read holding registers request"},
		{Payload: mustHex("0000000000030a880b"), Direction: FromServer, Comment: "exception response to function 0x08 (public capture)"},
		{Payload: mustHex("000100000006010300000002"), Direction: FromClient, Comment: "read holding registers request"},
		{Payload: mustHex("00010000000301830b"), Direction: FromServer, Comment: "exception response to function 0x03"},
	}
}
