package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class Motor extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERRORCODE = "iErrorCode";
    public static final String STATUS  = "iStatus";
    public static final String PBRESETERROR = "bPB_ResetError";
    public static final String PBFORWARD = "bPB_Forward";
    public static final String PBREVERSE = "bPB_Reverse";
    public static final String PBSTOP =  "bPB_Stop";
    public static final String PBENRSETERROR = "bPBEN_ResetError";
    public static final String PBENFORWARD = "bPBEN_Forward";
    public static final String PBENRESEVERSE = "bPBEN_Reverse";
    public static final String PBENSTOP = "bPBEN_Stop";
    public static final String FORWARDON = "bForwardOn";
    public static final String REVERSEON  = "bReverseOn";
    public static final String SIGNALFOR = "bSignalForward";
    public static final String SIGNALREVERSE = "bSignalReverse";
    public static final String ERROR =  "bError";
    public static final String INTERLOCK = "bInterlock";

    public Motor() {
        super();

    }

    @Override
    protected void initPropertyTable() {
        super.initPropertyTable();
        propertyTable.put(MODE, 1);
        propertyTable.put(ERRORCODE, 0);
        propertyTable.put(STATUS, 1);
        propertyTable.put(PBRESETERROR, false);
        propertyTable.put(PBFORWARD, false);
        propertyTable.put(PBREVERSE, false);
        propertyTable.put(PBSTOP, false);
        propertyTable.put(PBENRSETERROR, false);
        propertyTable.put(PBENFORWARD, false);
        propertyTable.put(PBENRESEVERSE, false);
        propertyTable.put(PBENSTOP, false);
        propertyTable.put(FORWARDON, false);
        propertyTable.put(REVERSEON, false);
        propertyTable.put(SIGNALFOR, false);
        propertyTable.put(SIGNALREVERSE, false);
        propertyTable.put(ERROR, false);
        propertyTable.put(INTERLOCK, false);
    }
}
