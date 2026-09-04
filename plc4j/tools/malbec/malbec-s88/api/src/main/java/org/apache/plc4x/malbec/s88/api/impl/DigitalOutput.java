package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class DigitalOutput extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String COMMAND = "bCommand";
    public static final String COMMANDAUTO = "bCommandAuto";
    public static final String INTERLOCK = "bInterlock";
    public static final String PBON = "bPB_On";
    public static final String PBOFF = "bPB_Off";
    public static final String PBENON = "bPBEN_On";
    public static final String PBENOFF =  "bPBEN_Off";


    public DigitalOutput() {
        super();
    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();
        propertyTable.put(MODE, 1);
        propertyTable.put(COMMAND, false);
        propertyTable.put(COMMANDAUTO, false);
        propertyTable.put(INTERLOCK, false);
        propertyTable.put(PBON, false);
        propertyTable.put(PBOFF, false);
        propertyTable.put(PBENON, false);
        propertyTable.put(PBENOFF, true);
    }
}
