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

class DeviceNodeLabelsTest {

    @Test
    void withoutNameReturnsTheSubstituteText() {
        assertEquals("(sin nombre)", DeviceNodeLabels.nameOf(null));
        assertEquals("(sin nombre)", DeviceNodeLabels.nameOf(device("")));
        assertEquals("(sin nombre)", DeviceNodeLabels.nameOf(device("   ")));
    }

    @Test
    void withNameReturnsTheName() {
        assertEquals("Compresor 1", DeviceNodeLabels.nameOf(device("Compresor 1")));
    }

    @Test
    void tooltipIdentifiesBrandModelAndProtocol() {
        DeviceConfigData d = PropertiesDevice("Compresor 1", "Siemens", "S7-1500", "S7");

        assertEquals("Siemens / S7-1500 / S7", DeviceNodeLabels.tooltipOf(d));
    }

    @Test
    void tooltipDoesNotBreakIfAFieldIsMissing() {
        DeviceConfigData d = PropertiesDevice("PLC", null, "S7-1200", null);

        assertEquals("null / S7-1200 / null",
                DeviceNodeLabels.tooltipOf(d),
                "antes se concatenaba tal cual; estos tests fijan ese comportamiento");
    }

    @Test
    void missingTextPropertyComesOutEmptyNotNull() {
        DeviceConfigData d = device("PLC");

        assertEquals("", DeviceNodeLabels.textOf(null, x -> "algo"),
                "sin dispositivo la propiedad se muestra vacia, no con un null");
        assertEquals("", DeviceNodeLabels.textOf(d, x -> null));
        assertEquals("PLC", DeviceNodeLabels.textOf(d, DeviceConfigData::getDeviceName));
    }

    @Test
    void booleanPropertyWithoutDeviceIsFalse() {
        assertFalse(DeviceNodeLabels.booleanOf(null, x -> true));
        assertTrue(DeviceNodeLabels.booleanOf(device("PLC"), x -> true));
        assertFalse(DeviceNodeLabels.booleanOf(device("PLC"), x -> null));
    }

    @Test
    void deletionMessagePicksSingularOrPluralByCount() {
        CommConfigData unGrupo = communication(1, 1, 1);
        String text = DeviceNodeLabels.deletionMessage("Compresor 1", unGrupo);

        assertTrue(text.contains("1 grupo\n"), "un grupo va en singular: " + text);
        assertFalse(text.contains("1 grupos"), "no puede decir '1 grupos': " + text);
        assertTrue(text.contains("1 \u00e1rea"), "un area va en singular: " + text);
        assertFalse(text.contains("1 \u00e1reas"), "no puede decir '1 áreas': " + text);
        assertTrue(text.contains("1 variable\n"), "una variable va en singular: " + text);
        assertFalse(text.contains("1 variables"), "no puede decir '1 variables': " + text);
    }

    @Test
    void withSeveralItemsItIsPlural() {
        String text = DeviceNodeLabels.deletionMessage("Compresor 1", communication(3, 2, 7));

        assertTrue(text.contains("3 grupos"), text);
        assertTrue(text.contains("2 \u00e1reas"), text);
        assertTrue(text.contains("7 variables"), text);
    }

    @Test
    void deviceWithNothingToLoseDoesNotListZeros() {
        String text = DeviceNodeLabels.deletionMessage("PLC", communication(0, 0, 0));

        assertFalse(text.contains("  \u2022 0 grupos"), "no tiene sentido listar ceros: " + text);
        assertTrue(text.contains("Va a eliminar el dispositivo 'PLC'."), text);
        assertTrue(text.contains("\u00bfDesea continuar?"), text);
    }

    @Test
    void deletionMessageWarnsThatBootDbIsAlsoTouched() {
        String text = DeviceNodeLabels.deletionMessage("PLC", communication(1, 0, 0));

        assertTrue(text.contains("comunicacion.xml"), text);
        assertTrue(text.contains("boot.db"), text);
        assertTrue(text.contains("Al borrarlo se pierden todas sus configuraciones."), text);
    }

    @Test
    void deletionMessageHandlesNullComms() {
        String text = DeviceNodeLabels.deletionMessage("PLC", null);

        assertTrue(text.contains("Va a eliminar el dispositivo 'PLC'."), text);
        assertFalse(text.contains("\u2022"), "sin comunicacion no hay nada que listar: " + text);
    }

    @Test
    void warningMessagesNameTheDevice() {
        assertTrue(DeviceNodeLabels.unknownDeviceMessage("PLC")
                .contains("'PLC'"));
        assertTrue(DeviceNodeLabels.partialSaveMessage("PLC").contains("boot.db"));
        assertTrue(DeviceNodeLabels.correctDeletionMessage("PLC").contains("correctamente"));
        assertTrue(DeviceNodeLabels.nothingToDeleteMessage("PLC").contains("No se encontr"));
    }

    private static DeviceConfigData device(String name) {
        return PropertiesDevice(name, null, null, null);
    }

    private static DeviceConfigData PropertiesDevice(String name, String marca, String modelo,
            String protocolo) {
        Properties p = new Properties();
        put(p, "deviceName", name);
        put(p, "brand", marca);
        put(p, "model", modelo);
        put(p, "protocol", protocolo);
        return new DeviceConfigData(p);
    }

    private static void put(Properties p, String clave, String valor) {
        if (valor != null) {
            p.put(clave, valor);
        }
    }

    private static CommConfigData communication(int grupos, int areas, int variables) {
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
            listaPvs.add(new CommConfigData.PvConfig("p" + i, "P" + i, "INT", "A0", "0", "d",
                    "500", true, true, "0", "1", "d", "%.2f", "u", "0", "1", "1", "m", ""));
        }
        return new CommConfigData("PLC", listaGrupos, listaAreas, listaPvs);
    }
}