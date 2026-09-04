package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class AnalogValve extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERROR_CODE = "iErrorCode";
    public static final String STATUS = "iStatus";
    public static final String MANUALSP = "rManulaSP";
    public static final String AUTOSP = "rAutoSP";
    public static final String ESTOPSP = "rEstopSP";
    public static final String ACTUAL = "rActual";
    public static final String PBRESETERROR = "bPB_ResetError";
    public static final String PBENRESETERROR = "bPBEN_ResetError";
    public static final String ERROR = "iError";
    public static final String INTERLOCK = "bInterlock";
    public static final String ESTOPFUNCTION = "iEstopFunction";

    public AnalogValve() {
        super();
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();

        propertyTable.put(MODE, 1);
        propertyTable.put(ERROR_CODE, 1);
        propertyTable.put(STATUS, 1);
        propertyTable.put(MANUALSP, 0d);
        propertyTable.put(AUTOSP, 0d);
        propertyTable.put(ESTOPSP, 0d);
        propertyTable.put(ACTUAL, 0d);
        propertyTable.put(PBRESETERROR, false);
        propertyTable.put(PBENRESETERROR, false);
        propertyTable.put(ERROR, false);
        propertyTable.put(INTERLOCK, false);
        propertyTable.put(ESTOPFUNCTION, 0);
    }


}
