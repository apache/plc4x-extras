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

class PvLimitesTest {

    private static final String CAMPO = "Display límite bajo";

    @Test
    void normalValueIsKeptAsIs() {
        PvLimites limites = new PvLimites();

        assertEquals(300, limites.clampToLimit("300", CAMPO));
        assertFalse(limites.hasAdjustments(), "no hay nada que ajustar: " + limites.adjustments());
    }

    @Test
    void commaIsAcceptedAsDecimalSeparator() {
        PvLimites limites = new PvLimites();

        assertEquals(300, limites.clampToLimit("300,0", CAMPO));
    }

    @Test
    void truncaElDecimalEnVezDeRedondear() {
        PvLimites limites = new PvLimites();

        assertEquals(12, limites.clampToLimit("12.9", CAMPO));
    }

    @Test
    void valueAboveIsClampedToTheMaximum() {
        PvLimites limites = new PvLimites();

        assertEquals(PvLimites.MAX, limites.clampToLimit("70000", CAMPO));
        assertTrue(limites.hasAdjustments(), "un recorte tiene que avisarse");
    }

    @Test
    void valueBelowIsClampedToTheMinimum() {
        PvLimites limites = new PvLimites();

        assertEquals(PvLimites.MIN, limites.clampToLimit("-5", CAMPO));
        assertTrue(limites.hasAdjustments());
    }

    @Test
    void invalidNumberFallsBackToTheMinimum() {
        PvLimites limites = new PvLimites();

        assertEquals(PvLimites.INICIAL, limites.clampToLimit("abc", CAMPO));
        assertTrue(limites.hasAdjustments());
    }

    @Test
    void vacioYNuloCaenEnElMinimo() {
        assertEquals(PvLimites.INICIAL, new PvLimites().clampToLimit("", CAMPO));
        assertEquals(PvLimites.INICIAL, new PvLimites().clampToLimit(null, CAMPO));
    }

    @Test
    void surroundingSpacesAreIgnored() {
        PvLimites limites = new PvLimites();

        assertEquals(300, limites.clampToLimit("  300  ", CAMPO));
        assertFalse(limites.hasAdjustments());
    }

    @Test
    void adjustmentsAccumulateAndAreCleared() {
        PvLimites limites = new PvLimites();

        limites.clampToLimit("abc", "Primero");
        limites.clampToLimit("70000", "Segundo");
        limites.annotate("Tercero, a mano");

        assertEquals(3, limites.adjustments().size(), "cada recorte avisa por separado");
        assertTrue(limites.adjustments().get(0).contains("Primero"));
        assertTrue(limites.adjustments().get(2).contains("Tercero"));

        limites.clear();

        assertFalse(limites.hasAdjustments(), "al abrir otra variable no quedan avisos del anterior");
    }

    @Test
    void ajustesDevuelveUnaCopiaInmutable() {
        PvLimites limites = new PvLimites();
        limites.clampToLimit("abc", CAMPO);

        List<String> copia = limites.adjustments();

        assertThrows(UnsupportedOperationException.class, copia::clear,
                "una lista inmutable avisa en vez de dejar que alguien la vacie en silencio");
        assertTrue(limites.hasAdjustments(), "la lista de verdad no se puede tocar desde fuera");
        assertEquals(1, limites.adjustments().size());
    }
}
