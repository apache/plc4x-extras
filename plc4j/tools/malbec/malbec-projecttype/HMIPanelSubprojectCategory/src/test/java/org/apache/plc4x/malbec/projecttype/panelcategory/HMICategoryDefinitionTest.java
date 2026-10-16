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
package org.apache.plc4x.malbec.projecttype.panelcategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.HMICategoryCreateDisplayAction;
import org.junit.jupiter.api.Test;

public class HMICategoryDefinitionTest {

    private static final Map<HMICategoryDefinition, String> NOMBRES_DE_CARPETA_EN_DISCO = new LinkedHashMap<>();

    static {
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.COMUNICATION, "Comunicacion");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.NOTICE_MANAGEMENT, "Gestion de Avisos");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.RECIPE, "Recetas");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.HISTORIAL, "Historial");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.SCRIPTS, "Scripts");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.REPORT, "Informes");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.TEXT_GRAPHIC, "Texto y Lista de graficos");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.ADMIN_USER, "Administracion de Usuarios runtime");
        NOMBRES_DE_CARPETA_EN_DISCO.put(HMICategoryDefinition.CONFIG_PANEL, "Configuracion de panel de operador");
    }

    @Test
    public void allEnumIconsExist() {
        for (HMICategoryDefinition definition : HMICategoryDefinition.values()) {
            URL icono = HMICategoryDefinition.class.getResource("/" + definition.getIconPath());
            assertNotNull(icono, "icono ausente para " + definition.name() + ": " + definition.getIconPath());
        }
    }

    @Test
    public void displayTemplateExists() {
        URL plantilla = HMICategoryDefinition.class
                .getResource("/" + HMICategoryCreateDisplayAction.DISPLAY_TEMPLATE_PATH);
        assertNotNull(plantilla, "plantilla ausente: " + HMICategoryCreateDisplayAction.DISPLAY_TEMPLATE_PATH);
    }

    @Test
    public void folderNamesMatchTheOnesOnDisk() {
        for (Map.Entry<HMICategoryDefinition, String> entrada : NOMBRES_DE_CARPETA_EN_DISCO.entrySet()) {
            assertEquals(entrada.getValue(), entrada.getKey().getDisplayName(),
                    "el NodeFactory de " + entrada.getKey().name()
                            + " no encontrara la carpeta '" + entrada.getValue() + "'");
        }
    }

    @Test
    public void constantsDoNotRepeatFolder() {
        assertEquals(NOMBRES_DE_CARPETA_EN_DISCO.size(),
                NOMBRES_DE_CARPETA_EN_DISCO.values().stream().distinct().count(),
                "dos constantes apuntan a la misma carpeta: sus NodeFactory colisionarian");
    }
}
