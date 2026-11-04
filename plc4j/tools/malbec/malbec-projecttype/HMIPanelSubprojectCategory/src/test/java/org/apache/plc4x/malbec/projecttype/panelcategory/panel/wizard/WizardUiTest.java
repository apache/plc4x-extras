/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WizardUiTest {

    @Test
    void loQueSobraSeReparteProporcionalmente() {
        int[] repartido = WizardUi.distributeProportionally(new int[]{100, 100, 100}, 450);
        assertNotNull(repartido);
        assertEquals(450, suma(repartido), "las columnas no llegan al borde");
        assertArrayEquals(new int[]{150, 150, 150}, repartido,
                "con columnas iguales el reparto tiene que ser igual");
    }

    @Test
    void proportionIsRespected() {
        int[] repartido = WizardUi.distributeProportionally(new int[]{200, 100}, 400);
        assertNotNull(repartido);
        assertEquals(400, suma(repartido), "las columnas no llegan al borde");
        assertTrue(repartido[0] > repartido[1],
                "la columna ancha no se llevó más parte: " + repartido[0]
                        + " contra " + repartido[1]);
        assertTrue(repartido[0] >= 260 && repartido[0] <= 280,
                "la columna ancha debería quedar alrededor de 267, no en "
                        + repartido[0]);
        assertTrue(repartido[1] >= 120 && repartido[1] <= 140,
                "la estrecha debería quedar alrededor de 133, no en " + repartido[1]);
    }

    @Test
    void pixelLeftByRoundingIsDelivered() {
        int[] repartido = WizardUi.distributeProportionally(new int[]{100, 100, 100}, 307);
        assertNotNull(repartido);
        assertEquals(307, suma(repartido), "el redondeo de las divisiones se comió píxeles");
    }

    @Test
    void withoutExtraRoomNothingIsDistributed() {
        assertNull(WizardUi.distributeProportionally(new int[]{400, 400}, 700),
                "se repartió aunque las columnas ya llenaban el visor");
    }

    @Test
    void withoutVisibleWidthNothingIsDistributed() {
        assertNull(WizardUi.distributeProportionally(new int[]{100, 100}, 0));
        assertNull(WizardUi.distributeProportionally(new int[]{}, 700), "no hay columnas");
        assertNull(WizardUi.distributeProportionally(null, 700));
    }

    @Test
    void columnsNeverShrink() {
        int[] repartido = WizardUi.distributeProportionally(new int[]{10, 10, 10, 10}, 100);
        assertNotNull(repartido);
        for (int width : repartido) {
            assertTrue(width >= 10, "una columna quedó más estrecha de lo que estaba");
        }
        assertEquals(100, suma(repartido));
    }

    @Test
    void textLengthLimitsStillAllowTyping() {
        assertEquals(20, WizardUi.MAX_NOMBRE);
        assertEquals(60, WizardUi.MAX_DESCRIPCION);
    }

    private static int suma(int[] valores) {
        int total = 0;
        for (int valor : valores) {
            total += valor;
        }
        return total;
    }
}