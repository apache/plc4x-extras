/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.plc4x.merlot.drv.s7.core;

import io.netty.buffer.Unpooled;
import java.time.Duration;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.core.DBBaseFactory;
import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.pv.Field;
import org.epics.pvdata.pv.FieldBuilder;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;

/**
 *
 * @author administrador
 */
public class S7DBValveSolenoidFactory extends DBBaseFactory {

    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();

    @Override
    public DBRecord create(String recordName) {

        final NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        final FieldBuilder fb = fieldCreate.createFieldBuilder();

        Field fCmd = fb.setId("cmd").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iStatus", fieldCreate.createScalar(ScalarType.pvShort)).
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPB_Home", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPB_Work", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_Home", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_Work", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bHomeOn", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bWorkOn", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bSignalHome", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bSignalWork", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bInterlock", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();

        Field fSts = fb.setId("sts").
                add("bNoHomeFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoWorkFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bHomeFeedbackStillActive", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bWorkFeedbackStillActive", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();
        
        Field fPar = fb.setId("par").
                add("tInTimeout", fieldCreate.createScalar(ScalarType.pvInt)).
                add("strTimeout", fieldCreate.createScalar(ScalarType.pvString)).                  
                createStructure();         

        PVStructure pvStructure = ntScalarBuilder.
                value(ScalarType.pvShort).
                addDescriptor().
                add("id", fieldCreate.createScalar(ScalarType.pvString)).
                add("offset", fieldCreate.createScalar(ScalarType.pvString)).
                add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
                add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("cmd", fCmd).
                add("sts", fSts). 
                add("par", fPar).                 
                addAlarm().
                addTimeStamp().
                addDisplay().
                addControl().
                createPVStructure();

        DBRecord dbRecord = new DBS7ValSolenoidRecord(recordName, pvStructure);

        return dbRecord;
    }

    class DBS7ValSolenoidRecord extends DBRecord implements PlcItemListener {

        private int BUFFER_SIZE = 14;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, "
                + "cmd{iMode, bPB_ResetError, bPB_Home, bPB_Work, bPBEN_ResetError, "
                + "bPBEN_Home, bPBEN_Work},"
                + "sts{tInTimeout})";  

        private PVShort value;
        private PVShort write_value;
        private PVBoolean write_enable;

        //pvCmd        
        private PVShort iMode;
        private PVShort iErrorCode;
        private PVShort iStatus;
        private PVBoolean bPB_ResetError;
        private PVBoolean bPB_Home;
        private PVBoolean bPB_Work;
        private PVBoolean bPBEN_ResetError;
        private PVBoolean bPBEN_Home;
        private PVBoolean bPBEN_Work;
        private PVBoolean bHomeOn;
        private PVBoolean bWorkOn;
        private PVBoolean bSignalHome;
        private PVBoolean bSignalWork;
        private PVBoolean bError;
        private PVBoolean bInterlock;

        //pvSts          
        private PVBoolean bNoHomeFeedback;
        private PVBoolean bNoWorkFeedback;
        private PVBoolean bHomeFeedbackStillActive;
        private PVBoolean bWorkFeedbackStillActive;

        //pvPar        
        private PVInt tInTimeout;
        private PVString strTimeout;        

        private Duration lastDuration;         
        byte byTemp;

        public DBS7ValSolenoidRecord(String recordName, PVStructure pvStructure) {
            super(recordName, pvStructure);

            bFirtsRun = true;

            //Read command values            
            PVStructure pvCmd   = pvStructure.getStructureField("cmd");
            iMode               = pvCmd.getShortField("iMode");
            iErrorCode          = pvCmd.getShortField("iErrorCode");
            iStatus             = pvCmd.getShortField("iStatus");
            bPB_ResetError      = pvCmd.getBooleanField("bPB_ResetError");
            bPB_Home            = pvCmd.getBooleanField("bPB_Home");
            bPB_Work            = pvCmd.getBooleanField("bPB_Work");
            bPBEN_ResetError    = pvCmd.getBooleanField("bPBEN_ResetError");
            bPBEN_Home          = pvCmd.getBooleanField("bPBEN_Home");
            bPBEN_Work          = pvCmd.getBooleanField("bPBEN_Work");
            bHomeOn             = pvCmd.getBooleanField("bHomeOn");
            bWorkOn             = pvCmd.getBooleanField("bWorkOn");
            bSignalHome         = pvCmd.getBooleanField("bSignalHome");
            bSignalWork         = pvCmd.getBooleanField("bSignalWork");
            bError              = pvCmd.getBooleanField("bError");
            bInterlock          = pvCmd.getBooleanField("bInterlock");

            //Read status values                 
            PVStructure udtError = pvStructure.getStructureField("udtError");
            bNoHomeFeedback     = udtError.getBooleanField("bNoHomeFeedback");
            bNoWorkFeedback     = udtError.getBooleanField("bNoWorkFeedback");
            bHomeFeedbackStillActive = udtError.getBooleanField("bHomeFeedbackStillActive");
            bWorkFeedbackStillActive = udtError.getBooleanField("bWorkFeedbackStillActive");
            
            //Parameters values
            PVStructure pvPar   = pvStructure.getStructureField("par");    
            tInTimeout          = pvPar.getIntField("tInTimeout");
            strTimeout          = pvPar.getStringField("strTimeout");            
            
            fieldOffsets.clear();
            fieldOffsets.add(0, null); 
            fieldOffsets.add(1, null);
            fieldOffsets.add(2, null);
            fieldOffsets.add(3, new ImmutablePair(0,  (byte) -1));//iMode
            fieldOffsets.add(4, new ImmutablePair(6, (byte) 0));  //bPB_ResetError
            fieldOffsets.add(5, new ImmutablePair(6, (byte) 1));  //bPB_Home   
            fieldOffsets.add(6, new ImmutablePair(6, (byte) 2));  //bPB_Work
            fieldOffsets.add(7, new ImmutablePair(6, (byte) 3));  //bPBEN_ResetError
            fieldOffsets.add(8, new ImmutablePair(6, (byte) 4));  //bPBEN_Home 
            fieldOffsets.add(9, new ImmutablePair(6, (byte) 5));  //bPBEN_Work                
            fieldOffsets.add(10, null);
            fieldOffsets.add(11, new ImmutablePair(10,(byte) -1));//iInTimeout            
        }

        @Override
        public void process() {
            if (null != plcItem) {               
                if (write_enable.get()) {                          
                    try {
                        Duration userTime = Duration.parse(strTimeout.get());
                        if (!lastDuration.equals(userTime)) {
                            int writeValue = S7DBStaticHelper.durationToS7Time(userTime);
                            tInTimeout.put(writeValue);        
                        }
                    } catch (Exception ex) {
                        LOGGER.info("S7 TIME mal formed.");
                    }                    
                    super.process();                       
                }
            }            
        }

        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem;
            ParseOffset(this.getPVStructure().getStringField("offset").get());
            innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);
        }

