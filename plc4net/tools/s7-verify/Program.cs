//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Threading.Tasks;
using org.apache.plc4net.api;
using org.apache.plc4net.api.value;
using org.apache.plc4net.drivers.s7;
using org.apache.plc4net.drivers.s7.readwrite.model;
using org.apache.plc4net.messages;
using org.apache.plc4net.model;
using org.apache.plc4net.spi.drivers;
using org.apache.plc4net.spi.drivers.functions;
using org.apache.plc4net.spi.drivers.messages;
using org.apache.plc4net.spi.transports;
using org.apache.plc4net.types;

namespace org.apache.plc4net.tools.s7verify
{
    /// <summary>
    /// End-to-end S7 hardware check. Drives the public driver API exactly as a
    /// NuGet consumer would - <c>new S7Driver(...).Connect("s7://host?...")</c> -
    /// then reads every scalar type from a data block, round-trips a write, and
    /// exercises an error path. Prints a Markdown report to stdout.
    ///
    ///   s7-verify &lt;host&gt; [--rack N] [--slot N] [--db N]
    ///             [--device-group PG_OR_PC|OS|OTHERS] [--remote-tsap 0xNNNN]
    ///
    /// Defaults: rack 0, slot 1, db 100. Build the data block per
    /// docs/hardware-verification.md before running.
    /// </summary>
    public static class Program
    {
        public static async Task<int> Main(string[] args)
        {
            if (args.Length == 0 || args[0] is "-h" or "--help")
            {
                var output = args.Length == 0 ? Console.Error : Console.Out;
                output.WriteLine(
                    "usage: s7-verify <host> [--rack N] [--slot N] [--db N] " +
                    "[--device-group PG_OR_PC|OS|OTHERS] [--remote-tsap 0xNNNN] [--read <address>] " +
                    "[--i-base N] [--q-base N] [--m-base N] [--write-db] " +
                    "[--write-markers] [--write-outputs] [--keep-db-values] " +
                    "[--keep-marker-values] [--keep-output-values]");
                return args.Length == 0 ? 2 : 0;
            }

            var host = args[0];
            Dictionary<string, string> opts;
            int db;
            try
            {
                opts = ParseOptions(args);
                db = GetInt(opts, "db", 100);
                if (db < 1 || db > ushort.MaxValue)
                    throw new ArgumentException("DB number must be between 1 and 65535");
                foreach (var option in new[] { "i-base", "q-base", "m-base" })
                    if (GetInt(opts, option, 0) < 0 || GetInt(opts, option, 0) > 65520)
                        throw new ArgumentException($"Invalid {option}");
            }
            catch (ArgumentException e)
            {
                Console.Error.WriteLine(e.Message);
                return 2;
            }

            var query = new List<string>
            {
                $"remote-rack={Get(opts, "rack", "0")}",
                $"remote-slot={Get(opts, "slot", "1")}",
                "read-timeout-ms=5000",
            };
            if (opts.TryGetValue("device-group", out var dg)) query.Add($"remote-device-group={dg}");
            if (opts.TryGetValue("remote-tsap", out var tsap)) query.Add($"remote-tsap={tsap}");
            var connectionString = $"s7://{host}?{string.Join("&", query)}";

            // --read <address>: connect, read one tag, print the outcome, exit. A
            // focused probe for troubleshooting a single address without running the
            // whole suite or needing DB100 to exist.
            if (opts.TryGetValue("read", out var probeAddress))
            {
                return await ProbeSingleRead(connectionString, probeAddress);
            }

            var report = new Report();
            report.Line("# S7 Hardware Verification Report");
            report.Line();
            report.Line($"- **Date**: {DateTime.Now:yyyy-MM-dd HH:mm}");
            report.Line($"- **Connection string**: `{connectionString}`");
            report.Line($"- **Data block**: DB{db}");
            report.Line();

            IPlcConnection? connection = null;
            try
            {
                // ── connect + Setup Communication ──
                var driver = new S7Driver(new DefaultTransportManager());
                connection = driver.Connect(connectionString);
                var s7 = (S7Connection)connection;
                report.Pass("Connect", $"COTP + Setup Communication ok, negotiated PDU length {s7.NegotiatedPduLength} bytes");

                var reader = (PlcReader)connection;
                var writer = (PlcWriter)connection;

                // ── reads ──
                await ReadScalar(reader, connection, report, "BOOL", $"%DB{db}.DBX0.0:BOOL",
                    v => v.GetBool() ? "true" : "false", "true");
                await ReadScalar(reader, connection, report, "BYTE", $"%DB{db}.DBB1:BYTE",
                    v => $"0x{v.GetByte():X2}", "0xA5");
                await ReadScalar(reader, connection, report, "INT", $"%DB{db}.DBW2:INT",
                    v => v.GetShort().ToString(CultureInfo.InvariantCulture), "-12345");
                await ReadScalar(reader, connection, report, "DINT", $"%DB{db}.DBD4:DINT",
                    v => v.GetInt().ToString(CultureInfo.InvariantCulture), "-1000000");
                await ReadScalar(reader, connection, report, "REAL", $"%DB{db}.DBD8:REAL",
                    v => v.GetFloat()
                        .ToString("0.####", CultureInfo.InvariantCulture), "3.1416");
                await ReadScalar(reader, connection, report, "WORD", $"%DB{db}.DBW12:WORD",
                    v => $"0x{v.GetUshort():X4}", "0xBEEF");
                await ReadScalar(reader, connection, report, "DWORD", $"%DB{db}.DBD14:DWORD",
                    v => $"0x{v.GetUint():X8}", "0xDEADBEEF");

                // ── absolute-address matrix ──
                var iBase = GetInt(opts, "i-base", 0);
                var qBase = GetInt(opts, "q-base", 0);
                var mBase = GetInt(opts, "m-base", 100);
                await ReadAreaMatrix(reader, connection, report, "I", iBase);
                await ReadAreaMatrix(reader, connection, report, "Q", qBase);
                await ReadAreaMatrix(reader, connection, report, "M", mBase);
                // ── multi-tag single request ──
                await MultiRead(reader, connection, report, db);

                // ── write matrices: read -> write -> read [-> restore -> read] ──
                var restoreDb = !opts.ContainsKey("keep-db-values");
                if (!restoreDb)
                    report.Line("- **WARNING**: DB values will remain changed after verification");
                if (opts.ContainsKey("write-db")) await WriteTypeMatrix(reader, writer, connection, report,
                    new[]
                    {
                        ("BOOL", $"%DB{db}.DBX0.0:BOOL", (object)false),
                        ("BYTE", $"%DB{db}.DBB1:BYTE", (object)(byte)0x3C),
                        ("INT", $"%DB{db}.DBW2:INT", (object)(short)23456),
                        ("DINT", $"%DB{db}.DBD4:DINT", (object)(-123456789)),
                        ("REAL", $"%DB{db}.DBD8:REAL", (object)(-12.5f)),
                        ("WORD", $"%DB{db}.DBW12:WORD", (object)0x1357),
                        ("DWORD", $"%DB{db}.DBD14:DWORD", (object)0x89ABCDEFL),
                    }, restoreDb);

                if (opts.ContainsKey("write-markers"))
                {
                    var restoreMarkers = !opts.ContainsKey("keep-marker-values");
                    if (!restoreMarkers)
                        report.Line("- **WARNING**: marker values will remain changed after verification");
                    await WriteTypeMatrix(reader, writer, connection, report,
                        AreaWriteMatrix("M", mBase), restoreMarkers);
                }
                if (opts.ContainsKey("write-outputs"))
                {
                    report.Line("- **WARNING**: output write matrix explicitly enabled");
                    var restoreOutputs = !opts.ContainsKey("keep-output-values");
                    if (!restoreOutputs)
                        report.Line("- **WARNING**: output values will remain changed after verification");
                    await WriteTypeMatrix(reader, writer, connection, report,
                        AreaWriteMatrix("Q", qBase), restoreOutputs);
                }

                // ── error path ──
                await ErrorPath(reader, connection, report);

                return report.Failures == 0 ? 0 : 1;
            }
            catch (Exception e)
            {
                report.Line();
                report.Line("## Result: FAIL - connection error");
                report.Line();
                report.Line($"**{e.GetType().Name}**: {e.Message}");
                report.Line();
                report.Line("```");
                report.Line(e.ToString());
                report.Line("```");
                report.Line();
                report.Line("Common causes: PUT/GET not enabled on the CPU; wrong rack/slot; " +
                            "S7-1200/1500 needs `--device-group OTHERS` or `--remote-tsap 0x0301`; " +
                            "TCP 102 blocked by a firewall.");
                return 1;
            }
            finally
            {
                report.Flush();
                connection?.Close();
            }
        }

