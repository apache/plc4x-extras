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
import org.epics.pvdata.pv.PVLong;
import org.epics.pvdata.pv.PVLongArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdatabase.PVRecord;


public class DBLongFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();

    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvLong).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)).
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)). 
            add("write_value", fieldCreate.createScalar(ScalarType.pvLong)).                  
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();   
        DBRecord dbRecord = new DBLongRecord(recordName,pvStructure);
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();                
        NTScalarArrayBuilder ntScalarArrayBuilder = NTScalarArray.createBuilder();
        PVStructure pvStructure = ntScalarArrayBuilder.
            value(ScalarType.pvLong).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)).
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).  
            add("write_value", fieldCreate.createFixedScalarArray(ScalarType.pvLong, length)).                   
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();
        PVLongArray pvValue = (PVLongArray) pvStructure.getScalarArrayField("value", ScalarType.pvLong);
        pvValue.setCapacity(length);
        pvValue.setLength(length);               
        DBRecord dbRecord = new DBLongRecord(recordName,pvStructure);
        return dbRecord;
    }
      
    class DBLongRecord extends DBRecord implements PlcItemListener {    
    
        private PVLong value;
        private PVLong write_value;
        private PVBoolean write_enable;                 

        public DBLongRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = pvStructure.getLongField("value");  
            write_value = pvStructure.getLongField("write_value");
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
                    innerWriteBuffer.writeLong(write_value.get());                         
                    super.process();                      
                }
            }
        }        

        @Override
        public void atach(PlcItem plcItem) {
            this.plcItem = plcItem;
            offset = this.getPVStructure().getIntField("offset").get();               
            innerBuffer = plcItem.getItemByteBuf().slice(offset, Long.BYTES);
            innerWriteBuffer = Unpooled.copiedBuffer(innerBuffer);
        }

        @Override
        public void detach() {
             this.plcItem  = null;
        }

        @Override
        public void update() {
            if (null != plcItem)   
                if (value.get() != innerBuffer.getLong(0))
                value.put(innerBuffer.getLong(0));
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_FIELDS;
        }        
    }              
}
