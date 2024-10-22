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
package org.apache.plc4x.merlot.db.core;

import io.netty.buffer.Unpooled;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarArray;
import org.epics.nt.NTScalarArrayBuilder;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PVUInt;
import org.epics.pvdata.pv.PVUIntArray;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdatabase.PVRecord;


public class DBUIntFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
    
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvUInt).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)).
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).  
            add("write_value", fieldCreate.createScalar(ScalarType.pvUInt)).                  
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();   
        DBRecord dbRecord = new DBUIntRecord(recordName,pvStructure);
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();                
        NTScalarArrayBuilder ntScalarArrayBuilder = NTScalarArray.createBuilder();
        PVStructure pvStructure = ntScalarArrayBuilder.
            value(ScalarType.pvUInt).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)).
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_value", fieldCreate.createFixedScalarArray(ScalarType.pvUInt, length)).                   
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();
        PVUIntArray pvValue = (PVUIntArray) pvStructure.getScalarArrayField("value", ScalarType.pvUInt);
        pvValue.setCapacity(length);
        pvValue.setLength(length);               
        DBRecord dbRecord = new DBUIntRecord(recordName,pvStructure);
        return dbRecord;
    }
    
    class DBUIntRecord extends DBRecord implements PlcItemListener {
        
        private PVUInt value; 
        private PVUInt write_value;         
        private PVBoolean write_enable;           
        
        public DBUIntRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = (PVUInt) pvStructure.getSubField("value");   
            write_value = (PVUInt) pvStructure.getSubField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");            
        }    

        /**
         * Implement real time data to the record.
         * The main code is here.
         */
        public void process()
        {
            if (null != plcItem) {               
                if (write_enable.get()) {                          
                    write_value.put(value.get());                           
                    innerWriteBuffer.clear();                     
                    innerWriteBuffer.writeInt(write_value.get());                         
                    super.process();                      
                }
            }              
        } 

        @Override
        public void atach(PlcItem plcItem) {
            this.plcItem = plcItem;
            offset = this.getPVStructure().getIntField("offset").get();              
            innerBuffer = plcItem.getItemByteBuf().slice(offset, Integer.BYTES);
            innerWriteBuffer = Unpooled.copiedBuffer(innerBuffer);
        }

        @Override
        public void detach() {
             this.plcItem  = null;
        }

        @Override
        public void update() {
            if (null != plcItem)   
                if (value.get() != innerBuffer.getUnsignedInt(0))
                value.put((byte) innerBuffer.getUnsignedInt(0));
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_FIELDS;
        }        
    }
    
}
