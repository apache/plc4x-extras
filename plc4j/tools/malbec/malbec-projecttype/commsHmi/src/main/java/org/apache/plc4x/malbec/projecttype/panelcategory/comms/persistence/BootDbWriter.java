///*
// * Licensed to the Apache Software Foundation (ASF) under one
// * or more contributor license agreements.  See the NOTICE file
// * distributed with this work for additional information
// * regarding copyright ownership.  The ASF licenses this file
// * to you under the Apache License, Version 2.0 (the
// * "License"); you may not use this file except in compliance
// * with the License.  You may obtain a copy of the License at
// *
// *   https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing,
// * software distributed under the License is distributed on an
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// * KIND, either express or implied.  See the License for the
// * specific language governing permissions and limitations
// * under the License.
// */
//package org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.AtomicMoveNotSupportedException;
//import java.nio.file.Files;
//import java.nio.file.StandardCopyOption;
//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.SQLException;
//import java.sql.Statement;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
//import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
//import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
//
//
//final class BootDbWriter {
//
//    private static final Logger LOGGER = Logger.getLogger(BootDbWriter.class.getName());
//    static final int SCHEMA_VERSION = 1;
//
//    private static final String[] SQL_CREATE_TABLES = {
//        BootDbRepository.SQL_CREATE_TABLE_DEVICES,
//        BootDbRepository.SQL_CREATE_TABLE_GROUPS,
//        BootDbRepository.SQL_CREATE_TABLE_ITEMS,
//        BootDbRepository.SQL_CREATE_TABLE_PVRECORDS
//    };
//
//    private final File target;
//
//    /** Por que fallo el ultimo write(), para poder decírselo al operador. */
//    private String ultimoMotivo;
//
//    BootDbWriter(File target) {
//        this.target = target;
//    }
//
//    String motivoDelFallo() {
//        return ultimoMotivo;
//    }
//
//    RebuildResults write(CommunicationsConfig config) throws SQLException {
//        File temporal = new File(target.getParentFile(), target.getName() + ".tmp");
//        deleteTemp(temporal);
//
//        try {
//            buildTemporary(temporal, config);
//        } catch (SQLException | RuntimeException ex) {
//            // El temporal se va: un fallo aqui no debe dejar nada a medias junto al panel.
//            deleteTemp(temporal);
//            ultimoMotivo = ex.getMessage();
//            LOGGER.log(Level.SEVERE, "No se pudo construir " + temporal.getName()
//                    + "; se conserva el boot.db anterior", ex);
//            return RebuildResults.fallo(ultimoMotivo);
//        }
//
//        try {
//            Files.move(temporal.toPath(), target.toPath(),
//                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
//        } catch (AtomicMoveNotSupportedException ex) {
//            LOGGER.log(Level.WARNING,
//                    "El sistema de ficheros no permite mover atómicamente boot.db;"
//                            + " se reemplaza directamente", ex);
//            try {
//                Files.move(temporal.toPath(), target.toPath(),
//                        StandardCopyOption.REPLACE_EXISTING);
//            } catch (IOException failure) {
//                deleteTemp(temporal);
//                ultimoMotivo = "no se pudo reemplazar " + target.getName() + ": " + failure.getMessage();
//                LOGGER.log(Level.SEVERE, "No se pudo reemplazar " + target.getName(), failure);
//                return RebuildResults.fallo(ultimoMotivo);
//            }
//        } catch (IOException ex) {
//            deleteTemp(temporal);
//            ultimoMotivo = "no se pudo reemplazar " + target.getName() + ": " + ex.getMessage();
//            LOGGER.log(Level.SEVERE, "No se pudo reemplazar " + target.getName(), ex);
//            return RebuildResults.fallo(ultimoMotivo);
//        }
//        ultimoMotivo = null;
//        return RebuildResults.ok();
//    }
//
//    private void buildTemporary(File temporal, CommunicationsConfig config) throws SQLException {
//        String url = "jdbc:sqlite:" + temporal;
//        try (Connection conn = DriverManager.getConnection(url)) {
//
//            try (Statement stmt = conn.createStatement()) {
//                stmt.execute("PRAGMA foreign_keys=ON");
//            }
//
//            conn.setAutoCommit(false);
//            try {
//                try (Statement stmt = conn.createStatement()) {
//                    for (String sql : SQL_CREATE_TABLES) {
//                        stmt.execute(sql);
//                    }
//                }
//                for (DeviceConfigData device : config.getDevices()) {
//                    CommConfigData comms = config.getComms(device.getUuid());
//                    String areaInvalida = BootDbRepository.firstAreaWithoutGroup(comms);
//                    if (areaInvalida != null) {
//                        throw new SQLException("el dispositivo '"
//                                + (device != null ? device.getDeviceName() : "?")
//                                + "' tiene el área '" + areaInvalida
//                                + "' sin grupo de escaneo");
//                    }
//                    String faltaClave = BootDbRepository.keyProblem(device);
//                    if (faltaClave != null) {
//                        throw new SQLException(faltaClave);
//                    }
//                    BootDbRepository.insertDevice(conn, device, comms);
//                }
//                try (Statement stmt = conn.createStatement()) {
//                    stmt.execute("PRAGMA user_version=" + SCHEMA_VERSION);
//                }
//                conn.commit();
//            } catch (SQLException | RuntimeException ex) {
//                conn.rollback();
//                throw ex;
//            }
//        }
//    }
//
//    private void deleteTemp(File temporal) {
//        for (String sufijo : new String[]{"", "-wal", "-shm", "-journal"}) {
//            try {
//                Files.deleteIfExists(new File(temporal.getParentFile(),
//                        temporal.getName() + sufijo).toPath());
//            } catch (IOException ex) {
//                LOGGER.log(Level.WARNING, "No se pudo borrar el temporal de boot.db", ex);
//            }
//        }
//    }
//}
