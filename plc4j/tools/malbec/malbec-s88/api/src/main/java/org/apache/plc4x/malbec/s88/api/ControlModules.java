package org.apache.plc4x.malbec.s88.api;

import org.apache.plc4x.malbec.s88.api.impl.AnalogInput;
import org.apache.plc4x.malbec.s88.api.impl.AnalogOutput;
import org.apache.plc4x.malbec.s88.api.impl.AnalogValve;
import org.apache.plc4x.malbec.s88.api.impl.DigitalInput;
import org.apache.plc4x.malbec.s88.api.impl.DigitalOutput;
import org.apache.plc4x.malbec.s88.api.impl.Motor;
import org.apache.plc4x.malbec.s88.api.impl.SolenoidValve;
import org.apache.plc4x.malbec.s88.api.impl.VFD;

/**
 * Catalog of the available control modules. Instantiates the concrete module
 * classes internally so callers only ever deal with the public S88ControlModule.
 */
public enum ControlModules {
    ANALOGINPUT("Analog Input"),
    ANALOGOUTPUT("Analog Output"),
    ANALOGVALVE("Analog Valve"),
    DIGITALINPUT("Digital Input"),
    DIGITALOUTPUT("Digital Output"),
    MOTOR("Motor"),
    SOLENOIDVALVE("Solenoid Valve"),
    VFD("VFD");

    private final String name;

    ControlModules(String name){
        this.name = name;
    }

    public String getName(){
        return this.name;
    }

    public S88ControlModule create() {
        return switch (this) {
            case ANALOGINPUT    -> new AnalogInput();
            case ANALOGOUTPUT   -> new AnalogOutput();
            case ANALOGVALVE    -> new AnalogValve();
            case DIGITALINPUT   -> new DigitalInput();
            case DIGITALOUTPUT  -> new DigitalOutput();
            case MOTOR          -> new Motor();
            case SOLENOIDVALVE  -> new SolenoidValve();
            case VFD            -> new VFD();
        };
    }

    /**
     * Resolves the catalog entry for a persisted concrete-type name
     * (typically {@code getClass().getSimpleName()}), or {@code null} when the
     * name is not recognized.
     */
    public static ControlModules fromTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        return switch (typeName) {
            case "AnalogInput" -> ANALOGINPUT;
            case "AnalogOutput" -> ANALOGOUTPUT;
            case "AnalogValve" -> ANALOGVALVE;
            case "DigitalInput" -> DIGITALINPUT;
            case "DigitalOutput" -> DIGITALOUTPUT;
            case "Motor" -> MOTOR;
            case "SolenoidValve" -> SOLENOIDVALVE;
            case "VFD" -> VFD;
            default -> null;
        };
    }
}