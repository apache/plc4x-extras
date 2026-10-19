<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# S7 verification tool

This CLI uses plc4net to connect to an S7 device, read one typed address or
run a fixed test matrix, and print the results. The matrix expects the DB100
layout and initial values in the PLC4Net historical hardware report. Other
devices or values will produce failures even if communication works.

The tool builds against a separate apache/plc4x checkout at commit
`1980c1b6b29625159db0d1315b385c3f4fa32aff`. Set `PLC4X_SOURCE_ROOT` to that
checkout's absolute path, or pass `-p:Plc4xSourceRoot=<path>` to dotnet.
With the .NET 8 SDK or a compatible newer SDK:

```sh
dotnet build plc4net/tools/s7-verify/s7-verify.csproj -c Release
dotnet run --project plc4net/tools/s7-verify -c Release --no-build -- 192.168.1.11 --read '%DB100.DBW2:INT'
dotnet run --project plc4net/tools/s7-verify -c Release --no-build -- 192.168.1.11 --db 100
```

The matrix is read-only by default. `--write-db`, `--write-markers`, and
`--write-outputs` opt into writes for each area. The tool attempts to restore
original values unless the corresponding `--keep-db-values`,
`--keep-marker-values`, or `--keep-output-values` flag is also supplied.
Restoration can fail if communication is interrupted. Only run writes on a
prepared test device with independently safe outputs.

`--rack`, `--slot`, `--device-group`, and `--remote-tsap` configure the S7
connection; `--i-base`, `--q-base`, and `--m-base` choose matrix offsets.
Run `python plc4net/tools/s7-verify/test_cli.py` after a Release build to test
the CLI against a scripted local peer. The migrated tool has not been
retested against physical hardware.
