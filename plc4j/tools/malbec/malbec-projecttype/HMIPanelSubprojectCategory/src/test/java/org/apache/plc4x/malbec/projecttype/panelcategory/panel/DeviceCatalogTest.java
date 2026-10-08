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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.util.List;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El catalogo de marcas y modelos que se pueden crear.
 *
 * <p>Antes era un mapa literal dentro de {@code CreateDevicePanel}, y comprobar que un
 * modelo existe obligaba a montar el panel. Estos casos son la mitad de lo que
 * {@code CreateDevicePanelTest} ya comprobaba, pero sin Swing de por medio.
 */
class DeviceCatalogTest {

    @Test
    void lasTresMarcasSiguenEstando() {
        assertEquals(List.of("Siemens", "Allen Bradley", "Modbus"), DeviceCatalog.marcas(),
                "las marcas que se ven en el desplegable no deben cambiar sin avisar");
    }

    @Test
    void cadaMarcaTieneSusModelos() {
        assertEquals(4, DeviceCatalog.de("Siemens").size());
        assertEquals(2, DeviceCatalog.de("Allen Bradley").size());
        assertEquals(3, DeviceCatalog.de("Modbus").size());
    }

    /**
     * El modelo encontrado por marca y nombre tiene que ser el que se guardo, con su
     * protocolo. Es lo que impide crear un S7-1500 que se guarde como Modbus.
     */
    @Test
    void elModeloTraeSuProtocolo() {
        DeviceModel s7 = DeviceCatalog.de("Siemens").stream()
                .filter(m -> "S7-1500".equals(m.getModel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta el S7-1500"));

        assertEquals("Siemens", s7.getBrand());
        assertEquals("S7", s7.getProtocol());
    }

    @Test
    void unaMarcaDesconocidaNoRompe() {
        assertTrue(DeviceCatalog.de("Marca Inventada").isEmpty(),
                "una marca que no existe devuelve vacio, no revienta");
        assertFalse(DeviceCatalog.existeMarca("Marca Inventada"));
    }

    @Test
    void lasMarcasDelCatalogoExistenTodas() {
        for (String marca : DeviceCatalog.marcas()) {
            assertTrue(DeviceCatalog.existeMarca(marca), "falta " + marca);
            assertFalse(DeviceCatalog.de(marca).isEmpty(), "la marca " + marca + " no tiene modelos");
        }
    }

/**
 * Los tres transportes de Modbus son los que se guardan en los ficheros de proyecto.
 *
 * <p>El protocolo es lo que decide como se conecta el dispositivo al PLC, asi que un
 * cambio accidental aqui no se ve en la interfaz: el dispositivo se crea y falla mas
 * tarde, en la planta. Se fija el valor exacto de los nueve protocolos del catalogo.
 */
@Test
void losProtocolosSonLosEsperados() {
    assertEquals(List.of("S7", "S7", "S7", "S7"),
            protocolosDe("Siemens"), "los S7 speaks S7");
    assertEquals(List.of("EtherNet/IP", "EtherNet/IP"), protocolosDe("Allen Bradley"));
    assertEquals(List.of("modbus-tcp", "modbus-rtu", "modbus-ascii"),
            protocolosDe("Modbus"), "cada variante de Modbus va por su sitio");
}

private static List<String> protocolosDe(String marca) {
    return DeviceCatalog.de(marca).stream().map(DeviceModel::getProtocol).toList();
}

/** Ningun modelo puede quedar sin protocolo: es lo que decide como se conecta. */
@Test
void ningunModeloSeQuedaSinProtocolo() {
        for (String marca : DeviceCatalog.marcas()) {
            for (DeviceModel modelo : DeviceCatalog.de(marca)) {
                assertFalse(modelo.getProtocol() == null || modelo.getProtocol().isBlank(),
                        modelo + " se quedo sin protocolo");
            }
        }
    }
}