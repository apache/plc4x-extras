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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
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
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVShortArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S7DBCounterFactory extends DBBaseFactory {

    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();

    @Override
    public DBRecord create(String recordName) {
        NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        FieldBuilder fb = fieldCreate.createFieldBuilder();

        PVStructure pvStructure = ntScalarBuilder.
                value(ScalarType.pvShort).
                addDescriptor().
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
        DBRecord dbRecord = new DBS7CounterRecord(recordName, pvStructure);
        return dbRecord;
    }

    @Override
    public DBRecord createArray(String recordName, int length) {
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
        DBRecord dbRecord = new DBS7CounterRecord(recordName, pvStructure);
        return dbRecord;
    }

    class DBS7CounterRecord extends DBRecord implements PlcItemListener {

        private int BUFFER_SIZE = 2;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, write_value)";

        private PVShort value;
        private PVShort write_value;
        private PVBoolean write_enable;
        short b, c, d, bcd;

        public DBS7CounterRecord(String recordName, PVStructure pvStructure) {
            super(recordName, pvStructure);

            bFirtsRun = true;

            fieldOffsets = new ArrayList<>();
            fieldOffsets.add(0, null);
            fieldOffsets.add(1, new ImmutablePair(0, -1));
            fieldOffsets.add(2, new ImmutablePair(0, -1));

            value = pvStructure.getShortField("value");
            write_value = pvStructure.getShortField("write_value");
            write_enable = pvStructure.getBooleanField("write_enable");
        }

        /**
         * Implement real time data to the record.private PVShort value; private
         * PVShort write_value; private PVBoolean write_enable; short b, c, d,
         * bcd; The main code is here.
         */
        public void process() {
            if (null != plcItem) {
                if (write_enable.get()) {
                    write_value.put(value.get());
                }
            }
        }

        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem;
            ParseOffset(this.getPVStructure().getStringField("offset").get());
            innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);
        }

        @Override
        public void detach() {
            this.plcItem = null;
        }

        @Override
        public void update() {
            if (null != plcItem) {
                b = (short) (innerBuffer.getByte(0) & 0x0F); 
                c = (short) ((innerBuffer.getByte(1) & 0xF0) >> 4);
                d = (short) (innerBuffer.getByte(1) & 0x0F);
                bcd = (short) (b * 100 + c * 10 + d);
                if (value.get() != bcd) {
                    value.put(bcd);
                }
            }
        }

        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }

    }

}