        @Override
        public void update() {
            if (null != plcItem) {
                innerBuffer.resetReaderIndex();
                
                //Update pvCmd   
                if (innerBuffer.getShort(0) != iMode.get()) {
                    iMode.put(innerBuffer.getShort(0));
                }                
                iErrorCode.put(innerBuffer.getShort(2));              
                iStatus.put(innerBuffer.getShort(4));   

                byTemp = innerBuffer.getByte(6);
                if (isBitSet(byTemp, 0) != bPB_ResetError.get()) {                
                   bPB_ResetError.put(isBitSet(byTemp, 0));
                }
                if (isBitSet(byTemp, 1) != bPB_Home.get()) {  
                   bPB_Home.put(isBitSet(byTemp, 1));
                }
                if (isBitSet(byTemp, 2) != bPB_Work.get()) {                  
                   bPB_Work.put(isBitSet(byTemp, 2));
                }
                if (isBitSet(byTemp, 3) != bPBEN_ResetError.get()) {                  
                   bPBEN_ResetError.put(isBitSet(byTemp, 3));
                }
                if (isBitSet(byTemp, 4) != bPBEN_Home.get()) {                 
                   bPBEN_Home.put(isBitSet(byTemp, 4));
                }
                if (isBitSet(byTemp, 5) != bPBEN_Work.get()) {                   
                   bPBEN_Work.put(isBitSet(byTemp, 5));
                }
                bHomeOn.put(isBitSet(byTemp, 6));
                bWorkOn.put(isBitSet(byTemp, 7));

                byTemp = innerBuffer.getByte(7);
                bSignalHome.put(isBitSet(byTemp, 0));
                bSignalWork.put(isBitSet(byTemp, 1));
                bError.put(isBitSet(byTemp, 2));
                bInterlock.put(isBitSet(byTemp, 3));                
                
                //Update pvSts                   
                byTemp = innerBuffer.getByte(8);
                bNoHomeFeedback.put(isBitSet(byTemp, 0));
                bNoWorkFeedback.put(isBitSet(byTemp, 1));
                bHomeFeedbackStillActive.put(isBitSet(byTemp, 2));
                bWorkFeedbackStillActive.put(isBitSet(byTemp, 3));

                //Update pvPar
                if (innerBuffer.getInt(28) != tInTimeout.get()) {
                    tInTimeout.put(innerBuffer.getInt(28));
                    lastDuration = S7DBStaticHelper.s7TimeToDuration(tInTimeout.get());
                    strTimeout.put(lastDuration.toString());                    
                }               

            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }        
        
        
    }
}
