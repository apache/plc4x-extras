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
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class S7DBMotorFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        
        Field cmd = fb.setId("cmd").
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
                
        
        Field sts = fb.setId("sts").               
                add("bMotorProtectorTripped", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bLocalDisconnectOff", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bClutchTripped", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoSignalForward", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoSignalReverse", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bMotorNotStopped", fieldCreate.createScalar(ScalarType.pvBoolean)).                
                createStructure();   
        
        Field par =  fb.setId("par").   
                add("tTimeOut", fieldCreate.createScalar(ScalarType.pvInt)).                
                
                createStructure();           
        
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvShort).
            addDescriptor().
            add("cmd", cmd).
            add("sts", sts).
            add("par", par).                
            add("id", fieldCreate.createScalar(ScalarType.pvString)).  
            add("offset", fieldCreate.createScalar(ScalarType.pvString)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).  
            add("write_value", fieldCreate.createScalar(ScalarType.pvShort)).                 
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
        private static final String MONITOR_TF_FIELDS = "field(write_enable, par{tTimeOut})";
        
        
        private PVShort value; 
        private PVShort write_value;
        private PVBoolean write_enable; 
        
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

        private PVBoolean bMotorProtectorTripped;         
        private PVBoolean bLocalDisconnectOff;  
        private PVBoolean bClutchTripped;  
        private PVBoolean bNoSignalForward;  
        private PVBoolean bNoSignalReverse;
        private PVBoolean bMotorNotStopped;  
        
        private PVInt tTimeOut;         
             
        
        byte byTemp;
    
        public DBS7MotorRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            
            bFirtsRun = true;
            
            fieldOffsets = new ArrayList<>();
            fieldOffsets.add(0, null);
            fieldOffsets.add(1, null);
            fieldOffsets.add(2, null); 
            fieldOffsets.add(3, new ImmutablePair(0,-1));   
            fieldOffsets.add(4, new ImmutablePair(6,0)); 
            fieldOffsets.add(5, new ImmutablePair(6,1));             
            fieldOffsets.add(6, new ImmutablePair(6,2));
            fieldOffsets.add(7, new ImmutablePair(6,3));            
            
            value = pvStructure.getShortField("value");
            write_value = pvStructure.getShortField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");
            
            //Read command values
            PVStructure pvStructureCmd = pvStructure.getStructureField("cmd");            
            iMode = pvStructureCmd.getShortField("iMode");
            iErrorCode = pvStructureCmd.getShortField("iErrorCode");            
            iStatus = pvStructureCmd.getShortField("iStatus");          
            bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");            
            bPB_Forward = pvStructureCmd.getBooleanField("bPB_Forward");
            bPB_Reverse = pvStructureCmd.getBooleanField("bPB_Reverse");            
            bPB_Stop = pvStructureCmd.getBooleanField("bPB_Stop");
            bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError"); 
            bPBEN_Forward = pvStructureCmd.getBooleanField("bPBEN_Forward"); 
            bPBEN_Reverse = pvStructureCmd.getBooleanField("bPBEN_Reverse"); 
            bPBEN_Stop = pvStructureCmd.getBooleanField("bPBEN_Stop");  
            bForwardOn = pvStructureCmd.getBooleanField("bForwardOn");
            bReverseOn = pvStructureCmd.getBooleanField("bReverseOn");  
            bSignalForward = pvStructureCmd.getBooleanField("bSignalForward");
            bSignalReverse = pvStructureCmd.getBooleanField("bSignalReverse"); 
            bError = pvStructureCmd.getBooleanField("bError");
            bInterlock = pvStructureCmd.getBooleanField("bInterlock"); 
            
            //Read status values            
            PVStructure pvStructureSts = pvStructure.getStructureField("sts");
            bMotorProtectorTripped = pvStructureSts.getBooleanField("bMotorProtectorTripped");
            bLocalDisconnectOff = pvStructureSts.getBooleanField("bLocalDisconnectOff"); 
            bClutchTripped = pvStructureSts.getBooleanField("bClutchTripped");
            bNoSignalForward = pvStructureSts.getBooleanField("bNoSignalForward");
            bNoSignalReverse = pvStructureSts.getBooleanField("bNoSignalReverse");
            bMotorNotStopped = pvStructureSts.getBooleanField("bMotorNotStopped"); 
            
            //Write command values            
            PVStructure pvStructurePar = pvStructure.getStructureField("par"); 
            tTimeOut = pvStructurePar.getIntField("tTimeOut");
            
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
                //cmd
                iMode.put(innerBuffer.getShort(0));
                iErrorCode.put(innerBuffer.getShort(2));                
                iStatus.put(innerBuffer.getShort(4));
                
                byTemp = innerBuffer.getByte(6);                
                bPB_ResetError.put(isBitSet(byTemp, 0));
                bPB_Forward.put(isBitSet(byTemp, 1));                
                bPB_Reverse.put(isBitSet(byTemp, 2));  
                bPB_Stop.put(isBitSet(byTemp, 3));                  
                bPBEN_ResetError.put(isBitSet(byTemp, 4));                
                bPBEN_Forward.put(isBitSet(byTemp, 5));                
                bPBEN_Reverse.put(isBitSet(byTemp, 6));                 
                bPBEN_Stop.put(isBitSet(byTemp, 7)); 
                
                        
                byTemp = innerBuffer.getByte(7); 
                bForwardOn.put(isBitSet(byTemp, 0));                 
                bReverseOn.put(isBitSet(byTemp, 1));
                bSignalForward.put(isBitSet(byTemp, 2));
                bSignalReverse.put(isBitSet(byTemp, 3));
                bError.put(isBitSet(byTemp, 4));  
                bInterlock.put(isBitSet(byTemp, 5));

                //sts
                byTemp = innerBuffer.getByte(8);
                bMotorProtectorTripped.put(isBitSet(byTemp, 0));
                bLocalDisconnectOff.put(isBitSet(byTemp, 1));
                bClutchTripped.put(isBitSet(byTemp, 2));
                bNoSignalForward.put(isBitSet(byTemp, 3));
                bNoSignalReverse.put(isBitSet(byTemp, 4));
                bMotorNotStopped .put(isBitSet(byTemp, 5));
                
                //par
                tTimeOut.put(innerBuffer.getInt(10));
            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }
    
        
    }
           
}
