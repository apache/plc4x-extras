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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicVariableXmlMapperTest {

    private static DeviceConfigData device() {
        Properties properties = new Properties();
        properties.setProperty("deviceName", "PLC-1");
        properties.setProperty("brand", "Siemens");
        properties.setProperty("model", "S7-1200");
        properties.setProperty("protocol", "s7");
        properties.setProperty("description", "Main PLC");
        properties.setProperty("uuid", "device-1");
        properties.setProperty("enable", "true");
        properties.setProperty("s88Node", "Line 1");
        properties.setProperty("s88Uuid", "s88-1");
        properties.setProperty("deviceKey", "key-1");
        properties.setProperty("specificParameters", "rack=0;slot=1");
        return new DeviceConfigData(properties);
    }

    private static PvConfigData pv() {
        return new PvConfigData(
                "pv-1", "Temperature", "real", "PLC-1", "%DB1.DBD0",
                "Oven temperature", "1000ms", true, false,
                "0", "100", "Temperature", "0.0", "C",
                "10", "90", "1", "abc123");
    }

    @Test
    void writesAndReadsDevicesAndPvs(@TempDir Path folder) throws Exception {
        DynamicVariableXmlMapper.write(
                folder.toFile(), List.of(device()), List.of(pv()));

        assertTrue(Files.exists(folder.resolve(DynamicVariableXmlMapper.FILE_NAME)));

        DynamicVariableXmlMapper.Content content
                = DynamicVariableXmlMapper.read(folder.toFile());

        assertEquals(1, content.devices().size());
        DeviceConfigData device = content.devices().get(0);
        assertEquals("PLC-1", device.getDeviceName());
        assertEquals("Siemens", device.getBrand());
        assertEquals("S7-1200", device.getModel());
        assertEquals("device-1", device.getUuid());
        assertTrue(device.isEnabled());

        assertEquals(1, content.pvs().size());
        PvConfigData pv = content.pvs().get(0);
        assertEquals("pv-1", pv.getUuid());
        assertEquals("Temperature", pv.getName());
        assertEquals("real", pv.getType());
        assertEquals("PLC-1", pv.getDeviceName());
        assertEquals("%DB1.DBD0", pv.getItemTag());
        assertEquals("Oven temperature", pv.getDescriptorText());
        assertEquals("1000ms", pv.getScanTime());
        assertTrue(pv.isScanEnable());
        assertFalse(pv.isWriteEnable());
        assertEquals("0", pv.getDisplayLimitLow());
        assertEquals("100", pv.getDisplayLimitHigh());
        assertEquals("abc123", pv.getMd5());
    }

    @Test
    void writingAgainRemovesVariablesThatAreNoLongerPresent(@TempDir Path folder)
            throws Exception {
        DynamicVariableXmlMapper.write(
                folder.toFile(), List.of(device()), List.of(pv()));
        DynamicVariableXmlMapper.write(
                folder.toFile(), List.of(device()), List.of());

        DynamicVariableXmlMapper.Content content
                = DynamicVariableXmlMapper.read(folder.toFile());

        assertEquals(1, content.devices().size());
        assertTrue(content.pvs().isEmpty());
    }

    @Test
    void missingFileReturnsEmptyContent(@TempDir Path folder) throws Exception {
        DynamicVariableXmlMapper.Content content
                = DynamicVariableXmlMapper.read(folder.toFile());

        assertTrue(content.devices().isEmpty());
        assertTrue(content.pvs().isEmpty());
        assertFalse(Files.exists(folder.resolve(DynamicVariableXmlMapper.FILE_NAME)));
    }
}
