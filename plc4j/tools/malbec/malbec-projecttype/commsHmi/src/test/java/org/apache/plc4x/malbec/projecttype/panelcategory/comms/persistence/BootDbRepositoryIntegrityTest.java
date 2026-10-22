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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootDbRepositoryIntegrityTest {

    @TempDir
    Path tempDir;

    @Test
    void unFalloEnElSegundoDispositivoNoDejaBootDbAPartial() throws Exception {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-ok", "PLC_Bueno", true));
        config.upsertComms("uuid-ok", commsWithGroup("g-ok", "it-ok", "pv-ok"));

        // Este dispositivo es invalido: su area no tiene grupo de escaneo.
        config.addDevice(device("uuid-malo", "PLC_Malo", true));
        config.upsertComms("uuid-malo", commsWithoutGroup("it-malo", "pv-malo"));

        boolean ok = BootDbRepository.rebuild(tempDir.toFile(), config);

        assertFalse(ok, "un dispositivo invalido hace fallar el guardado");
        assertFalse(new File(tempDir.toFile(), "boot.db").exists(),
                "no habia boot.db antes, y un guardado fallido no debe dejar uno a medias");
        assertEquals(0, countTemporaryTables(),
                "el temporal de la base debe recogerse, no quedarse junto al panel");
    }

    @Test
    void unFalloDeInsertarConservaLaBaseAnterior() throws Exception {
        // Primero una base buena.
        CommunicationsConfig buena = new CommunicationsConfig();
        buena.addDevice(device("uuid-1", "PLC_Uno", true));
        buena.upsertComms("uuid-1", commsWithGroup("g-1", "it-1", "pv-1"));
        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), buena));
        assertEquals(1, countDevices(), "precondicion: hay una base buena");

        // Ahora una configuracion que falla.
        CommunicationsConfig mala = new CommunicationsConfig();
        mala.addDevice(device("uuid-2", "PLC_Dos", true));
        mala.upsertComms("uuid-2", commsWithoutGroup("it-2", "pv-2"));
        assertFalse(BootDbRepository.rebuild(tempDir.toFile(), mala));

        assertEquals(1, countDevices(),
                "H-03: la base anterior tiene que seguir ahi, no quedar destruida");
        assertTrue(deviceExists("uuid-1"),
                "H-03: el dispositivo que ya estaba sigue en la base");
        assertFalse(deviceExists("uuid-2"),
                "H-03: el dispositivo que fallo no debe colarse en la base anterior");
    }

    /**
     * Dos guardados seguidos: el segundo reemplaza al primero sin dejar restos
     * del anterior.
     */
    @Test
    void unGuardadoCorrectoSustituyeEnteroAlAnterior() throws Exception {
        CommunicationsConfig primera = new CommunicationsConfig();
        primera.addDevice(device("uuid-1", "PLC_Uno", true));
        primera.upsertComms("uuid-1", commsWithGroup("g-1", "it-1", "pv-1"));
        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), primera));

        CommunicationsConfig segunda = new CommunicationsConfig();
        segunda.addDevice(device("uuid-2", "PLC_Dos", true));
        segunda.upsertComms("uuid-2", commsWithGroup("g-2", "it-2", "pv-2"));
        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), segunda));

        assertEquals(1, countDevices(), "la base nueva no hereda la anterior");
        assertFalse(deviceExists("uuid-1"));
        assertTrue(deviceExists("uuid-2"));
    }

    /**
     * La base escrita lleva la version del esquema en el PRAGMA user_version.
     */
    @Test
    void laBaseEscribeLaVersionDelEsquema() throws Exception {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-1", "PLC_Uno", true));
        config.upsertComms("uuid-1", commsWithGroup("g-1", "it-1", "pv-1"));
        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), config));

        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            rs.next();
            assertEquals(BootDbWriter.SCHEMA_VERSION, rs.getInt(1),
                    "la base debe decir que version de esquema es");
        }
    }

    /**
     * Cuantos ficheros temporales de boot.db quedan junto al panel.
     */
    private int countTemporaryTables() {
        File[] archivos = tempDir.toFile().listFiles(
                (dir, name) -> name.startsWith("boot.db.") || name.startsWith("boot.db-"));
        return archivos == null ? 0 : archivos.length;
    }

    @Test
    void elMd5DelDispositivoSeEscribeVacio() throws Exception {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-1", "PLC_Uno", true));
        config.upsertComms("uuid-1", commsWithGroup("g-1", "it-1", "pv-1"));

        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), config));

        assertEquals("", deviceValue("uuid-1", "Md5"),
                "no hay hash del dispositivo que escribir, así que no se escribe ninguno");
    }

    @Test
    void elCodigoYaNoTieneValoresInventados() throws Exception {
        for (String file : new String[]{
            "commsHmi/src/main/java/org/apache/plc4x/malbec/projecttype/panelcategory/comms"
            + "/persistence/BootDbRepository.java",
            "commsHmi/src/main/java/org/apache/plc4x/malbec/projecttype/panelcategory/comms"
            + "/persistence/BootDbWriter.java",
            "commsHmi/src/main/java/org/apache/plc4x/malbec/projecttype/panelcategory/model"
            + "/CommunicationWizardController.java"}) {
            java.nio.file.Path ruta = java.nio.file.Path.of("..", file);
            if (!java.nio.file.Files.exists(ruta)) {
                ruta = java.nio.file.Path.of(file);
            }
            String fuente = java.nio.file.Files.readString(ruta);
            for (String inventado : new String[]{
                "md5_device_hash", "md5_group_hash", "md5_item_hash", "md5_pv_hash", "Key123",
                "DefaultDriver", "tcp://localhost:502",
                "Dispositivo guardado desde DeviceManager"}) {
                assertFalse(fuente.contains(inventado),
                        "H-04: '" + inventado + "' sigue apareciendo en " + ruta.getFileName());
            }
        }
    }

    @Test
    void unDispositivoSinClaveNoSeGuardaYNoSeLeInventaUna() throws Exception {
        CommunicationsConfig buena = new CommunicationsConfig();
        buena.addDevice(device("uuid-ok", "PLC_Bueno", true));
        buena.upsertComms("uuid-ok", commsWithGroup("g-ok", "it-ok", "pv-ok"));
        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), buena));

        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", "Sin clave");
        p.put("uuid", "uuid-sin-clave");
        p.put("enable", true);
        p.put("s88Node", "S88.1");
        p.put("s88Uuid", "s88-1");
        p.put("specificParameters", "ipa=10.0.0.1");
        // deviceKey ausente a proposito

        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-ok", "PLC_Bueno", true));
        config.upsertComms("uuid-ok", commsWithGroup("g-ok", "it-ok", "pv-ok"));
        config.addDevice(new DeviceConfigData(p));
        config.upsertComms("uuid-sin-clave", commsWithGroup("g-1", "it-1", "pv-1"));

        RebuildResults resultado
                = BootDbRepository.rebuildWithReason(tempDir.toFile(), config);

        assertFalse(resultado.success(),
                "un dispositivo sin clave tiene que hacer fallar el guardado");
        assertEquals(1, countDevices(),
                "la base anterior se conserva tal cual");
        assertTrue(resultado.reason().contains("Sin clave"),
                "el motivo debe nombrar el dispositivo: " + resultado.reason());
    }

    @Test
    void unaClaveEnBlancoTampocoSeSustituye() throws Exception {
        Properties p = new Properties();
        p.put("protocol", "S7");
        p.put("deviceName", "Con clave vacia");
        p.put("uuid", "uuid-vacia");
        p.put("deviceKey", "   ");
        p.put("enable", true);
        p.put("specificParameters", "ipa=10.0.0.1");

        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(new DeviceConfigData(p));
        config.upsertComms("uuid-vacia", commsWithGroup("g-1", "it-1", "pv-1"));

        assertFalse(BootDbRepository.rebuild(tempDir.toFile(), config),
                "una clave en blanco es una clave que falta");
    }

    @Test
    void conDispositivoLaUrlVieneDeLosParametricos() throws Exception {
        Properties p = new Properties();
        p.put("protocol", "S7");
        p.put("deviceName", "Sin parametros");
        p.put("uuid", "uuid-sin-param");
        p.put("deviceKey", "key-1");
        p.put("enable", true);
        // specificParameters ausente a proposito

        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(new DeviceConfigData(p));
        config.upsertComms("uuid-sin-param", commsWithGroup("g-1", "it-1", "pv-1"));

        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), config));

        assertNull(deviceValue("uuid-sin-param", "DeviceUrl"),
                "con dispositivo no se inventa la URL: se escribe el valor, sea cual sea");
        assertEquals("S7", deviceValue("uuid-sin-param", "DriverName"),
                "con dispositivo el driver sale del protocolo, no de un literal");
    }

    @Test
    void losMd5DeGrupoAreaYPvSiVienenDelModelo() throws Exception {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-1", "PLC_Uno", true));
        config.upsertComms("uuid-1", commsWithGroup("g-1", "it-1", "pv-1"));

        assertTrue(BootDbRepository.rebuild(tempDir.toFile(), config));

        assertEquals("md5-real-del-grupo", valueOf("Groups", "GroupUuid", "g-1", "Md5"),
                "el md5 del grupo viene del modelo");
        assertEquals("md5-real-del-area", valueOf("Items", "ItemUuid", "it-1", "Md5"),
                "el md5 del area viene del modelo");
        assertEquals("md5-real-de-la-pv", valueOf("PvRecords", "PvUuId", "pv-1", "Md5"),
                "el md5 de la PV viene del modelo");
    }

    // ---------------------------------------------------------------- ayudas
    private static Properties propiedades(String uuid, String name, boolean conClave) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", name);
        p.put("uuid", uuid);
        p.put("enable", true);
        p.put("s88Node", "S88.1");
        p.put("s88Uuid", "s88-" + uuid);
        p.put("specificParameters", "ipa=10.0.0.1,port=102");
        if (conClave) {
            p.put("deviceKey", "key-" + uuid);
        }
        return p;
    }

    private static DeviceConfigData device(String uuid, String name, boolean conClave) {
        return new DeviceConfigData(propiedades(uuid, name, conClave));
    }

    private static CommConfigData commsWithGroup(String groupUuid, String itemUuid, String pvUuid) {
        return new CommConfigData("PLC",
                List.of(new CommConfigData.GroupConfig(groupUuid, "Grupo", "descripcion",
                        "500", true, "md5-real-del-grupo")),
                List.of(new CommConfigData.ItemConfig(itemUuid, "Area", "descripcion",
                        "%DB1.DBB0:INT", true, "md5-real-del-area", groupUuid)),
                List.of(new CommConfigData.PvConfig(pvUuid, "PV", "INT", "Area", "0", "",
                        "500", true, false, "0", "10", "desc", "%.2f", "bar", "0", "10", "0.1",
                        "md5-real-de-la-pv")));
    }

    private static CommConfigData commsWithoutGroup(String itemUuid, String pvUuid) {
        return new CommConfigData("PLC",
                List.of(new CommConfigData.GroupConfig("g-otro", "Grupo", "descripcion",
                        "500", true, "md5-real-del-grupo")),
                List.of(new CommConfigData.ItemConfig(itemUuid, "Area sin grupo", "descripcion",
                        "%DB1.DBB0:INT", true, "md5-real-del-area")),
                List.of(new CommConfigData.PvConfig(pvUuid, "PV", "INT", "Area sin grupo", "0", "",
                        "500", true, false, "0", "10", "desc", "%.2f", "bar", "0", "10", "0.1",
                        "md5-real-de-la-pv")));
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + new File(tempDir.toFile(), "boot.db"));
    }

    private int countDevices() throws Exception {
        return contar("Devices");
    }

    private int contar(String table) throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean deviceExists(String uuid) throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM Devices WHERE DeviceUuId = '" + uuid + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private String deviceValue(String uuid, String columna) throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                "SELECT " + columna + " FROM Devices WHERE DeviceUuId = '" + uuid + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private String uniqueValueOf(String table, String columna) throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT DISTINCT " + columna + " FROM " + table)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private String valueOf(String table, String columnaClave, String clave, String columna) throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT " + columna + " FROM " + table
                + " WHERE " + columnaClave + " = '" + clave + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
