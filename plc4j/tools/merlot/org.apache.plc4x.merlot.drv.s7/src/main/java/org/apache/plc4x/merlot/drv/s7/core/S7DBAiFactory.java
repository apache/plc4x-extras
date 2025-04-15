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
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Structure;


public class S7DBAiFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        
        Field fCmd = fb.setId("cmd").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).                
                add("iStatus", fieldCreate.createScalar(ScalarType.pvShort)). 
                add("rActiveValue", fieldCreate.createScalar(ScalarType.pvFloat)).                 
                add("rInputValue", fieldCreate.createScalar(ScalarType.pvFloat)).  
                add("rManualValue", fieldCreate.createScalar(ScalarType.pvFloat)).                 
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).                                 
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).    
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();
        
        Field fSts = fb.setId("sts").              
                add("bLowLowAlarm", fieldCreate.createScalar(ScalarType.pvBoolean)).                                 
                add("bHighHighAlarm", fieldCreate.createScalar(ScalarType.pvBoolean)).    
                add("bInvalid", fieldCreate.createScalar(ScalarType.pvBoolean)).  
                createStructure();
        
        Field fPar = fb.setId("par").              
                add("iSensorType", fieldCreate.createScalar(ScalarType.pvShort)).
                add("rInEngUnitsMin", fieldCreate.createScalar(ScalarType.pvFloat)).  
                add("rInEngUnitsMax", fieldCreate.createScalar(ScalarType.pvFloat)). 
                add("rInLowLow", fieldCreate.createScalar(ScalarType.pvFloat)).  
                add("rInLow", fieldCreate.createScalar(ScalarType.pvFloat)).  
                add("rInHigh", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("rInHighHigh", fieldCreate.createScalar(ScalarType.pvFloat)). 
                add("rInLowLowDeadband", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("rInLowDeadband", fieldCreate.createScalar(ScalarType.pvFloat)).  
                add("rInHighDeadband", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("rInHighHighDeadband", fieldCreate.createScalar(ScalarType.pvFloat)).                
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
        
        DBRecord dbRecord = new DBS7AiRecord(recordName,pvStructure);      
        return dbRecord;
    }
           
    class DBS7AiRecord extends DBRecord {   
    
        private int BUFFER_SIZE = 64;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, "
                + "cmd{iMode, rManualValue, bPB_ResetError,bPBEN_ResetError},"
                + "par{iSensorType, rInEngUnitsMin, rInEngUnitsMax,rInLowLow,"
                + "rInLow, rInHigh, rInHighHigh, rInLowLowDeadband, rInLowDeadband,"
                + "rInHighDeadband, rInHighHighDeadband})";         
       
        private PVShort value; 
        private PVBoolean write_enable; 
        
        //pvCmd
        private PVShort iMode; 
        private PVShort iErrorCode;
        private PVShort iStatus; 
        
        private PVFloat rActiveValue;
        private PVFloat rInputValue; 
        private PVFloat rManualValue;         
        
        private PVBoolean bPB_ResetError;
        private PVBoolean bPBEN_ResetError;                
        private PVBoolean bError; 
        
        //pvSts
        private PVBoolean bLowLowAlarm;   
        private PVBoolean bHighHighAlarm;
        private PVBoolean bInvalid; 
        
        //pvPar
        private PVShort iSensorType;
        private PVFloat rInEngUnitsMin;        
        private PVFloat rInEngUnitsMax; 
        private PVFloat rInLowLow; 
        private PVFloat rInLow;   
        private PVFloat rInHigh; 
        private PVFloat rInHighHigh;
        private PVFloat rInLowLowDeadband; 
        private PVFloat rInLowDeadband; 
        private PVFloat rInHighDeadband;
        private PVFloat rInHighHighDeadband;         
              
        byte byTemp;
    
        public DBS7AiRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            fieldOffsets.clear();      
            value = pvStructure.getShortField("value"); 
            write_enable = pvStructure.getBooleanField("write_enable");
            
            //Read command values
            PVStructure pvCms   = pvStructure.getStructureField("cms");              
            iMode               = pvCms.getShortField("iMode");
            iErrorCode          = pvCms.getShortField("iErrorCode");
            iStatus             = pvCms.getShortField("iStatus");              
            rActiveValue        = pvCms.getFloatField("rActiveValue"); 
            rInputValue         = pvCms.getFloatField("rInputValue");                         
            rManualValue        = pvCms.getFloatField("rManualValue");
            bPB_ResetError      = pvCms.getBooleanField("bPB_ResetError");
            bPBEN_ResetError    = pvCms.getBooleanField("bPBEN_ResetError");
            bError              = pvCms.getBooleanField("bError");               

            //Read status values
            PVStructure pvSts   = pvStructure.getStructureField("sts");
            bLowLowAlarm        = pvSts.getBooleanField("bLowLowAlarm");
            bHighHighAlarm      = pvSts.getBooleanField("bHighHighAlarm");
            bInvalid            = pvSts.getBooleanField("bInvalid");                        
            
            //Parameters values
            PVStructure pvPar   = pvStructure.getStructureField("par");            
            iSensorType         = pvPar.getShortField("iSensorType");
            rInEngUnitsMin      = pvPar.getFloatField("rInEngUnitsMin");    
            rInEngUnitsMax      = pvPar.getFloatField("rInEngUnitsMax"); 
            rInLowLow           = pvPar.getFloatField("rInLowLow");
            rInLow              = pvPar.getFloatField("rInLow");
            rInHigh             = pvPar.getFloatField("rInHigh "); 
            rInHighHigh         = pvPar.getFloatField("rInHighHigh");
            rInLowLowDeadband   = pvPar.getFloatField("rInLowLowDeadband"); 
            rInLowDeadband      = pvPar.getFloatField("rInLowDeadband"); 
            rInHighDeadband     = pvPar.getFloatField("rInHighDeadband");
            rInHighHighDeadband = pvPar.getFloatField("rInHighHighDeadband");            
              
            fieldOffsets.clear();
            fieldOffsets.add(0,  null); 
            fieldOffsets.add(1,  null);
            fieldOffsets.add(2,  null);
            fieldOffsets.add(3,  new ImmutablePair(0,  (byte) -1));  //iMode
            fieldOffsets.add(4,  new ImmutablePair(14, (byte) -1));  //rManualValue
            fieldOffsets.add(5,  new ImmutablePair(18, (byte) 0));   //bPB_ResetError  
            fieldOffsets.add(6,  new ImmutablePair(18, (byte) 1));   //bPBEN_ResetError 
            fieldOffsets.add(7,  null);  
            fieldOffsets.add(8,  new ImmutablePair(22,  (byte) -1));  //iSensorType           
            fieldOffsets.add(9,  new ImmutablePair(24,  (byte) -1));      
            fieldOffsets.add(10, new ImmutablePair(28,  (byte) -1));           
            fieldOffsets.add(11, new ImmutablePair(32,  (byte) -1));
            fieldOffsets.add(12, new ImmutablePair(36,  (byte) -1));           
            fieldOffsets.add(13, new ImmutablePair(40,  (byte) -1));
            fieldOffsets.add(14, new ImmutablePair(44,  (byte) -1));           
            fieldOffsets.add(15, new ImmutablePair(48,  (byte) -1));
            fieldOffsets.add(16, new ImmutablePair(52,  (byte) -1));           
            fieldOffsets.add(17, new ImmutablePair(56,  (byte) -1));  
            fieldOffsets.add(18, new ImmutablePair(60,  (byte) -1));             
        }    

        /**
         * Implement real time data to the record.
         * The main code is here.
         */
        public void process()
        {
            if (null != plcItem) {               
                if (write_enable.get()) {                                             
                    super.process();                      
                }
            }              
        }

        //pvCms_DigitalInput
        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem; 
            try {
                ParseOffset( this.getPVStructure().getStringField("offset").get()); 
                innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);
            } catch (Exception ex) {
                //TODO: Logger
                //System.out.println("Exception: " + ex.getMessage());
            }
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
                
                rActiveValue.put(innerBuffer.getFloat(6)); 
                rInputValue.put(innerBuffer.getFloat(10)); 
                
                if (innerBuffer.getFloat(14) != rManualValue.get()) {
                    rManualValue.put(innerBuffer.getFloat(14));
                }                
                                
                byTemp = innerBuffer.getByte(18);
                if (isBitSet(byTemp, 0) != bPB_ResetError.get()) {
                    bPB_ResetError.put(isBitSet(byTemp, 0));                    
                }
                if (isBitSet(byTemp, 1) != bPBEN_ResetError.get()) {
                    bPBEN_ResetError.put(isBitSet(byTemp, 1));                     
                }                 
                bError.put(isBitSet(byTemp, 2));                

                //Update pvSts
                byTemp = innerBuffer.getByte(20);                 
                bLowLowAlarm.put(isBitSet(byTemp, 0));                  
                bHighHighAlarm.put(isBitSet(byTemp, 1));
                bInvalid.put(isBitSet(byTemp, 2));                  
                
                //Update pvPar
                if (innerBuffer.getShort(22) != iSensorType.get()) {
                    iSensorType.put(innerBuffer.getShort(22));
                }
                if (innerBuffer.getFloat(24) != rInEngUnitsMin.get()) {
                    rInEngUnitsMin.put(innerBuffer.getFloat(24));
                } 
                if (innerBuffer.getFloat(28) != rInEngUnitsMax.get()) {
                    rInEngUnitsMax.put(innerBuffer.getFloat(28));
                } 
                if (innerBuffer.getFloat(32) != rInLowLow.get()) {
                    rInLowLow.put(innerBuffer.getFloat(32));
                }
                if (innerBuffer.getFloat(36) != rInLow.get()) {
                    rInLow.put(innerBuffer.getFloat(36));
                }  
                if (innerBuffer.getFloat(40) != rInHigh.get()) {
                    rInHigh.put(innerBuffer.getFloat(40));
                }
                if (innerBuffer.getFloat(44) != rInHighHigh.get()) {
                    rInHighHigh.put(innerBuffer.getFloat(44));
                } 
                if (innerBuffer.getFloat(48) != rInLowLowDeadband.get()) {
                    rInLowLowDeadband.put(innerBuffer.getFloat(48));
                }
                if (innerBuffer.getFloat(52) != rInLowDeadband.get()) {
                    rInLowDeadband.put(innerBuffer.getFloat(52));
                }                 
                if (innerBuffer.getFloat(56) != rInHighDeadband.get()) {
                    rInHighDeadband.put(innerBuffer.getFloat(56));
                }
                if (innerBuffer.getFloat(60) != rInHighHighDeadband.get()) {
                    rInHighHighDeadband.put(innerBuffer.getFloat(60));
                } 
                
                if (bFirtsRun) {
                    bFirtsRun = false;
                }
                
            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }
        
        
    }
           
}
