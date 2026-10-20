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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import java.util.List;

/**
 * Modo creativo: logica aparte que, solo sobre un area de tipo BYTE, permite mezclar
 * variables de cualquier tipo. A diferencia de {@code nextOffset}, no bloquea el area a un
 * unico tipo y va apilando cada variable a continuacion de la ultima agregada: un tipo de
 * dos o mas bytes ocupa la siguiente posicion par, un BYTE puede ir tambien en impar y un
 * BOOL se sigue bit a bit. Nunca se rebasa el tamano total del area.
 */
public final class CreativeAreaPacking {

    private CreativeAreaPacking() {
    }

    /** Solo las areas cuyo tag declara la familia BYTE admiten el modo creativo. */
    public static boolean supportsCreative(String family) {
        return family != null && "BYTE".equalsIgnoreCase(family.trim());
    }

    public static String nextOffset(int capacidad, List<CommConfigData.PvConfig> pvs,
            DataType type) {
        if (capacidad <= 0 || type == null || !type.isEnabled()) {
            return null;
        }
        Placed colocado = place(cursorOf(pvs), capacidad, type);
        return colocado == null ? null : colocado.offset();
    }

    public static int leftoverBytes(int capacidad, List<CommConfigData.PvConfig> pvs) {
        if (capacidad <= 0) {
            return 0;
        }
        Cursor cursor = cursorOf(pvs);
        int proximo = cursor.bit() == 0 ? cursor.bytePos() : cursor.bytePos() + 1;
        return Math.max(0, capacidad - proximo);
    }

    /** Cuantas variables mas de este tipo caben, encadenandolas desde la ultima. */
    public static int slotsFor(int capacidad, List<CommConfigData.PvConfig> pvs, DataType type,
            int max) {
        if (capacidad <= 0 || type == null || !type.isEnabled() || max <= 0) {
            return 0;
        }
        int count = 0;
        Cursor cursor = cursorOf(pvs);
        while (count < max) {
            Placed colocado = place(cursor, capacidad, type);
            if (colocado == null) {
                break;
            }
            cursor = colocado.next();
            count++;
        }
        return count;
    }

    private static Placed place(Cursor cursor, int capacidad, DataType type) {
        if (type.bitAddressed()) {
            int bytePos = cursor.bytePos();
            int bit = cursor.bit();
            if (bytePos >= capacidad) {
                return null;
            }
            Cursor next = bit >= 7 ? new Cursor(bytePos + 1, 0) : new Cursor(bytePos, bit + 1);
            return new Placed(next, bytePos + "." + bit);
        }
        int bytePos = cursor.bit() == 0 ? cursor.bytePos() : cursor.bytePos() + 1;
        if (!oddAllowed(type) && (bytePos & 1) == 1) {
            bytePos++;
        }
        if (bytePos + type.byteSize() > capacidad) {
            return null;
        }
        return new Placed(new Cursor(bytePos + type.byteSize(), 0), String.valueOf(bytePos));
    }

    private static Cursor cursorOf(List<CommConfigData.PvConfig> pvs) {
        Cursor max = new Cursor(0, 0);
        if (pvs == null) {
            return max;
        }
        for (CommConfigData.PvConfig pv : pvs) {
            DataType type = DataType.find(pv.getType());
            if (type == null) {
                continue;
            }
            int[] inicio = startOf(pv.getOffset());
            if (inicio == null) {
                continue;
            }
            Cursor fin = type.bitAddressed()
                    ? (inicio[1] >= 7 ? new Cursor(inicio[0] + 1, 0)
                            : new Cursor(inicio[0], inicio[1] + 1))
                    : new Cursor(inicio[0] + type.byteSize(), 0);
            max = later(max, fin);
        }
        return max;
    }

    private static Cursor later(Cursor a, Cursor b) {
        if (b.bytePos() > a.bytePos()) {
            return b;
        }
        if (b.bytePos() < a.bytePos()) {
            return a;
        }
        return b.bit() >= a.bit() ? b : a;
    }

    private static int[] startOf(String offset) {
        if (offset == null || offset.isEmpty()) {
            return null;
        }
        try {
            int punto = offset.indexOf('.');
            int byteOffset = Integer.parseInt(punto < 0 ? offset : offset.substring(0, punto));
            int bit = punto < 0 ? 0 : Integer.parseInt(offset.substring(punto + 1));
            return new int[] {byteOffset, bit};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Solo el tipo BYTE puede empezar en posicion impar; el resto ocupa byte par. */
    private static boolean oddAllowed(DataType type) {
        return type == DataType.BYTE;
    }

    private record Cursor(int bytePos, int bit) {
    }

    private record Placed(Cursor next, String offset) {
    }
}
