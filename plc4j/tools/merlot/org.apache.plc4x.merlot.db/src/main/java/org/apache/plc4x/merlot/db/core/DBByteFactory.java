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
import org.epics.pvdata.copy.PVCopy;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVByte;
import org.epics.pvdata.pv.PVByteArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdatabase.PVListener;
import org.epics.pvdatabase.PVRecord;
import org.epics.pvdatabase.PVRecordField;
import org.epics.pvdatabase.PVRecordStructure;


public class DBByteFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
    
    public DBByteFactory() {
    }
                
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvByte).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)). 
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_rate", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)). 
            add("write_value", fieldCreate.createScalar(ScalarType.pvByte)).                 
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl().                 
            createPVStructure();    
        DBRecord dbRecord = new DBByteRecord(recordName,pvStructure);
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();        
        NTScalarArrayBuilder ntScalarArrayBuilder = NTScalarArray.createBuilder();
        PVStructure pvStructure = ntScalarArrayBuilder.
            value(ScalarType.pvByte).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)).  
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_rate", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).   
            add("write_value", fieldCreate.createFixedScalarArray(ScalarType.pvByte, length)).                
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl().                 
            createPVStructure();
        PVByteArray pvValue = (PVByteArray) pvStructure.getScalarArrayField("value", ScalarType.pvByte);
        pvValue.setCapacity(length);
        pvValue.setLength(length);              
        DBRecord dbRecord = new DBByteRecord(recordName,pvStructure);
        return dbRecord;
    }
 
    class DBByteRecord extends DBRecord implements PlcItemListener {
                
        private PVByte value;
        private PVByte write_value;
        private PVBoolean write_enable;
              
        public DBByteRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = pvStructure.getByteField("value");
            write_value = pvStructure.getByteField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");
        }    

        /**
         * Implement real time data to the record.
         * The main code is here.
         */
        public void process()
        {           
            if (null != plcItem) {                  
                if (value.get() != write_value.get()) {
                    if (write_enable.get()) {      
                        write_value.put(value.get());                        
                        innerWriteBuffer.clear();
                        innerWriteBuffer.writeByte(write_value.get());
                        super.process();
                    }
                }
            }                     
        } 

        @Override
        public void atach(PlcItem plcItem) {
            this.plcItem = plcItem;
            offset = this.getPVStructure().getIntField("offset").get() * Byte.BYTES;             
            innerBuffer = Unpooled.wrappedBuffer(plcItem.getInnerBuffer(), offset, Byte.BYTES);
            innerWriteBuffer = Unpooled.copiedBuffer(innerBuffer);
        }

        @Override
        public void detach() {
            this.plcItem  = null;
        }

        @Override
        public void update() {
            if (null != plcItem) {           
                if (value.get() != innerBuffer.getByte(0))
                    value.put(innerBuffer.getByte(0));
            }
        }
       
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_FIELDS;
        }        

              
    }
    
}