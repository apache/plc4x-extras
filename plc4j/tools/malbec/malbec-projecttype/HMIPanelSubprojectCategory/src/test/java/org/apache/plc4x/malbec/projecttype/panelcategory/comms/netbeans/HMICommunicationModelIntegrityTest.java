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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HMICommunicationModelIntegrityTest {

    private static final String XML_ROTO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://www.plcopen.org/xml/tc6_0201">
              <instances>
                <configurations>
                  <configuration name="roto">
                    <addData>
                      <data name="http://apache.org/plc4x/malbec/device" handleUnknown="preserve">
            """;

    private static final String XML_VACIO = "";
    private static final String XML_CON_UN_DISPOSITIVO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://www.plcopen.org/xml/tc6_0201"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://www.plcopen.org/xml/tc6_0201 http://www.plcopen.org/xml/tc6_0201.xsd">
              <fileHeader companyName="Malbec" productName="Malbec Communication" productVersion="1.0" creationDateTime="2000-01-01T00:00:00Z"/>
              <contentHeader name="MalbecCommunication">
                <coordinateInfo>
                  <fbd><scaling x="1" y="1"/></fbd>
                  <ld><scaling x="1" y="1"/></ld>
                  <sfc><scaling x="1" y="1"/></sfc>
                </coordinateInfo>
              </contentHeader>
              <types>
                <dataTypes/>
                <pous/>
              </types>
              <instances>
                <configurations>
                  <configuration name="PLC_Control">
                    <addData>
                      <data name="http://apache.org/plc4x/malbec/device" handleUnknown="preserve">
                        <device uuid="uuid-control" enabled="true" s88Uuid="s88-control" xmlns="Device">
                          <brand>Siemens</brand>
                          <model>S7-1500</model>
                          <protocol>S7</protocol>
                          <deviceKey>key-control</deviceKey>
                          <description>PLC de control</description>
                          <s88Node>S88.1</s88Node>
                          <specificParameters>ipa=10.0.0.1,port=102</specificParameters>
                        </device>
                      </data>
                    </addData>
                  </configuration>
                </configurations>
              </instances>
            </project>
            """;

    @Test
    void repositoryPropagatesTheErrorOfAMalformedXml() {
        CommConfigRepository repo = CommConfigRepositoryLookup.createRepository(new TextoFijo(XML_ROTO));

        assertThrows(Exception.class, repo::load,
                "H-01, capa 1: la lectura de un XML roto tiene que fallar");
    }

    @Test
    void netbeansWiringDoesNotSwallowTheError() {
        CommConfigRepository repo = CommConfigRepositoryLookup.createRepository(new TextoFijo(XML_ROTO));

        assertThrows(Exception.class, repo::load,
                "H-01, capa 2: crear el repositorio no puede alterar como falla la lectura");
    }

    @Test
    void unArchivoVacioNoSeConsideraUnError() throws Exception {
        CommConfigLoader.Result carga = new CommConfigLoader(
                CommConfigRepositoryLookup.createRepository(new TextoFijo(XML_VACIO))).load();

        assertEquals(0, carga.config().getDevices().size(),
                "un archivo vacio es un proyecto nuevo, no una carga fallida");
        assertFalse(carga.uuidReparados());
    }

    @Test
    void damagedXmlCannotBeLoadedSilently() {
        CommConfigLoader loader = new CommConfigLoader(
                CommConfigRepositoryLookup.createRepository(new TextoFijo(XML_ROTO)));

        assertThrows(Exception.class, loader::load,
                "H-01: la carga de un XML danado tiene que fallar, no devolver un modelo vacio");
    }

    @Test
    void loadPropagatesTheRepositoryFailureAsIs() {
        Exception original = new IllegalStateException("xml corrupto en el byte 412");
        CommConfigRepository queFalla = new RepositorioQueFalla(original);

        Exception recibido = assertThrows(Exception.class,
                () -> new CommConfigLoader(queFalla).load());

        assertEquals(original, recibido,
                "H-01: el error debe llegar intacto para que el mensaje sea util");
    }

    @Test
    void loadRejectsANullConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> new CommConfigLoader(new RepositorioVacio()).load(),
                "un repositorio que devuelve null es un fallo, no una configuracion vacia");
    }

    @Test
    void laCargaReparaLosUuidsAusentesYLoInforma() throws Exception {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Compresor viejo"));
        config.upsertDevice(device("uuid-ok", "Compresor"));

        CommConfigLoader.Result carga = new CommConfigLoader(new RepositorioFijo(config)).load();

        assertTrue(carga.uuidReparados(),
                "el modelo debe saber que el fichero necesita reescribirse");
        assertEquals("uuid-ok", carga.config().getDevices().get(1).getUuid(),
                "un dispositivo con UUID no se toca");
        assertFalse(carga.config().getDevices().get(0).getUuid().isBlank(),
                "el dispositivo sin UUID debe recibir uno");
    }

    @Test
    void unaCargaCorrectaNoDejaUuidsQueReparar() throws Exception {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("uuid-ok", "Compresor"));

        CommConfigLoader.Result carga = new CommConfigLoader(new RepositorioFijo(config)).load();

        assertFalse(carga.uuidReparados(),
                "sin UUID ausentes no hay que reescribir el fichero");
    }

    @Test
    void damagedXmlBlocksSavingAndLeavesBootDbAlone(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("llenadora", tempDir.toFile())
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_ROTO);
        HMICommunicationModel model = new HMICommunicationModel(storage);

        assertTrue(model.getLoadError().isPresent(),
                "un XML danado tiene que dejar constancia del fallo");

        assertFalse(model.save().xmlSaved(),
                "H-01: con la carga fallida, guardar tiene que negarse");

        assertFalse(new File(tempDir.toFile(), "boot.db").exists(),
                "H-01: no se puede reconstruir boot.db desde un modelo que no se ha podido leer");
        assertEquals(0, storage.configWrites(),
                "H-01: tampoco se debe reescribir comunicacion.xml");
    }

    @Test
    void afterFixingTheXmlSavingIsAllowedAgain(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("llenadora", tempDir.toFile())
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_ROTO);
        HMICommunicationModel model = new HMICommunicationModel(storage);
        assertFalse(model.save().xmlSaved(), "precondición: con el XML roto no se guarda");

        storage.withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO);
        storage.fireChanged();

        assertTrue(model.getLoadError().isEmpty(),
                "una carga correcta tiene que limpiar el error anterior");
        assertEquals(1, model.getDevices().size());
        assertTrue(model.save().isComplete(),
                "H-01: reparado el fichero, guardar vuelve a estar permitido");
        assertTrue(new File(tempDir.toFile(), "boot.db").exists(),
                "con el modelo cargado, boot.db si se reconstruye");
    }

    @Test
    void failedReadDoesNotAllowOverwritingTheFile(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("Comunicacion", tempDir.toFile())
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO)
                .failNextReads(1);
        HMICommunicationModel model = new HMICommunicationModel(storage);

        assertTrue(model.getLoadError().isPresent(),
                "una lectura fallida tambien es un fallo de carga");
        assertEquals(0, model.getDevices().size(),
                "precondición: sin lectura no hay dispositivos en memoria");

        assertFalse(model.save().xmlSaved(),
                "H-01: el archivo es escribible, asi que sin guarda se perderian sus datos");

        assertEquals(XML_CON_UN_DISPOSITIVO, storage.contentOf(HMICommunicationModel.CONFIG_FILE),
                "H-01: el fichero original tiene que quedar intacto");
        assertEquals(0, storage.configWrites(), "no se debe escribir nada");
        assertFalse(new File(tempDir.toFile(), "boot.db").exists(),
                "H-01: ni boot.db debe reconstruirse desde un modelo vacio");
    }

    @Test
    void projectWithoutCommunicationNeitherSavesNorComplains(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("otro", tempDir.toFile());
        HMICommunicationModel model = new HMICommunicationModel(storage);

        assertTrue(model.getLoadError().isEmpty(),
                "un proyecto que no es de comunicaciones no tiene un error de carga");
        assertEquals(0, model.getDevices().size());
        assertFalse(new File(tempDir.toFile(), "boot.db").exists());
    }

    @Test
    void communicationsProjectCreatesTheFileIfMissing(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("Comunicacion", tempDir.toFile());
        new HMICommunicationModel(storage);

        assertTrue(storage.has(HMICommunicationModel.CONFIG_FILE),
                "un proyecto de comunicaciones debe materializar comunicacion.xml");
    }

    @Test
    void projectWithTheWrongNameCreatesNothing(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("llenadora", tempDir.toFile());
        HMICommunicationModel model = new HMICommunicationModel(storage);

        assertFalse(storage.has(HMICommunicationModel.CONFIG_FILE),
                "un proyecto que no es de comunicaciones no debe crear el archivo");
        assertTrue(model.getLoadError().isEmpty(),
                "y no debe reportarse como error de carga");
    }

    @Test
    void borrarElArchivoOlvidaElModelo(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("llenadora", tempDir.toFile())
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO);
        HMICommunicationModel model = new HMICommunicationModel(storage);
        assertEquals(1, model.getDevices().size(), "precondición: el XML es valido");

        storage.fireDeleted();

        assertEquals(0, model.getDevices().size(),
                "tras borrar el archivo no queda ningun dispositivo en memoria");
    }

    @Test
    void escribirNoDisparaUnaRecarga (@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("llenadora", tempDir.toFile())
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO);
        HMICommunicationModel model = new HMICommunicationModel(storage);
        storage.fireChanged();
        boolean estabaVigilando = storage.isWatched();

        model.upsertDevice(device("uuid-nuevo", "Compresor extra"));
        model.save();

        assertTrue(estabaVigilando, "precondición: el modelo vigila el archivo");
        assertEquals(2, model.getDevices().size(),
                "guardar no debe recargar el modelo sobre una escritura a medias");
    }

    private static DeviceConfigData device(String uuid, String name) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", name);
        p.put("deviceKey", "K-" + name);
        p.put("description", "descripcion de " + name);
        p.put("uuid", uuid);
        p.put("enable", Boolean.TRUE);
        p.put("s88Node", "MD70");
        p.put("s88Uuid", "uuid-md70");
        p.put("specificParameters", "s7:cotp://192.168.0.10:102");
        return new DeviceConfigData(p);
    }

    /** Repositorio que devuelve siempre la misma configuracion. */
    private static final class RepositorioFijo implements CommConfigRepository {

        private final CommunicationsConfig config;

        RepositorioFijo(CommunicationsConfig config) {
            this.config = config;
        }

        @Override
        public CommunicationsConfig load() {
            return config;
        }

        @Override
        public void save(CommunicationsConfig config) {
            // este test solo carga
        }
    }

    /** Repositorio que simula un fallo de lectura. */
    private static final class RepositorioQueFalla implements CommConfigRepository {

        private final Exception error;

        RepositorioQueFalla(Exception error) {
            this.error = error;
        }

        @Override
        public CommunicationsConfig load() throws Exception {
            throw error;
        }

        @Override
        public void save(CommunicationsConfig config) {
            // no llega aqui
        }
    }

    /** Repositorio que devuelve null, que es un fallo distinto de una carga vacia. */
    private static final class RepositorioVacio implements CommConfigRepository {

        @Override
        public CommunicationsConfig load() {
            return null;
        }

        @Override
        public void save(CommunicationsConfig config) {
            // este test solo carga
        }
    }


    @Test
    void alBorrarElArchivoSeVuelveACrear(@TempDir Path tempDir) {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("Comunicacion",
                tempDir.toFile());
        HMICommunicationModel model = new HMICommunicationModel(storage);
        assertTrue(storage.has(HMICommunicationModel.CONFIG_FILE), "precondicion: existe");

        storage.fireDeleted();

        assertTrue(storage.has(HMICommunicationModel.CONFIG_FILE),
                "borrar comunicacion.xml no puede dejar el proyecto sin el fichero");
        assertDoesNotThrow(() -> model.save(),
                "con el fichero recreado el modelo se puede volver a usar");
    }
}
