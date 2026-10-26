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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import org.plcopen.xml.tc60201.ProjectDocument;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations.Configuration;

class DeviceXmlMapperTest {

    @Test
    void templateIsSchemaValid() throws Exception {
        assertTrue(ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE)
                .validate());
    }

    @Test
    void roundTripDevice() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Properties pDevice = new Properties();

        pDevice.put("brand", "Siemens");
        pDevice.put("model", "S7-1500");
        pDevice.put("protocol", "S7");
        pDevice.put("deviceName", "PLC_Uno");
        pDevice.put("description", "Control principal");
        pDevice.put("uuid", "uuid-1");
        pDevice.put("enable", true);
        pDevice.put("s88Node", "S88.1");
        pDevice.put("s88Uuid", "s88-uuid-1");
        pDevice.put("specificParameters", "ipa=10.0.0.1\nrack=0\nslot=1");

        DeviceConfigData device = new DeviceConfigData(pDevice);

        DeviceXmlMapper.writeDevice(device, configurations);

        DeviceConfigData read = DeviceXmlMapper.readDevice(configurations.getConfigurationArray(0));

        assertNotNull(read);
        assertEquals(device.getBrand(), read.getBrand());
        assertEquals(device.getModel(), read.getModel());
        assertEquals(device.getProtocol(), read.getProtocol());
        assertEquals(device.getDeviceName(), read.getDeviceName());
        assertEquals(device.getDescription(), read.getDescription());
        assertEquals(device.getUuid(), read.getUuid());
        assertEquals(device.isEnabled(), read.isEnabled());
        assertEquals(device.getS88Node(), read.getS88Node());
        assertEquals(device.getS88Uuid(), read.getS88Uuid());
        assertEquals(device.getSpecificParameters(), read.getSpecificParameters());
    }

    @Test
    void escapeSpecialCharacters() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();
        Properties pDevice = new Properties();

        pDevice.put("brand", "Siemens & Co<\"XL\">");
        pDevice.put("model", "A 'B'");
        pDevice.put("protocol", "S7");
        pDevice.put("deviceName", "PLC & \"A\" <1>");
        pDevice.put("description", "line1\nline2 & more <now>");
        pDevice.put("uuid", "uuid-1");
        pDevice.put("enable", false);
        pDevice.put("s88Node", "");
        pDevice.put("s88Uuid", "");
        pDevice.put("specificParameters", "x=1&y=2");

        DeviceConfigData device = new DeviceConfigData(pDevice);

        DeviceXmlMapper.writeDevice(device, configurations);

        DeviceConfigData read = DeviceXmlMapper.readDevice(configurations.getConfigurationArray(0));

        assertEquals(device.getDeviceName(), read.getDeviceName());
        assertEquals(device.getBrand(), read.getBrand());
        assertEquals(device.getModel(), read.getModel());
        assertEquals(device.getDescription(), read.getDescription());
        assertEquals(device.getSpecificParameters(), read.getSpecificParameters());
    }

    @Test
    void readDeviceReturnsNullWhenNoPayload() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        assertTrue(doc.getProject().getInstances().getConfigurations().sizeOfConfigurationArray() == 0);
    }

    @Test
    void nullFieldsRoundTripAsEmptyString() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Properties pDevice = new Properties();

        pDevice.put("deviceName", "PLC");
        pDevice.put("enable", false);

        DeviceConfigData device = new DeviceConfigData(pDevice);

        DeviceXmlMapper.writeDevice(device, configurations);

        DeviceConfigData read = DeviceXmlMapper.readDevice(configurations.getConfigurationArray(0));

        assertEquals("PLC", read.getDeviceName());
        assertEquals("", read.getBrand());
        assertEquals("", read.getModel());
        assertEquals("", read.getDescription());
        assertEquals("", read.getUuid());
        assertEquals("", read.getS88Node());
        assertEquals("", read.getS88Uuid());
        assertEquals("", read.getSpecificParameters());
    }

    @Test
    void valuesWithSpecialCharactersAreEscapedByTheSerializer() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Properties pDevice = new Properties();
        pDevice.put("deviceName", "PLC");
        pDevice.put("enable", true);
        pDevice.put("uuid", "VFD Bomba A <<1>>");
        pDevice.put("description", "linea1\nlinea2 & mas <ahora> \"Q\" 'Z'");

        Configuration configuration =
                DeviceXmlMapper.writeDevice(new DeviceConfigData(pDevice), configurations);

        String xml = configuration.xmlText();

        assertTrue(xml.contains("&amp;"), "El ampersand debe escaparse: " + xml);
        assertTrue(xml.contains("&lt;"), "El menor que debe escaparse: " + xml);
        assertFalse(xml.contains("escape"), "No debe quedar el nombre de un metodo de escape en el XML");
        assertFalse(xml.contains("&amp;amp;"), "No debe escaparse dos veces: " + xml);

        DeviceConfigData read = DeviceXmlMapper.readDevice(configuration);

        assertEquals("linea1\nlinea2 & mas <ahora> \"Q\" 'Z'", read.getDescription(),
                "Los saltos de linea y los caracteres especiales deben volver intactos");
        assertEquals("VFD Bomba A <<1>>", read.getUuid());
    }

    @Test
    void deviceKeepsEveryFieldIncludingTheOnesWithoutAttribute() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Properties pDevice = new Properties();
        pDevice.put("deviceName", "PLC_Uno");
        pDevice.put("brand", "Siemens");
        pDevice.put("model", "S7-1500");
        pDevice.put("protocol", "S7");
        pDevice.put("deviceKey", "key-1");
        pDevice.put("description", "Control principal");
        pDevice.put("uuid", "uuid-1");
        pDevice.put("enable", false);
        pDevice.put("s88Node", "S88.1");
        pDevice.put("s88Uuid", "s88-uuid-1");
        pDevice.put("specificParameters", "ipa=10.0.0.1,rack=0");

        Configuration configuration =
                DeviceXmlMapper.writeDevice(new DeviceConfigData(pDevice), configurations);

        DeviceConfigData read = DeviceXmlMapper.readDevice(configuration);

        assertEquals("Siemens", read.getBrand());
        assertEquals("S7-1500", read.getModel());
        assertEquals("S7", read.getProtocol());
        assertEquals("key-1", read.getDeviceKey());
        assertEquals("Control principal", read.getDescription());
        assertEquals("S88.1", read.getS88Node());
        assertEquals("s88-uuid-1", read.getS88Uuid());
        assertEquals("ipa=10.0.0.1,rack=0", read.getSpecificParameters());
        assertFalse(read.isEnabled(), "El atributo se llama enabled pero la propiedad enable");
        assertEquals("PLC_Uno", read.getDeviceName(), "El nombre viene de configuration, no del XML");
    }
}
