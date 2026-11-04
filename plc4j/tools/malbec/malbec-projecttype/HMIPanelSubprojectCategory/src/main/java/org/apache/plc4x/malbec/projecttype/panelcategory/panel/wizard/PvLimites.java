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

final class PvLimites {

    static final int MIN = 1;
    static final int MAX = 65535;
    static final int INICIAL = MIN;
    
    private final List<String> adjustments = new ArrayList<>();

    int clampToLimit(String valor, String etiqueta) {
        String text = valor == null ? "" : valor.trim();
        double number;
        try {
            number = Double.parseDouble(text.isEmpty() ? "x" : text.replace(',', '.'));
        } catch (NumberFormatException e) {
            adjustments.add(etiqueta + ": '" + text + "' no es un número,"
                    + " se guardará " + INICIAL);
            return INICIAL;
        }
        int entero = (int) number;
        if (entero < MIN) {
            adjustments.add(etiqueta + ": '" + text + "' está por debajo de "
                    + MIN + ", se guardará " + MIN);
            return MIN;
        }
        if (entero > MAX) {
            adjustments.add(etiqueta + ": '" + text + "' está por encima de "
                    + MAX + ", se guardará " + MAX);
            return MAX;
        }
        return entero;
    }

    List<String> adjustments() {
        return List.copyOf(adjustments);
    }

    boolean hasAdjustments() {
        return !adjustments.isEmpty();
    }

    void annotate(String aviso) {
        adjustments.add(aviso);
    }

    void clear() {
        adjustments.clear();
    }
}