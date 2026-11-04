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

final class DeteccionCategoria {

    static final List<String> FICHEROS_MARCADOR = List.of(
            "template.bob", ProjectIds.COMMUNICATION_CONFIG_FILE, "base.oppc", "record.record",
            "base.rcp", "base.rpt", "base.scp", "base.wmg", "base.tlc", "base.rtum");

    private DeteccionCategoria() {
    }

    static Optional<String> detect(ProjectTree raiz) {
        if (raiz == null) {
            return Optional.empty();
        }
        Set<String> names = new HashSet<>();
        recorrer(raiz, names);
        Optional<String> porMarcador = FICHEROS_MARCADOR.stream()
                .filter(marcador -> names.contains(normalizar(marcador)))
                .findFirst();
        if (porMarcador.isPresent()) {
            return porMarcador;
        }
        return HMICategoryDefinition.byFolderName(raiz.name())
                .map(HMICategoryDefinition::detectionKey)
                .filter(clave -> clave != null);
    }

    private static void recorrer(ProjectTree raiz, Set<String> names) {
        Deque<ProjectTree> pendientes = new ArrayDeque<>();
        pendientes.push(raiz);
        while (!pendientes.isEmpty()) {
            ProjectTree actual = pendientes.pop();
            if (actual.isFolder()) {
                for (ProjectTree hijo : actual.children()) {
                    if (hijo != null) {
                        pendientes.push(hijo);
                    }
                }
            } else {
                names.add(normalizar(actual.name()));
            }
        }
    }

    private static String normalizar(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
