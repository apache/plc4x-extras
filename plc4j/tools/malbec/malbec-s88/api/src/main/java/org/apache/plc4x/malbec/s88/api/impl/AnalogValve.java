package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class AnalogValve extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERROR_CODE = "iErrorCode";
    public static final String STATUS = "iStatus";
    public static final String ERROR = "iError";
    public static final String INTERLOCK = "bInterlock";

    public AnalogValve(S88Element parent) {
        super(parent);
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();

        propertyTable.put(MODE, 1);
        propertyTable.put(ERROR_CODE, 1);
        propertyTable.put(STATUS, 1);
        propertyTable.put(ERROR, false);
        propertyTable.put(INTERLOCK, false);
    }


}