        private static async Task<int> ProbeSingleRead(string connectionString, string address)
        {
            Console.WriteLine($"Connecting: {connectionString}");
            IPlcConnection? connection = null;
            try
            {
                var driver = new S7Driver(new DefaultTransportManager());
                connection = driver.Connect(connectionString);
                var s7 = (S7Connection)connection;
                Console.WriteLine($"Connected. Negotiated PDU length: {s7.NegotiatedPduLength} bytes");

                var reader = (PlcReader)connection;
                var rb = (DefaultPlcReadRequestBuilder)(connection.ReadRequestBuilder ?? throw new S7DriverException("Read is not supported"));
                rb.AddTagAddress("tag", address);
                var resp = (DefaultPlcReadResponse)await reader.Read((DefaultPlcReadRequest)rb.Build());

                var code = resp.GetResponseCode("tag");
                Console.WriteLine($"Read {address}: {code}");
                if (code == PlcResponseCode.Ok)
                {
                    Console.WriteLine($"  value: {RenderAddress(address, (resp.GetValue("tag") ?? throw new S7DriverException("Missing tag value")))}");
                    return 0;
                }
                if (code == PlcResponseCode.AccessDenied)
                {
                    Console.WriteLine(
                        "  The CPU refused access (S7 error 0x8104). Enable \"Permit access with " +
                        "PUT/GET communication from remote partner\" in the CPU's Protection & " +
                        "Security settings, then recompile and download.");
                }
                return 1;
            }
            catch (Exception e)
            {
                Console.WriteLine($"FAIL - {e.GetType().Name}: {e.Message}");
                return 1;
            }
            finally
            {
                connection?.Close();
            }
        }

