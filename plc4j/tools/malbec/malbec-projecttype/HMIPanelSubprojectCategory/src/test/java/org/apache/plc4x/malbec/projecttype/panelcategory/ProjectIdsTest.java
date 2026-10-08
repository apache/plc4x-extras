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

/**
 * Los identificadores compartidos con NetBeans (T4.4).
 *
 * <p>Dos comprobaciones. La primera fija los valores, porque cambiarlos deja de reconocer los
 * proyectos que un usuario ya tiene en disco. La segunda es la que evita que el problema
 * vuelva: si alguien escribe {@code "category.cfg"} a mano, el test lo dice.
 */
class ProjectIdsTest {

    /**
     * Valores que no se pueden cambiar sin romper los proyectos existentes.
     *
     * <p>Un proyecto creado antes sigue buscando estos nombres en disco. Si cambian, NetBeans
     * deja de encontrar la categoria y el proyecto aparece vacio.
     */
    @Test
    void losValoresNoCambian() {
        assertEquals("org-apache-plc4x-category", ProjectIds.CATEGORY_PROJECT_TYPE);
        assertEquals("org-apache-plc4x-panel", ProjectIds.PANEL_PROJECT_TYPE);
        assertEquals("category.cfg", ProjectIds.CATEGORY_MARKER_FILE);
        assertEquals("comunicacion.xml", ProjectIds.COMMUNICATION_CONFIG_FILE);
    }

    @Test
    void lasConstantesSirvenParaLasAnotaciones() {
        // Un valor de anotacion tiene que ser una expresion constante en tiempo de compilacion.
        // Esta comprobacion falla al compilar si alguna deja de ser static final String con
        // literal, que es justo lo que haria que las anotaciones no compilaran.
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

    /**
     * Solo se considera infraccion un literal que sea <em>exactamente</em> el identificador.
     *
     * <p>Un aviso al usuario como "se guardo en comunicacion.xml pero no se pudo actualizar
     * boot.db" lleva el nombre dentro de un texto mas largo, y ahi conviene que siga escrito
     * asi de legible: convertirlo en concatenacion estropearia el mensaje sin evitar ningun
     * error. Lo que si es un error es que el identificador aparezca suelto, porque eso es
     * justo lo que se esta comparando.
     */
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