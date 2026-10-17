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

"""Hardware-free smoke and TCP interoperability tests for the built CLI."""

import socket
import subprocess
import unittest
from pathlib import Path


DLL = Path(__file__).parent / "bin" / "Release" / "net8.0" / "modbus-verify.dll"


class CliTests(unittest.TestCase):
    def run_cli(self, *args):
        return subprocess.run(
            ["dotnet", str(DLL), *args], capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=20,
        )

    def test_help_and_missing_target(self):
        help_result = self.run_cli("--help")
        self.assertEqual(0, help_result.returncode, help_result.stderr)
        self.assertIn("Modbus TCP", help_result.stdout)
        missing = self.run_cli("--baud", "9600")
        self.assertEqual(1, missing.returncode)
        self.assertIn("required", missing.stderr)

    def test_invalid_rtu_unit_rejected_before_opening_port(self):
        result = self.run_cli("modbus-rtu:serial://COM999?unit-identifier=248")
        self.assertEqual(1, result.returncode)
        self.assertIn("between 1 and 247", result.stdout + result.stderr)

    def test_tcp_read_holding_register(self):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            server.settimeout(15)
            port = server.getsockname()[1]
            process = subprocess.Popen(
                ["dotnet", str(DLL), "127.0.0.1", str(port), "1", "holding-register:1"],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                encoding="utf-8", errors="replace",
            )
            try:
                with server.accept()[0] as conn:
                    conn.settimeout(15)
                    request = bytearray()
                    while len(request) < 12:
                        chunk = conn.recv(12 - len(request))
                        if not chunk:
                            self.fail("Connection closed before the request was complete")
                        request.extend(chunk)
                    self.assertEqual(b"\x00\x00\x00\x06\x01\x03\x00\x00\x00\x01", request[2:])
                    conn.sendall(request[:2] + b"\x00\x00\x00\x05\x01\x03\x02\x12\x34")
                    stdout, stderr = process.communicate(timeout=15)
                self.assertEqual(0, process.returncode, stdout + stderr)
                self.assertIn("4660", stdout)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate()


if __name__ == "__main__":
    unittest.main()
