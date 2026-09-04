package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class SolenoidValve extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERRORCODE = "iErrorCode";
    public static final String STATUS  = "iStatus";
    public static final String PBRESETERROR = "bPB_ResetError";
    public static final String PBHOME = "bPB_Home";
    public static final String PBWORK = "bPB_Work";
    public static final String PBENRESETERROR = "bPBEN_ResetError";
    public static final String PBENHOME = "bPBEN_Home";
    public static final String PBENWORK = "bPBEN_Work";
    public static final String PBWORKON = "bPBEN_WorkOn";
    public static final String SIGNALHOME = "bSignalHome";
    public static final String SIGNALWORK = "bSignalWork";
    public static final String ERROR =  "bError";
    public static final String INTERLOCK = "bInterlock";

    public SolenoidValve() {
        super();
    }

    @Override
    protected void initPropertyTable() {
        propertyTable.put(MODE, 1);
        propertyTable.put(ERRORCODE, 0);
        propertyTable.put(STATUS, 0);
        propertyTable.put(PBRESETERROR, false);
        propertyTable.put(PBHOME, false);
        propertyTable.put(PBWORK, false);
        propertyTable.put(PBENRESETERROR, false);
        propertyTable.put(PBENHOME, false);
        propertyTable.put(PBENWORK, false);
        propertyTable.put(PBWORKON, false);
        propertyTable.put(SIGNALHOME, false);
        propertyTable.put(SIGNALWORK, false);
        propertyTable.put(ERROR, false);
        propertyTable.put(INTERLOCK, false);

    }
}
