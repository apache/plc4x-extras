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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BootDbRepository {

    private static final Logger LOGGER = Logger.getLogger(BootDbRepository.class.getName());

    static final String SQL_CREATE_TABLE_DEVICES
            = "CREATE TABLE IF NOT EXISTS Devices("
            + "DeviceUuId TEXT NOT NULL PRIMARY KEY,"
            + "DriverName TEXT,"
            + "DeviceKey TEXT,"
            + "DeviceUrl TEXT,"
            + "DeviceName TEXT,"
            + "DeviceDescription TEXT,"
            + "DeviceEnable TEXT,"
            + "Md5 TEXT)";

    static final String SQL_CREATE_TABLE_GROUPS
            = "CREATE TABLE IF NOT EXISTS Groups("
            + "GroupUuid TEXT NOT NULL PRIMARY KEY,"
            + "DeviceUuid TEXT,"
            + "GroupName TEXT,"
            + "GroupDescription TEXT,"
            + "GroupScantime TEXT,"
            + "GroupEnable TEXT,"
            + "Md5 TEXT)";

    static final String SQL_CREATE_TABLE_ITEMS
            = "CREATE TABLE IF NOT EXISTS Items("
            + "ItemUuid TEXT NOT NULL PRIMARY KEY,"
            + "DeviceUuid TEXT,"
            + "GroupUuid TEXT,"
            + "ItemName TEXT,"
            + "ItemDescription TEXT,"
            + "ItemTag TEXT,"
            + "ItemEnable TEXT,"
            + "Md5 TEXT)";
    /* TODO: todos estos serian los PVType 
        s5time, s7date s7time s7tod s7dat s7counter s7di s7ai s7ao s7valve
        s7vlv s7avlv s7motor boolean byte double float int long short
        string ubyte uint ulong ushort
     */
    static final String SQL_CREATE_TABLE_PVRECORDS
            = "CREATE TABLE IF NOT EXISTS PvRecords("
            + "PvUuId TEXT NOT NULL PRIMARY KEY,"
            + "PvName TEXT,"
            + "PvType TEXT,"
            + "PvId TEXT,"
            + "PvOffset TEXT,"
            + "PvDescriptor TEXT,"
            + "PvScanTime TEXT,"
            + "PvScanEnable TEXT,"
            + "PvWriteEnable TEXT,"
            + "PvDisplayLimitLow TEXT,"
            + "PvDisplayLimitHigh TEXT,"
            + "PvDisplayDescription TEXT,"
            + "PvDisplayFormat TEXT,"
            + "PvDisplayUnits TEXT,"
            + "PvControlLimitLow TEXT,"
            + "PvControlLimitHigh TEXT,"
            + "PvControlMinStep TEXT,"
            + "Md5 TEXT)";

    static final String SQL_INSERT_DEVICE
            = "INSERT OR REPLACE INTO Devices(DeviceUuId, DriverName, DeviceKey, DeviceUrl, DeviceName, DeviceDescription, DeviceEnable, Md5) VALUES(?,?,?,?,?,?,?,?)";

    static final String SQL_INSERT_GROUP
            = "INSERT OR REPLACE INTO Groups(GroupUuid, DeviceUuid, GroupName, GroupDescription, GroupScantime, GroupEnable, Md5) VALUES(?,?,?,?,?,?,?)";

    static final String SQL_INSERT_ITEM
            = "INSERT OR REPLACE INTO Items(ItemUuid, DeviceUuid, GroupUuid, ItemName, ItemDescription, ItemTag, ItemEnable, Md5) VALUES(?,?,?,?,?,?,?,?)";

    static final String SQL_INSERT_PV
            = "INSERT OR REPLACE INTO PvRecords(PvUuId, PvName, PvType, PvId, PvOffset, PvDescriptor, PvScanTime, PvScanEnable, PvWriteEnable, PvDisplayLimitLow, PvDisplayLimitHigh, PvDisplayDescription, PvDisplayFormat, PvDisplayUnits, PvControlLimitLow, PvControlLimitHigh, PvControlMinStep, Md5) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public static boolean createDB(String rutaCarpeta) {
        File folder = new File(rutaCarpeta);
        File archivoDB = new File(folder, "boot.db");
        String url = "jdbc:sqlite:" + archivoDB;

        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            if (conn != null) {
                stmt.execute(SQL_CREATE_TABLE_DEVICES);
                stmt.execute(SQL_CREATE_TABLE_GROUPS);
                stmt.execute(SQL_CREATE_TABLE_ITEMS);
                stmt.execute(SQL_CREATE_TABLE_PVRECORDS);
            }
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al crear las tablas", e);
            return false;
        }
    }

    public static boolean insertDeviceConfig(String rutaCarpeta, DeviceConfigData device, CommConfigData comms) {
        File archivoDB = new File(rutaCarpeta, "boot.db");
        String url = "jdbc:sqlite:" + archivoDB;

        try (Connection conn = DriverManager.getConnection(url)) {
            String areaInvalida = firstAreaWithoutGroup(comms);
            if (areaInvalida != null) {
                LOGGER.log(Level.WARNING,
                        "No se guardó el dispositivo '{0}': el área '{1}' no tiene grupo de escaneo.",
                        new Object[]{device != null ? device.getDeviceName() : "",
                            areaInvalida});
                return false;
            }
            String faltaClave = keyProblem(device);
            if (faltaClave != null) {
                LOGGER.log(Level.WARNING, "No se guardó el dispositivo: {0}", faltaClave);
                return false;
            }
            conn.setAutoCommit(false);
            try {
                insertDevice(conn, device, comms);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error insertando datos en SQLite", e);
            return false;
        }
    }

    /**
     * Motivo por el que un dispositivo no se puede escribir, o {@code null} si vale.
     *
     * <p>La clave no se sustituye por un valor inventado: si falta, el dispositivo no se
     * guarda.
     */
    static String keyProblem(DeviceConfigData device) {
        if (device == null) {
            return "no hay ficha de dispositivo que guardar.";
        }
        String clave = device.getDeviceKey();
        if (clave == null || clave.isBlank()) {
            return "el dispositivo '" + device.getDeviceName() + "' no tiene clave."
                    + " Edítelo y rellene el campo Key antes de guardar.";
        }
        return null;
    }

    static void insertDevice(Connection conn, DeviceConfigData device, CommConfigData comms)
            throws SQLException {

        String deviceUuid = (device != null && device.getUuid() != null && !device.getUuid().isEmpty())
                ? device.getUuid() : UUID.randomUUID().toString();
        String deviceName = (device != null) ? device.getDeviceName() : (comms != null ? comms.getDeviceName() : "");
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_DEVICE)) {
            pstmt.setString(1, deviceUuid);
            pstmt.setString(2, device != null ? device.getProtocol() : null);
            pstmt.setString(3, device != null ? device.getDeviceKey() : null);
            pstmt.setString(4, device != null ? device.getSpecificParameters() : null);
            pstmt.setString(5, deviceName);
            pstmt.setString(6, device != null ? device.getDescription() : null);
            pstmt.setString(7, (device != null && !device.isEnabled()) ? "false" : "true");
            pstmt.setString(8, "");
            pstmt.executeUpdate();
        }

        if (comms != null) {
            try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_GROUP)) {
                for (CommConfigData.GroupConfig g : orEmpty(comms.getGroups())) {
                    pstmt.setString(1, g.getUuid());
                    pstmt.setString(2, deviceUuid);
                    pstmt.setString(3, g.getName());
                    pstmt.setString(4, g.getDescription());
                    pstmt.setString(5, g.getScantime());
                    pstmt.setString(6, g.isEnable() ? "true" : "false");
                    pstmt.setString(7, g.getMd5());
                    pstmt.executeUpdate();
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_ITEM)) {
                for (CommConfigData.ItemConfig item : orEmpty(comms.getItems())) {
                    pstmt.setString(1, item.getUuid());
                    pstmt.setString(2, deviceUuid);
                    pstmt.setString(3, item.getGroupUuid());
                    pstmt.setString(4, item.getName());
                    pstmt.setString(5, item.getDescription());
                    pstmt.setString(6, item.getTag());
                    pstmt.setString(7, item.isEnable() ? "true" : "false");
                    pstmt.setString(8, item.getMd5());
                    pstmt.executeUpdate();
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_PV)) {
                for (CommConfigData.PvConfig pv : orEmpty(comms.getPvs())) {
                    pstmt.setString(1, pv.getUuid());
                    pstmt.setString(2, pv.getName());
                    pstmt.setString(3, pv.getType());
                    pstmt.setString(4, pv.getAreaName());
                    pstmt.setString(5, pv.getOffset());
                    pstmt.setString(6, pv.getDescriptorText());
                    pstmt.setString(7, pv.getScanTime());
                    pstmt.setString(8, pv.isScanEnable() ? "true" : "false");
                    pstmt.setString(9, pv.isWriteEnable() ? "true" : "false");
                    pstmt.setString(10, pv.getDisplayLimitLow());
                    pstmt.setString(11, pv.getDisplayLimitHigh());
                    pstmt.setString(12, pv.getDisplayDescription());
                    pstmt.setString(13, pv.getDisplayFormat());
                    pstmt.setString(14, pv.getDisplayUnits());
                    pstmt.setString(15, pv.getControlLimitLow());
                    pstmt.setString(16, pv.getControlLimitHigh());
                    pstmt.setString(17, pv.getControlMinStep());
                    pstmt.setString(18, pv.getMd5());
                    pstmt.executeUpdate();
                }
            }
        }
    }

    static String firstAreaWithoutGroup(CommConfigData comms) {
        if (comms == null) {
            return null;
        }
        for (CommConfigData.ItemConfig item : orEmpty(comms.getItems())) {
            String groupUuid = item.getGroupUuid();
            if (groupUuid == null || groupUuid.isEmpty()) {
                return item.getName();
            }
        }
        return null;
    }

    public static RebuildResultado rebuildWithReason(File dbFolder, CommunicationsConfig config) {
        if (dbFolder == null || config == null) {
            return RebuildResultado.fallo("no hay proyecto ni configuracion que guardar");
        }
        if (!dbFolder.isDirectory()) {
            return RebuildResultado.fallo("la carpeta de boot.db no existe");
        }
        try {
            return new BootDbWriter(new File(dbFolder, "boot.db")).write(config);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error reconstruyendo boot.db", ex);
            return RebuildResultado.fallo(ex.getMessage());
        }
    }

    public static boolean rebuild(File dbFolder, CommunicationsConfig config) {
        return rebuildWithReason(dbFolder, config).success();
    }

    private BootDbRepository() {
    }

    static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
