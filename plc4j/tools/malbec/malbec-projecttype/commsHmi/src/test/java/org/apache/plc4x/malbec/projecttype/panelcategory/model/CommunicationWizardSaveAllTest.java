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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import java.util.List;
import java.util.Properties;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationStore;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que informa {@code saveAll()} del guardado a quien lo llama.
 *
 * <p>Antes de que {@code save()} devolviera un {@link SaveResult}, {@code saveAll()}
 * seguia devolviendo un {@code boolean} que solo decia "se actualizo boot.db". El
 * diálogo del wizard lo traducía a dos mensajes, y con eso un guardado en el que H-01
 * impedia escribir por una carga fallida salia como "guardado parcial": decia que se
 * habia escrito comunicacion.xml sin haberse escrito nada.
 *
 * <p>Estas pruebas fijan que el detalle llega intacto hasta arriba.
 */
class CommunicationWizardSaveAllTest {

    /** Almacen que devuelve el resultado que le pidamos, y cuenta las escrituras. */
    private static final class AlmacenQueDevuelve implements CommunicationStore {

        private final SaveResult resultado;
        private int escrituras;

        AlmacenQueDevuelve(SaveResult resultado) {
            this.resultado = resultado;
        }

        @Override
        public List<DeviceConfigData> getDevices() {
            return List.of();
        }

        @Override
        public DeviceConfigData findByUuid(String uuid) {
            return null;
        }

        @Override
        public CommConfigData getComms(String deviceUuid) {
            return null;
        }

        @Override
        public void upsertDevice(DeviceConfigData device) {
        }

        @Override
        public void upsertComms(String deviceUuid, CommConfigData config) {
        }

        @Override
        public boolean removeDevice(String uuid) {
            return false;
        }

        @Override
        public SaveResult save() {
            escrituras++;
            return resultado;
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
        }
    }

    private static DeviceConfigData dispositivo() {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", "PLC de control");
        p.put("deviceKey", "key");
        p.put("description", "");
        p.put("uuid", "uuid-control");
        p.put("enable", "true");
        return new DeviceConfigData(p);
    }

    private static CommunicationWizardController controller(CommunicationStore store) {
        CommunicationWizardController c = new CommunicationWizardController(
                store, new CommunicationWizardState());
        c.getState().setSelectedDevice(dispositivo());
        return c;
    }

    @Test
    void unGuardadoCompletoSeInformaComoCompleto() {
        CommunicationWizardController c = controller(
                new AlmacenQueDevuelve(SaveResult.exito()));

        SaveResult resultado = c.saveAll();

        assertTrue(resultado.isComplete(), "un guardado completo tiene que decirselo");
        assertTrue(resultado.xmlSaved());
        assertTrue(resultado.databaseUpdated());
    }

    /**
     * El caso que se estaba perdiendo: H-01 impidio escribir, asi que no hay nada
     * guardado. No puede llegar como un fallo de boot.db, porque eso hace que el
     * usuario repare comunicacion.xml creyendo que se actualizo.
     */
    @Test
    void unaCargaFallidaSeInformaComoQueNoSeGuardoNada() {
        AlmacenQueDevuelve almacen = new AlmacenQueDevuelve(SaveResult.noGuardado(
                "la carga de comunicacion.xml fallo, asi que el modelo en memoria no es de fiar"));
        CommunicationWizardController c = controller(almacen);

        SaveResult resultado = c.saveAll();

        assertFalse(resultado.xmlSaved(),
                "con la carga fallida no se escribe nada, y hay que decirlo");
        assertFalse(resultado.isComplete());
        assertTrue(resultado.message().contains("comunicacion.xml"),
                "el motivo deberia nombrar el fichero: " + resultado.message());
    }

    @Test
    void unGuardadoParcialSeDistingueDeNoHaberGuardadoNada() {
        CommunicationWizardController c = controller(
                new AlmacenQueDevuelve(SaveResult.parcial(
                        "se escribio comunicacion.xml pero no se pudo actualizar boot.db")));

        SaveResult resultado = c.saveAll();

        assertTrue(resultado.xmlSaved(),
                "aqui el XML si se escribio, que es lo que lo hace distinto");
        assertFalse(resultado.databaseUpdated());
        assertFalse(resultado.isComplete());
        assertFalse(resultado.message().isBlank());
    }

    /** Sin dispositivo no hay nada que guardar, y eso no es un fallo de escritura. */
    @Test
    void sinDispositivoNoSeInventaUnGuardado() {
        AlmacenQueDevuelve almacen = new AlmacenQueDevuelve(SaveResult.exito());
        CommunicationWizardController c = new CommunicationWizardController(
                almacen, new CommunicationWizardState());

        SaveResult resultado = c.saveAll();

        assertFalse(resultado.xmlSaved());
        assertTrue(resultado.message().contains("dispositivo"),
                "el motivo deberia decir que falta el dispositivo: " + resultado.message());
    }

    /**
     * Tras guardar bien, no pueden quedar cambios pendientes: si no, al cerrar el
     * asistente saltaria el aviso de "cambios sin guardar" justo despues de guardar.
     */
    @Test
    void guardarBienDejaLosCambosMarcadosComoGuardados() {
        CommunicationWizardController c = controller(
                new AlmacenQueDevuelve(SaveResult.exito()));

        c.saveAll();

        assertFalse(c.getState().isCambiosSinGuardar(),
                "tras un guardado completo no debe quedar nada sin guardar");
    }

    /** Un guardado a medias tiene que dejar los cambios pendientes. */
    @Test
    void guardarAPartiasDejaLosCambosPendientes() {
        CommunicationWizardController c = controller(
                new AlmacenQueDevuelve(SaveResult.parcial("boot.db no pudo actualizarse")));

        c.saveAll();

        assertTrue(c.getState().isCambiosSinGuardar(),
                "con boot.db sin actualizar, el cambio sigue sin estar del todo guardado");
    }
}