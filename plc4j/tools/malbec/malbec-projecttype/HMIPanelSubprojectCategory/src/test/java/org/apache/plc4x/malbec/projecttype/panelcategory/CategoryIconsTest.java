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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CategoryIconsTest {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};

    @Test
    void lasRutasDeclaradasResuelvenAUnPngReal() {
        for (String ruta : List.of(CategoryIcons.DEFAULT, CategoryIcons.COMMUNICATION)) {
            URL recurso = recurso(ruta);
            assertNotNull(recurso, "no existe el icono: " + ruta);
            assertArrayEquals(FIRMA_PNG, primerosBytes(recurso, 4),
                    "el fichero de " + ruta + " no es un PNG valido");
        }
    }

    @Test
    void elIconoDeCadaCategoriaExiste() {
        for (HMICategoryDefinition definicion : HMICategoryDefinition.values()) {
            URL recurso = recurso(definicion.getIconPath());
            assertNotNull(recurso,
                    "el icono de " + definicion.name() + " no existe: " + definicion.getIconPath());
        }
    }

    @Test
    void ningunaRutaDeIconoDelModuloEstaRota() throws IOException {
        Map<String, String> rutas = rutasDeIconoDeclaradas();
        assertTrue(!rutas.isEmpty(),
                "el recorrido deberia encontrar al menos una ruta .png; si no encuentra "
                        + "ninguna, el test esta pasando sin comprobar nada");

        List<String> rotas = new ArrayList<>();
        for (Map.Entry<String, String> entrada : rutas.entrySet()) {
            String ruta = entrada.getKey();
            URL recurso = recurso(ruta);
            if (recurso == null) {
                rotas.add(entrada.getValue() + " -> " + ruta + " (no existe en el classpath)");
            } else if (ruta.startsWith("/")) {
                rotas.add(entrada.getValue() + " -> " + ruta + " (no debe empezar por barra)");
            }
        }
        assertTrue(rotas.isEmpty(), "Rutas de icono rotas:\n  " + String.join("\n  ", rotas));
    }

    /** Recorre el codigo y devuelve ruta -> "fichero:linea". */
    private static Map<String, String> rutasDeIconoDeclaradas() throws IOException {
        Pattern literal = Pattern.compile("\"([^\"]*\\.png)\"");
        Map<String, String> encontradas = new LinkedHashMap<>();
        Path raiz = Path.of(System.getProperty("basedir", "."), "src", "main", "java");
        try (Stream<Path> archivos = Files.walk(raiz)) {
            for (Path p : archivos.filter(x -> x.toString().endsWith(".java")).toList()) {
                List<String> lineas = Files.readAllLines(p);
                for (int i = 0; i < lineas.size(); i++) {
                    String limpia = lineas.get(i).trim();
                    if (limpia.startsWith("//") || limpia.startsWith("*")) {
                        continue;
                    }
                    Matcher m = literal.matcher(limpia);
                    while (m.find()) {
                        encontradas.putIfAbsent(m.group(1), p.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
        return encontradas;
    }

    private static URL recurso(String ruta) {

        return CategoryIconsTest.class.getResource(
                ruta.startsWith("/") ? ruta : "/" + ruta);
    }

    private static byte[] primerosBytes(URL recurso, int cuantos) {
        try (InputStream in = recurso.openStream()) {
            return in.readNBytes(cuantos);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + recurso, e);
        }
    }
}