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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;

class PlcOpenSaveIntegrityTest {

    @Test
    void unaEscrituraFallidaDejaElArchivoComoEstaba() throws Exception {
        AtomicStorage storage = new AtomicStorage();
        CommConfigRepository repo = new PlcOpenCommConfigRepositoryImpl(storage);

        repo.save(configWith(1));
        String anterior = storage.text();
        assertFalse(anterior.isBlank(), "precondicion: hay contenido escrito previamente");

        storage.failOnWrite = true;
        assertThrows(Exception.class, () -> repo.save(configWith(20)),
                "la escritura debe fallar a mitad");

        assertEquals(anterior, storage.text(),
                "H-02: un fallo al guardar no puede cambiar lo que habia escrito");
        assertTrue(storage.text().contains("PLC_1"),
                "H-02: el dispositivo de la escritura anterior tiene que seguir ahi");
        assertFalse(storage.text().contains("PLC_20"),
                "H-02: el contenido a medias no debe aplicarse");
    }

    @Test
    void noSePideElDestinoHastaTenerElDocumentoEntero() throws Exception {
        AtomicStorage storage = new AtomicStorage();
        CommConfigRepository repo = new PlcOpenCommConfigRepositoryImpl(storage);

        repo.save(configWith(3));

        assertEquals(0, storage.vecesCreate,
                "save() no debe usar create(), que trunca el archivo al abrirlo");
        assertEquals(1, storage.vecesWrite, "el contenido se entrega de una vez");
        assertTrue(storage.ultimoContenido.length > 0);
        assertTrue(new String(storage.ultimoContenido, StandardCharsets.UTF_8).contains("PLC_3"),
                "lo entregado es el documento completo, no un trozo");
    }

    @Test
    void unaEscrituraCorrectaSiReemplazaElContenido() throws Exception {
        AtomicStorage storage = new AtomicStorage();
        CommConfigRepository repo = new PlcOpenCommConfigRepositoryImpl(storage);

        repo.save(configWith(1));
        String conUnDispositivo = storage.text();
        repo.save(configWith(3));

        assertNotEquals(conUnDispositivo, storage.text());
        assertTrue(storage.text().contains("PLC_1"));
        assertTrue(storage.text().contains("PLC_3"));
        assertEquals(3, ocurrencias(storage.text(), "<configuration "),
                "el guardado es un reemplazo completo: queda un <configuration> por"
                + " dispositivo y nada del contenido anterior");
    }

    @Test
    void despuesDeUnFalloSePuedeGuardarOtraVez() throws Exception {
        AtomicStorage storage = new AtomicStorage();
        CommConfigRepository repo = new PlcOpenCommConfigRepositoryImpl(storage);
        repo.save(configWith(1));

        storage.failOnWrite = true;
        assertThrows(Exception.class, () -> repo.save(configWith(5)));
        storage.failOnWrite = false;

        repo.save(configWith(2));

        assertEquals(2, ocurrencias(storage.text(), "<configuration "),
                "el fallo anterior no dejo el almacenamiento en un estado raro");
        assertFalse(storage.text().contains("PLC_5"));
    }

    private static CommunicationsConfig configWith(int numeroDeDispositivos) {
        CommunicationsConfig config = new CommunicationsConfig();
        for (int i = 1; i <= numeroDeDispositivos; i++) {
            String uuid = "uuid-" + i;
            config.addDevice(new DeviceConfigData(propiedades(uuid, "PLC_" + i)));
            config.upsertComms(uuid, new CommConfigData("PLC_" + i,
                    List.of(new CommConfigData.GroupConfig("g-" + i, "Grupo", "d", "500", true, "m")),
                    List.of(new CommConfigData.ItemConfig("it-" + i, "Area", "d",
                            "%DB" + i + ".DBB0:INT", true, "m", "g-" + i)),
                    List.of(new CommConfigData.PvConfig("pv-" + i, "PV", "INT", "Area",
                            "0", "", "500", true, false, "0", "10", "d", "%.2f", "u",
                            "0", "10", "0.1", "m"))));
        }
        return config;
    }

    private static int ocurrencias(String text, String aguja) {
        int total = 0;
        int desde = text.indexOf(aguja);
        while (desde >= 0) {
            total++;
            desde = text.indexOf(aguja, desde + aguja.length());
        }
        return total;
    }

    private static Properties propiedades(String uuid, String name) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", name);
        p.put("deviceKey", "key-" + uuid);
        p.put("description", "descripcion de " + name);
        p.put("uuid", uuid);
        p.put("enable", true);
        p.put("s88Node", "S88.1");
        p.put("s88Uuid", "s88-" + uuid);
        p.put("specificParameters", "ipa=10.0.0.1,port=102");
        return p;
    }

    private static final class AtomicStorage implements CommConfigStorage {

        private String text = "";
        private boolean failOnWrite;
        private int vecesCreate;
        private int vecesWrite;
        private byte[] ultimoContenido = new byte[0];

        String text() {
            return text;
        }

        @Override
        public String open() {
            return text;
        }

        @Override
        public OutputStream create() {
            vecesCreate++;
            text = "";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            return new OutputStream() {
                @Override
                public void write(int b) {
                    buffer.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    buffer.write(b, off, len);
                }

                @Override
                public void close() {
                    text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                }
            };
        }

        @Override
        public void write(byte[] content) throws IOException {
            vecesWrite++;
            ultimoContenido = content;
            if (failOnWrite) {
                throw new IOException("no hay espacio en el dispositivo");
            }
            text = new String(content, StandardCharsets.UTF_8);
        }
    }
}
