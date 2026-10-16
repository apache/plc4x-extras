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
package org.apache.plc4x.malbec.projecttype.panel.action;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipUtilsTest {

    private static Set<String> entries(Path zip) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String content(Path zip, String entrada) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entrada.equals(entry.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("no esta la entrada " + entrada);
    }

    @Test
    void comprimeLosFicherosConservandoLaEstructuraDeCarpetas(@TempDir Path origen) throws IOException {
        Files.createDirectories(origen.resolve("Image/CELDA1"));
        Files.writeString(origen.resolve("panel.cfg"), "contenido del panel");
        Files.writeString(origen.resolve("Image/CELDA1/CELDA1.bob"), "plantilla");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertEquals(Set.of("panel.cfg", "Image/", "Image/CELDA1/", "Image/CELDA1/CELDA1.bob"),
                entries(zip));
    }

    @Test
    void conservaLasCarpetasVacias(@TempDir Path origen) throws IOException {
        Files.createDirectories(origen.resolve("Image/SIN_NADA_QUE_MOSTRAR"));
        Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertTrue(entries(zip).contains("Image/SIN_NADA_QUE_MOSTRAR/"),
                "una carpeta vacia tambien es parte del panel");
    }

    @Test
    void elContenidoLlegaIntacto(@TempDir Path origen) throws IOException {
        String text = "uuid=abc-123\nenable=TRUE\n";
        Files.writeString(origen.resolve("comunicacion.xml"), text);
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertEquals(text, content(zip, "comunicacion.xml"));
    }

    @Test
    void losFicherosNoQuedanBajoElNombreDeLaCarpetaDeOrigen(@TempDir Path origen) throws IOException {
        Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        for (String entrada : entries(zip)) {
            assertTrue(!entrada.startsWith(origen.getFileName().toString()),
                    "el ZIP no debe repetir la carpeta de origen en cada ruta: " + entrada);
        }
    }

    @Test
    void unaCarpetaVaciaProduceUnZipSinEntradas(@TempDir Path origen) throws IOException {
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertTrue(Files.size(zip) > 0, "un ZIP valido, aunque este vacio");
        assertTrue(entries(zip).isEmpty());
    }

    @Test
    void elOrigenQuedaLibreParaBorrarse(@TempDir Path origen) throws IOException {
        Path file = Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        Files.delete(file);
        assertDoesNotThrowOnWindows(() -> Files.delete(origen),
                "el ZIP se cerro mal y la carpeta de origen sigue bloqueada");
    }

    @Test
    void elOrigenSePuedeRenombrar(@TempDir Path origen) throws IOException {
        Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        Path destino = origen.resolveSibling("renombrada-" + java.util.UUID.randomUUID());
        assertDoesNotThrowOnWindows(() -> Files.move(origen, destino),
                "no se pudo renombrar la carpeta de origen: sigue abierta");
    }

    private static void assertDoesNotThrowOnWindows(IoRunnable accion, String mensaje) {
        try {
            accion.run();
        } catch (IOException ex) {
            throw new AssertionError(mensaje, ex);
        }
    }

    private interface IoRunnable {

        void run() throws IOException;
    }

    @Test
    void unaRutaInexistenteFalla(@TempDir Path temp) throws IOException {
        Path zip = Files.createTempFile("panel", ".zip");

        assertThrows(IOException.class, () -> ZipUtils.zipFolder(temp.resolve("no-existe"), zip),
                "comprimir algo que no esta tiene que decirse");
    }

    @Test
    void elZipDestinoNoSeComprimeASiMismo(@TempDir Path panel) throws Exception {
        Files.createDirectories(panel.resolve("Image"));
        Files.writeString(panel.resolve("panel.cfg"), "contenido");
        Files.writeString(panel.resolve("Image/CELDA1.bob"), "plantilla");
        Path destino = panel.resolve("respaldo.zip");

        ZipUtils.zipFolder(panel, destino);

        Set<String> entries = entries(destino);
        assertFalse(entries.contains("respaldo.zip"),
                "el zip no puede contenerse: " + entries);
        assertEquals(Set.of("panel.cfg", "Image/", "Image/CELDA1.bob"), entries);
    }

    @Test
    void elZipDeFueraDelPanelTampocoSeArrastra(@TempDir Path panel) throws Exception {
        Files.writeString(panel.resolve("panel.cfg"), "contenido");
        Path destino = Files.createTempFile("respaldo", ".zip");

        ZipUtils.zipFolder(panel, destino);

        assertEquals(Set.of("panel.cfg"), entries(destino));
    }
}