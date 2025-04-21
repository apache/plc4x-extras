/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.merlot.drv.s7.core;

import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.ArrayList;
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


public class S7DBMotorFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        
        Field fCmd = fb.setId("cmd").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).                
                add("iStatus", fieldCreate.createScalar(ScalarType.pvShort)).                  
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).  
                add("bPB_Forward", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPB_Reverse", fieldCreate.createScalar(ScalarType.pvBoolean)).   
                add("bPB_Stop", fieldCreate.createScalar(ScalarType.pvBoolean)).                 
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPBEN_Forward", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPBEN_Reverse", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPBEN_Stop", fieldCreate.createScalar(ScalarType.pvBoolean)).  
                add("bForwardOn", fieldCreate.createScalar(ScalarType.pvBoolean)).                
                add("bReverseOn", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bSignalForward", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bSignalReverse", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bInterlock", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();
                
        
        Field fSts = fb.setId("sts").               
                add("bMotorProtectorTripped", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bLocalDisconnectOff", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bClutchTripped", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoSignalForward", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoSignalReverse", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bMotorNotStopped", fieldCreate.createScalar(ScalarType.pvBoolean)).                
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
        
        DBRecord dbRecord = new DBS7MotorRecord(recordName,pvStructure);      
        
        return dbRecord;
    }

                                
    class DBS7MotorRecord extends DBRecord implements PlcItemListener {   
        
        private int BUFFER_SIZE = 14;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, "
                + "cmd{iMode, bPB_ResetError, bPB_Forward, bPB_Reverse,"
                + "bPB_Stop, bPBEN_ResetError, bPBEN_Forward, bPBEN_Reverse,"
                + "bPBEN_Stop},"
                + "par{tInTimeout})";
        
        
        private PVShort value; 
        private PVShort write_value;
        private PVBoolean write_enable; 
        
        //pvCmd
        private PVShort iMode; 
        private PVShort iErrorCode; 
        private PVShort iStatus;   
        private PVBoolean bPB_ResetError; 
        private PVBoolean bPB_Forward; 
        private PVBoolean bPB_Reverse; 
        private PVBoolean bPB_Stop;     
        private PVBoolean bPBEN_ResetError;         
        private PVBoolean bPBEN_Forward;
        private PVBoolean bPBEN_Reverse;
        private PVBoolean bPBEN_Stop;
        private PVBoolean bForwardOn;
        private PVBoolean bReverseOn;
        private PVBoolean bSignalForward;
        private PVBoolean bSignalReverse;
        private PVBoolean bError; 
        private PVBoolean bInterlock; 

        //pvSts        
        private PVBoolean bMotorProtectorTripped;         
        private PVBoolean bLocalDisconnectOff;  
        private PVBoolean bClutchTripped;  
        private PVBoolean bNoSignalForward;  
        private PVBoolean bNoSignalReverse;
        private PVBoolean bMotorNotStopped;  
        
        //pvPar
        private PVInt tInTimeout;         
        private PVString strTimeout;             
        
        private Duration lastDuration;         
        byte byTemp;
    
        public DBS7MotorRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            
            bFirtsRun = true;
                                 
            value = pvStructure.getShortField("value");
            write_enable = pvStructure.getBooleanField("write_enable");
            write_enable.put(false);
            
            //Read command values
            PVStructure pvCmd   = pvStructure.getStructureField("cmd");            
            iMode               = pvCmd.getShortField("iMode");
            iErrorCode          = pvCmd.getShortField("iErrorCode");            
            iStatus             = pvCmd.getShortField("iStatus");          
            bPB_ResetError      = pvCmd.getBooleanField("bPB_ResetError");            
            bPB_Forward         = pvCmd.getBooleanField("bPB_Forward");
            bPB_Reverse         = pvCmd.getBooleanField("bPB_Reverse");            
            bPB_Stop            = pvCmd.getBooleanField("bPB_Stop");
            bPBEN_ResetError    = pvCmd.getBooleanField("bPBEN_ResetError"); 
            bPBEN_Forward       = pvCmd.getBooleanField("bPBEN_Forward"); 
            bPBEN_Reverse       = pvCmd.getBooleanField("bPBEN_Reverse"); 
            bPBEN_Stop          = pvCmd.getBooleanField("bPBEN_Stop");  
            bForwardOn          = pvCmd.getBooleanField("bForwardOn");
            bReverseOn          = pvCmd.getBooleanField("bReverseOn");  
            bSignalForward      = pvCmd.getBooleanField("bSignalForward");
            bSignalReverse      = pvCmd.getBooleanField("bSignalReverse"); 
            bError              = pvCmd.getBooleanField("bError");
            bInterlock          = pvCmd.getBooleanField("bInterlock"); 
            
            //Read status values            
            PVStructure pvSts   = pvStructure.getStructureField("sts");
            bMotorProtectorTripped = pvSts.getBooleanField("bMotorProtectorTripped");
            bLocalDisconnectOff  = pvSts.getBooleanField("bLocalDisconnectOff"); 
            bClutchTripped      = pvSts.getBooleanField("bClutchTripped");
            bNoSignalForward    = pvSts.getBooleanField("bNoSignalForward");
            bNoSignalReverse    = pvSts.getBooleanField("bNoSignalReverse");
            bMotorNotStopped    = pvSts.getBooleanField("bMotorNotStopped"); 
            
            //Parameters values
            PVStructure pvPar   = pvStructure.getStructureField("par");    
            tInTimeout          = pvPar.getIntField("tInTimeout"); 
            strTimeout          = pvPar.getStringField("strTimeout");             
            
            fieldOffsets.clear();
            fieldOffsets.add(0,  null);
            fieldOffsets.add(1,  null);
            fieldOffsets.add(2,  null); 
            fieldOffsets.add(3,  new ImmutablePair(0,-1));   
            fieldOffsets.add(4,  new ImmutablePair(6,0)); 
            fieldOffsets.add(5,  new ImmutablePair(6,1));             
            fieldOffsets.add(6,  new ImmutablePair(6,2));
            fieldOffsets.add(7,  new ImmutablePair(6,3)); 
            fieldOffsets.add(8,  new ImmutablePair(6,4)); 
            fieldOffsets.add(9,  new ImmutablePair(6,5));
            fieldOffsets.add(10, new ImmutablePair(6,6)); 
            fieldOffsets.add(11, new ImmutablePair(6,7)); 
            fieldOffsets.add(12, new ImmutablePair(7,0)); 
            fieldOffsets.add(13, new ImmutablePair(7,1));             
            fieldOffsets.add(14, new ImmutablePair(7,2));
            fieldOffsets.add(15, new ImmutablePair(7,3)); 
            fieldOffsets.add(16, new ImmutablePair(7,4)); 
            fieldOffsets.add(17, new ImmutablePair(7,5));
            fieldOffsets.add(18, null);  
            fieldOffsets.add(19, new ImmutablePair(10,-1));            
        }    

        /**
         * For other special types of data, adaptation must be made here 
         * to write to the PLC.
         * 
         * 1. In the first write all fields are written
         * 2. In the second one only the changes are written.
         * 
         */
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

        //pvCmd_DigitalInput
        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem;
            ParseOffset( this.getPVStructure().getStringField("offset").get());            
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
                if (isBitSet(byTemp, 1) != bPB_Forward.get()) {                
                    bPB_Forward.put(isBitSet(byTemp, 1)); 
                }                
                if (isBitSet(byTemp, 2) != bPB_Reverse.get()) {  
                    bPB_Reverse.put(isBitSet(byTemp, 2));                      
                }               
                if (isBitSet(byTemp, 3) != bPB_Stop.get()) { 
                    bPB_Stop.put(isBitSet(byTemp, 3));                      
                }
                if (isBitSet(byTemp, 4) != bPBEN_ResetError.get()) {
                    bPBEN_ResetError.put(isBitSet(byTemp, 4));                     
                }                 
                if (isBitSet(byTemp, 5) != bPBEN_Forward.get()) {
                    bPBEN_Forward.put(isBitSet(byTemp, 5));                      
                }                
                if (isBitSet(byTemp, 6) != bPBEN_Reverse.get()) {
                    bPBEN_Reverse.put(isBitSet(byTemp, 6));                      
                }              
                if (isBitSet(byTemp, 7) != bPBEN_Stop.get()) {
                    bPBEN_Stop.put(isBitSet(byTemp, 7));                     
                }               

                byTemp = innerBuffer.getByte(7); 
                bForwardOn.put(isBitSet(byTemp, 0));                 
                bReverseOn.put(isBitSet(byTemp, 1));
                bSignalForward.put(isBitSet(byTemp, 2));
                bSignalReverse.put(isBitSet(byTemp, 3));
                bError.put(isBitSet(byTemp, 4));  
                bInterlock.put(isBitSet(byTemp, 5));                
                                
                //Update pvSts                 
                byTemp = innerBuffer.getByte(8);
                bMotorProtectorTripped.put(isBitSet(byTemp, 0));
                bLocalDisconnectOff.put(isBitSet(byTemp, 1));
                bClutchTripped.put(isBitSet(byTemp, 2));
                bNoSignalForward.put(isBitSet(byTemp, 3));
                bNoSignalReverse.put(isBitSet(byTemp, 4));
                bMotorNotStopped .put(isBitSet(byTemp, 5));                
                
                //Update pvPar
                if (innerBuffer.getInt(10) != tInTimeout.get()) {
                    tInTimeout.put(innerBuffer.getInt(10));
                    lastDuration = S7DBStaticHelper.s7TimeToDuration(tInTimeout.get());
                    strTimeout.put(lastDuration.toString());                    
                }

                if (bFirtsRun) {
                    bFirtsRun = false;
                    write_enable.put(true);
                }  

            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }
    
        
    }
           
}