        /// <summary>Renders a scalar <see cref="IPlcValue"/> without assuming its concrete type.</summary>
        private static string Render(IPlcValue v)
        {
            if (v.IsBool()) return v.GetBool().ToString();
            if (v.IsByte()) return $"0x{v.GetByte():X2}";
            if (v.IsUshort()) return $"0x{v.GetUshort():X4}";
            if (v.IsUint()) return $"0x{v.GetUint():X8}";
            var raw = v.GetRaw();
            return raw != null ? BitConverter.ToString(raw) : v.GetString();
        }

        private static async Task ReadScalar(PlcReader reader, IPlcConnection connection, Report report,
            string type, string address, Func<org.apache.plc4net.api.value.IPlcValue, string> render, string expected)
        {
            try
            {
                var rb = (DefaultPlcReadRequestBuilder)(connection.ReadRequestBuilder ?? throw new S7DriverException("Read is not supported"));
                rb.AddTagAddress("v", address);
                var resp = (DefaultPlcReadResponse)await reader.Read((DefaultPlcReadRequest)rb.Build());
                var code = resp.GetResponseCode("v");
                if (code != PlcResponseCode.Ok)
                {
                    report.Fail($"Read {type} {address}", $"response code {code}");
                    return;
                }
                var actual = render((resp.GetValue("v") ?? throw new S7DriverException("Missing v value")));
                if (actual == expected)
                {
                    report.Pass($"Read {type} {address}", $"= {actual}");
                }
                else
                {
                    report.Fail($"Read {type} {address}", $"got {actual}, expected {expected}");
                }
            }
            catch (Exception e)
            {
                report.Fail($"Read {type} {address}", e.Message);
            }
        }

