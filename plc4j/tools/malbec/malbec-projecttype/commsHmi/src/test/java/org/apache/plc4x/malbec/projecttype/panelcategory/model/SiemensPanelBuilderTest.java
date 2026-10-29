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

class SiemensPanelBuilderTest {

    private static SiemensPanelBuilder builder(String model) {
        SiemensPanelBuilder b = new SiemensPanelBuilder();
        b.initForModel(new DeviceModel("Siemens", model, "s7"));
        return b;
    }

    @Test
    void transportHasNoEmptyOptionAndDefaultsToCotp() {
        SiemensPanelBuilder b = builder("S7-1200");
        assertArrayEquals(new String[]{"cotp"}, b.getTransportOptions());
        assertEquals("cotp", b.getDefaultTransport());
    }

    @Test
    void catalogContainsAllDriverAndCotpOptions() {
        SiemensPanelBuilder b = builder("S7-1200");
        LinkedHashMap<String, String> catalog = b.getParameterDefaults();
        assertEquals(27, catalog.size());

        assertEquals("1024", catalog.get("pdu-size"));
        assertEquals("8", catalog.get("max-amq-caller"));
        assertEquals("8", catalog.get("max-amq-callee"));
        assertEquals("ANY", catalog.get("controller-type"));
        assertEquals("10000", catalog.get("read-timeout-ms"));
        assertEquals("4000", catalog.get("ha-heartbeat-interval-ms"));
        assertEquals("2000", catalog.get("ha-failover-timeout-ms"));

        assertEquals("1", catalog.get("cotp.local-rack"));
        assertEquals("1", catalog.get("cotp.local-slot"));
        assertEquals("OTHERS", catalog.get("cotp.local-device-group"));
        assertEquals("0", catalog.get("cotp.remote-rack"));
        assertEquals("0", catalog.get("cotp.remote-slot"));
        assertEquals("PG_OR_PC", catalog.get("cotp.remote-device-group"));
        assertEquals("0", catalog.get("cotp.local-tsap"));
        assertEquals("0", catalog.get("cotp.remote-tsap"));
        assertEquals("8192", catalog.get("cotp.tpdu-size"));
        assertEquals("5000", catalog.get("cotp.handshake-timeout-ms"));
        assertEquals("0", catalog.get("cotp.protocol-class"));
        assertEquals("5000", catalog.get("cotp.connect-timeout-ms"));
        assertEquals("0", catalog.get("cotp.read-timeout-ms"));
        assertEquals("0", catalog.get("cotp.write-timeout-ms"));
        assertEquals("true", catalog.get("cotp.no-delay"));
        assertEquals("false", catalog.get("cotp.keep-alive"));
        assertEquals("81920", catalog.get("cotp.send-buffer-size"));
        assertEquals("81920", catalog.get("cotp.receive-buffer-size"));
        assertEquals("", catalog.get("cotp.local-address"));
        assertEquals("0", catalog.get("cotp.local-port"));
    }

    @Test
    void noArgAndTransportCatalogsAreIdentical() {
        SiemensPanelBuilder b = builder("S7-1200");
        assertEquals(b.getParameterDefaults(), b.getParameterDefaults("cotp"));
        assertEquals(b.getParameterDefaults(), b.getParameterDefaults(""));
    }

    @Test
    void initForModelDoesNotSeedControllerType() {
        SiemensPanelBuilder b = builder("S7-1200");
        b.setHost("10.10.1.33");
        b.addParameter("cotp.remote-rack", "0");
        b.addParameter("cotp.remote-slot", "3");
        String url = b.getSpecificParametersAsString();
        assertTrue(url.startsWith("s7:cotp://10.10.1.33?"));
        assertTrue(url.contains("cotp.remote-rack=0"));
        assertTrue(url.contains("cotp.remote-slot=3"));
        assertFalse(url.contains("controller-type"));
        assertEquals("s7:cotp://10.10.1.33?cotp.remote-rack=0&cotp.remote-slot=3", url);
        assertTrue(b.getParameterDefaults().containsKey("controller-type"));
        assertTrue(b.isPortApplicable());
        assertEquals("Host/IP:", b.getHostLabel());
    }

