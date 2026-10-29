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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DynamicTag {

    private static final Pattern DB = Pattern.compile(
            "^%?DB(\\d+)\\.DB([XBWD])(\\d+)(?:\\.([0-7]))?$");
    private static final Pattern IO = Pattern.compile(
            "^%?([IQ])([BWD]?)(\\d+)(?:\\.([0-7]))?$");

    public enum Area { DB, INPUT, OUTPUT }

    public enum Code {
        X(1, DataType.BOOLEAN),
        B(8, DataType.BYTE),
        W(16, DataType.WORD),
        D(32, DataType.DWORD);

        private final int bits;
        private final DataType defaultType;

        Code(int bits, DataType defaultType) {
            this.bits = bits;
            this.defaultType = defaultType;
        }

        public int bits() {
            return bits;
        }

        public DataType defaultType() {
            return defaultType;
        }

        public boolean accepts(DataType type) {
            if (type == null || type.bitAddressed() != (this == X)) {
                return false;
            }
            return this == X ? type == DataType.BOOLEAN : type.byteSize() * 8 == bits;
        }
    }

    private final Area area;
    private final int block;
    private final Code code;
    private final int byteOffset;
    private final int bit;
    private final DataType dataType;

    private DynamicTag(Area area, int block, Code code, int byteOffset, int bit,
                       DataType dataType) {
        this.area = area;
        this.block = block;
        this.code = code;
        this.byteOffset = byteOffset;
        this.bit = bit;
        this.dataType = dataType;
    }

    public static DynamicTag parse(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String s = tag.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("%")) {
            s = s.substring(1);
        }

        Matcher db = DB.matcher(s);
        if (db.matches()) {
            Code code = Code.valueOf(db.group(2));
            int block = number(db.group(1));
            int offset = number(db.group(3));
            int bit = db.group(4) == null ? -1 : number(db.group(4));
            if (block < 0 || offset < 0 || (code == Code.X) != (bit >= 0)) {
                return null;
            }
            return new DynamicTag(Area.DB, block, code, offset, bit, code.defaultType());
        }

        Matcher io = IO.matcher(s);
        if (io.matches()) {
            String letter = io.group(2);
            Code code = letter.isEmpty() ? Code.X : Code.valueOf(letter);
            int offset = number(io.group(3));
            int bit = io.group(4) == null ? -1 : number(io.group(4));
            if (offset < 0 || (code == Code.X) != (bit >= 0)) {
                return null;
            }
            Area area = "I".equals(io.group(1)) ? Area.INPUT : Area.OUTPUT;
            return new DynamicTag(area, 0, code, offset, bit, code.defaultType());
        }

        return null;
    }

    public static DynamicTag parse(String text, DataType type) {
        DynamicTag tag = parse(text);
        return tag == null ? null : tag.withDataType(type);
    }

    public DynamicTag withDataType(DataType type) {
        if (!code.accepts(type)) {
            throw new IllegalArgumentException(
                    "The type " + type + " is not compatible with the code. " + code);
        }
        if (type.byteSize() > 1 && (byteOffset & 1) != 0) {
            throw new IllegalArgumentException(
                    "The tag " + address() + " starts at byte " + byteOffset
                            + "; type " + type.label() + " must start on an even byte.");
        }
        return new DynamicTag(area, block, code, byteOffset, bit, type);
    }

    public DynamicTag withAddress(int newByteOffset, int newBit) {
        if (newByteOffset < 0) {
            throw new IllegalArgumentException("El offset no puede ser negativo.");
        }
        if (isBit()) {
            if (newBit < 0 || newBit > 7) {
                throw new IllegalArgumentException("El bit debe estar entre 0 y 7.");
            }
        } else if (newBit >= 0) {
            throw new IllegalArgumentException("Un tag que no es de bit no puede tener bit.");
        }
        return new DynamicTag(area, block, code, newByteOffset, newBit, dataType);
    }

    public Area area() {
        return area;
    }

    public int block() {
        return block;
    }

    public Code code() {
        return code;
    }

    public int byteOffset() {
        return byteOffset;
    }

    public int bit() {
        return bit;
    }

    public DataType dataType() {
        return dataType;
    }

    public boolean isBit() {
        return bit >= 0;
    }

    public int codeBits() {
        return isBit() ? 1 : code.bits();
    }

    public int byteCapacity() {
        return isBit() ? 0 : dataType.byteSize();
    }

    public String address() {
        StringBuilder text = new StringBuilder("%");
        if (area == Area.DB) {
            text.append("DB").append(block).append(".DB").append(code).append(byteOffset);
        } else {
            text.append(area == Area.INPUT ? "I" : "Q");
            if (code != Code.X) {
                text.append(code);
            }
            text.append(byteOffset);
        }
        if (isBit()) {
            text.append('.').append(bit);
        }
        return text.toString();
    }

    public String toPlc4xAddress() {
        return address() + ":" + plc4xType();
    }

    private String plc4xType() {
        return switch (dataType) {
            case BOOLEAN -> "BOOL";
            case SHORT -> "INT";
            case USHORT -> "UINT";
            case INT -> "DINT";
            case UINT -> "UDINT";
            case LONG -> "LINT";
            case ULONG -> "ULINT";
            case FLOAT -> "REAL";
            case DOUBLE -> "LREAL";
            case UBYTE -> "USINT";
            case WORD -> "INT";
            default -> dataType.name();
        };
    }

    public int startByte() {
        return byteOffset;
    }

    public int endByte() {
        return isBit() ? byteOffset : byteOffset + byteCapacity() - 1;
    }

    public boolean overlaps(DynamicTag other) {
        if (other == null || area != other.area) {
            return false;
        }
        if (area == Area.DB && block != other.block) {
            return false;
        }
        if (isBit() && other.isBit()) {
            return byteOffset == other.byteOffset && bit == other.bit;
        }
        return startByte() <= other.endByte() && endByte() >= other.startByte();
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}