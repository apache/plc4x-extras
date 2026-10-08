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
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Comprime una carpeta en un ZIP.
 *
 * <p>Estaba como clase interna de {@link HMIPanelCompileAction}, con un metodo estatico:
 * una combinacion que no se puede instanciar y que hacia imposible probarla sin montar la
 * accion. Aqui es una clase normal y tiene sus tests.
 *
 * <p>Se recorre con {@link Files#walkFileTree} y no con {@code Files.walk}, porque el
 * arbol de ficheros se cierra solo al terminar la visita mientras que el flujo de
 * {@code walk} hay que cerrarlo a mano. Sin ese cierre, en Windows el directorio de origen
 * se queda con un descriptor abierto y no se puede renombrar ni borrar hasta que la
 * aplicacion termine.
 */
final class ZipUtils {

    private ZipUtils() {
    }

    /**
     * Comprime el contenido de {@code origen} en {@code destino}.
     *
     * <p>Se conservan las carpetas vacias, porque un panel puede tener una carpeta de
     * figuras sin ninguna y perderla cambia lo que ve el operador al descomprimir.
     *
     * @param origen  carpeta whose contents go into the ZIP
     * @param destino fichero ZIP a escribir, que se crea o se sobrescribe
     * @throws IOException si no se puede leer el origen o escribir el destino
     */
    static void zipFolder(Path origen, Path destino) throws IOException {
        try (OutputStream salida = Files.newOutputStream(destino);
                ZipOutputStream zos = new ZipOutputStream(salida)) {

            Files.walkFileTree(origen, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    // El directorio raiz no se mete: dentro del ZIP los ficheros van en la
                    // raiz y no bajo el nombre de la carpeta.
                    if (!dir.equals(origen)) {
                        String relativa = relativa(origen, dir) + "/";
                        zos.putNextEntry(new ZipEntry(relativa));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    zos.putNextEntry(new ZipEntry(relativa(origen, file)));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /** Ruta dentro del ZIP, siempre con barras normales y sin directorio inicial. */
    private static String relativa(Path origen, Path hijo) {
        return origen.relativize(hijo).toString().replace('\\', '/');
    }
}