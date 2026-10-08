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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La compresion del panel en ZIP.
 *
 * <p>Estaba dentro de la accion como clase interna, asi que no se podia probar sin
 * montar Swing. Estos tests fijan lo que importa de verdad: que el contenido llegue con
 * su estructura y sus bytes intactos, porque un ZIP mal hecho no falla al crearse sino
 * cuando el operador lo abre en otra maquina.
 */
class ZipUtilsTest {

    /** Nombres de las entradas del ZIP, en el orden en que salen. */
    private static Set<String> entradas(Path zip) throws IOException {
        Set<String> nombres = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                nombres.add(entry.getName());
            }
        }
        return nombres;
    }

    private static String contenido(Path zip, String entrada) throws IOException {
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
                entradas(zip));
    }

    /**
     * Las carpetas vacias se conservan.
     *
     * <p>Un panel puede tener una carpeta de figuras sin ninguna. Si al comprimir
     * desaparece, al descomprimir en el equipo del operador la carpeta no esta y el panel
     * avisa de figuras que faltan.
     */
    @Test
    void conservaLasCarpetasVacias(@TempDir Path origen) throws IOException {
        Files.createDirectories(origen.resolve("Image/SIN_NADA_QUE_MOSTRAR"));
        Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertTrue(entradas(zip).contains("Image/SIN_NADA_QUE_MOSTRAR/"),
                "una carpeta vacia tambien es parte del panel");
    }

    @Test
    void elContenidoLlegaIntacto(@TempDir Path origen) throws IOException {
        String texto = "uuid=abc-123\nenable=TRUE\n";
        Files.writeString(origen.resolve("comunicacion.xml"), texto);
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertEquals(texto, contenido(zip, "comunicacion.xml"));
    }

    /** El directorio raiz no se mete como carpeta: los ficheros van en la raiz del ZIP. */
    @Test
    void losFicherosNoQuedanBajoElNombreDeLaCarpetaDeOrigen(@TempDir Path origen) throws IOException {
        Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        for (String entrada : entradas(zip)) {
            assertTrue(!entrada.startsWith(origen.getFileName().toString()),
                    "el ZIP no debe repetir la carpeta de origen en cada ruta: " + entrada);
        }
    }

    @Test
    void unaCarpetaVaciaProduceUnZipSinEntradas(@TempDir Path origen) throws IOException {
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        assertTrue(Files.size(zip) > 0, "un ZIP valido, aunque este vacio");
        assertTrue(entradas(zip).isEmpty());
    }

    /**
     * El origen queda libre en Windows.
     *
     * <p>La version anterior recorria con {@code Files.walk} sin cerrar el flujo, y eso
     * dejaba un descriptor abierto sobre el arbol de ficheros. En Windows el resultado es
     * que la carpeta no se puede renombrar ni borrar hasta que la aplicacion termina.
     * Este test falla si el descriptor sigue abierto.
     */
    @Test
    void elOrigenQuedaLibreParaBorrarse(@TempDir Path origen) throws IOException {
        Path fichero = Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        // Se borra primero el fichero y despues la carpeta, para que el fallo que
        // interesa sea el de la carpeta y no un "no esta vacia" que no dice nada.
        Files.delete(fichero);
        assertDoesNotThrowOnWindows(() -> Files.delete(origen),
                "el ZIP se cerro mal y la carpeta de origen sigue bloqueada");
    }

    /** Renombrar la carpeta tambien falla si queda un descriptor abierto sobre ella. */
    @Test
    void elOrigenSePuedeRenombrar(@TempDir Path origen) throws IOException {
        Files.writeString(origen.resolve("panel.cfg"), "x");
        Path zip = Files.createTempFile("panel", ".zip");

        ZipUtils.zipFolder(origen, zip);

        // Nombre unico: si se dejase el resultado de una ejecucion anterior en TEMP, la
        // segunda vez fallaria con FileAlreadyExistsException y parecería que la carpeta
        // estaba bloqueada cuando lo que se chocaba era el nombre.
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

    /** Una ruta que no existe falla en vez de dejar un ZIP vacio sin avisar. */
    @Test
    void unaRutaInexistenteFalla(@TempDir Path temp) throws IOException {
        Path zip = Files.createTempFile("panel", ".zip");

        assertThrows(IOException.class, () -> ZipUtils.zipFolder(temp.resolve("no-existe"), zip),
                "comprimir algo que no esta tiene que decirse");
    }
}