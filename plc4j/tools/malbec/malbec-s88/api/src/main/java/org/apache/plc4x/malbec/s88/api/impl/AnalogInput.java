package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class AnalogInput extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERROR_CODE = "iErrorCode";
    public static final String STATUS = "iStatus";
    public static final String ACTIVE = "rActiveValue";
    public static final String INPUT = "rInputValue";
    public static final String MANUAL = "rManualValue";
    public static final String PBRESETERROR = "bPB_ResetError";
    public static final String PBENRESETERROR = "bPBEN_ResetError";
    public static final String ERROR =  "bError";

    public AnalogInput() {
        super();
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();

        propertyTable.put(MODE, 1);
        propertyTable.put(ERROR_CODE, 1);
        propertyTable.put(STATUS, 1);
        propertyTable.put(ACTIVE, 0d);
        propertyTable.put(INPUT, 0d);
        propertyTable.put(MANUAL, 0d);
        propertyTable.put(PBRESETERROR, false);
        propertyTable.put(PBENRESETERROR, false);
        propertyTable.put(ERROR, false);
    }
}
