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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class ModbusPanelBuilderTest {

    private static ModbusPanelBuilder builder(String protocol) {
        ModbusPanelBuilder b = new ModbusPanelBuilder();
        b.initForModel(new DeviceModel("Modbus", protocol, protocol));
        return b;
    }

    @Test
    void tcpVariantDoesNotOfferSerial() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        assertArrayEquals(new String[]{"tcp", "tls", "tls-psk", "udp"}, b.getTransportOptions());
        assertEquals("tcp", b.getDefaultTransport());
    }

    @Test
    void rtuVariantDefaultsToSerial() {
        ModbusPanelBuilder b = builder("modbus-rtu");
        assertArrayEquals(new String[]{"serial", "tcp", "tls", "tls-psk", "udp"}, b.getTransportOptions());
        assertEquals("serial", b.getDefaultTransport());
    }

    @Test
    void asciiVariantDefaultsToSerial() {
        ModbusPanelBuilder b = builder("modbus-ascii");
        assertArrayEquals(new String[]{"serial", "tcp", "tls", "tls-psk", "udp"}, b.getTransportOptions());
        assertEquals("serial", b.getDefaultTransport());
    }

    @Test
    void unknownProtocolFallsBackToTcpVariant() {
        ModbusPanelBuilder b = builder("modbus-weird");
        assertEquals("modbus-tcp", b.getProtocol());
        assertArrayEquals(new String[]{"tcp", "tls", "tls-psk", "udp"}, b.getTransportOptions());
    }

    @Test
    void commonOptionsAlwaysPresent() {
        ModbusPanelBuilder b = builder("modbus-rtu");
        LinkedHashMap<String, String> serial = b.getParameterDefaults("serial");
        assertEquals("1", serial.get("default-unit-identifier"));
        assertEquals("5000", serial.get("request-timeout-ms"));
        assertEquals("4x00001:BOOL", serial.get("ping-address"));
        assertEquals("BIG_ENDIAN", serial.get("default-payload-byte-order"));
        assertEquals("2000", serial.get("max-coils-per-request"));
        assertEquals("125", serial.get("max-registers-per-request"));
    }

    @Test
    void transportOptionsAppearOnlyForTheirTransport() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        LinkedHashMap<String, String> tcp = b.getParameterDefaults("tcp");
        assertTrue(tcp.keySet().stream().noneMatch(k -> k.startsWith("serial.")));
        assertTrue(tcp.keySet().stream().noneMatch(k -> k.startsWith("udp.")));
        assertTrue(tcp.keySet().stream().noneMatch(k -> k.startsWith("tls.")));
        assertTrue(tcp.keySet().stream().noneMatch(k -> k.startsWith("tls-psk.")));
        assertEquals("5000", tcp.get("tcp.connect-timeout-ms"));
        assertFalse(tcp.isEmpty());

        LinkedHashMap<String, String> udp = b.getParameterDefaults("udp");
        assertEquals("65507", udp.get("udp.max-packet-size"));
        assertTrue(udp.keySet().stream().noneMatch(k -> k.startsWith("tcp.")));
    }

    @Test
    void tlsCatalogInheritsTcpOptionsAndAddsTlsOnes() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        LinkedHashMap<String, String> tls = b.getParameterDefaults("tls");
        assertEquals("5000", tls.get("tcp.connect-timeout-ms"));
        assertEquals("true", tls.get("tls.verify"));
        assertEquals("false", tls.get("tls.ignore-common-name"));
        assertEquals("PKCS12", tls.get("tls.trust-store-type"));
        assertTrue(tls.containsKey("tls.trust-store"));
        assertTrue(tls.containsKey("tls.trust-store-password"));
        assertTrue(tls.containsKey("tls.version"));
        assertTrue(tls.containsKey("tls.keystore"));
        assertTrue(tls.containsKey("tls.keystore-password"));
        assertTrue(tls.containsKey("tls.keystore-type"));
        assertEquals("false", tls.get("tls.log-session-keys"));
        assertEquals("1", tls.get("default-unit-identifier"));
    }

    @Test
    void tlsPskCatalogInheritsTcpOptionsAndAddsPskOnes() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        LinkedHashMap<String, String> psk = b.getParameterDefaults("tls-psk");
        assertEquals("5000", psk.get("tcp.connect-timeout-ms"));
        assertTrue(psk.containsKey("tls-psk.psk-identity"));
        assertTrue(psk.containsKey("tls-psk.psk-key"));
        assertEquals("false", psk.get("tls-psk.log-session-keys"));
        assertTrue(psk.keySet().stream().noneMatch(k -> k.startsWith("tls.")));
    }

    @Test
    void tlsPskNotOfferedByDefaultButIsAPortTransport() {
        ModbusPanelBuilder b = builder("modbus-rtu");
        b.setTransport("tls-psk");
        b.setHost("10.0.0.5");
        b.setPort("802");
        b.addParameter("tls-psk.psk-identity", "mydevice");
        b.addParameter("tls-psk.psk-key", "0011223344556677");
        assertEquals("modbus-rtu:tls-psk://10.0.0.5:802?tls-psk.psk-identity=mydevice&tls-psk.psk-key=0011223344556677",
                b.getSpecificParametersAsString());
        assertTrue(b.isPortApplicable());
        assertEquals("Host/IP:", b.getHostLabel());
    }

    @Test
    void tlsTransportUsesHostAndPort() {
        ModbusPanelBuilder b = builder("modbus-rtu");
        b.setTransport("tls");
        b.setHost("10.0.0.5");
        b.setPort("802");
        b.addParameter("tls.verify", "false");
        assertEquals("modbus-rtu:tls://10.0.0.5:802?tls.verify=false",
                b.getSpecificParametersAsString());
    }

    @Test
    void serialTransportIgnoresPortAndUsesDevicePath() {
        ModbusPanelBuilder b = builder("modbus-rtu");
        b.setTransport("serial");
        b.setHost("/dev/ttyUSB0");
        b.setPort("1234");
        b.addParameter("default-unit-identifier", "2");
        assertEquals("modbus-rtu:serial:///dev/ttyUSB0?default-unit-identifier=2",
                b.getSpecificParametersAsString());
        assertFalse(b.isPortApplicable());
        assertEquals("Puerto/Dispositivo:", b.getHostLabel());
    }

    @Test
    void tcpTransportAppendsPortAndParams() {
        ModbusPanelBuilder b = builder("modbus-rtu");
        b.setTransport("tcp");
        b.setHost("127.0.0.1");
        b.setPort("5020");
        b.addParameter("default-unit-identifier", "1");
        assertEquals("modbus-rtu:tcp://127.0.0.1:5020?default-unit-identifier=1",
                b.getSpecificParametersAsString());
        assertTrue(b.isPortApplicable());
        assertEquals("Host/IP:", b.getHostLabel());
    }

    @Test
    void udpTransportUsesHostAndPort() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        b.setTransport("udp");
        b.setHost("10.0.0.5");
        b.setPort("502");
        b.addParameter("udp.broadcast", "true");
        assertEquals("modbus-tcp:udp://10.0.0.5:502?udp.broadcast=true",
                b.getSpecificParametersAsString());
    }

    @Test
    void omitOptionalFields() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        b.setTransport("tcp");
        b.setHost("127.0.0.1");
        assertEquals("modbus-tcp:tcp://127.0.0.1", b.getSpecificParametersAsString());
    }

    @Test
    void retainParametersPrunesKeysOutsideCatalog() {
        ModbusPanelBuilder b = builder("modbus-tcp");
        b.setTransport("tcp");
        b.addParameter("tcp.no-delay", "true");
        b.addParameter("default-unit-identifier", "1");
        b.setTransport("udp");
        b.retainParameters(b.getParameterDefaults("udp").keySet());
        String url = b.getSpecificParametersAsString();
        assertTrue(url.contains("default-unit-identifier=1"));
        assertFalse(url.contains("tcp.no-delay"));
    }
}