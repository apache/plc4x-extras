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
    public static final String ERROR =  "bError";

    public AnalogInput(S88Element parent) {
        super(parent);
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();

        propertyTable.put(MODE, 1);
        propertyTable.put(ERROR_CODE, 1);
        propertyTable.put(STATUS, 1);
        propertyTable.put(ACTIVE, false);
        propertyTable.put(INPUT, false);
        propertyTable.put(MANUAL, 0);
        propertyTable.put(ERROR, false);
    }
}
