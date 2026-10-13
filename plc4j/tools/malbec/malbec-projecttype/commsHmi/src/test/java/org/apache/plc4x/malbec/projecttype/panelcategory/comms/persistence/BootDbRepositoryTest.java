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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootDbRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void pvIdIsTheOnlyLinkBetweenPvAndItem() throws Exception {
        File dbFolder = tempDir.toFile();
        String deviceUuid = "uuid-1";
        String groupUuid = "g1";
        String itemUuid = "i1";
        String pvUuid = "p1";

        BootDbRepository.createDB(dbFolder.getAbsolutePath());
        BootDbRepository.insertDeviceConfig(
                dbFolder.getAbsolutePath(),
                device(deviceUuid, "PLC_Uno"),
                comms(groupUuid, itemUuid, pvUuid));

        File dbFile = new File(dbFolder, "boot.db");
        assertTrue(dbFile.isFile(), "boot.db debe haberse creado");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT PvUuId, PvId FROM PvRecords WHERE PvUuId = '" + pvUuid + "'")) {
                assertTrue(rs.next(), "La PV debe estar en PvRecords");
                assertEquals(itemUuid, rs.getString("PvId"),
                        "PvId debe contener el uuid del item (área)");
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT ItemUuid, DeviceUuid, GroupUuid FROM Items WHERE ItemUuid = '" + itemUuid + "'")) {
                assertTrue(rs.next(), "El item debe estar en Items");
                assertEquals(itemUuid, rs.getString("ItemUuid"));
                assertEquals(deviceUuid, rs.getString("DeviceUuid"),
                        "El item debe quedar ligado al device");
                assertEquals(groupUuid, rs.getString("GroupUuid"),
                        "El item debe quedar ligado a su grupo de escaneo");
            }
        }
    }

    @Test
    void savingTwiceOverwritesTheExistingRows() throws Exception {
        File dbFolder = tempDir.toFile();
        String deviceUuid = "uuid-3";
        String itemUuid = "i3";

        BootDbRepository.createDB(dbFolder.getAbsolutePath());
        assertTrue(BootDbRepository.insertDeviceConfig(
                dbFolder.getAbsolutePath(), device(deviceUuid, "PLC_Tres"),
                comms("g1", itemUuid, "p3")));

        CommConfigData modificado = new CommConfigData("PLC_Tres",
                List.of(new CommConfigData.GroupConfig("g1", "Grupo1", "Grupo 1 editado",
                        "800", true, "md5g")),
                List.of(new CommConfigData.ItemConfig(itemUuid, "Item1", "Item 1 editado",
                        "tag1", false, "md5i", "g1")),
                List.of());
        assertTrue(BootDbRepository.insertDeviceConfig(
                dbFolder.getAbsolutePath(), device(deviceUuid, "PLC_Tres"), modificado));

        File dbFile = new File(dbFolder, "boot.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM Items WHERE ItemUuid = '" + itemUuid + "'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "No debe duplicar el área al modificarla");
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT ItemDescription, GroupUuid FROM Items WHERE ItemUuid = '"
                            + itemUuid + "'")) {
                assertTrue(rs.next());
                assertEquals("Item 1 editado", rs.getString("ItemDescription"),
                        "La descripción debe quedar actualizada en la base");
                assertEquals("g1", rs.getString("GroupUuid"));
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM Groups WHERE GroupUuid = 'g1'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "No debe duplicar el grupo al modificarlo");
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT GroupDescription, GroupScantime FROM Groups WHERE GroupUuid = 'g1'")) {
                assertTrue(rs.next());
                assertEquals("Grupo 1 editado", rs.getString("GroupDescription"));
                assertEquals("800", rs.getString("GroupScantime"));
            }
        }
    }

    private static CommConfigData comms(String groupUuid, String itemUuid, String pvUuid) {
        return new CommConfigData("PLC_Uno",
                List.of(new CommConfigData.GroupConfig(groupUuid, "Grupo1", "Grupo 1", "500", true, "md5g")),
                List.of(new CommConfigData.ItemConfig(itemUuid, "Item1", "Item 1", "tag1", false, "md5i",
                        groupUuid)),
                List.of(new CommConfigData.PvConfig(pvUuid, "PV1", "INT", itemUuid, "0", "descr",
                        "500", true, false, "-100", "100", "desc", "%.2f", "gpm",
                        "-50", "50", "0.1", "md5p")));
    }

    @Test
    void itemWithoutGroupIsNotSavedAtAll() throws Exception {
        File dbFolder = tempDir.toFile();
        String deviceUuid = "uuid-2";
        String itemUuid = "i2";

        CommConfigData comms = new CommConfigData("PLC_Dos",
                List.of(new CommConfigData.GroupConfig("g1", "Grupo1", "Grupo 1", "500", true, "md5g"),
                        new CommConfigData.GroupConfig("g2", "Grupo2", "Grupo 2", "500", true, "md5g")),
                List.of(new CommConfigData.ItemConfig(itemUuid, "Item2", "Item 2", "tag2", false, "md5i")),
                List.of());

        BootDbRepository.createDB(dbFolder.getAbsolutePath());
        boolean guardado = BootDbRepository.insertDeviceConfig(
                dbFolder.getAbsolutePath(), device(deviceUuid, "PLC_Dos"), comms);

        assertFalse(guardado,
                "Un área sin grupo de escaneo no se guarda: el grupo es obligatorio");

        File dbFile = new File(dbFolder, "boot.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM Items WHERE ItemUuid = '" + itemUuid + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                        "En la base no puede quedar un área sin GroupUuid");
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM Items WHERE GroupUuid IS NULL")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "No se admiten GroupUuid nulos en Items");
            }
        }
    }

    private static DeviceConfigData device(String uuid, String deviceName) {
        Properties pDevice = new Properties();
        pDevice.put("brand", "Siemens");
        pDevice.put("model", "S7-1500");
        pDevice.put("protocol", "S7");
        pDevice.put("deviceName", deviceName);
        pDevice.put("deviceKey", "key-" + uuid);
        pDevice.put("description", "Control principal");
        pDevice.put("uuid", uuid);
        pDevice.put("enable", true);
        pDevice.put("s88Node", "S88.1");
        pDevice.put("s88Uuid", "s88-uuid-1");
        pDevice.put("specificParameters", "ipa=10.0.0.1");
        return new DeviceConfigData(pDevice);
    }
}