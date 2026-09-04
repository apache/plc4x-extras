package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class DigitalInput extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ON = "bOn";
    public static final String ONACTUAL = "bOnActual";
    public static final String PBON = "bPB_On";
    public static final String PBOFF = "bPB_Off";
    public static final String PBENON = "bPBEN_On";
    public static final String PBENOFF =  "bPBEN_Off";

    public DigitalInput() {
        super();
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();
        propertyTable.put(MODE, 1);
        propertyTable.put(ON, false);
        propertyTable.put(ONACTUAL, false);
        propertyTable.put(PBON, false);
        propertyTable.put(PBOFF, false);
        propertyTable.put(PBENON, false);
        propertyTable.put(PBENOFF, false);
    }
}
