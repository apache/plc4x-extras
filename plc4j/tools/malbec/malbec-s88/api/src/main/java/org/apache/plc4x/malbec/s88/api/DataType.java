package org.apache.plc4x.malbec.s88.api;

public enum DataType {
    INTEGER,
    REAL,
    STRING,
    ENUMERATION;

    public static DataType fromString(String text) {
        if (text == null) return null;
        for (DataType type : values()) {
            if (type.name().equalsIgnoreCase(text.trim())) return type;
        }
        return null;
    }

    public static boolean isEnumeration(String text) {
        return fromString(text) == ENUMERATION;
    }

    public static String[] displayNames() {
        return new String[]{
                INTEGER.name(),
                REAL.name(),
                STRING.name(),
                ENUMERATION.name()
        };
    }

    public static DataType defaultForEmpty() {
        return INTEGER;
    }
}
