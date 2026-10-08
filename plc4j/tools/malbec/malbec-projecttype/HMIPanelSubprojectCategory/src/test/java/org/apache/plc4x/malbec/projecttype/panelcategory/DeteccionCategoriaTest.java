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

/**
 * Deteccion del fichero marcador de categoria (H-05).
 *
 * <p>El defecto era un {@code NullPointerException}: {@code findExistingCategoryFile()}
 * devolvia {@code null} si no encontraba ningun marcador y {@code getActions()} hacia
 * {@code switch} sobre ese {@code String}. En Java, cambiar un {@code String} nulo
 * lanza {@code NullPointerException} y la rama {@code default} no lo evita, asi que
 * se reproducia al hacer clic derecho sobre un proyecto sin marcador.
 *
 * <p>La deteccion se extrajo a {@link DeteccionCategoria} sobre {@link ArbolProyecto}
 * precisamente para poder verificarla: en este build no hay ningun
 * {@code FileSystem} registrado, de modo que un {@code FileObject} real no se puede
 * construir.
 */
class DeteccionCategoriaTest {

    @Test
    void unProyectoSinMarcadorNoDetectaNada() {
        ArbolProyecto raiz = carpeta("llenadora",
                fichero("README.txt"),
                carpeta("documentacion", fichero("notas.md")));

        Optional<String> detectado = DeteccionCategoria.detectar(raiz);

        assertTrue(detectado.isEmpty(),
                "H-05: un proyecto sin marcador no debe devolver nada; antes devolvia null"
                        + " y el switch de getActions lanzaba NullPointerException");
    }

    @Test
    void unProyectoVacioNoLanzaNiDetecta() {
        Optional<String> detectado = DeteccionCategoria.detectar(carpeta("vacio"));

        assertTrue(detectado.isEmpty());
    }

    @Test
    void unaRaizNulaNoLanza() {
        assertTrue(DeteccionCategoria.detectar(null).isEmpty(),
                "una raiz nula se resuelve como ausencia de categoria, no como excepcion");
    }

    @Test
    void detectaElMarcadorEnLaRaiz() {
        ArbolProyecto raiz = carpeta("llenadora", fichero("comunicacion.xml"));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detectar(raiz));
    }

    @Test
    void detectaElMarcadorEnUnSubdirectorio() {
        ArbolProyecto raiz = carpeta("llenadora",
                carpeta("config", carpeta("anidado", fichero("comunicacion.xml"))));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detectar(raiz),
                "el marcador puede estar anidado, como permitia el codigo anterior");
    }

    @Test
    void elMarcadorGanaPorOrdenDePrioridad() {
        ArbolProyecto raiz = carpeta("llenadora",
                fichero("base.rcp"),
                fichero("template.bob"),
                fichero("comunicacion.xml"));

        assertEquals(Optional.of("template.bob"), DeteccionCategoria.detectar(raiz),
                "template.bob va primero en la lista y debe ganar");
    }

    @Test
    void laBusquedaNoDistingueMayusculas() {
        ArbolProyecto raiz = carpeta("llenadora", fichero("Comunicacion.XML"));

        assertEquals(Optional.of("comunicacion.xml"), DeteccionCategoria.detectar(raiz),
                "el codigo anterior comparaba con equalsIgnoreCase");
    }

    @Test
    void unaCarpetaNoCuentaComoMarcador() {
        ArbolProyecto raiz = carpeta("llenadora", carpeta("comunicacion.xml"));

        assertTrue(DeteccionCategoria.detectar(raiz).isEmpty(),
                "una carpeta con el nombre del marcador no es el fichero marcador");
    }

    @Test
    void unArbolProfundoNoAgotaLaPila() {
        // 20000 carpetas anidadas. Con la recursividad anterior esto era un
        // StackOverflowError; el recorrido actual usa una pila explicita.
        ArbolProyecto actual = carpeta("nivel-19999");
        for (int i = 19_998; i >= 0; i--) {
            actual = new Nodo("nivel-" + i, true, List.of(actual));
        }

        assertTrue(DeteccionCategoria.detectar(actual).isEmpty(),
                "un arbol muy profundo se recorre sin desbordar la pila");
    }

    @Test
    void recorreElArbolUnaSolaVez() {
        CountingTree raiz = new CountingTree("llenadora", List.of(
                new CountingTree("config", List.of(new CountingTree("comunicacion.xml", List.of()))),
                new CountingTree("datos", List.of(new CountingTree("datos.bin", List.of())))));

        DeteccionCategoria.detectar(raiz);

        assertEquals(1, raiz.visitas,
                "H-05: antes se recorria el arbol entero una vez por cada fichero marcador");
    }

    @Test
    void losDiezMarcadoresSiguenCubiertos() {
        for (String marcador : DeteccionCategoria.FICHEROS_MARCADOR) {
            ArbolProyecto raiz = carpeta("llenadora",
                    carpeta("sub", fichero(marcador)));

            assertEquals(Optional.of(marcador), DeteccionCategoria.detectar(raiz),
                    "el marcador " + marcador + " debe seguir detectandose");
        }
    }

    // ---------------------------------------------------------------- ayudas

    private static ArbolProyecto carpeta(String nombre, ArbolProyecto... hijos) {
        return new Nodo(nombre, true, List.of(hijos));
    }

    private static ArbolProyecto fichero(String nombre) {
        return new Nodo(nombre, false, List.of());
    }

    private record Nodo(String nombre, boolean carpeta, List<ArbolProyecto> hijos) implements ArbolProyecto {

        @Override
        public String nombre() {
            return nombre;
        }

        @Override
        public boolean esCarpeta() {
            return carpeta;
        }

        @Override
        public List<ArbolProyecto> hijos() {
            return hijos;
        }
    }

    /** Arbol que lleva la cuenta de cuantas veces se le visita, para medir los recorridos. */
    private static final class CountingTree implements ArbolProyecto {

        private final String nombre;
        private final List<ArbolProyecto> hijos;
        private int visitas;

        CountingTree(String nombre, List<ArbolProyecto> hijos) {
            this.nombre = nombre;
            this.hijos = new ArrayList<>(hijos);
        }

        @Override
        public String nombre() {
            return nombre;
        }

        @Override
        public boolean esCarpeta() {
            return !hijos.isEmpty();
        }

        @Override
        public List<ArbolProyecto> hijos() {
            visitas++;
            return hijos;
        }
    }
}
