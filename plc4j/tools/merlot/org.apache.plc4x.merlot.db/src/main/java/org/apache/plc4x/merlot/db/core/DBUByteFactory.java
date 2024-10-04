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
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.PVUByte;
import org.epics.pvdata.pv.PVUByteArray;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdatabase.PVRecord;


public class DBUByteFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();
           
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
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();   
        DBRecord dbRecord = new DBUByteRecord(recordName,pvStructure);
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();                
        NTScalarArrayBuilder ntScalarArrayBuilder = NTScalarArray.createBuilder();
        PVStructure pvStructure = ntScalarArrayBuilder.
            value(ScalarType.pvUByte).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)). 
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                  
            add("scan_rate", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).  
            add("write_value", fieldCreate.createFixedScalarArray(ScalarType.pvUByte, length)).                   
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();
        PVUByteArray pvValue = (PVUByteArray) pvStructure.getScalarArrayField("value", ScalarType.pvUByte);
        pvValue.setCapacity(length);
        pvValue.setLength(length);               
        DBRecord dbRecord = new DBUByteRecord(recordName,pvStructure);
        return dbRecord;
    }

    class DBUByteRecord extends DBRecord implements PlcItemListener {
        
        private PVUByte value; 
        private PVUByte write_value;         

        private int offset = 0;           
        
        public DBUByteRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = (PVUByte) pvStructure.getByteField("value");
            write_value = (PVUByte) pvStructure.getByteField("write_value");
        }    

        /**
         * Implement real time data to the record.
         * The main code is here.
         */
        public void process()
        {
            super.process();
            if (null != plcItem) {    
                System.out.println("Paso por UByte");                
                if (value.get() != write_value.get())
                    write_value.put(value.get());
            }               
        }

        @Override
        public void atach(PlcItem plcItem) {
            this.plcItem = plcItem;            
            offset = this.getPVStructure().getIntField("offset").get() * Byte.BYTES;        
            innerBuffer = Unpooled.wrappedBuffer(plcItem.getInnerBuffer(), offset, Byte.BYTES);
        }

        @Override
        public void detach() {
             this.plcItem  = null;
        }

        @Override
        public void update() {
            if (null != plcItem)  
                if (value.get() != innerBuffer.getUnsignedByte(0)) {
                    value.put((byte) innerBuffer.getUnsignedByte(offset));
                }
        }
        
        @Override
        public String getFieldsToMonitor() {
            return MONITOR_WRITE_FIELD;
        }        
    }
     
}