package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class AnalogOutput extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERROR_CODE = "iErrorCode";
    public static final String VALUE = "rValue";
    public static final String AUTOVALUE = "rAutoValue";
    public static final String MANUALVALUE = "rManualValue";
    public static final String ESTOPVALUE = "rStopValue";
    public static final String ERROR =  "bError";

    public AnalogOutput(S88Element parent) {
        super(parent);
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();

        propertyTable.put(MODE, 1);
        propertyTable.put(ERROR_CODE, 1);
        propertyTable.put(VALUE, 1);
        propertyTable.put(AUTOVALUE, false);
        propertyTable.put(MANUALVALUE, false);
        propertyTable.put(ESTOPVALUE, 0);
        propertyTable.put(ERROR, false);
    }
}
