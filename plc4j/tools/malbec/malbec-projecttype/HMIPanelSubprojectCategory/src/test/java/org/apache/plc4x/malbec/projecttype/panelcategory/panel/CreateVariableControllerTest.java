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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.BootDbTagDynamicRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateVariableControllerTest {

    private static DeviceConfigData device(String name) {
        Properties properties = new Properties();
        properties.setProperty("deviceName", name);
        properties.setProperty("deviceKey", "key-" + name);
        properties.setProperty("uuid", "uuid-" + name);
        properties.setProperty("protocol", "S7");
        properties.setProperty("specificParameters", "ipa=10.0.0.1");
        properties.setProperty("enable", "true");
        return new DeviceConfigData(properties);
    }

    private static int count(Path folder, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + folder.resolve("boot.db"));
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + table);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : -1;
        }
    }

    private static CreateVariableController.Row row(int rowNumber, String name,
            String deviceName, String dataType, String tag) {
        return new CreateVariableController.Row(
                rowNumber,
                "",
                name,
                deviceName,
                dataType,
                tag,
                "",
                "1000ms",
                false,
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");
    }

    @Test
    void rechazaElMismoTagEnElMismoDispositivo(@TempDir Path folder) {
        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device("PLC-1")),
                List.of(
                        row(1, "Var_1", "PLC-1", "int", "%DB1.DBD0"),
                        row(2, "VAR3", "PLC-1", "float", "%DB1.DBD0")));

        assertFalse(result.success());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("is already used"));
        assertFalse(Files.exists(folder.resolve("boot.db")));
        assertFalse(Files.exists(folder.resolve(DynamicVariableXmlMapper.FILE_NAME)));
    }

    @Test
    void rechazaTagsQueSeSolapanEnElMismoDispositivo(@TempDir Path folder) {
        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device("PLC-1")),
                List.of(
                        row(1, "Var_1", "PLC-1", "int", "%DB1.DBD0"),
                        row(2, "Var_2", "PLC-1", "word", "%DB1.DBW0")));

        assertFalse(result.success());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("overlaps with"));
        assertFalse(Files.exists(folder.resolve("boot.db")));
        assertFalse(Files.exists(folder.resolve(DynamicVariableXmlMapper.FILE_NAME)));
    }

    @Test
    void rechazaElMismoNombreEnElMismoDispositivo(@TempDir Path folder) {
        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device("PLC-1")),
                List.of(
                        row(1, "Var_1", "PLC-1", "int", "%DB1.DBD0"),
                        row(2, "Var_1", "PLC-1", "float", "%DB1.DBD4")));

        assertFalse(result.success());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream()
                .allMatch(error -> error.contains("used more than once")));
        assertFalse(Files.exists(folder.resolve("boot.db")));
        assertFalse(Files.exists(folder.resolve(DynamicVariableXmlMapper.FILE_NAME)));
    }

    @Test
    void permiteElMismoNombreEnDispositivosDistintos(@TempDir Path folder) throws Exception {
        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device("PLC-1"), device("PLC-2")),
                List.of(
                        row(1, "Var_1", "PLC-1", "int", "%DB1.DBD0"),
                        row(2, "Var_1", "PLC-2", "int", "%DB1.DBD0")));

        assertTrue(result.success(), String.join("\n", result.errors()));
        assertEquals(2, count(folder, "PvRecords"));
        assertEquals(2, count(folder, "Devices"));
    }

    @Test
    void comparaLosNombresDeFormaExacta(@TempDir Path folder) throws Exception {
        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device("PLC-1")),
                List.of(
                        row(1, "Var_1", "PLC-1", "int", "%DB1.DBD0"),
                        row(2, "var_1", "PLC-1", "int", "%DB1.DBD4")));

        assertTrue(result.success(), String.join("\n", result.errors()));
        assertEquals(2, count(folder, "PvRecords"));
    }

    @Test
    void eliminaPvDeBootDbAlGuardar(@TempDir Path folder) throws Exception {
        String uuid = "pv-to-delete";
        DeviceConfigData device = device("PLC-1");
        PvConfigData pv = new PvConfigData(
                uuid, "Var_1", "int", "PLC-1", "%DB1.DBD0",
                "", "1000ms", false, false,
                "", "", "", "", "", "", "", "", "");

        assertTrue(BootDbTagDynamicRepository.createDB(folder.toString()));
        assertTrue(BootDbTagDynamicRepository.insertDeviceConfig(
                folder.toString(), device, List.of(pv)));

        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device),
                List.of(),
                List.of(uuid));

        assertTrue(result.success(), String.join("\n", result.errors()));
        assertEquals(0, count(folder, "PvRecords"));
        assertEquals(1, count(folder, "Devices"));
        assertTrue(Files.exists(folder.resolve(DynamicVariableXmlMapper.FILE_NAME)));
        assertTrue(DynamicVariableXmlMapper.read(folder.toFile()).pvs().isEmpty());
    }

    @Test
    void loadRowsRejectsInvalidXml(@TempDir Path folder) throws Exception {
        DynamicVariableXmlMapper.write(
                folder.toFile(), List.of(device("PLC-1")), List.of());
        Path xml = folder.resolve(DynamicVariableXmlMapper.FILE_NAME);
        Files.writeString(xml, Files.readString(xml)
                .replace("deviceKey=\"key-PLC-1\"", "deviceKey=\"\""));

        assertThrows(IllegalStateException.class,
                () -> CreateVariableController.loadRows(folder.toFile()));
    }

    @Test
    void guardaLaVariableEnXmlYEnBootDb(@TempDir Path folder) throws Exception {
        CreateVariableController.Result result = CreateVariableController.saveVariables(
                folder.toString(),
                List.of(device("PLC-1")),
                List.of(row(1, "Var_1", "PLC-1", "int", "%DB1.DBD0")));

        assertTrue(result.success(), String.join("\n", result.errors()));
        assertEquals(1, count(folder, "PvRecords"));
        assertEquals(1, count(folder, "Devices"));

        DynamicVariableXmlMapper.Content content
                = DynamicVariableXmlMapper.read(folder.toFile());
        assertEquals(1, content.pvs().size());
        assertEquals("Var_1", content.pvs().get(0).getName());
        assertEquals("%DB1.DBD0", content.pvs().get(0).getTag());
    }
}
