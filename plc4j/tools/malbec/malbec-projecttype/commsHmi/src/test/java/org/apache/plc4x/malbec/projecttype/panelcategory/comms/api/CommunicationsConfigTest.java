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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.api;

import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunicationsConfigTest {

    private DeviceConfigData device(String uuid, String nombre) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", nombre);
        p.put("deviceKey", "K-" + nombre);
        p.put("description", "");
        p.put("uuid", uuid == null ? "" : uuid);
        p.put("enable", Boolean.TRUE);
        p.put("s88Node", "MD70");
        p.put("s88Uuid", "uuid-md70");
        p.put("specificParameters", "s7:cotp://192.168.0.10:102");
        return new DeviceConfigData(p);
    }

    @Test
    void modificarUnDispositivoLoReemplazaYNoLoDuplica() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("uuid-1", "Compresor 1"));

        config.upsertDevice(device("uuid-1", "Compresor 1 renombrado"));

        assertEquals(1, config.getDevices().size(),
                "modificar no puede agregar un dispositivo nuevo");
        assertEquals("Compresor 1 renombrado", config.getDevices().get(0).getDeviceName());
        assertEquals("Compresor 1 renombrado", config.findByUuid("uuid-1").getDeviceName(),
                "T3.2: el indice tiene que devolver el dispositivo ya cambiado");
    }

    // ------------------------------------------------------- T3.2

    @Test
    void elIndiceNoSeDesalineaAlBorrarEnElMedio() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-1", "Uno"));
        config.addDevice(device("uuid-2", "Dos"));
        config.addDevice(device("uuid-3", "Tres"));

        assertTrue(config.removeDevice("uuid-2"), "se borra el del medio");

        assertEquals(2, config.getDevices().size());
        assertEquals("Uno", config.getDevices().get(0).getDeviceName());
        assertEquals("Tres", config.getDevices().get(1).getDeviceName(),
                "T3.2: tras borrar, los siguientes se desplazan y el indice debe seguirlos");
        assertNull(config.findByUuid("uuid-2"), "el borrado ya no se encuentra");
        assertEquals("Tres", config.findByUuid("uuid-3").getDeviceName());
        assertEquals("Uno", config.findByUuid("uuid-1").getDeviceName());
    }

    @Test
    void elIndiceAguantaVariosBorradosSeguros() {
        CommunicationsConfig config = new CommunicationsConfig();
        for (int i = 1; i <= 5; i++) {
            config.addDevice(device("uuid-" + i, "D" + i));
        }
        config.removeDevice("uuid-1");
        config.removeDevice("uuid-3");
        config.removeDevice("uuid-5");

        assertEquals(2, config.getDevices().size());
        for (int i : new int[]{2, 4}) {
            assertEquals("D" + i, config.findByUuid("uuid-" + i).getDeviceName(),
                    "el indice debe seguir apuntando al sitio correcto tras varios borrados");
        }
        for (int i : new int[]{1, 3, 5}) {
            assertNull(config.findByUuid("uuid-" + i));
        }
    }

    @Test
    void dosDispositivosSinUuidNoSePisan() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Sin uuid A"));
        config.upsertDevice(device("", "Sin uuid B"));

        assertEquals(2, config.getDevices().size(),
                "T3.2: si compartieran clave, uno pisaria al otro");
        assertEquals("Sin uuid A", config.getDevices().get(0).getDeviceName());
        assertEquals("Sin uuid B", config.getDevices().get(1).getDeviceName());
    }

    @Test
    void unDispositivoSinUuidSeAgregaYNoSeRechaza() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Sin uuid"));

        assertEquals(1, config.getDevices().size(),
                "T3.2: rechazarlo lo haria desaparecer en silencio y el dialogo de creacion"
                        + " seguiria anunciando que se guardo");
        assertNull(config.findByUuid(""), "buscar por uuid vacio no devuelve nada");
        assertNull(config.findByUuid(null));
    }

    @Test
    void laListaDeDispositivosNoSePuedeModificar() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-1", "Uno"));

        assertThrows(UnsupportedOperationException.class,
                () -> config.getDevices().add(device("uuid-2", "Dos")),
                "T3.2: la vista tiene que ser inmutable, si no se puede sortear el indice");
    }

    @Test
    void reemplazarEnUnaPosicionReindexa() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.addDevice(device("uuid-1", "Uno"));
        config.addDevice(device("uuid-2", "Dos"));

        // Es lo que hace repararUuidsAusentes: el dispositivo gana su UUID.
        config.replaceDevice(0, device("uuid-nuevo", "Uno reparado"));

        assertEquals("Uno reparado", config.findByUuid("uuid-nuevo").getDeviceName());
        assertNull(config.findByUuid("uuid-1"),
                "T3.2: el UUID viejo debe desaparecer del indice");
        assertEquals("Dos", config.findByUuid("uuid-2").getDeviceName(),
                "el resto del indice no se mueve");
    }

    @Test
    void unDispositivoSinUuidNoSeModificaSinoQueSeAgrega() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Sin uuid"));

        config.upsertDevice(device("", "Sin uuid editado"));

        assertEquals(2, config.getDevices().size(),
                "sin UUID no hay identidad: el comportamiento es agregar, y por eso avisa");
    }

    @Test
    void removeDeviceSacaElDispositivoYSuComunicacion() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("uuid-1", "Compresor 1"));
        config.upsertComms("uuid-1", null);

        assertTrue(config.removeDevice("uuid-1"));
        assertEquals(0, config.getDevices().size());
    }

    @Test
    void replaceDeviceCambiaElDispositivoDeLaPosicion() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Sin uuid"));

        config.replaceDevice(0, device("uuid-reparado", "Sin uuid"));

        assertEquals(1, config.getDevices().size());
        assertNotNull(config.getDevices().get(0).getUuid());
        assertEquals("uuid-reparado", config.getDevices().get(0).getUuid());
        assertEquals("Sin uuid", config.getDevices().get(0).getDeviceName(),
                "la reparación del UUID no debe perder el resto de los datos");
    }
}