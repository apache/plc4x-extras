#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

"""Small, read-only Modbus TCP test peer using only the Python standard library.

Serves exactly the four register/coil values and the one out-of-range address
that run exercised:

  holding register 0 -> 4096; input register 0 -> 8192
  coils 0/1 -> False / True; discrete inputs 0/1 -> True / False
  holding register 200 -> exception 0x02 (illegal address)

Usage:
    python3 modbus_tcp_sim.py [port]      # loopback, default port 5502
"""

import socket
import struct
import sys

HOLDING_REGISTERS = {0: 4096}
INPUT_REGISTERS = {0: 8192}
COILS = {0: False, 1: True}
DISCRETE_INPUTS = {0: True, 1: False}

READ_COILS = 0x01
READ_DISCRETE_INPUTS = 0x02
READ_HOLDING_REGISTERS = 0x03
READ_INPUT_REGISTERS = 0x04

ILLEGAL_FUNCTION = 0x01
ILLEGAL_DATA_ADDRESS = 0x02
ILLEGAL_DATA_VALUE = 0x03


def _exception(function: int, code: int) -> bytes:
    return bytes([function | 0x80, code])


def _read_bits(table: dict, address: int, quantity: int, function: int) -> bytes:
    bits = []
    for offset in range(quantity):
        if (address + offset) not in table:
            return _exception(function, ILLEGAL_DATA_ADDRESS)
        bits.append(1 if table[address + offset] else 0)
    byte_count = (quantity + 7) // 8
    packed = bytearray(byte_count)
    for i, bit in enumerate(bits):
        if bit:
            packed[i // 8] |= 1 << (i % 8)
    return bytes([function, byte_count]) + bytes(packed)


def _read_registers(table: dict, address: int, quantity: int, function: int) -> bytes:
    values = []
    for offset in range(quantity):
        if (address + offset) not in table:
            return _exception(function, ILLEGAL_DATA_ADDRESS)
        values.append(table[address + offset])
    byte_count = quantity * 2
    data = b"".join(struct.pack(">H", v) for v in values)
    return bytes([function, byte_count]) + data


def handle_pdu(pdu: bytes) -> bytes:
    if not pdu:
        raise ValueError("empty Modbus PDU")

    function = pdu[0]
    if function not in (
        READ_COILS,
        READ_DISCRETE_INPUTS,
        READ_HOLDING_REGISTERS,
        READ_INPUT_REGISTERS,
    ):
        return _exception(function, ILLEGAL_FUNCTION)
    if len(pdu) != 5:
        return _exception(function, ILLEGAL_DATA_VALUE)
    address, quantity = struct.unpack(">HH", pdu[1:5])
    maximum = 2000 if function in (READ_COILS, READ_DISCRETE_INPUTS) else 125
    if quantity < 1 or quantity > maximum or address + quantity > 65536:
        return _exception(function, ILLEGAL_DATA_VALUE)

    if function == READ_COILS:
        return _read_bits(COILS, address, quantity, function)
    if function == READ_DISCRETE_INPUTS:
        return _read_bits(DISCRETE_INPUTS, address, quantity, function)
    if function == READ_HOLDING_REGISTERS:
        return _read_registers(HOLDING_REGISTERS, address, quantity, function)
    if function == READ_INPUT_REGISTERS:
        return _read_registers(INPUT_REGISTERS, address, quantity, function)

    return _exception(function, ILLEGAL_FUNCTION)


def _recv_exact(conn: socket.socket, size: int) -> bytes:
    """recv() only returns "up to size" bytes; loop until size is met or the
    peer closes early. Returns fewer than size bytes only on early close."""
    chunks = bytearray()
    while len(chunks) < size:
        chunk = conn.recv(size - len(chunks))
        if not chunk:
            break
        chunks.extend(chunk)
    return bytes(chunks)


def _handle_connection(conn: socket.socket) -> None:
    with conn:
        while True:
            header = _recv_exact(conn, 7)
            if len(header) < 7:
                break
            txn_id, proto_id, length, unit_id = struct.unpack(">HHHB", header)
            if proto_id != 0 or not 2 <= length <= 254:
                break
            pdu = _recv_exact(conn, length - 1)
            if len(pdu) != length - 1:
                break
            response_pdu = handle_pdu(pdu)
            response_header = struct.pack(
                ">HHHB", txn_id, proto_id, len(response_pdu) + 1, unit_id
            )
            conn.sendall(response_header + response_pdu)


def serve(port: int) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("127.0.0.1", port))
        server.listen(1)
        print(f"Modbus TCP test peer listening on 127.0.0.1:{port}", flush=True)
        while True:
            conn, _ = server.accept()
            try:
                _handle_connection(conn)
            except OSError as e:
                # A client dropping mid-exchange (reset, closed early) must not
                # take the whole slave down -- move on to the next connection.
                print(f"Connection dropped: {e}", flush=True)


if __name__ == "__main__":
    serve(int(sys.argv[1]) if len(sys.argv) > 1 else 5502)
