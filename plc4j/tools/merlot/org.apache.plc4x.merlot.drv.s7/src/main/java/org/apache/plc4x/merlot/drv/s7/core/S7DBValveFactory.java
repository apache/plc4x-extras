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
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class S7DBValveFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        final NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        final FieldBuilder fb = fieldCreate.createFieldBuilder();
        
        
        Field cmd = fb.setId("cmd_t").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).                
                add("iStatus", fieldCreate.createScalar(ScalarType.pvShort)).                  
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).  
                add("bPB_Home", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPB_Work", fieldCreate.createScalar(ScalarType.pvBoolean)).                   
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPBEN_Home", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPBEN_Work", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPBEN_Stop", fieldCreate.createScalar(ScalarType.pvBoolean)).  
                add("bHomeOn", fieldCreate.createScalar(ScalarType.pvBoolean)).                
                add("bWorkOn", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bSignalHome", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bSignalWork", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bInterlock", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                createStructure();
        
        Field sts =  fb.setId("sts_t").               
                add("bNoHomeFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bNoWorkFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bHomeFeedbackStillActive", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bWorkFeedbackStillActive", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();  
        
        Field out =  fb.setId("output_t").   
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).                
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPB_Home", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPB_Work", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                createStructure();          
        
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvShort).
            addDescriptor().
            add("cmd", cmd).
            add("sts", sts).
            add("out", out).                
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
        
        DBRecord dbRecord = new DBS7ValveRecord(recordName,pvStructure);      
        
        return dbRecord;
    }

           
    class DBS7ValveRecord extends DBRecord implements PlcItemListener {   
        
        private int BUFFER_SIZE = 9;        
        private static final String MONITOR_TF_FIELDS = "field(write_enable, out{iMode, bPB_ResetError, bPB_Home, bPB_Work})";

        private PVShort value; 
        private PVShort write_value;
        private PVBoolean write_enable; 
        
        private PVShort iMode; 
        private PVShort iErrorCode; 
        private PVShort iStatus;         
        
        private PVBoolean bPB_ResetError; 
        private PVBoolean bPB_Home; 
        private PVBoolean bPB_Work; 
        private PVBoolean bPBEN_ResetError;        
        private PVBoolean bPBEN_Home;
        private PVBoolean bPBEN_Work;
        private PVBoolean bPBEN_Stop;        
        
        private PVBoolean bHomeOn; 
        private PVBoolean bWorkOn;   
        private PVBoolean bSignalHome;  
        private PVBoolean bSignalWork;  
        private PVBoolean bError; 
        private PVBoolean bInterlock; 

        private PVBoolean bNoHomeFeedback;
        private PVBoolean bNoWorkFeedback;  
        private PVBoolean bHomeFeedbackStillActive;
        private PVBoolean bWorkFeedbackStillActive;

        private PVShort out_iMode;         
        private PVBoolean out_bPB_ResetError; 
        private PVBoolean out_bPB_Home; 
        private PVBoolean out_bPB_Work;
        
        byte byTemp;
    
        public DBS7ValveRecord(String recordName,PVStructure pvStructure) {
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
            
            value = pvStructure.getShortField("value");
            write_value = pvStructure.getShortField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");
            
            //Read command values
            PVStructure pvStructureCmd = pvStructure.getStructureField("cmd");             
            iMode = pvStructureCmd.getShortField("iMode");
            iErrorCode = pvStructureCmd.getShortField("iErrorCode");            
            iStatus = pvStructureCmd.getShortField("iStatus");                                              
            bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
            bPB_Home = pvStructureCmd.getBooleanField("bPB_Home");
            bPB_Work = pvStructureCmd.getBooleanField("bPB_Work");
            bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
            bPBEN_Home = pvStructureCmd.getBooleanField("bPBEN_Home");            
            bPBEN_Work = pvStructureCmd.getBooleanField("bPBEN_Work");
            bPBEN_Stop = pvStructureCmd.getBooleanField("bPBEN_Stop");            
            bHomeOn = pvStructureCmd.getBooleanField("bHomeOn");            
            bWorkOn = pvStructureCmd.getBooleanField("bWorkOn");
            bSignalHome = pvStructureCmd.getBooleanField("bSignalHome");            
            bSignalWork = pvStructureCmd.getBooleanField("bSignalWork");   
            bError = pvStructureCmd.getBooleanField("bError");
            bInterlock = pvStructureCmd.getBooleanField("bInterlock");
            
            //Read status values            
            PVStructure pvStructureSts = pvStructure.getStructureField("sts");            
            bNoHomeFeedback = pvStructureSts.getBooleanField("bNoHomeFeedback");
            bNoWorkFeedback = pvStructureSts.getBooleanField("bNoWorkFeedback");
            bHomeFeedbackStillActive = pvStructureSts.getBooleanField("bHomeFeedbackStillActive");  
            bWorkFeedbackStillActive = pvStructureSts.getBooleanField("bWorkFeedbackStillActive"); 

            //Write command values            
            PVStructure pvStructureOut = pvStructure.getStructureField("out");
            out_iMode = pvStructureOut.getShortField("iMode");
            out_bPB_ResetError = pvStructureOut.getBooleanField("bPB_ResetError"); 
            out_bPB_Home = pvStructureOut.getBooleanField("bPB_Home"); 
            out_bPB_Work = pvStructureOut.getBooleanField("bPB_Work");             
            
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

            if (iMode.get() != out_iMode.get()) out_iMode.put(iMode.get()); 
            if (bPB_ResetError.get() != out_bPB_ResetError.get()) out_bPB_ResetError.put(bPB_ResetError.get()); 
            if (bPB_Home.get() != out_bPB_Home.get()) out_bPB_Home.put(bPB_Home.get()); 
            if (bPB_Work.get() != out_bPB_Work.get()) out_bPB_Work.put(bPB_Work.get());                

        }

        //udtHMI_DigitalInput
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
                iMode.put(innerBuffer.readShort());
                iErrorCode.put(innerBuffer.readShort());
                iStatus.put(innerBuffer.readShort());
                
                byTemp = innerBuffer.readByte();                
                bPB_ResetError.put(isBitSet(byTemp, 0));
                bPB_Home.put(isBitSet(byTemp, 1));                
                bPB_Work.put(isBitSet(byTemp, 2));  
                bPBEN_ResetError.put(isBitSet(byTemp, 3));                  
                bPBEN_Home.put(isBitSet(byTemp, 4));
                bPBEN_Work.put(isBitSet(byTemp, 5));
                bPBEN_Stop.put(isBitSet(byTemp, 6)); 
                bHomeOn.put(isBitSet(byTemp, 7));
                
                if (bFirtsRun) {
                    out_iMode.put(iMode.get());
                    out_bPB_ResetError.put(bPB_ResetError.get());
                    out_bPB_Home.put(bPB_Home.get());
                    out_bPB_Work.put(bPB_Work.get());
                    bFirtsRun = false;                
                }                
                
                byTemp = innerBuffer.readByte(); 
                bWorkOn.put(isBitSet(byTemp, 0));
                bSignalHome.put(isBitSet(byTemp, 1));                
                bSignalWork.put(isBitSet(byTemp, 2)); 
                bError.put(isBitSet(byTemp, 3));
                bInterlock.put(isBitSet(byTemp, 4));
                
                byTemp = innerBuffer.readByte(); 
                bNoHomeFeedback.put(isBitSet(byTemp, 0));
                bNoWorkFeedback.put(isBitSet(byTemp, 1)); 
                bHomeFeedbackStillActive.put(isBitSet(byTemp, 2)); 
                bWorkFeedbackStillActive.put(isBitSet(byTemp, 3));                                 
            }
        }
        
                
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }
    
        
    }
           
}
