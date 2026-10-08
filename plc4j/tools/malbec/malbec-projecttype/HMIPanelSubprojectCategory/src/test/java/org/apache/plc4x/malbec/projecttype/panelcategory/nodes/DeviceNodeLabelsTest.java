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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;

/**
 * Los textos del nodo de dispositivo (T4.3).
 *
 * <p>Estaban dentro de la factory, como metodos privados junto al codigo Swing, asi que no
 * se podia comprobar nada de ellos sin levantar la ventana principal de NetBeans. Ahora son
 * funciones y se fijan aqui.
 *
 * <p>El texto de borrado tiene la regla que mas se rompe en silencio: decide entre singular y
 * plural segun la cantidad. Un "1 grupos" no rompe nada, asi que nadie se entera hasta que lo
 * lee un usuario.
 */
class DeviceNodeLabelsTest {

    @Test
    void sinNombreDevuelveElTextoSustituto() {
        assertEquals("(sin nombre)", DeviceNodeLabels.nombreDe(null));
        assertEquals("(sin nombre)", DeviceNodeLabels.nombreDe(dispositivo("")));
        assertEquals("(sin nombre)", DeviceNodeLabels.nombreDe(dispositivo("   ")));
    }

    @Test
    void conNombreDevuelveElNombre() {
        assertEquals("Compresor 1", DeviceNodeLabels.nombreDe(dispositivo("Compresor 1")));
    }

    @Test
    void elTooltipIdentificaMarcaModeloYProtocolo() {
        DeviceConfigData d = PropertiesDevice("Compresor 1", "Siemens", "S7-1500", "S7");

        assertEquals("Siemens / S7-1500 / S7", DeviceNodeLabels.tooltipDe(d));
    }

    @Test
    void elTooltipNoSeRompeSiFaltaUnCampo() {
        DeviceConfigData d = PropertiesDevice("PLC", null, "S7-1200", null);

        assertEquals("null / S7-1200 / null",
                DeviceNodeLabels.tooltipDe(d),
                "antes se concatenaba tal cual; estos tests fijan ese comportamiento");
    }

    @Test
    void unaPropitudDeTextoAusenteSaleVaciaYNoNula() {
        DeviceConfigData d = dispositivo("PLC");

        assertEquals("", DeviceNodeLabels.textoDe(null, x -> "algo"),
                "sin dispositivo la propiedad se muestra vacia, no con un null");
        assertEquals("", DeviceNodeLabels.textoDe(d, x -> null));
        assertEquals("PLC", DeviceNodeLabels.textoDe(d, DeviceConfigData::getDeviceName));
    }

    @Test
    void unaPropitudBooleanaSinDispositivoEsFalsa() {
        assertFalse(DeviceNodeLabels.booleanoDe(null, x -> true));
        assertTrue(DeviceNodeLabels.booleanoDe(dispositivo("PLC"), x -> true));
        assertFalse(DeviceNodeLabels.booleanoDe(dispositivo("PLC"), x -> null));
    }

    @Test
    void elMensajeDeBorradoEscogeSingularOPluralSegunLaCantidad() {
        CommConfigData unGrupo = comunicacion(1, 1, 1);
        String texto = DeviceNodeLabels.mensajeDeBorrado("Compresor 1", unGrupo);

        assertTrue(texto.contains("1 grupo\n"), "un grupo va en singular: " + texto);
        assertFalse(texto.contains("1 grupos"), "no puede decir '1 grupos': " + texto);
        assertTrue(texto.contains("1 \u00e1rea"), "un area va en singular: " + texto);
        assertFalse(texto.contains("1 \u00e1reas"), "no puede decir '1 áreas': " + texto);
        assertTrue(texto.contains("1 variable\n"), "una variable va en singular: " + texto);
        assertFalse(texto.contains("1 variables"), "no puede decir '1 variables': " + texto);
    }

