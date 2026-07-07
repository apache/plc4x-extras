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
import java.time.Duration;
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
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;

public class S7DBValveAnalogFactory extends DBBaseFactory {

    private static FieldCreate fieldCreate = FieldFactory.getFieldCreate();

    @Override
    public DBRecord create(String recordName) {
        final NTScalarBuilder ntScalarBuilder = NTScalar.createBuilder();
        final FieldBuilder fb = fieldCreate.createFieldBuilder();

        Field fCmd = fb.setId("cmd").
                add("iMode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iErrorCode", fieldCreate.createScalar(ScalarType.pvShort)).
                add("iStatus", fieldCreate.createScalar(ScalarType.pvShort)).
                add("rManualSP", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("rAutoSP", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("rEstopSP", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("rActual", fieldCreate.createScalar(ScalarType.pvFloat)).
                add("bPB_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bPBEN_ResetError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bError", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("bInterlock", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("iEstopFunction", fieldCreate.createScalar(ScalarType.pvShort)).
                createStructure();

        Field fSts = fb.setId("sts").
                add("IvalidFeedback", fieldCreate.createScalar(ScalarType.pvBoolean)).
                createStructure();
        
        Field fPar = fb.setId("par").
                add("tInTimeout", fieldCreate.createScalar(ScalarType.pvInt)).
                add("strTimeout", fieldCreate.createScalar(ScalarType.pvString)).                
                add("rInSignalCommand", fieldCreate.createScalar(ScalarType.pvFloat)).                
                createStructure();        

        PVStructure pvStructure = ntScalarBuilder.
                value(ScalarType.pvShort).
                addDescriptor().
                add("id", fieldCreate.createScalar(ScalarType.pvString)).
                add("offset", fieldCreate.createScalar(ScalarType.pvString)).
                add("scan_time", fieldCreate.createScalar(ScalarType.pvString)).
                add("scan_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("write_enable", fieldCreate.createScalar(ScalarType.pvBoolean)).
                add("cmd", fCmd).
                add("sts", fSts).
                add("par", fPar).
                addAlarm().
                addTimeStamp().
                addDisplay().
                addControl().
                createPVStructure();

        DBRecord dbRecord = new DBS7ValveRecord(recordName, pvStructure);

        return dbRecord;
    }

    class DBS7ValveRecord extends DBRecord implements PlcItemListener {

        private int BUFFER_SIZE = 36;
        private static final String MONITOR_TF_FIELDS = "field(write_enable, "
                + "cmd{iMode, rManualSP, rAutoSP, rEstopSP, bPB_ResetError, bPBEN_ResetError},"
                + "par{tInTimeout, rInSignalCommand})";  

        private PVShort value;
        private PVShort write_value;
        private PVBoolean write_enable;

        //pvCmd
        private PVShort iMode;
        private PVShort iErrorCode;
        private PVShort iStatus;
        private PVFloat rManualSP;
        private PVFloat rAutoSP;
        private PVFloat rEstopSP;
        private PVFloat rActual;
        private PVBoolean bPB_ResetError;
        private PVBoolean bPBEN_ResetError;
        private PVBoolean bError;
        private PVBoolean bInterlock;
        private PVShort iEstopFunction;

        //pvSts
        private PVBoolean IvalidFeedback;
        
        //pvPar
        private PVInt tInTimeout;
        private PVString strTimeout;        
        private PVFloat rInSignalCommand;

        private Duration lastDuration;          
        byte byTemp;

        public DBS7ValveRecord(String recordName, PVStructure pvStructure) {
            super(recordName, pvStructure);

            bFirtsRun = true;
            
            value = pvStructure.getShortField("value");
            write_enable = pvStructure.getBooleanField("write_enable");
            write_enable.put(false);

            //Read command values
            PVStructure pvCmd   = pvStructure.getStructureField("cmd");
            iMode               = pvCmd.getShortField("iMode");
            iErrorCode          = pvCmd.getShortField("iErrorCode");
            iStatus             = pvCmd.getShortField("iStatus");
            rManualSP           = pvCmd.getFloatField("rManualSP");
            rAutoSP             = pvCmd.getFloatField("rAutoSP");
            rEstopSP            = pvCmd.getFloatField("rEstopSP");
            rActual             = pvCmd.getFloatField("rActual");
            bPB_ResetError      = pvCmd.getBooleanField("bPB_ResetError");
            bPBEN_ResetError    = pvCmd.getBooleanField("bPBEN_ResetError");
            bError              = pvCmd.getBooleanField("bError");
            bInterlock          = pvCmd.getBooleanField("bInterlock");
            iEstopFunction      = pvCmd.getShortField("iEstopFunction");

            //Read status values            
            PVStructure pvSts   = pvStructure.getStructureField("sts");
            IvalidFeedback      = pvSts.getBooleanField("IvalidFeedback");
            
            //Parameters values
            PVStructure pvPar   = pvStructure.getStructureField("par");    
            tInTimeout          = pvPar.getIntField("tInTimeout");
            strTimeout          = pvPar.getStringField("strTimeout");
            rInSignalCommand    = pvPar.getFloatField("rInSignalCommand");

            fieldOffsets.clear();
            fieldOffsets.add(0, null);
            fieldOffsets.add(1, null);
            fieldOffsets.add(2, null);
            fieldOffsets.add(3, new ImmutablePair(0, (byte) -1));
            fieldOffsets.add(4, new ImmutablePair(6, (byte) -1));
            fieldOffsets.add(5, new ImmutablePair(10,(byte) -1));
            fieldOffsets.add(6, new ImmutablePair(14,(byte) -1));
            fieldOffsets.add(7, new ImmutablePair(22,(byte) 0));            
            fieldOffsets.add(8, new ImmutablePair(22,(byte) 1));
            fieldOffsets.add(9, null);  
            fieldOffsets.add(10, new ImmutablePair(28,(byte) -1));
            fieldOffsets.add(11, new ImmutablePair(32,(byte) -1));            
        }

        /**
         * For other special types of data, adaptation must be made here to
         * write to the PLC.
         *
         * 1. In the first write all fields are written 
         * 2. In the second one only the changes are written.
         *
         */
        public void process() {
            if (null != plcItem) {               
                if (write_enable.get()) {
                    try {
                        Duration userTime = Duration.parse(strTimeout.get());
                        if (!lastDuration.equals(userTime)) {
                            int writeValue = S7DBStaticHelper.durationToS7Time(userTime);
                            tInTimeout.put(writeValue);        
                        }
                    } catch (Exception ex) {
                        LOGGER.info("S7 TIME mal formed.");
                    }                    
//                    super.process();                      
                }
            }  
        }

        //pvCmd_DigitalInput
        @Override
        public void atach(final PlcItem plcItem) {
            this.plcItem = plcItem;
            ParseOffset(this.getPVStructure().getStringField("offset").get());
            innerBuffer = plcItem.getItemByteBuf().slice(byteOffset, BUFFER_SIZE);
        }

        @Override
        public void update() {
            if (null != plcItem) {
                innerBuffer.resetReaderIndex();
                
                //Update pvCmd  
                if (innerBuffer.getShort(0) != iMode.get()) {
                    iMode.put(innerBuffer.getShort(0));
                }
                
                iErrorCode.put(innerBuffer.getShort(2));
                iStatus.put(innerBuffer.getShort(4));
                
                if (innerBuffer.getFloat(6) != rManualSP.get()) {
                    rManualSP.put(innerBuffer.getFloat(6));                    
                }
                if (innerBuffer.getFloat(10) != rAutoSP.get()) {                
                    rAutoSP.put(innerBuffer.getFloat(10));
                }
                if (innerBuffer.getFloat(14) != rEstopSP.get()) { 
                    rEstopSP.put(innerBuffer.getFloat(14));                    
                }                
                               
                rActual.put(innerBuffer.getFloat(18));

                byTemp = innerBuffer.getByte(22);
                if (isBitSet(byTemp, 0) != bPB_ResetError.get()) {
                    bPB_ResetError.put(isBitSet(byTemp, 0));
                }
                if (isBitSet(byTemp, 1) != bPBEN_ResetError.get()) {                
                    bPBEN_ResetError.put(isBitSet(byTemp, 1));
                }
                
                bError.put(isBitSet(byTemp, 2));
                bInterlock.put(isBitSet(byTemp, 3));
                
                if (innerBuffer.getShort(24) != iEstopFunction.get()) {                
                    iEstopFunction.put(innerBuffer.getShort(24));
                }

                //Update pvSts                 
                byTemp = innerBuffer.getByte(26);
                IvalidFeedback.put(isBitSet(byTemp, 0));

                //Update pvPar
                if (innerBuffer.getInt(28) != tInTimeout.get()) {
                    tInTimeout.put(innerBuffer.getInt(28));
                    lastDuration = S7DBStaticHelper.s7TimeToDuration(tInTimeout.get());
                    strTimeout.put(lastDuration.toString());                    
                }

                if (innerBuffer.getFloat(32) != rInSignalCommand.get()) {
                    rInSignalCommand.put(innerBuffer.getFloat(32));
                }                 
                
                if (bFirtsRun) {
                    bFirtsRun = false;
                    write_enable.put(true);                    
                }                  

            }
        }

        @Override
        public String getFieldsToMonitor() {
            return MONITOR_TF_FIELDS;
        }

    }

}
