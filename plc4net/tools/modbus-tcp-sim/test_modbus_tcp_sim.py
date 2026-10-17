# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import socket
import struct
import threading
import unittest

import modbus_tcp_sim as sim


class SimulatorTests(unittest.TestCase):
    def test_reads_and_errors(self):
        cases = [
            (b"\x03\x00\x00\x00\x01", b"\x03\x02\x10\x00"),
            (b"\x04\x00\x00\x00\x01", b"\x04\x02\x20\x00"),
            (b"\x01\x00\x00\x00\x02", b"\x01\x01\x02"),
            (b"\x02\x00\x00\x00\x02", b"\x02\x01\x01"),
            (b"\x03\x00\xc8\x00\x01", b"\x83\x02"),
            (b"\x03\x00\x00\x00\x00", b"\x83\x03"),
            (b"\x03\x00\x00\x00\x7e", b"\x83\x03"),
            (b"\x06\x00\x00\x00\x01", b"\x86\x01"),
        ]
        for request, expected in cases:
            with self.subTest(request=request):
                self.assertEqual(expected, sim.handle_pdu(request))

    def test_fragmented_and_multiple_requests(self):
        client, server = socket.socketpair()
        thread = threading.Thread(target=sim._handle_connection, args=(server,))
        thread.start()
        try:
            request = struct.pack(">HHHB", 42, 0, 6, 1) + b"\x03\x00\x00\x00\x01"
            client.sendall(request[:3])
            client.sendall(request[3:] + request)
            expected = struct.pack(">HHHB", 42, 0, 5, 1) + b"\x03\x02\x10\x00"
            self.assertEqual(expected * 2, sim._recv_exact(client, len(expected) * 2))
        finally:
            client.close()
            thread.join(timeout=3)
        self.assertFalse(thread.is_alive())

    def test_invalid_mbap_is_closed(self):
        client, server = socket.socketpair()
        thread = threading.Thread(target=sim._handle_connection, args=(server,))
        thread.start()
        client.settimeout(3)
        try:
            client.sendall(struct.pack(">HHHB", 1, 0, 0, 1))
            self.assertEqual(b"", client.recv(1))
        finally:
            client.close()
            thread.join(timeout=3)


if __name__ == "__main__":
    unittest.main()
