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
import java.util.ArrayList;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcItemListener;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.nt.NTScalar;
import org.epics.nt.NTScalarArray;
import org.epics.nt.NTScalarArrayBuilder;
import org.epics.nt.NTScalarBuilder;
import org.epics.pvdata.factory.FieldFactory;
import org.epics.pvdata.property.AlarmSeverity;
import org.epics.pvdata.property.AlarmStatus;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVBooleanArray;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;


public class DBBooleanFactory extends DBBaseFactory {
    
    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();    
    
   
    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        PVStructure pvStructure = ntScalarBuilder.
            value(ScalarType.pvBoolean).
            addDescriptor(). 
            add("id", fieldCreate.createScalar(ScalarType.pvString)). 
            add("offset", fieldCreate.createScalar(ScalarType.pvString)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
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
            add("offset", fieldCreate.createScalar(ScalarType.pvString)).                 
            add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
            add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
            add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)). 
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
    
    class DBBooleanRecord extends DBRecord {
        
        private int BUFFER_SIZE = Byte.BYTES;                 
        private PVBoolean value;
        private boolean blnValue = false;
                 
        public DBBooleanRecord(String recordName,PVStructure pvStructure) {
            super(recordName, pvStructure);
            value = pvStructure.getBooleanField("value");
            write_enable = pvStructure.getBooleanField("write_enable");            
        }    

        @Override
        public void atach(PlcItem plcItem) {
            try {
                this.plcItem = plcItem;
                ParseOffset( this.getPVStructure().getStringField("offset").get());
                innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);                
            } catch (Exception ex) {
                LOGGER.error(this.getClass().getName() + " : " + ex.getMessage());
            }
        }

        /*
        * ***************************************************************************
        * Modbus      : Return a array of bytes, where every byte is a boolean.
        * S7          : Return a array of bytes, where every byte, packet 8 booleans.        
        * Ethernet/IP : TODO:
        * ***************************************************************************        
        */
        @Override
        public void update() {
            if (null != plcItem)   {   
                if (bitOffset == -1) {
                    if (value.get() != innerBuffer.getBoolean(0))                    
                        value.put(innerBuffer.getBoolean(0));
                } else {
                    blnValue = ((innerBuffer.getByte(0) >> bitOffset & 1) == 1);
                    value.put(blnValue);
                }
            }
        }

        @Override
        public String getFieldsToMonitor() {
            return MONITOR_SCALAR_FIELDS;
        }
                                
    }
    
    
}
