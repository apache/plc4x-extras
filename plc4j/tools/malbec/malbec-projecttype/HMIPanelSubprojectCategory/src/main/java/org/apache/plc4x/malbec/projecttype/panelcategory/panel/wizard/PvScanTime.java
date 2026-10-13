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

import java.awt.Dimension;

final class PvScanTime {

    static final int MIN = 100;
    static final int MAX = 1000;
    static final int PASO = 100;

    private PvScanTime() {
    }

    static Dimension ofertas() {
        return new Dimension((MAX - MIN) / PASO + 1, 1);
    }

    static boolean estaEnLasOfertas(String scanTime) {
        if (scanTime == null) {
            return false;
        }
        String texto = scanTime.trim();
        if (texto.isEmpty()) {
            return false;
        }
        try {
            int valor = Integer.parseInt(texto);
            return valor >= MIN && valor <= MAX && (valor - MIN) % PASO == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String avisoFueraDeOfertas(String scanTime) {
        return "ScanTime: '" + (scanTime == null ? "" : scanTime.trim()) + "' no es una de las"
                + " ofertas (" + MIN + " a " + MAX
                + " de " + PASO + " en " + PASO + "),"
                + " así que hay que elegir uno";
    }
}