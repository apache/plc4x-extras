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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * El recorte de los limites numericos del formulario de variable de proceso.
 *
 * <p>Esta logica estaba dentro de {@code StepPvPanel} y ahora tiene su propia clase sin
 * Swing. Los tests no son nuevos en el comportamiento: {@code StepPvPanelTest} ya
 * cubria el truncado, el recorte y la sustitucion por el minimo a traves del formulario.
 * Lo que se anade aqui es que se puede comprobar sin montar un panel entero, y que los
 * casos raros que por el camino del panel no se ven tienen sitio.
 */
class PvLimitesTest {

    private static final String CAMPO = "Display límite bajo";

    @Test
    void unValorNormalSeQuedaComoEsta() {
        PvLimites limites = new PvLimites();

        assertEquals(300, limites.aLimite("300", CAMPO));
        assertFalse(limites.hayAjustes(), "no hay nada que ajustar: " + limites.ajustes());
    }

    /** La coma decimal se acepta: el fichero puede venir escrito a mano. */
    @Test
    void seAceptaComaComoSeparadorDecimal() {
        PvLimites limites = new PvLimites();

        assertEquals(300, limites.aLimite("300,0", CAMPO));
    }

    /** Trunca, no redondea: un limite redondeado al alza dejaria fuera lo que se queria ver. */
    @Test
    void truncaElDecimalEnVezDeRedondear() {
        PvLimites limites = new PvLimites();

        assertEquals(12, limites.aLimite("12.9", CAMPO));
    }

    @Test
    void unValorPorEncimaSeRecortaAlMaximo() {
        PvLimites limites = new PvLimites();

        assertEquals(PvLimites.MAX, limites.aLimite("70000", CAMPO));
        assertTrue(limites.hayAjustes(), "un recorte tiene que avisarse");
    }

    @Test
    void unValorPorDebajoSeRecortaAlMinimo() {
        PvLimites limites = new PvLimites();

        assertEquals(PvLimites.MIN, limites.aLimite("-5", CAMPO));
        assertTrue(limites.hayAjustes());
    }

    @Test
    void unNumeroInvalidoVuelveAlMinimo() {
        PvLimites limites = new PvLimites();

        assertEquals(PvLimites.INICIAL, limites.aLimite("abc", CAMPO));
        assertTrue(limites.hayAjustes());
    }

    /** Un vacio y un nulo dan el mismo resultado que un numero invalido. */
    @Test
    void vacioYNuloCaenEnElMinimo() {
        assertEquals(PvLimites.INICIAL, new PvLimites().aLimite("", CAMPO));
        assertEquals(PvLimites.INICIAL, new PvLimites().aLimite(null, CAMPO));
    }

    /** Los espacios de los bordes no estorban: un fichero puede traerlos. */
    @Test
    void seIgnoranLosEspaciosDeLosBordes() {
        PvLimites limites = new PvLimites();

        assertEquals(300, limites.aLimite("  300  ", CAMPO));
        assertFalse(limites.hayAjustes());
    }

    /** Varios ajustes se acumulan y se limpian; no se come el anterior al recargar. */
    @Test
    void losAjustesSeAcumulanYSeLimpian() {
        PvLimites limites = new PvLimites();

        limites.aLimite("abc", "Primero");
        limites.aLimite("70000", "Segundo");
        limites.anotar("Tercero, a mano");

        assertEquals(3, limites.ajustes().size(), "cada recorte avisa por separado");
        assertTrue(limites.ajustes().get(0).contains("Primero"));
        assertTrue(limites.ajustes().get(2).contains("Tercero"));

        limites.limpiar();

        assertFalse(limites.hayAjustes(), "al abrir otra variable no quedan avisos del anterior");
    }

    /**
     * Ajustes devuelve una copia, y además inmutable.
     *
     * <p>La inmutabilidad es mejor que la copia a efectos practicos: no hace falta
     * acordarse de copiarla, porque no hay forma de modificarla por accidente.
     */
    @Test
    void ajustesDevuelveUnaCopiaInmutable() {
        PvLimites limites = new PvLimites();
        limites.aLimite("abc", CAMPO);

        List<String> copia = limites.ajustes();

        assertThrows(UnsupportedOperationException.class, copia::clear,
                "una lista inmutable avisa en vez de dejar que alguien la vacie en silencio");
        assertTrue(limites.hayAjustes(), "la lista de verdad no se puede tocar desde fuera");
        assertEquals(1, limites.ajustes().size());
    }
}
