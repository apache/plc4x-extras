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
import org.epics.pvdata.pv.PVBooleanArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class DBBooleanFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();    
    
    public DBBooleanFactory() {};
    
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvBoolean).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)). 
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_value", fieldCreate.createScalar(ScalarType.pvBoolean)). 
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();    
        DBRecord dbRecord = new DBBooleanRecord(recordName,pvStructure);
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();        
        NTScalarArrayBuilder ntScalarArrayBuilder = NTScalarArray.createBuilder();
        PVStructure pvStructure = ntScalarArrayBuilder.
            value(ScalarType.pvBoolean).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)). 
            add("offset", fieldCreate.createScalar(ScalarType.pvInt)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)). 
            add("write_value", fieldCreate.createFixedScalarArray(ScalarType.pvBoolean, length)).
            addAlarm().
            addTimeStamp().
            addDisplay().
            addControl(). 
            createPVStructure();
        PVBooleanArray pvValue = (PVBooleanArray) pvStructure.getScalarArrayField("value", ScalarType.pvBoolean);
        pvValue.setCapacity(length);
        pvValue.setLength(length);        
        DBRecord dbRecord = new DBBooleanRecord(recordName,pvStructure);
        return dbRecord;
    }
    
    class DBBooleanRecord extends DBRecord implements PlcItemListener {
        
        private PVBoolean value;
        private PVBoolean write_value;
        private PVBoolean write_enable;
                 
        public DBBooleanRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = pvStructure.getBooleanField("value");
            write_value = pvStructure.getBooleanField("write_value"); 
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
                    innerWriteBuffer.writeBoolean(write_value.get());                         
                    super.process();                      
                }
                
            }             
        } 

        @Override
        public void atach(PlcItem plcItem) {
            try {
            this.plcItem = plcItem;
                offset = this.getPVStructure().getIntField("offset").get() * Byte.BYTES;              
                innerBuffer = plcItem.getItemByteBuf().slice(offset, Byte.BYTES);
                innerWriteBuffer = Unpooled.copiedBuffer(innerBuffer);
            } catch (Exception ex) {
                System.out.println("Falla al atach()");
                System.out.println(plcItem.toString());
                ex.printStackTrace();
            }
        }

        @Override
        public void detach() {
            this.plcItem  = null;
        }

        @Override
        public void update() {
            if (null != plcItem)      
                if (value.get() != innerBuffer.getBoolean(0))                    
                    value.put(innerBuffer.getBoolean(0));
        }

        @Override
        public String getFieldsToMonitor() {
            return MONITOR_FIELDS;
        }
                                
    }
    
    
}
