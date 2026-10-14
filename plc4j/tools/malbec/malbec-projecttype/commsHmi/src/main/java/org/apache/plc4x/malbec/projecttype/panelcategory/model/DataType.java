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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum DataType {

    BOOLEAN("boolean", 1, true, true),
    BYTE("byte", 1, false, true),
    WORD("word", 2, false, true),
    DWORD("dword", 4, false, true),
    SINT("sint", 1, false, true),
    USINT("usint", 1, false, true),
    INT("int", 4, false, true),
    UINT("uint", 4, false, true),
    DINT("dint", 4, false, true),
    UDINT("udint", 4, false, true),
    LINT("lint", 8, false, true),
    ULINT("ulint", 8, false, true),
    REAL("real", 4, false, true),
    LREAL("lreal", 8, false, true),
    // Legacy types (kept for backward compatibility)
    UBYTE("ubyte", 1, false, true),
    SHORT("short", 2, false, true),
    USHORT("ushort", 2, false, true),
    FLOAT("float", 4, false, true),
    LONG("long", 8, false, true),
    ULONG("ulong", 8, false, true),
    DOUBLE("double", 8, false, true),

    /** Tamaño variable: no admite cálculo de offset por ahora (esperar por definir). */
    STRING("string", 0, false, false),
    TIME("time", 4, false, true),
    DATE("date", 2, false, true),
    TIME_OF_DAY("tod", 4, false, true),
    DATE_AND_TIME("dt", 8, false, true),
    S5TIME("s5time", 0, false, false),
    S7DATE("s7date", 0, false, false),
    S7TIME("s7time", 0, false, false),
    S7TOD("s7tod", 0, false, false),
    S7DAT("s7dat", 0, false, false),
    S7COUNTER("s7counter", 0, false, false),
    S7DI("s7di", 0, false, false),
    S7AI("s7ai", 0, false, false),
    S7AO("s7ao", 0, false, false),
    S7VALVE("s7valve", 0, false, false),
    S7VLV("s7vlv", 0, false, false),
    S7AVLV("s7avlv", 0, false, false),
    S7MOTOR("s7motor", 0, false, false);

    private final String name;
    private final int byteSize;
    private final boolean bitAddressed;
    private final boolean enabled;


    private static final Set<String> ESPECIFICOS_DE_SIEMENS = Set.of(
            "S5TIME", "S7DATE", "S7TIME", "S7TOD", "S7DAT", "S7COUNTER", "S7DI", "S7AI",
            "S7AO", "S7VALVE", "S7VLV", "S7AVLV", "S7MOTOR");
    private static final Map<String, DataType> POR_NOMBRE = new HashMap<>();
    private static final Map<String, DataType> POR_NOMBRE_SIN_MAYUSCULAS = new HashMap<>();
    private static final String NOMBRES_CONCATENADOS;

    static {
        StringBuilder texto = new StringBuilder();
        for (DataType t : values()) {
            POR_NOMBRE.put(t.name, t);
            POR_NOMBRE_SIN_MAYUSCULAS.put(t.name.toUpperCase(Locale.ROOT), t);
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append(t.name);
        }
        NOMBRES_CONCATENADOS = texto.toString();
    }

    DataType(String name, int byteSize, boolean bitAddressed, boolean enabled) {
        this.name = name;
        this.byteSize = byteSize;
        this.bitAddressed = bitAddressed;
        this.enabled = enabled;
    }

    public String label() {
        return name;
    }

    public int byteSize() {
        return byteSize;
    }

    public boolean bitAddressed() {
        return bitAddressed;
    }

    public boolean isEnabled() {
        return enabled;
    }
    public boolean isProtocolSpecific() {
        return ESPECIFICOS_DE_SIEMENS.contains(name());
    }

    public int slotBits() {
        return bitAddressed ? 1 : byteSize * 8;
    }

    public static DataType find(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return POR_NOMBRE.get(name);
    }

    public static DataType findIgnoreCase(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return POR_NOMBRE_SIN_MAYUSCULAS.get(name.toUpperCase(Locale.ROOT));
    }

    /** Nombres de todos los tipos, separados por coma. Se calcula una sola vez. */
    public static String nombres() {
        return NOMBRES_CONCATENADOS;
    }

    public static List<DataType> candidatos(String familia, int bitsArea) {
        if (familia == null) {
            return List.of();
        }
        return switch (familia.toUpperCase()) {
            case "BYTE" -> bitsArea == 8 ? List.of(BYTE, UBYTE) : List.of();
            case "UBYTE" -> List.of(BYTE, UBYTE);
            case "WORD" -> bitsArea == 16 ? List.of(WORD) : List.of();
            case "INTEGER" -> switch (bitsArea) {
                case 16 -> List.of(SHORT, USHORT);
                case 32 -> List.of(INT, UINT);
                case 64 -> List.of(LONG, ULONG);
                default -> List.of();
            };
            case "REAL" -> esReal(bitsArea) ? List.of(FLOAT, DOUBLE) : List.of();
            case "BOOL" -> bitsArea == 1 || bitsArea == 8 ? List.of(BOOLEAN) : List.of();
            case "INT", "UINT" -> List.of(SHORT, USHORT);
            case "DINT", "UDINT", "DWORD" -> List.of(INT, UINT);
            case "LINT", "ULINT", "LWORD" -> List.of(LONG, ULONG);
            case "SINT", "USINT" -> List.of(BYTE, UBYTE);
            case "CHAR" -> List.of(BYTE, UBYTE);
            case "WCHAR" -> bitsArea == 16 ? List.of(WORD) : List.of(BYTE, UBYTE);
            case "RAW_BYTE_ARRAY" -> List.of(BYTE, UBYTE);
            case "USHORT" -> List.of(SHORT, USHORT);
            case "ULONG" -> List.of(LONG, ULONG);
            case "LREAL" -> List.of(DOUBLE);
            default -> List.of();
        };
    }
    
    private static boolean esReal(int bitsArea) {
        return bitsArea == 0 || bitsArea >= 32;
    }

    public static int bitsDeTipo(String nombre) {
        if (nombre == null) {
            return 0;
        }
        return switch (nombre.toUpperCase()) {
            case "BOOL" -> 1;
            case "BYTE", "SINT", "USINT", "CHAR" -> 8;
            case "WORD", "INT", "UINT", "WCHAR" -> 16;
            case "DWORD", "DINT", "UDINT", "REAL" -> 32;
            case "LWORD", "LINT", "ULINT", "LREAL" -> 64;
            default -> 0;
        };
    }
}