    @Test
    void explicitTransportCanBeDeclared() {
        SiemensPanelBuilder b = builder("S7-300");
        b.setTransport("cotp");
        b.setHost("192.168.0.10");
        b.addParameter("controller-type", "S7_300");
        assertEquals("s7:cotp://192.168.0.10?controller-type=S7_300",
                b.getSpecificParametersAsString());
    }

    @Test
    void controllerTypeKeepsItsInsertionOrder() {
        SiemensPanelBuilder b = builder("S7-1500");
        b.setHost("10.0.0.1");
        b.addParameter("controller-type", "S7_1500");
        b.addParameter("cotp.remote-rack", "0");
        b.addParameter("cotp.remote-slot", "1");
        b.addParameter("read-timeout-ms", "20000");
        String url = b.getSpecificParametersAsString();
        assertTrue(url.indexOf("controller-type=S7_1500") < url.indexOf("read-timeout-ms=20000"));
        assertEquals("s7:cotp://10.0.0.1?controller-type=S7_1500&cotp.remote-rack=0&cotp.remote-slot=1&read-timeout-ms=20000",
                url);
    }

    @Test
    void retainParametersPrunesKeysOutsideCatalog() {
        SiemensPanelBuilder b = builder("S7-400");
        b.setHost("10.0.0.2");
        b.addParameter("cotp.remote-rack", "0");
        b.addParameter("pdu-size", "512");
        b.addParameter("legacy.no-delay", "true");
        b.retainParameters(b.getParameterDefaults().keySet());
        String url = b.getSpecificParametersAsString();
        assertTrue(url.contains("cotp.remote-rack=0"));
        assertTrue(url.contains("pdu-size=512"));
        assertFalse(url.contains("legacy.no-delay"));

        b.addParameter("controller-type", "S7_400");
        b.retainParameters(java.util.Set.of("controller-type"));
        url = b.getSpecificParametersAsString();
        assertFalse(url.contains("cotp.remote-rack"));
        assertFalse(url.contains("pdu-size"));
        assertTrue(url.contains("controller-type=S7_400"));
    }

    @Test
    void removeLastParameterAndClearWork() {
        SiemensPanelBuilder b = builder("S7-1200");
        b.setHost("10.0.0.3");
        b.addParameter("cotp.remote-rack", "0");
        b.addParameter("cotp.remote-slot", "2");
        b.removeLastParameter();
        assertFalse(b.getSpecificParametersAsString().contains("cotp.remote-slot"));
        b.clearParameters();
        assertEquals("s7:cotp://10.0.0.3", b.getSpecificParametersAsString());
    }

    @Test
    void validateParameterAcceptsACatalogValue() {
        SiemensPanelBuilder b = builder("S7-1200");

        assertFalse(b.validateParameter("pdu-size", "2048").isValid());
        assertTrue(b.validateParameter("cotp.remote-slot", "3").isValid());
    }

    @Test
    void validateParameterRejectsANonNumericValue() {
        SiemensPanelBuilder b = builder("S7-1200");

        ValidationResult result = b.validateParameter("pdu-size", "mucho");

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("pdu-size"), result.getMessage());
    }

    @Test
    void validateParameterRejectsNonPositiveInteger() {
        SiemensPanelBuilder b = builder("S7-1200");

        assertFalse(b.validateParameter("pdu-size", "0").isValid());
        assertFalse(b.validateParameter("pdu-size", "-8").isValid());
        assertFalse(b.validateParameter("read-timeout-ms", "-1").isValid());
    }

    @Test
    void validateParameterRejectsPortOutOfRange() {
        SiemensPanelBuilder b = builder("S7-1200");

        assertTrue(b.validateParameter("cotp.local-port", "65535").isValid());
        assertFalse(b.validateParameter("cotp.local-port", "65536").isValid());
    }

    @Test
    void validateParameterIgnoresUnknownKeys() {
        SiemensPanelBuilder b = builder("S7-1200");

        assertTrue(b.validateParameter("legacy.no-delay", "cualquier cosa").isValid());
    }

    @Test
    void validateParameterAcceptsEmptyValueForOptionalParameters() {
        SiemensPanelBuilder b = builder("S7-1200");

        assertTrue(b.validateParameter("controller-type", "").isValid());
        assertTrue(b.validateParameter("cotp.local-address", null).isValid());
    }
}
