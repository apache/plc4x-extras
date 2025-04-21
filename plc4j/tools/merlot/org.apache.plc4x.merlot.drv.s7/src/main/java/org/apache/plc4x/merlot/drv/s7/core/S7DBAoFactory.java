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
import org.epics.pvdata.pv.PVByte;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class S7DBAoFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        
        Field fCmd = fb.setId("cmd").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).                
                add("rValue", fieldCreate.createScalar(ScalarType.pvFloat)).  
                add("rAutoValue", fieldCreate.createScalar(ScalarType.pvFloat)). 
                add("rManualValue", fieldCreate.createScalar(ScalarType.pvFloat)).                
                add("rEstopValue", fieldCreate.createScalar(ScalarType.pvFloat)).                 
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).                                 
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).    
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)).     
                add("bInterlock", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("iEstopFunction", fieldCreate.createScalar(ScalarType.pvShort)).                
                createStructure();
        
        Field fSts = fb.setId("sts").                
                add("bOutOfRange", fieldCreate.createScalar(ScalarType.pvBoolean)).                                 
                add("bConfigurationError", fieldCreate.createScalar(ScalarType.pvBoolean)).                  
                createStructure(); 
        
        Field fPar = fb.setId("par").                
                add("iSensorType", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iInEstopFunction", fieldCreate.createScalar(ScalarType.pvShort)). 
                add("rInEngUnitsMin", fieldCreate.createScalar(ScalarType.pvFloat)). 
                add("rInEngUnitsMax", fieldCreate.createScalar(ScalarType.pvFloat)).                  
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
        DBRecord dbRecord = new DBS7AoRecord(recordName,pvStructure);      
        return dbRecord;
    }

           
    class DBS7AoRecord extends DBRecord implements PlcItemListener {   
        
        private int BUFFER_SIZE = 36;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, "
                + "cmd{iMode, rManualValue, bPB_ResetError, bPBEN_ResetError},"
                + "par{iSensorType, rInEngUnitsMin, rInEngUnitsMax})";   
    
    
        private PVShort value; 
        private PVShort write_value;
        private PVBoolean write_enable; 
        
        //pvCmd       
        private PVShort iMode; 
        private PVShort iErrorCode;          
        private PVFloat rValue;        
        private PVFloat rAutoValue;         
        private PVFloat rManualValue; 
        private PVFloat rEstopValue;        
        private PVBoolean bPB_ResetError; 
        private PVBoolean bPBEN_ResetError;   
        private PVBoolean bError; 
        private PVBoolean bInterlock;         
        private PVShort iEstopFunction;
        
        //pvSts       
        private PVBoolean bOutOfRange; 
        private PVBoolean bConfigurationError;
        
        //pvPar        
        private PVShort iSensorType;
        private PVFloat rInEngUnitsMin;        
        private PVFloat rInEngUnitsMax; 
        
               
        
        byte byTemp;
    
        public DBS7AoRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            
            value = pvStructure.getShortField("value");            
            write_enable = pvStructure.getBooleanField("write_enable");
            write_enable.put(false);
                        
            //Read command values
            PVStructure pvCmd   = pvStructure.getStructureField("cmd");              
            iMode               = pvCmd.getShortField("iMode");
            iErrorCode          = pvCmd.getShortField("iErrorCode");             
            rValue              = pvCmd.getFloatField("rValue");             
            rAutoValue          = pvCmd.getFloatField("rAutoValue"); 
            rManualValue        = pvCmd.getFloatField("rManualValue");
            rEstopValue         = pvCmd.getFloatField("rEstopValue");                                 
            bPB_ResetError      = pvCmd.getBooleanField("bPB_ResetError");
            bPBEN_ResetError    = pvCmd.getBooleanField("bPBEN_ResetError");
            bError              = pvCmd.getBooleanField("bError");
            bInterlock          = pvCmd.getBooleanField("bInterlock");             
            iEstopFunction      =  pvCmd.getShortField("iEstopFunction");
            
            //Read status values            
            PVStructure pvSts = pvStructure.getStructureField("sts");            
            bOutOfRange         =  pvSts.getBooleanField("bOutOfRange");
            bConfigurationError =  pvSts.getBooleanField("bConfigurationError");
            
            //Parameters values
            PVStructure pvPar   = pvStructure.getStructureField("par");              
            iSensorType         = pvPar.getShortField("iSensorType");
            rInEngUnitsMin      = pvPar.getFloatField("rInEngUnitsMin");    
            rInEngUnitsMax      = pvPar.getFloatField("rInEngUnitsMax");            
            
            fieldOffsets.clear();
            fieldOffsets.add(0,  null); 
            fieldOffsets.add(1,  null);
            fieldOffsets.add(2,  null);
            fieldOffsets.add(3,  new ImmutablePair(0,  (byte) -1)); //iMode
            fieldOffsets.add(4,  new ImmutablePair(12, (byte) -1)); //rManualValue
            fieldOffsets.add(5,  new ImmutablePair(20, (byte) 0));  //bPB_ResetError  
            fieldOffsets.add(6,  new ImmutablePair(20,(byte) 1));  //bPBEN_ResetError   
            fieldOffsets.add(7,  null); 
            fieldOffsets.add(8,  new ImmutablePair(26, (byte) -1)); //iSensorType
            fieldOffsets.add(9,  new ImmutablePair(28, (byte) -1)); //rInEngUnitsMin
            fieldOffsets.add(10, new ImmutablePair(32,(byte) -1)); //rInEngUnitsMax            
            
            
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
                
                rValue.put(innerBuffer.getFloat(4));                 
                rAutoValue.put(innerBuffer.getFloat(8));
                
                if (innerBuffer.getShort(12) != rManualValue.get()) {
                    rManualValue.put(innerBuffer.getFloat(12));     
                };
                
                rEstopValue.put(innerBuffer.getFloat(16)); 
                
                byTemp = innerBuffer.getByte(20);
                if (isBitSet(byTemp, 0) != bPB_ResetError.get()) {
                    bPB_ResetError.put(isBitSet(byTemp, 0));                    
                }
                if (isBitSet(byTemp, 1) != bPBEN_ResetError.get()) {
                    bPBEN_ResetError.put(isBitSet(byTemp, 1));                     
                }  
                bError.put(isBitSet(byTemp, 2));
                bInterlock.put(isBitSet(byTemp, 3));
                
                if (innerBuffer.getShort(22) != iEstopFunction.get()) {                
                    iEstopFunction.put(innerBuffer.getShort(22));        
                }
                                
                //Update pvSts                
                byTemp = innerBuffer.getByte(24);
                bOutOfRange.put(isBitSet(byTemp, 0)); 
                bConfigurationError.put(isBitSet(byTemp, 1));   
                
                //Update pvPar
                if (innerBuffer.getShort(26) != iSensorType.get()) {
                    iSensorType.put(innerBuffer.getShort(26));
                }
                if (innerBuffer.getFloat(28) != rInEngUnitsMin.get()) {
                    rInEngUnitsMin.put(innerBuffer.getFloat(28));
                } 
                if (innerBuffer.getFloat(32) != rInEngUnitsMax.get()) {
                    rInEngUnitsMax.put(innerBuffer.getFloat(32));
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
