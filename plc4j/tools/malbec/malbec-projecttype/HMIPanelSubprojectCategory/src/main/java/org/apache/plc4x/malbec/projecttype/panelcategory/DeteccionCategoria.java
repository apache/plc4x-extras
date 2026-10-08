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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.plc4x.malbec.projecttype.panelcategory.ProjectIds;

/**
 * Deteccion del fichero marcador que identifica la categoria de un proyecto (H-05).
 *
 * <p>Dos correcciones respecto a como estaba:
 *
 * <ul>
 *   <li>Devuelve {@link Optional}, nunca {@code null}. El codigo anterior hacia
 *       {@code switch} sobre un {@code String} que podia ser nulo, y en Java eso
 *       lanza {@code NullPointerException}: la rama {@code default} no cubre el
 *       {@code null}. Se reproducia al hacer clic derecho sobre un proyecto sin
 *       fichero marcador.</li>
 *   <li><b>Una sola pasada</b> en vez de diez. Antes se llamaba a una busqueda
 *       recursiva por cada fichero marcador, y cada busqueda recorria el arbol
 *       entero. Ahora se recogen todos los nombres en una vuelta con un iterador
 *       explicito, y se decide al final.</li>
 * </ul>
 *
 * <p>El recorrido usa una pila en lugar de recursividad, para que un arbol muy
 * profundo no agote la pila.
 */
final class DeteccionCategoria {

    /**
     * Ficheros que identifican una categoria, en orden de prioridad: gana el
     * primero que aparezca, igual que antes.
     */
    static final List<String> FICHEROS_MARCADOR = List.of(
            "template.bob", ProjectIds.COMMUNICATION_CONFIG_FILE, "base.oppc", "record.record",
            "base.rcp", "base.rpt", "base.scp", "base.wmg", "base.tlc", "base.rtum");

    private DeteccionCategoria() {
    }

    /**
     * Primer fichero marcador presente en el arbol, si lo hay.
     *
     * @param raiz carpeta del proyecto
     * @return el nombre del fichero marcador, o vacio si el proyecto no es de ninguna
     *         categoria reconocida
     */
    static Optional<String> detectar(ArbolProyecto raiz) {
        if (raiz == null) {
            return Optional.empty();
        }
        Set<String> nombres = new HashSet<>();
        recorrer(raiz, nombres);
        return FICHEROS_MARCADOR.stream()
                .filter(marcador -> nombres.contains(normalizar(marcador)))
                .findFirst();
    }

    private static void recorrer(ArbolProyecto raiz, Set<String> nombres) {
        Deque<ArbolProyecto> pendientes = new ArrayDeque<>();
        pendientes.push(raiz);
        while (!pendientes.isEmpty()) {
            ArbolProyecto actual = pendientes.pop();
            if (actual.esCarpeta()) {
                for (ArbolProyecto hijo : actual.hijos()) {
                    if (hijo != null) {
                        pendientes.push(hijo);
                    }
                }
            } else {
                // Solo cuentan los ficheros: una carpeta llamada comunicacion.xml no es
                // el fichero marcador.
                nombres.add(normalizar(actual.nombre()));
            }
        }
    }

    private static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
    }
}
