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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

public final class MemoryTag {

    private final String raw;
    private final String block;
    private final String code;
    private final int baseByte;
    private final int bit;
    private final int firstByte;
    private final int lastByte;
    private final int cantidad;
    private final String family;
    private final boolean hasRange;

    private MemoryTag(String raw, String block, String code, int baseByte, int bit,
                      int firstByte, int lastByte, int cantidad, String family, boolean hasRange) {
        this.raw = raw;
        this.block = block;
        this.code = code;
        this.baseByte = baseByte;
        this.bit = bit;
        this.firstByte = firstByte;
        this.lastByte = lastByte;
        this.cantidad = cantidad;
        this.family = family;
        this.hasRange = hasRange;
    }

    public static MemoryTag parse(String raw) {
        MemoryTagParser.Partes partes = MemoryTagParser.parse(raw);
        if (partes == null) {
            return null;
        }
        return new MemoryTag(partes.original(), partes.block(), partes.code(),
                partes.baseByte(), partes.bit(), partes.firstByte(), partes.lastByte(),
                partes.cantidad(), partes.family(), partes.hasRange());
    }

    public boolean tieneDireccion() {
        return !code.isEmpty() || baseByte >= 0;
    }

    public String raw() {
        return raw;
    }

    public String block() {
        return block;
    }

    public String code() {
        return code;
    }

    public int baseByte() {
        return baseByte;
    }

    public int bit() {
        return bit;
    }

    public boolean hasRange() {
        return hasRange;
    }

    public String family() {
        return family;
    }

    public int firstByte() {
        return firstByte;
    }

    public int lastByte() {
        return lastByte;
    }

    /** false solo si hay rango escrito y va de mayor a menor: -1 significa "no hay rango". */
    public boolean rangoValido() {
        return lastByte < 0 || lastByte >= firstByte;
    }

    public int byteCapacity() {
        if (firstByte >= 0 && lastByte >= 0) {
            return Math.max(0, lastByte - firstByte + 1);
        }

        if (cantidad > 0) {
            return cantidad;
        }

        if (!tieneDireccion() || bit >= 0) {
            return 0;
        }

        if ("STRING".equalsIgnoreCase(family)) {
            return 254;
        }
        if ("WSTRING".equalsIgnoreCase(family)) {
            return 254 * 2;
        }

        int bitsCodigo = codeBits();
        if (bitsCodigo > 0) {
            return bitsCodigo / 8;
        }

        int bitsTipo = DataType.bitsDeTipo(family);
        return bitsTipo > 0 ? bitsTipo / 8 : 0;
    }

    public int codeBits() {
        if (bit >= 0) return 1;
        if (code.isEmpty()) return 0;
        String c = code.toUpperCase();
        if (c.endsWith("X")) return 1;
        if (c.endsWith("LD") || c.endsWith("L")) return 64;
        if (c.endsWith("B")) return 8;
        if (c.endsWith("W")) return 16;
        if (c.endsWith("D")) return 32;
        return 0;
    }

    public int startByteEffective() {
        int base = Math.max(0, baseByte);
        int offset = Math.max(0, firstByte);
        return base + offset;
    }

    public int endByteEffective() {
        int start = startByteEffective();
        int cap = byteCapacity();
        return start + Math.max(1, cap) - 1;
    }

    public boolean solapaCon(MemoryTag otro) {
        if (otro == null || !tieneDireccion() || !otro.tieneDireccion()) {
            return false;
        }
        if (!block.equalsIgnoreCase(otro.block)) {
            return false;
        }
        // Comparación de bits explícitos en la misma posición de byte
        if (bit >= 0 && otro.bit >= 0) {
            return baseByte == otro.baseByte && bit == otro.bit;
        }
        // Verificación de intersección de rangos de bytes
        return this.startByteEffective() <= otro.endByteEffective() && 
               this.endByteEffective() >= otro.startByteEffective();
    }
}