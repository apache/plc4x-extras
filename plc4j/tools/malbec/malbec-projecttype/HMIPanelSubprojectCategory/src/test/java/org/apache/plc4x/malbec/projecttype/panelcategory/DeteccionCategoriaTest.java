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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeteccionCategoriaTest {

    @Test
    void projectWithoutMarkerDetectsNothing() {
        ProjectTree raiz = folder("llenadora",
                file("README.txt"),
                folder("documentacion", file("notas.md")));

        Optional<String> detectado = DeteccionCategoria.detect(raiz);

        assertTrue(detectado.isEmpty(),
                "H-05: un proyecto sin marcador no debe devolver nada; antes devolvia null"
                        + " y el switch de getActions lanzaba NullPointerException");
    }

    @Test
    void emptyProjectNeitherThrowsNorDetects() {
        Optional<String> detectado = DeteccionCategoria.detect(folder("vacio"));

        assertTrue(detectado.isEmpty());
    }

    @Test
    void nullRootDoesNotThrow() {
        assertTrue(DeteccionCategoria.detect(null).isEmpty(),
                "una raiz nula se resuelve como ausencia de categoria, no como excepcion");
    }

    @Test
    void detectaElMarcadorEnLaRaiz() {
        ProjectTree raiz = folder("llenadora", file("comunicacion.xml"));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detect(raiz));
    }

    @Test
    void detectaElMarcadorEnUnSubdirectorio() {
        ProjectTree raiz = folder("llenadora",
                folder("config", folder("anidado", file("comunicacion.xml"))));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detect(raiz),
                "el marcador puede estar anidado, como permitia el codigo anterior");
    }

    @Test
    void markerWinsByPriorityOrder() {
        ProjectTree raiz = folder("llenadora",
                file("base.rcp"),
                file("template.bob"),
                file("comunicacion.xml"));

        assertEquals(Optional.of("template.bob"), DeteccionCategoria.detect(raiz),
                "template.bob va primero en la lista y debe ganar");
    }

    @Test
    void lookupIsCaseInsensitive() {
        ProjectTree raiz = folder("llenadora", file("Comunicacion.XML"));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detect(raiz),
                "el codigo anterior comparaba con equalsIgnoreCase");
    }

    @Test
    void folderDoesNotCountAsMarker() {
        ProjectTree raiz = folder("llenadora", folder("comunicacion.xml"));

        assertTrue(DeteccionCategoria.detect(raiz).isEmpty(),
                "una carpeta con el nombre del marcador no es el fichero marcador");
    }

    @Test
    void deepTreeDoesNotExhaustTheStack() {

        ProjectTree actual = folder("nivel-19999");
        for (int i = 19_998; i >= 0; i--) {
            actual = new node("nivel-" + i, true, List.of(actual));
        }

        assertTrue(DeteccionCategoria.detect(actual).isEmpty(),
                "un arbol muy profundo se recorre sin desbordar la pila");
    }

    @Test
    void recorreElArbolUnaSolaVez() {
        CountingTree raiz = new CountingTree("llenadora", List.of(
                new CountingTree("config", List.of(new CountingTree("comunicacion.xml", List.of()))),
                new CountingTree("datos", List.of(new CountingTree("datos.bin", List.of())))));

        DeteccionCategoria.detect(raiz);

        assertEquals(1, raiz.visitas,
                "H-05: antes se recorria el arbol entero una vez por cada fichero marcador");
    }

    @Test
    void allTenMarkersAreStillCovered() {
        for (String marcador : DeteccionCategoria.FICHEROS_MARCADOR) {
            ProjectTree raiz = folder("llenadora",
                    folder("sub", file(marcador)));

            assertEquals(Optional.of(marcador), DeteccionCategoria.detect(raiz),
                    "el marcador " + marcador + " debe seguir detectandose");
        }
    }

    private static ProjectTree folder(String name, ProjectTree... children) {
        return new node(name, true, List.of(children));
    }

    private static ProjectTree file(String name) {
        return new node(name, false, List.of());
    }

    private record node(String name, boolean folder, List<ProjectTree> children) implements ProjectTree {

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isFolder() {
            return folder;
        }

        @Override
        public List<ProjectTree> children() {
            return children;
        }
    }

    private static final class CountingTree implements ProjectTree {

        private final String name;
        private final List<ProjectTree> children;
        private int visitas;

        CountingTree(String name, List<ProjectTree> children) {
            this.name = name;
            this.children = new ArrayList<>(children);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isFolder() {
            return !children.isEmpty();
        }

        @Override
        public List<ProjectTree> children() {
            visitas++;
            return children;
        }
    }

    @Test
    void categoryIsRecognisedByNameEvenWithoutMarker() {
        ProjectTree raiz = folder("Comunicacion", file("README.txt"));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detect(raiz),
                "borrar comunicacion.xml no puede dejar la categoria sin acciones");
    }

    @Test
    void withoutNameNorMarkerThereIsNoCategory() {
        ProjectTree raiz = folder("llenadora", file("README.txt"));

        assertEquals(Optional.empty(), DeteccionCategoria.detect(raiz),
                "una carpeta cualquiera no es una categoria");
    }

    @Test
    void markerStillWinsOverTheName() {
        ProjectTree raiz = folder("Gestion de Avisos", file("comunicacion.xml"));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detect(raiz));
    }
}