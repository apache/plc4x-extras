package org.apache.plc4x.malbec.s88.api.impl;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;

public class VFD extends S88ControlModule {
    public static final String MODE = "iMode";
    public static final String ERRORCODE = "iErrorCode";
    public static final String STATUS = "iStatus";
    public static final String MANUALSPEEDSP = "rManualSpeedSP";
    public static final String AUTOSPEEDSP = "rAutoSpeedSP";
    public static final String ACTUALSPEED =  "rActualSpeed";
    public static final String ACTUALCURRENT = "rActualCurrent";
    public static final String ACTUALPOWER = "rActualPower";
    public static final String PBRESETERROR = "bPB_ResetError";
    public static final String PBFORWARD = "bPB_Forward";
    public static final String PBREVERSE = "bPB_Reverse";
    public static final String PBSTOP =  "bPB_Stop";
    public static final String PBENRESETERROR = "bPBEN_ResetError";
    public static final String PBENFORWARD = "bPBEN_Forward";
    public static final String PBENREVERSE = "bPBEN_Reverse";
    public static final String PBENSTOP = "bPBEN_Stop";
    public static final String FORWARDON = "bForwardOn";
    public static final String REVERSEON = "bReverseOn";
    public static final String SIGNALFORWARD = "bSignalForward";
    public static final String SIGNALREVERSE = "bSignalReverse";
    public static final String ERROR = "bError";
    public static final String INTERLOCK = "bInterlock";


    public VFD() {
        super();
    }

    @Override
    protected void initPropertyTable(){
        propertyTable.put(MODE, 2);
        propertyTable.put(ERRORCODE, 0);
        propertyTable.put(STATUS, 0);
        propertyTable.put(MANUALSPEEDSP, 0d);
        propertyTable.put(AUTOSPEEDSP, 0d);
        propertyTable.put(ACTUALSPEED, 0d);
        propertyTable.put(ACTUALCURRENT, 0d);
        propertyTable.put(ACTUALPOWER, 0d);
        propertyTable.put(PBRESETERROR, false);
        propertyTable.put(PBFORWARD, false);
        propertyTable.put(PBREVERSE, false);
        propertyTable.put(PBSTOP, false);
        propertyTable.put(PBENRESETERROR, false);
        propertyTable.put(PBENFORWARD, false);
        propertyTable.put(PBENREVERSE, false);
        propertyTable.put(PBENSTOP, false);
        propertyTable.put(FORWARDON, false);
        propertyTable.put(REVERSEON, false);
        propertyTable.put(SIGNALFORWARD, false);
        propertyTable.put(SIGNALREVERSE, false);
        propertyTable.put(ERROR, false);
        propertyTable.put(INTERLOCK, false);
    }
}
