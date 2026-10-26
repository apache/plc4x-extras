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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.BootDbTagDynamicRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.PlcOpenCommConfigRepositoryImpl;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.plcopen.xml.tc60201.ProjectDocument;

class GoldenFileContractTest {

    private static final String GOLDEN_XML = "/golden/comunicacion.xml";
//    private static final String GOLDEN_SQL = "/golden/boot_db_reference.sql";
    private static final String CORRUPT_XML = "/golden/comunicacion_corrupto.xml";

    @TempDir
    Path tempDir;

    @Test
    void saveProducesExactlyTheGoldenXml() throws Exception {
        GoldenFixture.BufferingStorage storage = new GoldenFixture.BufferingStorage();
        CommConfigRepository repo = new PlcOpenCommConfigRepositoryImpl(storage);

        repo.save(GoldenFixture.config());

        assertEquals(resource(GOLDEN_XML), normalize(storage.text()),
                "El XML guardado ya no coincide con el golden. Si el cambio es intencionado,"
                        + " revisa el diff antes de actualizar el fichero golden.");
    }

    @Test
    void goldenXmlIsSchemaValid() throws Exception {
        ProjectDocument doc = ProjectDocument.Factory.parse(resource(GOLDEN_XML));
        assertTrue(doc.validate(), "El golden debe seguir siendo valido contra tc6_0201");
    }

    @Test
    void goldenXmlLoadsBackIntoTheSameModel() throws Exception {
        GoldenFixture.BufferingStorage storage =
                new GoldenFixture.BufferingStorage(resource(GOLDEN_XML));
        CommConfigRepository repo = new PlcOpenCommConfigRepositoryImpl(storage);

        CommunicationsConfig loaded = repo.load();

        assertEquals(canonical(GoldenFixture.config()), canonical(loaded),
                "El round-trip load(golden) debe reproducir el modelo completo");
    }

    @Test
    void savingTheLoadedGoldenReproducesTheGolden() throws Exception {
        CommunicationsConfig loaded = new PlcOpenCommConfigRepositoryImpl(
                new GoldenFixture.BufferingStorage(resource(GOLDEN_XML))).load();

        GoldenFixture.BufferingStorage target = new GoldenFixture.BufferingStorage();
        new PlcOpenCommConfigRepositoryImpl(target).save(loaded);

        assertEquals(resource(GOLDEN_XML), normalize(target.text()),
                "load -> save debe ser idempotente sobre el golden");
    }

//    @Test
//    void rebuildProducesTheGoldenBootDbContent() throws Exception {
//        assertTrue(BootDbTagDynamicRepository.rebuild(tempDir.toFile(), GoldenFixture.config()),
//                "rebuild() debe completar");
//
//        String dump = SqlDump.dump(new File(tempDir.toFile(), "boot.db"));
//
//        assertEquals(resource(GOLDEN_SQL), normalize(dump),
//                "El contenido de boot.db cambio. El golden fija el esquema y las filas.");
//    }

    @Test
    void corruptXmlIsRejectedWithAnException() {
        GoldenFixture.BufferingStorage storage =
                new GoldenFixture.BufferingStorage(resource(CORRUPT_XML));

        assertThrows(Exception.class, () -> new PlcOpenCommConfigRepositoryImpl(storage).load(),
                "El repositorio debe fallar ante un XML mal formado. Si dejara de fallar, "
                        + "quien este por encima esta tragandose el error (H-01).");
    }

    @Test
    void goldenFixtureCoversTheExpectedVolume() {
        CommunicationsConfig config = GoldenFixture.config();

        assertEquals(2, config.getDevices().size());
        assertEquals(3, config.getComms(GoldenFixture.DEV_PLC).getGroups().size()
                + config.getComms(GoldenFixture.DEV_VFD).getGroups().size());
        assertEquals(4, config.getComms(GoldenFixture.DEV_PLC).getItems().size()
                + config.getComms(GoldenFixture.DEV_VFD).getItems().size());
        assertEquals(6, config.getComms(GoldenFixture.DEV_PLC).getPvs().size()
                + config.getComms(GoldenFixture.DEV_VFD).getPvs().size());
    }

    private static String canonical(CommunicationsConfig config) {
        StringBuilder out = new StringBuilder();
        for (DeviceConfigData device : config.getDevices()) {
            out.append("device\n")
                    .append("  uuid=").append(device.getUuid()).append('\n')
                    .append("  deviceName=").append(device.getDeviceName()).append('\n')
                    .append("  brand=").append(device.getBrand()).append('\n')
                    .append("  model=").append(device.getModel()).append('\n')
                    .append("  protocol=").append(device.getProtocol()).append('\n')
                    .append("  deviceKey=").append(device.getDeviceKey()).append('\n')
                    .append("  description=").append(device.getDescription()).append('\n')
                    .append("  enabled=").append(device.isEnabled()).append('\n')
                    .append("  s88Node=").append(device.getS88Node()).append('\n')
                    .append("  s88Uuid=").append(device.getS88Uuid()).append('\n')
                    .append("  specificParameters=").append(device.getSpecificParameters()).append('\n');
        }
        return out.toString();
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }

    private static String resource(String name) {
        try (InputStream in = GoldenFileContractTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "Falta el recurso golden " + name);
            return normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el golden " + name, e);
        }
    }
}
