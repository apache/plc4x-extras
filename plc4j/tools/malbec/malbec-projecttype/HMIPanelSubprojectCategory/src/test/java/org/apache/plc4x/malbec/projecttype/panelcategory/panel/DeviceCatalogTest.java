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

class DeviceCatalogTest {

    @Test
    void theThreeBrandsAreStillThere() {
        assertEquals(List.of("Siemens", "Allen Bradley", "Modbus"), DeviceCatalog.brands(),
                "las marcas que se ven en el desplegable no deben cambiar sin avisar");
    }

    @Test
    void eachBrandHasItsModels() {
        assertEquals(4, DeviceCatalog.from("Siemens").size());
        assertEquals(2, DeviceCatalog.from("Allen Bradley").size());
        assertEquals(3, DeviceCatalog.from("Modbus").size());
    }

    @Test
    void modelCarriesItsProtocol() {
        DeviceModel s7 = DeviceCatalog.from("Siemens").stream()
                .filter(m -> "s7-1500".equals(m.getModel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta el S7-1500"));

        assertEquals("Siemens", s7.getBrand());
        assertEquals("s7", s7.getProtocol());
    }

    @Test
    void unknownBrandDoesNotBreak() {
        assertTrue(DeviceCatalog.from("Marca Inventada").isEmpty(),
                "una marca que no existe devuelve vacio, no revienta");
        assertFalse(DeviceCatalog.hasBrand("Marca Inventada"));
    }

    @Test
    void allCatalogBrandsExist() {
        for (String brand : DeviceCatalog.brands()) {
            assertTrue(DeviceCatalog.hasBrand(brand), "falta " + brand);
            assertFalse(DeviceCatalog.from(brand).isEmpty(), "la marca " + brand + " no tiene modelos");
        }
    }

@Test
void protocolsAreTheExpectedOnes() {
    assertEquals(List.of("s7", "s7", "s7", "s7"),
            protocolsOf("Siemens"), "los S7 speaks S7");
    assertEquals(List.of("EtherNet/IP", "EtherNet/IP"), protocolsOf("Allen Bradley"));
    assertEquals(List.of("modbus-tcp", "modbus-rtu", "modbus-ascii"),
            protocolsOf("Modbus"), "cada variante de Modbus va por su sitio");
}

private static List<String> protocolsOf(String brand) {
    return DeviceCatalog.from(brand).stream().map(DeviceModel::getProtocol).toList();
}

@Test
void noModelIsLeftWithoutProtocol() {
        for (String brand : DeviceCatalog.brands()) {
            for (DeviceModel model : DeviceCatalog.from(brand)) {
                assertFalse(model.getProtocol() == null || model.getProtocol().isBlank(),
                        model + " se quedo sin protocolo");
            }
        }
    }
}