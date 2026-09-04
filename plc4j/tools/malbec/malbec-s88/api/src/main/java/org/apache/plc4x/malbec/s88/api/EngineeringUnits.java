package org.apache.plc4x.malbec.s88.api;

public enum EngineeringUnits {
    CELSIUS(Magnitude.TEMPERATURE, "C"),
    FAHRENHEIT(Magnitude.TEMPERATURE, "F"),
    KELVIN(Magnitude.TEMPERATURE, "K"),

    MILLISECOND(Magnitude.TIME, "MS"),
    SECOND(Magnitude.TIME, "S"),
    MINUTE(Magnitude.TIME, "MIN"),
    HOUR(Magnitude.TIME, "HOUR"),
    DAY(Magnitude.TIME, "DAY"),
    WEEK(Magnitude.TIME, "WEEK"),
    MONTH(Magnitude.TIME, "MONTH"),
    YEAR(Magnitude.TIME, "YEAR"),

    PASCAL(Magnitude.PRESSURE, "PA"),
    PSI(Magnitude.PRESSURE, "PSI"),
    MILLIBAR(Magnitude.PRESSURE, "MBAR"),
    BAR(Magnitude.PRESSURE, "BAR"),
    ATMOSPHERE(Magnitude.PRESSURE, "ATM"),

    MILLIGRAM(Magnitude.WEIGHT, "MG"),
    KILOGRAM(Magnitude.WEIGHT, "KG"),
    POUND(Magnitude.PRESSURE, "LB"),

    MILLISIEMENS(Magnitude.CONDUCTIVITY, "MILLISIEMENS"),
    SIEMENS(Magnitude.CONDUCTIVITY, "SIEMENS"),

    MILLILITER(Magnitude.VOLUME, "ML"),
    LITER(Magnitude.VOLUME, "L"),
    HECTOLITER(Magnitude.VOLUME, "HL"),
    CUBIC_CENTIMETER(Magnitude.VOLUME, "C3"),
    CUBIC_METER(Magnitude.VOLUME, "M3"),

    MILLIMETER(Magnitude.LONGITUDE, "MM"),
    CENTIMETER(Magnitude.LONGITUDE, "CM"),
    METER(Magnitude.LONGITUDE, "M"),
    INCHES(Magnitude.LONGITUDE, "IN"),
    FEET(Magnitude.LONGITUDE, "FEET"),

    RPM(Magnitude.FREQUENCY, "RPM"),

    CUBIC_METER_H(Magnitude.FLOW, "M3H"),

    PERCENTAGE(Magnitude.NONE, "%"),
    NONE(Magnitude.NONE, "")

            ;
    private final Magnitude magnitude;
    private final String name;

    EngineeringUnits(Magnitude m, String name) {
        this.magnitude = m;
        this.name = name;
    }

    public Magnitude getMagnitude() {
        return this.magnitude;
    }

    public String getName() {
        return this.name;
    }

    public static EngineeringUnits fromName(String name) {
        if (name == null) return NONE;
        String trimmed = name.trim();
        for (EngineeringUnits e : values()) {
            if (e.getName().equalsIgnoreCase(trimmed) || e.name().equalsIgnoreCase(trimmed)) return e;
        }
        return NONE;
    }
}
