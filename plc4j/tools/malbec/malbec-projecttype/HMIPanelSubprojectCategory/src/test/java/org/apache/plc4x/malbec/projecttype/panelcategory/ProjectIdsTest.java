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
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProjectIdsTest {

    @Test
    void losValoresNoCambian() {
        assertEquals("org-apache-plc4x-category", ProjectIds.CATEGORY_PROJECT_TYPE);
        assertEquals("org-apache-plc4x-panel", ProjectIds.PANEL_PROJECT_TYPE);
        assertEquals("category.cfg", ProjectIds.CATEGORY_MARKER_FILE);
        assertEquals("comunicacion.xml", ProjectIds.COMMUNICATION_CONFIG_FILE);
    }

    @Test
    void lasConstantesSirvenParaLasAnotaciones() {
        assertTrue(esConstanteEnTiempoDeCompilacion("CATEGORY_PROJECT_TYPE"));
        assertTrue(esConstanteEnTiempoDeCompilacion("PANEL_PROJECT_TYPE"));
    }

    private static boolean esConstanteEnTiempoDeCompilacion(String nombre) {
        try {
            java.lang.reflect.Field campo = ProjectIds.class.getField(nombre);
            return java.lang.reflect.Modifier.isStatic(campo.getModifiers())
                    && java.lang.reflect.Modifier.isFinal(campo.getModifiers())
                    && campo.getType() == String.class;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Test
    void losIdentificadoresNoVuelvenAFueraDeProjectIds() throws IOException {
        List<String> infracciones = new ArrayList<>();
        Path raiz = Path.of(System.getProperty("basedir", "."), "src", "main", "java");
        java.util.regex.Pattern literal = java.util.regex.Pattern.compile("\"([^\"]*)\"");

        try (Stream<Path> archivos = Files.walk(raiz)) {
            for (Path p : archivos.filter(x -> x.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("ProjectIds.java")) {
                    continue;
                }
                List<String> lineas = Files.readAllLines(p);
                for (int i = 0; i < lineas.size(); i++) {
                    String limpia = lineas.get(i).trim();
                    if (limpia.startsWith("//") || limpia.startsWith("*")
                            || limpia.startsWith("/*")) {
                        continue;
                    }
                    java.util.regex.Matcher m = literal.matcher(limpia);
                    while (m.find()) {
                        String valor = m.group(1);
                        if (identificadores().contains(valor)) {
                            infracciones.add(p.getFileName() + ":" + (i + 1)
                                    + "  usa \"" + valor + "\" a mano  " + limpia);
                        }
                    }
                }
            }
        }
        assertTrue(infracciones.isEmpty(),
                "T4.4: los identificadores compartidos deben salir de ProjectIds.\n  "
                        + String.join("\n  ", infracciones));
    }

    private static List<String> identificadores() {
        return List.of(ProjectIds.CATEGORY_PROJECT_TYPE, ProjectIds.PANEL_PROJECT_TYPE,
                ProjectIds.CATEGORY_MARKER_FILE, ProjectIds.COMMUNICATION_CONFIG_FILE);
    }
}