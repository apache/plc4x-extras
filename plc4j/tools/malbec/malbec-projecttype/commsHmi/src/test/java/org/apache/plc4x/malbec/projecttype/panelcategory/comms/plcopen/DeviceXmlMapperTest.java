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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
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
    void roundTripComms() throws Exception {
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
        pDevice.put("specificParameters", "");

        DeviceConfigData device = new DeviceConfigData(pDevice);

        Configuration configuration = DeviceXmlMapper.writeDevice(device, configurations);

        CommConfigData comms = new CommConfigData("PLC_Uno",
                List.of(new CommConfigData.GroupConfig("g1", "Grupo1", "Grupo 1", "500", true, "md5g")),
                List.of(new CommConfigData.ItemConfig("i1", "Item1", "Item 1", "tag1", true, "md5i")),
                List.of(new CommConfigData.PvConfig("p1", "PV1", "INT", "pv-id", "0", "descriptor",
                        "500", true, true, "-100", "100", "PV uno", "%.2f", "gpm",
                        "-50", "50", "0.1", "md5p")));

        DeviceXmlMapper.writeComms(comms, configuration);

        CommConfigData read = DeviceXmlMapper.readComms(configuration);

        assertNotNull(read);
        assertEquals("PLC_Uno", read.getDeviceName());
        assertEquals(1, read.getGroups().size());
        CommConfigData.GroupConfig g = read.getGroups().get(0);
        assertEquals("g1", g.getUuid());
        assertEquals("Grupo1", g.getName());
        assertEquals("Grupo 1", g.getDescription());
        assertEquals("500", g.getScantime());
        assertEquals(true, g.isEnable());
        assertEquals("md5g", g.getMd5());
        assertEquals(1, read.getItems().size());
        CommConfigData.ItemConfig i = read.getItems().get(0);
        assertEquals("i1", i.getUuid());
        assertEquals("Item1", i.getName());
        assertEquals("Item 1", i.getDescription());
        assertEquals("tag1", i.getTag());
        assertEquals(true, i.isEnable());
        assertEquals(1, read.getPvs().size());
        CommConfigData.PvConfig p = read.getPvs().get(0);
        assertEquals("p1", p.getUuid());
        assertEquals("PV1", p.getName());
        assertEquals("INT", p.getType());
        assertEquals("pv-id", p.getAreaUuid());
        assertEquals("0", p.getOffset());
        assertEquals("descriptor", p.getDescriptorText());
        assertEquals("500", p.getScanTime());
        assertEquals(true, p.isScanEnable());
        assertEquals(true, p.isWriteEnable());
        assertEquals("-100", p.getDisplayLimitLow());
        assertEquals("100", p.getDisplayLimitHigh());
        assertEquals("PV uno", p.getDisplayDescription());
        assertEquals("%.2f", p.getDisplayFormat());
        assertEquals("gpm", p.getDisplayUnits());
        assertEquals("-50", p.getControlLimitLow());
        assertEquals("50", p.getControlLimitHigh());
        assertEquals("0.1", p.getControlMinStep());
        assertEquals("md5p", p.getMd5());
    }

    @Test
    void childrenKeepInsertionOrder() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Configuration configuration = DeviceXmlMapper.writeDevice(namedDevice("PLC"), configurations);

        DeviceXmlMapper.writeComms(new CommConfigData("PLC",
                List.of(new CommConfigData.GroupConfig("g-1", "Uno", "", "500", true, "m1"),
                        new CommConfigData.GroupConfig("g-2", "Dos", "", "500", true, "m2"),
                        new CommConfigData.GroupConfig("g-3", "Tres", "", "500", true, "m3")),
                List.of(), List.of()), configuration);

        String xml = configuration.xmlText();

        assertTrue(xml.indexOf("\"g-1\"") < xml.indexOf("\"g-2\""), "g-1 debe preceder a g-2");
        assertTrue(xml.indexOf("\"g-2\"") < xml.indexOf("\"g-3\""), "g-2 debe preceder a g-3");
    }

    @Test
    void emptyListsProduceEmptyContainers() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Configuration configuration = DeviceXmlMapper.writeDevice(namedDevice("PLC"), configurations);

        DeviceXmlMapper.writeComms(
                new CommConfigData("PLC", List.of(), List.of(), List.of()), configuration);

        String xml = configuration.xmlText();

        assertTrue(xml.contains("<groups/>"), "Un grupo sin filas debe quedar vacio: " + xml);
        assertTrue(xml.contains("<items/>"), "Un item sin filas debe quedar vacio: " + xml);
        assertTrue(xml.contains("<pvs/>"), "Un pv sin filas debe quedar vacio: " + xml);

        CommConfigData read = DeviceXmlMapper.readComms(configuration);

        assertNotNull(read);
        assertTrue(read.getGroups().isEmpty());
        assertTrue(read.getItems().isEmpty());
        assertTrue(read.getPvs().isEmpty());
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
    void everyGroupFieldSurvivesTheRoundTrip() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Configuration configuration = DeviceXmlMapper.writeDevice(namedDevice("PLC"), configurations);

        DeviceXmlMapper.writeComms(new CommConfigData("PLC",
                List.of(new CommConfigData.GroupConfig("g1", "Escaneo Rapido", "Grupo de alta frecuencia",
                        "500", true, "b3f1a0c2d4e5")),
                List.of(new CommConfigData.ItemConfig("i1", "Presion Zona 1", "Presion de la zona",
                        "%DB1.DBB0:INT", true, "d5e6c2b4f6a7", "g1")),
                List.of(new CommConfigData.PvConfig("p1", "Presion Zona 1", "INT", "i1", "0", "descriptor",
                        "500", true, false, "0", "10", "Presion (bar)", "%.2f", "bar", "0", "10", "0.1",
                        "f7a8e4d6b8c9", "S88.1.Alfa"))),
                configuration);

        CommConfigData read = DeviceXmlMapper.readComms(configuration);

        CommConfigData.GroupConfig g = read.getGroups().get(0);
        assertEquals("g1", g.getUuid());
        assertEquals("Escaneo Rapido", g.getName());
        assertEquals("Grupo de alta frecuencia", g.getDescription());
        assertEquals("500", g.getScantime());
        assertTrue(g.isEnable());
        assertEquals("b3f1a0c2d4e5", g.getMd5());

        CommConfigData.ItemConfig i = read.getItems().get(0);
        assertEquals("i1", i.getUuid());
        assertEquals("%DB1.DBB0:INT", i.getTag());
        assertEquals("d5e6c2b4f6a7", i.getMd5());
        assertEquals("g1", i.getGroupUuid(), "El campo group debe volver a su sitio");

        CommConfigData.PvConfig p = read.getPvs().get(0);
        assertEquals("descriptor", p.getDescriptorText());
        assertTrue(p.isScanEnable());
        assertFalse(p.isWriteEnable());
        assertEquals("Presion (bar)", p.getDisplayDescription());
        assertEquals("%.2f", p.getDisplayFormat());
        assertEquals("bar", p.getDisplayUnits());
        assertEquals("0.1", p.getControlMinStep());
        assertEquals("f7a8e4d6b8c9", p.getMd5());
        assertEquals("S88.1.Alfa", p.getS88Path(),
                "md5 y s88Path estan en orden distinto en el XML y en el constructor");
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

    @Test
    void emptyValuesAreWrittenAsEmptyAttributesNotOmitted() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = doc.getProject().getInstances().getConfigurations();

        Configuration configuration = DeviceXmlMapper.writeDevice(namedDevice("PLC"), configurations);

        DeviceXmlMapper.writeComms(new CommConfigData("PLC",
                List.of(new CommConfigData.GroupConfig("g1", null, null, null, true, null)),
                List.of(),
                List.of(new CommConfigData.PvConfig("p1", null, null, null, null, null, null,
                        true, true, null, null, null, null, null, null, null, null, null, null))),
                configuration);

        String xml = configuration.xmlText();

        assertTrue(xml.contains("descriptor=\"\""),
                "Un valor nulo debe escribir el atributo vacio, no omitirlo: " + xml);
        assertTrue(xml.contains("s88Path=\"\""),
                "Un valor nulo debe escribir el atributo vacio, no omitirlo: " + xml);
        assertTrue(xml.contains("md5=\"\""),
                "Un valor nulo debe escribir el atributo vacio, no omitirlo: " + xml);

        CommConfigData read = DeviceXmlMapper.readComms(configuration);

        assertEquals("", read.getGroups().get(0).getName());
        assertEquals("", read.getGroups().get(0).getMd5());
        assertEquals("", read.getPvs().get(0).getDescriptorText());
        assertEquals("", read.getPvs().get(0).getMd5());
    }

    private static DeviceConfigData namedDevice(String name) {
        Properties p = new Properties();
        p.put("deviceName", name);
        p.put("enable", true);
        return new DeviceConfigData(p);
    }

    @Test
    void readCommsReturnsNullWhenNoCommsBlock() throws Exception {
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
        pDevice.put("specificParameters", "");

        DeviceConfigData device = new DeviceConfigData(pDevice);

        DeviceXmlMapper.writeDevice(device, configurations);

        assertNull(DeviceXmlMapper.readComms(configurations.getConfigurationArray(0)));
    }
}