        private static async Task MultiRead(PlcReader reader, IPlcConnection connection, Report report, int db)
        {
            try
            {
                var rb = (DefaultPlcReadRequestBuilder)(connection.ReadRequestBuilder ?? throw new S7DriverException("Read is not supported"));
                rb.AddTagAddress("bit", $"%DB{db}.DBX0.0:BOOL");
                rb.AddTagAddress("byte", $"%DB{db}.DBB1:BYTE");
                rb.AddTagAddress("word", $"%DB{db}.DBW12:WORD");
                var resp = (DefaultPlcReadResponse)await reader.Read((DefaultPlcReadRequest)rb.Build());
                var ok = resp.GetResponseCode("bit") == PlcResponseCode.Ok
                         && resp.GetResponseCode("byte") == PlcResponseCode.Ok
                         && resp.GetResponseCode("word") == PlcResponseCode.Ok
                         && (resp.GetValue("bit") ?? throw new S7DriverException("Missing bit value")).GetBool()
                         && (resp.GetValue("byte") ?? throw new S7DriverException("Missing byte value")).GetByte() == 0xA5
                         && (resp.GetValue("word") ?? throw new S7DriverException("Missing word value")).GetUshort() == 0xBEEF;
                if (ok) report.Pass("Read 3 tags in one request", "all three correct");
                else report.Fail("Read 3 tags in one request", "a tag was wrong or errored");
            }
            catch (Exception e)
            {
                report.Fail("Read 3 tags in one request", e.Message);
            }
        }

        private static async Task ReadAreaMatrix(PlcReader reader, IPlcConnection connection,
            Report report, string area, int offset)
        {
            var addresses = new[]
            {
                $"%{area}{offset}.0:BOOL", $"%{area}B{offset}:BYTE",
                $"%{area}W{offset}:WORD", $"%{area}D{offset}:DWORD",
            };
            foreach (var address in addresses)
            {
                try
                {
                    var value = await ReadOne(reader, connection, address);
                    report.Pass($"Read {address}", $"= {RenderAddress(address, value)}");
                }
                catch (Exception e)
                {
                    report.Fail($"Read {address}", e.Message);
                }
            }
        }

        private static (string Type, string Address, object Value)[] AreaWriteMatrix(string area, int offset) =>
            new[]
            {
                ("BOOL", $"%{area}{offset}.0:BOOL", (object)true),
                ("BYTE", $"%{area}B{offset + 1}:BYTE", (object)(byte)0x3C),
                ("INT", $"%{area}W{offset + 2}:INT", (object)(short)23456),
                ("DINT", $"%{area}D{offset + 4}:DINT", (object)(-123456789)),
                ("REAL", $"%{area}D{offset + 8}:REAL", (object)(-12.5f)),
                ("WORD", $"%{area}W{offset + 12}:WORD", (object)0x1357),
                ("DWORD", $"%{area}D{offset + 14}:DWORD", (object)0x89ABCDEFL),
            };

        private static async Task WriteTypeMatrix(PlcReader reader, PlcWriter writer,
            IPlcConnection connection, Report report,
            IEnumerable<(string Type, string Address, object Value)> cases, bool restore)
        {
            foreach (var item in cases)
            {
                await WriteRoundTripRestored(reader, writer, connection, report,
                    item.Type, item.Address, item.Value, restore);
            }
        }

        private static async Task WriteRoundTripRestored(PlcReader reader, PlcWriter writer,
            IPlcConnection connection, Report report, string type, string address,
            object testValue, bool restore)
        {
            IPlcValue? original = null;
            try
            {
                original = await ReadOne(reader, connection, address);
                var before = RenderAs(type, original);
                await WriteOne(writer, connection, address, testValue);
                var after = RenderAs(type, await ReadOne(reader, connection, address));
                var expected = RenderExpected(type, testValue);
                if (after == expected)
                    report.Pass($"Read/write/read {type} {address}", $"before {before}, after {after}");
                else
                    report.Fail($"Read/write/read {type} {address}",
                        $"before {before}, read back {after}, expected {expected}");
            }
            catch (Exception e)
            {
                report.Fail($"Read/write/read {type} {address}", e.Message);
            }
            finally
            {
                if (restore && original != null)
                {
                    try
                    {
                        await WriteOne(writer, connection, address, ToWritableValue(address, original));
                        var restored = await ReadOne(reader, connection, address);
                        if (Snapshot(address, restored) == Snapshot(address, original))
                            report.Pass($"Restore {address}", $"= {RenderAddress(address, restored)}");
                        else
                            report.Fail($"Restore {address}",
                                $"got {RenderAddress(address, restored)}, " +
                                $"expected {RenderAddress(address, original)}");
                    }
                    catch (Exception e)
                    {
                        report.Fail($"Restore {address}", e.Message);
                    }
                }
            }
        }

