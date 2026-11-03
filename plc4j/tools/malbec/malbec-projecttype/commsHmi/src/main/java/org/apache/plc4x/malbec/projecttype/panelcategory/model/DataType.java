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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
//    LINT("lint", 8, false, true),
//    ULINT("ulint", 8, false, true),
//    REAL("real", 4, false, true),
//    LREAL("lreal", 8, false, true),
    // Legacy types (kept for backward compatibility)
    UBYTE("ubyte", 1, false, true),
    SHORT("short", 2, false, true),
    USHORT("ushort", 2, false, true),
    FLOAT("float", 4, false, true),
    LONG("long", 8, false, true),
    ULONG("ulong", 8, false, true),
    DOUBLE("double", 8, false, true),

    /** Tamaño variable de bytes (String por definir) */
    STRING("string", 0, false, false),
//    TIME("time", 4, false, true),
//    DATE("date", 2, false, true),
//    TIME_OF_DAY("tod", 4, false, true),
//    DATE_AND_TIME("dt", 8, false, true),
    S5TIME("s5time", 2, false, true, "s7"),
    S7DATE("s7date", 4, false, true, "s7"),
    S7TIME("s7time", 4, false, true, "s7"),
    S7TOD("s7tod", 2, false, true, "s7"),
    S7DAT("s7dat", 8, false, true, "s7"),
    S7COUNTER("s7counter", 2, false, true, "s7"),
    S7DI("s7di", 3, false, true, "s7"),
    S7AI("s7ai", 64, false, true, "s7"),
    S7AO("s7ao", 36, false, true, "s7"),
//    S7VALVE("s7valve", 0, false, true, "s7"),
    S7VLV("s7vlv", 14, false, true, "s7"),
    S7AVLV("s7avlv", 36, false, true, "s7"),
    S7MOTOR("s7motor", 14, false, true, "s7");

    private final String name;
    private final int byteSize;
    private final boolean bitAddressed;
    private final boolean enabled;
    private final String protocol;

    private static final Map<String, DataType> POR_NOMBRE = new HashMap<>();
    private static final Map<String, DataType> POR_NOMBRE_SIN_MAYUSCULAS = new HashMap<>();
    private static final String CONCATENATED_NAMES;

    static {
        StringBuilder text = new StringBuilder();
        for (DataType t : values()) {
            POR_NOMBRE.put(t.name, t);
            POR_NOMBRE_SIN_MAYUSCULAS.put(t.name.toUpperCase(Locale.ROOT), t);
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(t.name);
        }
        CONCATENATED_NAMES = text.toString();
    }

    DataType(String name, int byteSize, boolean bitAddressed, boolean enabled) {
        this(name, byteSize, bitAddressed, enabled, null);
    }

    DataType(String name, int byteSize, boolean bitAddressed, boolean enabled,
            String protocol) {
        this.name = name;
        this.byteSize = byteSize;
        this.bitAddressed = bitAddressed;
        this.enabled = enabled;
        this.protocol = protocol;
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

    public String protocol() {
        return protocol;
    }

    public boolean isProtocolSpecific() {
        return protocol != null;
    }

    public boolean isByteBlock() {
        return protocol != null && !bitAddressed && byteSize > 0;
    }

    public boolean supportsProtocol(String protocol) {
        if (this.protocol == null) {
            return true;
        }
        return protocol != null && this.protocol.equalsIgnoreCase(protocol.trim());
    }

    public boolean isEnabledFor(String protocol) {
        if (!supportsProtocol(protocol)) {
            return false;
        }
        return enabled || (isProtocolSpecific() && byteSize > 0);
    }

    public int slotBits() {
        return bitAddressed ? 1 : byteSize * 8;
    }

    public static List<DataType> valuesForProtocol(String protocol) {
        List<DataType> result = new ArrayList<>();
        for (DataType dataType : values()) {
            if (dataType.isEnabledFor(protocol)) {
                result.add(dataType);
            }
        }
        return result;
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
    public static String names() {
        return CONCATENATED_NAMES;
    }

    public static List<DataType> candidates(String familia, int bitsArea) {
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
            case "REAL" -> isReal(bitsArea) ? List.of(FLOAT, DOUBLE) : List.of();
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
    
    private static boolean isReal(int bitsArea) {
        return bitsArea == 0 || bitsArea >= 32;
    }

    public static int bitsOfType(String name) {
        if (name == null) {
            return 0;
        }
        return switch (name.toUpperCase()) {
            case "BOOL" -> 1;
            case "BYTE", "SINT", "USINT", "CHAR" -> 8;
            case "WORD", "INT", "UINT", "WCHAR" -> 16;
            case "DWORD", "DINT", "UDINT", "REAL" -> 32;
            case "LWORD", "LINT", "ULINT", "LREAL" -> 64;
            default -> 0;
        };
    }
}