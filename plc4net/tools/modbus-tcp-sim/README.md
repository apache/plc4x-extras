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

# Modbus TCP test peer

A small, read-only Modbus TCP peer for local verification. It uses only the
Python standard library and listens on loopback; it is not a general-purpose
PLC simulator. Start it with:

```sh
python plc4net/tools/modbus-tcp-sim/modbus_tcp_sim.py 5502
```

It supports read functions 01 through 04. The following addresses are wire
addresses (zero-based): holding register 0 = 4096, input register 0 = 8192,
coils 0 and 1 = false and true, and discrete inputs 0 and 1 = true and false.
Other addresses return illegal-address exceptions. Writes are not supported.

When using the plc4net Modbus verification tool proposed in #779, the
equivalent one-based tag for holding register 0 is `holding-register:1`:

```sh
dotnet run --project plc4net/tools/modbus-verify -- 127.0.0.1 5502 1 holding-register:1
```

Run the simulator's own tests with:

```sh
python -m unittest discover -s plc4net/tools/modbus-tcp-sim
```

The proposed verification tool and the simulator are independent; the example
requires both to be available locally.
