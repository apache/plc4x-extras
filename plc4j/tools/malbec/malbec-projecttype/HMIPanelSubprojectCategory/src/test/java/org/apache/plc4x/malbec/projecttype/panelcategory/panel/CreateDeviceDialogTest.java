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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;

class CreateDeviceDialogTest {

    private static DeviceConfigData device(String uuid, String name, String url) {
        Properties properties = new Properties();
        properties.setProperty("uuid", uuid);
        properties.setProperty("deviceName", name);
        properties.setProperty("specificParameters", url == null ? "" : url);
        return new DeviceConfigData(properties);
    }

    @Test
    void rejectsDuplicateNameEvenWhenIpDiffers() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", " Compresor 1 ", "s7://10.0.0.2"),
                List.of(device("uuid-old", "compresor 1", "s7://10.0.0.3")));

        assertNotNull(message);
        assertTrue(message.contains("The name 'Compresor 1'"));
    }

    @Test
    void rejectsDuplicateIpEvenWhenNameDiffers() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo", "s7://192.168.0.10:102"),
                List.of(device("uuid-old", "PLC viejo",
                        "s7:cotp://192.168.0.10:102?cotp.remote-slot=3")));

        assertNotNull(message);
        assertTrue(message.contains("The IP '192.168.0.10'"));
        assertTrue(message.contains("PLC viejo"));
    }

    @Test
    void rejectsSameIpWithDifferentPort() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo", "s7://192.168.0.10:102"),
                List.of(device("uuid-old", "PLC", "s7://192.168.0.10:502")));

        assertNotNull(message);
        assertTrue(message.contains("The IP '192.168.0.10'"));
    }

    @Test
    void allowsDifferentNamesAndIps() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo", "s7://192.168.0.10:102"),
                List.of(device("uuid-old", "PLC", "s7://192.168.0.11:102")));

        assertNull(message);
    }

    @Test
    void allowsEditingTheSameDevice() {
        DeviceConfigData candidate =
                device("uuid-1", "Compresor 1", "s7://192.168.0.10:102");
        DeviceConfigData existing =
                device("uuid-1", "Compresor 1", "s7://192.168.0.10:102");

        assertNull(CreateDeviceDialog.duplicateDeviceMessage(candidate, List.of(existing)));
    }

    @Test
    void detectsRedundantHostOverlap() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo",
                        "s7:cotp://10.0.0.1:102/10.0.0.2:102"),
                List.of(device("uuid-old", "PLC",
                        "s7:cotp://10.0.0.9:102/10.0.0.2:102")));

        assertNotNull(message);
        assertTrue(message.contains("The IP '10.0.0.2'"));
    }

    @Test
    void reportsNameAndIpConflictsTogether() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Compresor 1", "s7://192.168.0.50:102"),
                List.of(
                        device("uuid-name", "Compresor 1", "s7://192.168.0.10:102"),
                        device("uuid-ip", "Otro", "s7://192.168.0.50:102")));

        assertNotNull(message);
        assertTrue(message.contains("The name 'Compresor 1'"));
        assertTrue(message.contains("The IP '192.168.0.50'"));
    }

    @Test
    void ignoresEmptyOrMalformedUrls() {
        assertNull(CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo", ""),
                List.of(device("uuid-old", "PLC", ""))));
        assertNull(CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo", "not-a-url"),
                List.of(device("uuid-old", "PLC", "also-not-a-url"))));
    }

    @Test
    void doesNotSplitSerialPathsAsRedundantHosts() {
        String message = CreateDeviceDialog.duplicateDeviceMessage(
                device("uuid-new", "Nuevo", "modbus-rtu:///dev/ttyUSB0"),
                List.of(device("uuid-old", "PLC", "modbus-rtu:///dev/ttyUSB1")));

        assertNull(message);
    }
}
