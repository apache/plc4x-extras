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
import subprocess
import threading
import unittest
from pathlib import Path

DLL = Path(__file__).parent / "bin" / "Release" / "net8.0" / "s7-verify.dll"


def receive_frame(conn):
    def receive(size):
        data = bytearray()
        while len(data) < size:
            chunk = conn.recv(size - len(data))
            if not chunk:
                raise EOFError("connection closed before the frame was complete")
            data.extend(chunk)
        return bytes(data)

    header = receive(4)
    return header + receive(int.from_bytes(header[2:4], "big") - 4)


def dt(payload):
    return (
        b"\x03\x00" + (len(payload) + 7).to_bytes(2, "big") + b"\x02\xf0\x80" + payload
    )


class CliTests(unittest.TestCase):
    def run_cli(self, *args):
        return subprocess.run(
            ["dotnet", str(DLL), *args],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=15,
        )

    def test_invalid_arguments_fail_without_connection(self):
        help_result = self.run_cli("--help")
        self.assertEqual(
            0, help_result.returncode, help_result.stdout + help_result.stderr
        )
        self.assertIn("usage:", help_result.stdout)
        for args in (
            ("host", "--db"),
            ("host", "--db", "oops"),
            ("host", "--unknown"),
            ("host", "--keep-db-values"),
        ):
            with self.subTest(args=args):
                result = self.run_cli(*args)
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)

    def test_single_read_with_scripted_s7_peer(self):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            server.settimeout(15)
            process = subprocess.Popen(
                [
                    "dotnet",
                    str(DLL),
                    f"127.0.0.1:{server.getsockname()[1]}",
                    "--read",
                    "%DB1.DBW10:INT",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            try:
                with server.accept()[0] as conn:
                    conn.settimeout(15)
                    self.assertEqual(0xE0, receive_frame(conn)[5])
                    conn.sendall(bytes.fromhex("0300000b06d00001000200"))
                    setup = receive_frame(conn)
                    self.assertEqual(0xF0, setup[17])
                    conn.sendall(
                        dt(bytes.fromhex("320300000001000800000000f0000001000100f0"))
                    )
                    read = receive_frame(conn)
                    self.assertEqual(0x04, read[17])
                    self.assertEqual(
                        bytes.fromhex("120a10050001000184000050"), read[19:31]
                    )
                    ref = read[11:13]
                    conn.sendall(
                        dt(
                            b"\x32\x03\x00\x00"
                            + ref
                            + bytes.fromhex("0002000600000401ff050010002a")
                        )
                    )
                    stdout, stderr = process.communicate(timeout=15)
                self.assertEqual(0, process.returncode, stdout + stderr)
                self.assertIn("42", stdout)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate()

    def test_db_write_matrix_restores_original_values(self):
        memory = bytearray(32)
        memory[0:18] = struct.pack(
            ">BBhifHI", 1, 0xA5, -12345, -1000000, 3.14159, 0xBEEF, 0xDEADBEEF
        )
        original = bytes(memory)
        writes = []
        errors = []
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            server.settimeout(15)

            def peer():
                try:
                    with server.accept()[0] as conn:
                        conn.settimeout(15)
                        receive_frame(conn)
                        conn.sendall(bytes.fromhex("0300000b06d00001000200"))
                        receive_frame(conn)
                        conn.sendall(
                            dt(
                                bytes.fromhex(
                                    "320300000001000800000000f0000001000100f0"
                                )
                            )
                        )
                        while True:
                            try:
                                request = receive_frame(conn)
                            except EOFError:
                                break
                            ref, function, count = (
                                request[11:13],
                                request[17],
                                request[18],
                            )
                            items = [
                                request[19 + 12 * i : 31 + 12 * i] for i in range(count)
                            ]
                            response = bytearray()
                            data_offset = 19 + 12 * count
                            for index, item in enumerate(items):
                                kind, db, area = (
                                    item[3],
                                    int.from_bytes(item[6:8], "big"),
                                    item[8],
                                )
                                offset = int.from_bytes(item[9:12], "big") // 8
                                width = {1: 1, 2: 1, 4: 2, 5: 2, 6: 4, 7: 4, 8: 4}[kind]
                                if function == 4:
                                    if area == 0x84 and db != 100:
                                        data = b"\x05\x00\x00\x00"
                                    else:
                                        raw = (
                                            bytes(memory[offset : offset + width])
                                            if area == 0x84
                                            else bytes(width)
                                        )
                                        if kind == 1:
                                            raw = bytes([raw[0] & 1])
                                        size = (
                                            3
                                            if kind == 1
                                            else 5
                                            if kind in (5, 7)
                                            else 7
                                            if kind == 8
                                            else 4
                                        )
                                        length = (
                                            1
                                            if size == 3
                                            else len(raw)
                                            if size == 7
                                            else len(raw) * 8
                                        )
                                        data = (
                                            bytes([0xFF, size])
                                            + length.to_bytes(2, "big")
                                            + raw
                                        )
                                    response.extend(data)
                                    if index < count - 1 and len(data) % 2:
                                        response.append(0)
                                else:
                                    size = request[data_offset + 1]
                                    length = int.from_bytes(
                                        request[data_offset + 2 : data_offset + 4],
                                        "big",
                                    )
                                    count_bytes = (
                                        1
                                        if size == 3
                                        else length
                                        if size == 7
                                        else (length + 7) // 8
                                    )
                                    raw = request[
                                        data_offset + 4 : data_offset + 4 + count_bytes
                                    ]
                                    self.assertEqual(0x84, area)
                                    self.assertEqual(100, db)
                                    memory[offset : offset + len(raw)] = raw
                                    writes.append((offset, raw))
                                    response.append(0xFF)
                                    data_offset += (
                                        4
                                        + count_bytes
                                        + (count_bytes % 2 if index < count - 1 else 0)
                                    )
                            header = (
                                b"\x32\x03\x00\x00"
                                + ref
                                + bytes([0, 2])
                                + len(response).to_bytes(2, "big")
                                + b"\x00\x00"
                            )
                            conn.sendall(
                                dt(header + bytes([function, count]) + response)
                            )
                except Exception as error:
                    errors.append(error)

            thread = threading.Thread(target=peer)
            thread.start()
            process = subprocess.run(
                [
                    "dotnet",
                    str(DLL),
                    f"127.0.0.1:{server.getsockname()[1]}",
                    "--write-db",
                ],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=30,
            )
            thread.join(timeout=15)
        self.assertFalse(thread.is_alive())
        self.assertFalse(errors, str(errors))
        self.assertEqual(0, process.returncode, process.stdout + process.stderr)
        self.assertEqual(14, len(writes))
        self.assertEqual(original, bytes(memory))


if __name__ == "__main__":
    unittest.main()
