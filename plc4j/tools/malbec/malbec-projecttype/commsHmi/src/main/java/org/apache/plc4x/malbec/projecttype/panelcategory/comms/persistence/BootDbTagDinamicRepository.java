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
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;

public class BootDbTagDinamicRepository {

    private static final Logger LOGGER = Logger.getLogger(BootDbTagDinamicRepository.class.getName());

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

    static final String SQL_CREATE_TABLE_PVRECORDS
            = "CREATE TABLE IF NOT EXISTS PvRecords("
            + "PvUuId TEXT NOT NULL PRIMARY KEY,"
            + "PvName TEXT,"
            + "PvType TEXT,"
            + "PvDevice TEXT,"
            + "PvItemTag TEXT,"
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
            = "INSERT OR REPLACE INTO Devices(DeviceUuId, DriverName, DeviceKey, "
            + "DeviceUrl, DeviceName, DeviceDescription, DeviceEnable, Md5) "
            + "VALUES(?,?,?,?,?,?,?,?)";

    static final String SQL_INSERT_PV
            = "INSERT OR REPLACE INTO PvRecords(PvUuId, PvName, PvType, PvDevice, "
            + "PvItemTag, PvDescriptor, PvScanTime, PvScanEnable, PvWriteEnable, "
            + "PvDisplayLimitLow, PvDisplayLimitHigh, PvDisplayDescription, "
            + "PvDisplayFormat, PvDisplayUnits, PvControlLimitLow, PvControlLimitHigh,"
            + " PvControlMinStep, Md5) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    static final String SQL_DELETE_PV
            = "DELETE FROM PvRecords WHERE PvUuId = ?";

    public static boolean createDB(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() && !folder.mkdirs()) {
            LOGGER.log(Level.SEVERE, "No se pudo crear la carpeta: {0}", folderPath);
            return false;
        }

        File archivoDB = new File(folder, "boot.db");
        String url = "jdbc:sqlite:" + archivoDB;

        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_CREATE_TABLE_DEVICES);
            stmt.execute(SQL_CREATE_TABLE_PVRECORDS);
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al crear las tablas", e);
            return false;
        }
    }

    public static boolean insertDeviceConfig(String folderPath, DeviceConfigData device,
            List<PvConfigData> pvs) {
        File DbFile = new File(folderPath, "boot.db");
        String url = "jdbc:sqlite:" + DbFile;

        try (Connection conn = DriverManager.getConnection(url)) {
            String problem = deviceProblem(device);
            if (problem != null) {
                LOGGER.log(Level.WARNING, "The device was not saved: {0}", problem);
                return false;
            }

            conn.setAutoCommit(false);
            try {
                insertDevice(conn, device, pvs);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting data into SQLite", e);
            return false;
        }
    }

    public static boolean insertDeviceConfig(String rutaCarpeta, DeviceConfigData device,
            PvConfigData pv) {
        return insertDeviceConfig(rutaCarpeta, device,
                pv == null ? List.of() : List.of(pv));
    }

    public static boolean deletePvs(String folderPath, Collection<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return true;
        }

        File dbFile = new File(folderPath, "boot.db");
        String url = "jdbc:sqlite:" + dbFile;

        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(SQL_DELETE_PV)) {
                for (String uuid : uuids) {
                    if (uuid == null || uuid.isBlank()) {
                        continue;
                    }
                    pstmt.setString(1, uuid);
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit();
                return true;

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting variables from SQLite", e);
            return false;
        }
    }

    static void insertDevice(Connection conn, DeviceConfigData device,
            List<PvConfigData> pvs) throws SQLException {

        if (device == null) {
            throw new SQLException("No hay dispositivo que guardar");
        }

        String deviceUuid = uuidOrRandom(device.getUuid());

        try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_DEVICE)) {
            pstmt.setString(1, deviceUuid);
            pstmt.setString(2, device.getProtocol());
            pstmt.setString(3, device.getDeviceKey());
            pstmt.setString(4, device.getSpecificParameters());
            pstmt.setString(5, device.getDeviceName());
            pstmt.setString(6, device.getDescription());
            pstmt.setString(7, device.isEnabled() ? "true" : "false");
            pstmt.setString(8, "");
            pstmt.executeUpdate();
        }

        try (PreparedStatement pstmt = conn.prepareStatement(SQL_INSERT_PV)) {
            for (PvConfigData pv : pvs == null ? List.<PvConfigData>of() : pvs) {
                if (pv == null) {
                    continue;
                }

                String pvUuid = uuidOrRandom(pv.getUuid());

                pstmt.setString(1, pvUuid);
                pstmt.setString(2, pv.getName());
                pstmt.setString(3, pv.getType());
                pstmt.setString(4, pv.getDeviceName());
                pstmt.setString(5, pv.getItemTag());
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

    private static String deviceProblem(DeviceConfigData device) {
        if (device == null) {
            return "There is no device record to save.";
        }
        if (device.getDeviceName() == null || device.getDeviceName().isBlank()) {
            return "The device has no name. Assign one before saving.";
        }
        if (device.getDeviceKey() == null || device.getDeviceKey().isBlank()) {
            return "The Device '" + device.getDeviceName() + "' it has no name.";
        }
        return null;
    }

    private static String uuidOrRandom(String uuid) {
        return uuid == null || uuid.isBlank()
                ? UUID.randomUUID().toString()
                : uuid;
    }

}
