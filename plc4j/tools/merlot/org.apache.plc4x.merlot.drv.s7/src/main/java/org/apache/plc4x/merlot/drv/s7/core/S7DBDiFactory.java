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
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class S7DBDiFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();
        
        Field udtHMI = fb.setId("udtHMI").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("bOn", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bOnActual", fieldCreate.createScalar(ScalarType.pvBoolean)). 
                add("bPB_On", fieldCreate.createScalar(ScalarType.pvBoolean)).                 
                add("bPB_Off", fieldCreate.createScalar(ScalarType.pvBoolean)).                                 
                add("bPBEN_On", fieldCreate.createScalar(ScalarType.pvBoolean)).    
                add("bPBEN_Off", fieldCreate.createScalar(ScalarType.pvBoolean)).                 
                createStructure(); 
        
        Field udtError = fb.setId("udtError").                                
                createStructure();        
                
        
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvShort).
            addDescriptor().
            add("udtHMI", udtHMI).
            add("udtError", udtError).                      
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
        DBRecord dbRecord = new DBS7DiRecord(recordName,pvStructure);      
        return dbRecord;
    }

           
    class DBS7DiRecord extends DBRecord implements PlcItemListener {   
        
        private int BUFFER_SIZE = 3;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, par{bPBEN_On,bPBEN_Off})";          
    
        private PVShort value; 
        private PVShort write_value;
        private PVBoolean write_enable; 
        
        private PVShort iMode; 
        private PVBoolean bOn; 
        private PVBoolean bOnActual;   
        private PVBoolean bPB_On;
        private PVBoolean bPB_Off; 
        private PVBoolean bPBEN_On;  
        private PVBoolean bPBEN_Off;  
        
        private PVShort par_iMode; 
        private PVBoolean par_bPB_On;
        private PVBoolean par_bPB_Off;         
        
        byte byTemp;
    
        public DBS7DiRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = pvStructure.getShortField("value");
            write_enable = pvStructure.getBooleanField("write_enable");
                                       
            //Read command values
            PVStructure udtHMI = pvStructure.getStructureField("udtHMI");                        
            iMode = udtHMI.getShortField("iMode");            
            bOn = udtHMI.getBooleanField("bOn");            
            bOnActual = udtHMI.getBooleanField("bOnActual");            
            bPB_On = udtHMI.getBooleanField("bPB_On");
            bPB_Off = udtHMI.getBooleanField("bPB_Off");            
            bPBEN_On = udtHMI.getBooleanField("bPBEN_On");
            bPBEN_Off = udtHMI.getBooleanField("bPBEN_Off");
            
            fieldOffsets.clear();
            fieldOffsets.add(0, null); 
            fieldOffsets.add(1, null);
            fieldOffsets.add(2, null);
            fieldOffsets.add(3, new ImmutablePair(2,  (byte) -1));  //iMode
            fieldOffsets.add(4, new ImmutablePair(4, (byte) 2));    //bPB_On
            fieldOffsets.add(5, new ImmutablePair(4, (byte) 3));    //bPB_Off 
            fieldOffsets.add(6, new ImmutablePair(4, (byte) 4));    //bPBEN_On
            fieldOffsets.add(7, new ImmutablePair(4, (byte) 5));    //bPBEN_Off             
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
                
                byTemp = innerBuffer.readByte();                
                bOn.put(isBitSet(byTemp, 0));
                bOnActual.put(isBitSet(byTemp, 1));                
                bPB_On.put(isBitSet(byTemp, 2));  
                bPB_Off.put(isBitSet(byTemp, 3)); 
                
                if (bFirtsRun) {                   
                    bFirtsRun = false;
                }                  
                
                bPBEN_On.put(isBitSet(byTemp, 4));
                bPBEN_Off.put(isBitSet(byTemp, 5)); 
                
              
                
                
            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_FIELDS;
        }

        public PVShort getValue() {
            return value;
        }

       
        
    }
           
}