        private static async Task<IPlcValue> ReadOne(PlcReader reader,
            IPlcConnection connection, string address)
        {
            var rb = (DefaultPlcReadRequestBuilder)(connection.ReadRequestBuilder ?? throw new S7DriverException("Read is not supported"));
            rb.AddTagAddress("v", address);
            var response = (DefaultPlcReadResponse)await reader.Read((DefaultPlcReadRequest)rb.Build());
            var code = response.GetResponseCode("v");
            if (code != PlcResponseCode.Ok)
                throw new S7DriverException($"read returned {code}");
            return response.GetValue("v") ?? throw new S7DriverException("Missing read value");
        }

        private static async Task WriteOne(PlcWriter writer, IPlcConnection connection,
            string address, object value)
        {
            var wb = (DefaultPlcWriteRequestBuilder)(connection.WriteRequestBuilder ?? throw new S7DriverException("Write is not supported"));
            switch (value)
            {
                case bool v: wb.AddTag("w", address, v); break;
                case byte v: wb.AddTag("w", address, v); break;
                case short v: wb.AddTag("w", address, v); break;
                case int v: wb.AddTag("w", address, v); break;
                case long v: wb.AddTag("w", address, v); break;
                case float v: wb.AddTag("w", address, v); break;
                default: throw new S7DriverException($"unsupported write value {value.GetType().Name}");
            }
            var response = (DefaultPlcWriteResponse)await writer.Write((DefaultPlcWriteRequest)wb.Build());
            var code = response.GetResponseCode("w");
            if (code != PlcResponseCode.Ok)
                throw new S7DriverException($"write returned {code}");
        }

        private static object ToWritableValue(string address, IPlcValue value)
        {
            return S7Tag.Parse(address).DataType switch
            {
                TransportSize.BOOL => value.GetBool(),
                TransportSize.BYTE => value.GetByte(),
                TransportSize.INT => value.GetShort(),
                TransportSize.DINT => value.GetInt(),
                TransportSize.REAL => value.GetFloat(),
                TransportSize.WORD => (int)value.GetUshort(),
                TransportSize.DWORD => (long)value.GetUint(),
                _ => throw new S7DriverException($"Unsupported value type for {address}")
            };
        }

        private static string Snapshot(string address, IPlcValue value)
            => S7Tag.Parse(address).DataType == TransportSize.REAL
                ? BitConverter.SingleToInt32Bits(value.GetFloat()).ToString("X8", CultureInfo.InvariantCulture)
                : RenderAddress(address, value);

        private static string RenderAddress(string address, IPlcValue value)
            => RenderAs(S7Tag.Parse(address).DataType.ToString(), value);

        private static string RenderAs(string type, IPlcValue value) => type switch
        {
            "BOOL" => value.GetBool().ToString(),
            "BYTE" => $"0x{value.GetByte():X2}",
            "SINT" => value.GetSbyte().ToString(CultureInfo.InvariantCulture),
            "USINT" => value.GetByte().ToString(CultureInfo.InvariantCulture),
            "INT" => value.GetShort().ToString(CultureInfo.InvariantCulture),
            "UINT" => value.GetUshort().ToString(CultureInfo.InvariantCulture),
            "DINT" => value.GetInt().ToString(CultureInfo.InvariantCulture),
            "UDINT" => value.GetUint().ToString(CultureInfo.InvariantCulture),
            "REAL" => value.GetFloat()
                .ToString("0.####", CultureInfo.InvariantCulture),
            "WORD" => $"0x{value.GetUshort():X4}",
            "DWORD" => $"0x{value.GetUint():X8}",
            _ => Render(value),
        };

