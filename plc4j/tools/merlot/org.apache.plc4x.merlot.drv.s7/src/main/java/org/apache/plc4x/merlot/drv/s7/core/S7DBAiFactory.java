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
        
        Field cmd = fb.setId("cmd").
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
        
        Field sts = fb.setId("sts").              
                add("bLowLowAlarm", fieldCreate.createScalar(ScalarType.pvBoolean)).                                 
                add("bHighHighAlarm", fieldCreate.createScalar(ScalarType.pvBoolean)).    
                add("bInvalid", fieldCreate.createScalar(ScalarType.pvBoolean)).  
                createStructure();

        Field par =  fb.setId("par"). 
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("rManualValue", fieldCreate.createScalar(ScalarType.pvFloat)).                 
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                
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
            add("write_value", fieldCreate.createScalar(ScalarType.pvShort)). 
            add("cmd", cmd).
            add("sts", sts).   
            add("par", par).  
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();          
        DBRecord dbRecord = new DBS7AiRecord(recordName,pvStructure);      
        return dbRecord;
    }

           
    class DBS7AiRecord extends DBRecord implements PlcItemListener {   
    
        private int BUFFER_SIZE = 64;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, "
                + "out{iMode, rManualValue, bPB_ResetError,"
                + "iSensorType, rInEngUnitsMin, rInEngUnitsMax,"
                + "rInLowLow, rInLow, rInHigh,"
                + "rInHighHigh, rInLowLowDeadband, rInLowDeadband,"
                + "rInHighDeadband, rInHighHighDeadband})";         
   
    
        private PVShort value; 
        private PVShort write_value;
        private PVBoolean write_enable; 
        
        private PVShort iMode; 
        private PVShort iErrorCode;
        private PVShort iStatus; 
        
        private PVFloat rActiveValue;
        private PVFloat rInputValue; 
        private PVFloat rManualValue;         
        
        private PVBoolean bPB_ResetError;
        private PVBoolean bPBEN_ResetError;                
        private PVBoolean bError; 
        
        private PVBoolean bLowLowAlarm;   
        private PVBoolean bHighHighAlarm;
        private PVBoolean bInvalid; 
        
        private PVShort out_iMode;         
        private PVFloat out_rManualValue;         
        private PVBoolean out_bPB_ResetError; 
        
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
            write_value = pvStructure.getShortField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");
            
            //Read command values
            PVStructure pvStructureCmd = pvStructure.getStructureField("cmd");              
            iMode = pvStructureCmd.getShortField("iMode");
            iErrorCode = pvStructureCmd.getShortField("iErrorCode");
            iStatus = pvStructureCmd.getShortField("iStatus");              
            rActiveValue = pvStructureCmd.getFloatField("rActiveValue"); 
            rInputValue = pvStructureCmd.getFloatField("rInputValue");                         
            rManualValue = pvStructureCmd.getFloatField("rManualValue");
            bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
            bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
            bError = pvStructureCmd.getBooleanField("bError");               

            //Read status values
            PVStructure pvStructureSts = pvStructure.getStructureField("sts");
            bLowLowAlarm =  pvStructureSts.getBooleanField("bLowLowAlarm");
            bHighHighAlarm =  pvStructureSts.getBooleanField("bHighHighAlarm");
            bInvalid =  pvStructureSts.getBooleanField("bInvalid");
            
            //Write command and parameters values
            PVStructure pvStructurePar = pvStructure.getStructureField("par");
            out_iMode = pvStructurePar.getShortField("iMode");     
            out_rManualValue = pvStructurePar.getFloatField("rManualValue");  
            out_bPB_ResetError = pvStructurePar.getBooleanField("bPB_ResetError");
            
            iSensorType = pvStructurePar.getShortField("iSensorType");
            rInEngUnitsMin = pvStructurePar.getFloatField("rInEngUnitsMin");
            rInEngUnitsMax = pvStructurePar.getFloatField("rInEngUnitsMax");  
            rInLowLow = pvStructurePar.getFloatField("rInLowLow"); 
            rInLow = pvStructurePar.getFloatField("rInLow");
            rInHigh = pvStructurePar.getFloatField("rInHigh");       
            rInHighHigh = pvStructurePar.getFloatField("rInHighHigh");
            rInLowLowDeadband = pvStructurePar.getFloatField("rInLowLowDeadband");        
            rInLowDeadband = pvStructurePar.getFloatField("rInLowDeadband"); 
            rInHighDeadband = pvStructurePar.getFloatField("rInHighDeadband");
            rInHighHighDeadband = pvStructurePar.getFloatField("rInHighHighDeadband");            
            
            fieldOffsets.add(0, null); 
            fieldOffsets.add(1, null);
            fieldOffsets.add(2, null);
            fieldOffsets.add(3, new ImmutablePair(0,  (byte) -1));   //iMode
            fieldOffsets.add(4, new ImmutablePair(14, (byte) -1));  //rManualValue
            fieldOffsets.add(5, new ImmutablePair(18, (byte) 0));   //bPB_ResetError  
            fieldOffsets.add(6, new ImmutablePair(22, (byte) -1));  //iSensorType   
            fieldOffsets.add(7, new ImmutablePair(24, (byte) -1));  //rInEngUnitsMin 
            fieldOffsets.add(8, new ImmutablePair(28, (byte) -1));  //rInEngUnitsMax 
            fieldOffsets.add(9, new ImmutablePair(32, (byte) -1));  //rInLowLow
            fieldOffsets.add(10, new ImmutablePair(36,(byte) -1)); //rInLow
            fieldOffsets.add(11, new ImmutablePair(40,(byte) -1)); //rInHigh 
            fieldOffsets.add(12, new ImmutablePair(44,(byte) -1)); //rInHighHigh
            fieldOffsets.add(13, new ImmutablePair(48,(byte) -1)); //rInLowLowDeadband 
            fieldOffsets.add(14, new ImmutablePair(52,(byte) -1)); //rInLowDeadband 
            fieldOffsets.add(15, new ImmutablePair(56,(byte) -1)); //rInHighDeadband  
            fieldOffsets.add(16, new ImmutablePair(60,(byte) -1)); //rInHighHighDeadband             
        }    

        /**
         * Implement real time data to the record.
         * The main code is here.
         */
        public void process()
        {
            if (null != plcItem) {               
                if (write_enable.get()) {                          
                    if (iMode.get() != out_iMode.get()) out_iMode.put(iMode.get());                     
                    super.process();                      
                }
            }              
        }

        //udtHMI_DigitalInput
        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem; 
            ParseOffset( this.getPVStructure().getStringField("offset").get());            
            innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);
            innerWriteBuffer = Unpooled.copiedBuffer(innerBuffer);
        }

        @Override
        public void detach() {
            this.plcItem  = null;
        }

        @Override
        public void update() {    
            if (null != plcItem) {
                innerBuffer.resetReaderIndex();
                iMode.put(innerBuffer.getShort(0));
                iErrorCode.put(innerBuffer.getShort(2));  
                iStatus.put(innerBuffer.getShort(4)); 
                
                rActiveValue.put(innerBuffer.getFloat(6)); 
                rInputValue.put(innerBuffer.getFloat(10)); 
                
                if (bFirtsRun) {
                    rManualValue.put(innerBuffer.getFloat(14));
                }
                
                byTemp = innerBuffer.getByte(18);
                bPB_ResetError.put(isBitSet(byTemp, 0));
                bPBEN_ResetError.put(isBitSet(byTemp, 1));                
                bError.put(isBitSet(byTemp, 2));  
                
                byTemp = innerBuffer.getByte(20);                 
                bLowLowAlarm.put(isBitSet(byTemp, 0));                  
                bHighHighAlarm.put(isBitSet(byTemp, 1));
                bInvalid.put(isBitSet(byTemp, 2)); 
                
                if (bFirtsRun) {
                    iSensorType.put(innerBuffer.getShort(22));
                    rInEngUnitsMin.put(innerBuffer.getFloat(24));
                    rInEngUnitsMax.put(innerBuffer.getFloat(28));
                    rInLowLow.put(innerBuffer.getFloat(32)); 
                    rInLow.put(innerBuffer.getFloat(36));
                    rInHigh.put(innerBuffer.getFloat(40));
                    rInHighHigh.put(innerBuffer.getFloat(44));
                    rInLowLowDeadband.put(innerBuffer.getFloat(48)); 
                    rInLowDeadband.put(innerBuffer.getFloat(52));
                    rInHighDeadband.put(innerBuffer.getFloat(56)); 
                    rInHighHighDeadband.put(innerBuffer.getFloat(60));                     
                }
                
            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }
        
        
    }
           
}
