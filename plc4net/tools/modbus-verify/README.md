<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Modbus verification tool

This CLI checks a Modbus TCP or serial RTU target using plc4net. For RTU it
also reports the raw request and response and validates the response CRC.
Use `--help` for the supported arguments and serial settings.

## Build

The tool currently builds against a PLC4X source checkout, rather than
published plc4net packages. CI uses commit
`1980c1b6b29625159db0d1315b385c3f4fa32aff` from `apache/plc4x`; using another
revision may require code changes. Clone that repository separately and check
out the commit above, then set `PLC4X_SOURCE_ROOT` to its absolute path.
Alternatively, pass `-p:Plc4xSourceRoot=<path>` to each `dotnet` command.
The checkout is a build dependency and is not copied into this repository.

With the .NET 8 SDK or a compatible newer SDK, from this directory:

```sh
dotnet build modbus-verify.csproj
dotnet run --project modbus-verify.csproj --no-build -- --help
dotnet run --project modbus-verify.csproj --no-build -- 192.168.0.9 502 1 holding-register:1
dotnet run --project modbus-verify.csproj --no-build -- COM3 1 holding-register:1 --baud 19200 --parity Even
```

Set `PLC4X_SOURCE_ROOT` with `export PLC4X_SOURCE_ROOT=/path/to/plc4x` on
Unix-like systems or `$env:PLC4X_SOURCE_ROOT = 'C:\path\to\plc4x'` in
PowerShell. A working serial port and compatible device are required for RTU;
the examples are placeholders and must match the target's configuration.