    @Test
    void conVariosElementosVaEnPlural() {
        String texto = DeviceNodeLabels.mensajeDeBorrado("Compresor 1", comunicacion(3, 2, 7));

        assertTrue(texto.contains("3 grupos"), texto);
        assertTrue(texto.contains("2 \u00e1reas"), texto);
        assertTrue(texto.contains("7 variables"), texto);
    }

    @Test
    void unDispositivoSinNadaQuePerderNoEnumeraCeros() {
        String texto = DeviceNodeLabels.mensajeDeBorrado("PLC", comunicacion(0, 0, 0));

        assertFalse(texto.contains("  \u2022 0 grupos"), "no tiene sentido listar ceros: " + texto);
        assertTrue(texto.contains("Va a eliminar el dispositivo 'PLC'."), texto);
        assertTrue(texto.contains("\u00bfDesea continuar?"), texto);
    }

    @Test
    void elMensajeDeBorradoAvanzaQueTambienSeTocaBootDb() {
        String texto = DeviceNodeLabels.mensajeDeBorrado("PLC", comunicacion(1, 0, 0));

        assertTrue(texto.contains("comunicacion.xml"), texto);
        assertTrue(texto.contains("boot.db"), texto);
        assertTrue(texto.contains("Al borrarlo se pierden todas sus configuraciones."), texto);
    }

    @Test
    void elMensajeDeBorradoAguantaCommsNull() {
        String texto = DeviceNodeLabels.mensajeDeBorrado("PLC", null);

        assertTrue(texto.contains("Va a eliminar el dispositivo 'PLC'."), texto);
        assertFalse(texto.contains("\u2022"), "sin comunicacion no hay nada que listar: " + texto);
    }

    @Test
    void losMensajesDeAvisoNombranAlDispositivo() {
        assertTrue(DeviceNodeLabels.mensajeDispositivoInexistente("PLC")
                .contains("'PLC'"));
        assertTrue(DeviceNodeLabels.mensajeGuardadoParcial("PLC").contains("boot.db"));
        assertTrue(DeviceNodeLabels.mensajeBorradoCorrecto("PLC").contains("correctamente"));
        assertTrue(DeviceNodeLabels.mensajeNadaQueBorrar("PLC").contains("No se encontr"));
    }

    private static DeviceConfigData dispositivo(String nombre) {
        return PropertiesDevice(nombre, null, null, null);
    }

    private static DeviceConfigData PropertiesDevice(String nombre, String marca, String modelo,
            String protocolo) {
        Properties p = new Properties();
        // Properties no admite valores null, y DeviceConfigData lee con getProperty(),
        // que devuelve null si la clave no está.
        poner(p, "deviceName", nombre);
        poner(p, "brand", marca);
        poner(p, "model", modelo);
        poner(p, "protocol", protocolo);
        return new DeviceConfigData(p);
    }

    private static void poner(Properties p, String clave, String valor) {
        if (valor != null) {
            p.put(clave, valor);
        }
    }

    private static CommConfigData comunicacion(int grupos, int areas, int variables) {
        List<CommConfigData.GroupConfig> listaGrupos = new java.util.ArrayList<>();
        for (int i = 0; i < grupos; i++) {
            listaGrupos.add(new CommConfigData.GroupConfig("g" + i, "G" + i, "d", "500", true, "m"));
        }
        List<CommConfigData.ItemConfig> listaAreas = new java.util.ArrayList<>();
        for (int i = 0; i < areas; i++) {
            listaAreas.add(new CommConfigData.ItemConfig("i" + i, "A" + i, "d", "t" + i, true, "m", "g0"));
        }
        List<CommConfigData.PvConfig> listaPvs = new java.util.ArrayList<>();
        for (int i = 0; i < variables; i++) {
            listaPvs.add(new CommConfigData.PvConfig("p" + i, "P" + i, "INT", "i0", "0", "d",
                    "500", true, true, "0", "1", "d", "%.2f", "u", "0", "1", "1", "m", ""));
        }
        return new CommConfigData("PLC", listaGrupos, listaAreas, listaPvs);
    }
}