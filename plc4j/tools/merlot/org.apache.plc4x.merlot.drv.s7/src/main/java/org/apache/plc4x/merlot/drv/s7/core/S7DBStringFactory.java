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
import java.nio.charset.Charset;
import java.util.ArrayList;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.apache.plc4x.merlot.db.core.DBBaseFactory;
import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarArray;
import org.epics.nt.NTScalarArrayBuilder;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.pv.FieldBuilder;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVShortArray;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class S7DBStringFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
       
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();

        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvString).
            addDescriptor().            
            add("id", fieldCreate.createScalar(ScalarType.pvString)).  
            add("offset", fieldCreate.createScalar(ScalarType.pvString)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).  
            add("write_value", fieldCreate.createScalar(ScalarType.pvString)). 
            add("strValue", fieldCreate.createScalar(ScalarType.pvString)).  
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();          
        DBRecord dbRecord = new DBS7StringRecord(recordName,pvStructure);      
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName,int length) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();                
        NTScalarArrayBuilder ntScalarArrayBuilder = NTScalarArray.createBuilder();

        PVStructure pvStructure = ntScalarArrayBuilder.
            value(ScalarType.pvShort).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)). 
            add("offset", fieldCreate.createScalar(ScalarType.pvString)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)). 
            add("write_value", fieldCreate.createFixedScalarArray(ScalarType.pvShort, length)).                   
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();
        PVShortArray pvValue = (PVShortArray) pvStructure.getScalarArrayField("value", ScalarType.pvShort);
        pvValue.setCapacity(length);
        pvValue.setLength(length);
        DBRecord dbRecord = new DBS7StringRecord(recordName,pvStructure);
        return dbRecord;
    }
           
    class DBS7StringRecord extends DBRecord implements PlcItemListener {    
    
        private int buffer_size = 0;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, write_value)";        
        
        private PVString value; 
        private PVString write_value;
        private PVBoolean write_enable;
        private PVString strValue;         
        private byte k, m;        

        String tempValue;
    
        public DBS7StringRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            
             bFirtsRun = true;
            
            fieldOffsets = new ArrayList<>();
            fieldOffsets.add(0, null);
            fieldOffsets.add(1, new ImmutablePair(0,-1));
                        
            value = pvStructure.getStringField("value");
            write_value = pvStructure.getStringField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");
            strValue = pvStructure.getStringField("strValue");
        }    

        /**
         * Implement real time data to the record.
         * The main code is here.
         */
        public void process()
        {
            if (null != plcItem) {               
                if (write_enable.get()) {   
                    try {                        
                        if (!value.equals(tempValue)) {
                            String wValue = (value.get().length() > k)?value.get().substring(1, k) : value.get();
                            write_value.put(wValue);        
                        }
                    } catch (Exception ex) {
                        LOGGER.info("S7 TIME mal formed.");
                    }
                                              
                }
            }              
        }

        /**
         * S7 String
         *             +---------------------+
         * Byte n      | Maximum length      | k
         *             +---------------------+ 
         * Byte n + 1  | Current length      | m
         *             +---------------------+
         * Byte n + 2  | 1st character       |
         *             +---------------------+
         *             /                     /
         *             +---------------------+
         *  Byte n+k+1 | mth character       |
         *             +---------------------+
         * 
         * 
         * 
         * @param plcItem 
         */
        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem;  
            ParseOffset( this.getPVStructure().getStringField("offset").get()); 
            k = plcItem.getItemByteBuf().getByte(byteOffset);
            innerBuffer = plcItem.getItemByteBuf().slice(byteOffset + 1, k + 1);
            innerWriteBuffer = Unpooled.buffer(k + 1);
        }

        @Override
        public void detach() {
            this.plcItem  = null;
        }

        @Override
        public void update() {    
            if (null != plcItem) {
                m = innerBuffer.getByte(0);
                tempValue = innerBuffer.toString(1, m, Charset.defaultCharset());
                if (!value.equals(tempValue)) {
                    value.put(tempValue);
                    if (bFirtsRun ){
                        bFirtsRun = false;
                    }
                    strValue.put(tempValue);                    
                }
            }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }
        
    }
           
}
