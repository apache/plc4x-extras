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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import java.util.ArrayList;
import java.util.List;

/**
 * Los limites numericos del formulario de variable de proceso.
 *
 * <p>Antes vivia esto dentro de {@code StepPvPanel}, mezclado con los desplegables y
 * la tabla. Era el unico trozo de logica pura del panel, y como tal se puede probar
 * sin Swing, que es justo lo que hace {@code PvLimitesTest}.
 *
 * <p>Los ajustes que se hacen al cargar un valor no se esconden: se acumulan aqui y el
 * panel los enseña antes de guardar. Un recorte silencioso seria peor que un recorte,
 * porque el usuario creeria que se guardo lo que habia en el fichero cuando en realidad
 * se ha escrito otra cosa.
 *
 * <p>No hay ningun componente de Swing aqui a proposito: si aparece uno, esta clase ha
 * dejado de ser sobre lo que es.
 */
final class PvLimites {

    static final int MIN = 1;
    static final int MAX = 65535;
    static final int INICIAL = MIN;

    private final List<String> ajustes = new ArrayList<>();

    /**
     * Convierte un valor guardado en algo que quepa en el desplegable, y avisa si no
     * puede dejarlo igual.
     *
     * <p>Acepta coma o punto como separador decimal porque el fichero puede venir de una
     * configuracion escrita a mano. Trunca en vez de redondear: un limite de visualization
     * redondeado al alza podria dejar fuera un valor que el usuario queria ver, y
     * truncar siempre deja el campo dentro del rango.
     *
     * @param valor   lo que habia guardado, puede ser nulo, vacio o no numerico
     * @param etiqueta como se llama el campo, para poder nombrarlo en el aviso
     * @return el entero que se va a dejar en el campo
     */
    int aLimite(String valor, String etiqueta) {
        String texto = valor == null ? "" : valor.trim();
        double numero;
        try {
            numero = Double.parseDouble(texto.isEmpty() ? "x" : texto.replace(',', '.'));
        } catch (NumberFormatException e) {
            ajustes.add(etiqueta + ": '" + texto + "' no es un número,"
                    + " se guardará " + INICIAL);
            return INICIAL;
        }
        int entero = (int) numero;
        if (entero < MIN) {
            ajustes.add(etiqueta + ": '" + texto + "' está por debajo de "
                    + MIN + ", se guardará " + MIN);
            return MIN;
        }
        if (entero > MAX) {
            ajustes.add(etiqueta + ": '" + texto + "' está por encima de "
                    + MAX + ", se guardará " + MAX);
            return MAX;
        }
        return entero;
    }

    /** Copia de los avisos acumulados, en el orden en que se produjeron. */
    List<String> ajustes() {
        return List.copyOf(ajustes);
    }

    boolean hayAjustes() {
        return !ajustes.isEmpty();
    }

    /** Anota un ajuste que no viene de un limite numerico, como el ScanTime. */
    void anotar(String aviso) {
        ajustes.add(aviso);
    }

    void limpiar() {
        ajustes.clear();
    }
}