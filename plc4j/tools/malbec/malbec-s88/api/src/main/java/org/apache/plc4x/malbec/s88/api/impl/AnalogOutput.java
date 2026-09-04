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
    public static final String PBRESEETERROR = "bPB_ResetError";
    public static final String PBENRESETERROR = "bPBEN_ResetError";
    public static final String ERROR =  "bError";
    public static final String INTERLOCK = "bInterlock";
    public static final String ESTOPFUNCTION = "iEstopFunction";

    public AnalogOutput() {
        super();
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();

        propertyTable.put(MODE, 1);
        propertyTable.put(ERROR_CODE, 1);
        propertyTable.put(VALUE, 0d);
        propertyTable.put(AUTOVALUE, 0d);
        propertyTable.put(MANUALVALUE, 0d);
        propertyTable.put(ESTOPVALUE, 0d);
        propertyTable.put(PBRESEETERROR, false);
        propertyTable.put(PBENRESETERROR, false);
        propertyTable.put(ERROR, false);
        propertyTable.put(INTERLOCK, false);
        propertyTable.put(ESTOPFUNCTION, 0);
    }
}