        private static string RenderExpected(string type, object value) => type switch
        {
            "BOOL" => ((bool)value).ToString(),
            "BYTE" => $"0x{(byte)value:X2}",
            "INT" => ((short)value).ToString(CultureInfo.InvariantCulture),
            "DINT" => ((int)value).ToString(CultureInfo.InvariantCulture),
            "REAL" => ((float)value).ToString("0.####", CultureInfo.InvariantCulture),
            "WORD" => $"0x{Convert.ToUInt16(value, CultureInfo.InvariantCulture):X4}",
            "DWORD" => $"0x{Convert.ToUInt32(value, CultureInfo.InvariantCulture):X8}",
            _ => value.ToString() ?? string.Empty,
        };

        private static async Task ErrorPath(PlcReader reader, IPlcConnection connection, Report report)
        {
            try
            {
                var rb = (DefaultPlcReadRequestBuilder)(connection.ReadRequestBuilder ?? throw new S7DriverException("Read is not supported"));
                rb.AddTagAddress("v", "%DB31999.DBW0:WORD");
                var resp = (DefaultPlcReadResponse)await reader.Read((DefaultPlcReadRequest)rb.Build());
                var code = resp.GetResponseCode("v");
                if (code != PlcResponseCode.Ok)
                {
                    report.Pass("Read a non-existent DB", $"rejected with {code} (connection survived)");
                }
                else
                {
                    report.Fail("Read a non-existent DB", "unexpectedly returned Ok");
                }
            }
            catch (Exception e)
            {
                report.Fail("Read a non-existent DB", e.Message);
            }
        }

        private static Dictionary<string, string> ParseOptions(string[] args)
        {
            var values = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
                { "rack", "slot", "db", "device-group", "remote-tsap", "read", "i-base", "q-base", "m-base" };
            var flags = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
                { "write-db", "write-markers", "write-outputs", "keep-db-values", "keep-marker-values", "keep-output-values" };
            var opts = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            for (var i = 1; i < args.Length; i++)
            {
                if (!args[i].StartsWith("--", StringComparison.Ordinal))
                    throw new ArgumentException($"Unexpected argument: {args[i]}");
                var key = args[i].Substring(2);
                if (opts.ContainsKey(key)) throw new ArgumentException($"Duplicate option: --{key}");
                if (flags.Contains(key)) opts.Add(key, "true");
                else if (values.Contains(key) && i + 1 < args.Length && !args[i + 1].StartsWith("--", StringComparison.Ordinal))
                    opts.Add(key, args[++i]);
                else throw new ArgumentException($"Unknown option or missing value: --{key}");
            }
            foreach (var area in new[] { "db", "marker", "output" })
                if (opts.ContainsKey($"keep-{area}-values") && !opts.ContainsKey(area == "db" ? "write-db" : $"write-{area}s"))
                    throw new ArgumentException($"--keep-{area}-values requires the corresponding write option");
            return opts;
        }

        private static string Get(Dictionary<string, string> opts, string key, string fallback) =>
            opts.TryGetValue(key, out var v) ? v : fallback;

        private static int GetInt(Dictionary<string, string> opts, string key, int fallback) =>
            opts.TryGetValue(key, out var value)
                ? int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out var parsed)
                    ? parsed : throw new ArgumentException($"Invalid value for --{key}: {value}")
                : fallback;

        private sealed class Report
        {
            private readonly List<string> _lines = new();
            private readonly List<string> _rows = new();
            public int Passes { get; private set; }
            public int Failures { get; private set; }

            public void Line(string text = "") => _lines.Add(text);

            public void Pass(string step, string detail)
            {
                Passes++;
                _rows.Add($"| ✅ | {step} | {detail} |");
            }

            public void Fail(string step, string detail)
            {
                Failures++;
                _rows.Add($"| ❌ | {step} | {detail} |");
            }

            public void Flush()
            {
                var w = Console.Out;
                foreach (var l in _lines) w.WriteLine(l);
                if (_rows.Count > 0)
                {
                    w.WriteLine();
                    w.WriteLine("| | Step | Detail |");
                    w.WriteLine("|---|---|---|");
                    foreach (var r in _rows) w.WriteLine(r);
                    w.WriteLine();
                    w.WriteLine(Failures == 0
                        ? $"## Result: PASS ({Passes}/{Passes})"
                        : $"## Result: FAIL ({Passes} passed, {Failures} failed)");
                }
                w.Flush();
                _lines.Clear();
                _rows.Clear();
            }
        }
    }
}