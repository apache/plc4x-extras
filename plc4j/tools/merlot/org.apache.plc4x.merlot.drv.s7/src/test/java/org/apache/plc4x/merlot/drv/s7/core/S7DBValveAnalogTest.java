/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.drv.s7.core;

import io.netty.buffer.ByteBuf;
import static io.netty.buffer.Unpooled.buffer;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcRawByteArray;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author lerb
 */
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S7DBValveAnalogTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBValveAnalogTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord ValveAng;

    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;

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

    private PVBoolean Invalid;
    private PVInt tTimeOut;

    //@BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the analog valve class");
        logger.info("Test analog valve for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(50);
        byteBuf.setByte(0, 0b0000_0001);       //Invalid
        byteBuf.setShort(2, 1234);              //iMode
        byteBuf.setShort(4, 4321);              //iErrorCode
        byteBuf.setShort(6, 1010);              //iStatus
        byteBuf.setFloat(8, 3.1416F);           //rManualSP
        byteBuf.setFloat(12, 3.1416F * 2);      //rAutoSP
        byteBuf.setFloat(16, 3.1416F * 4);      //rEstopSP
        byteBuf.setFloat(20, 3.1416F * 6);      //rActual
        byteBuf.setByte(24, 0b0000_1010);       //bPB_ResetError
                                                //bPBEN_ResetError
                                                //bError 
                                                //bInterlock
        byteBuf.setShort(26, 1234);             //iEstopFunction
    }

    //@AfterAll
    public static void tearDownClass() {
        logger.info("Ending the analog valve class test");
    }

    //@BeforeEach
    public void setUp() {
        //Create PLCList for the items
        plcValue = new PlcRawByteArray(byteBuf.array());
        //Create the Item 
        String uuid = UUID.randomUUID().toString();
        plcItem = new PlcItemImpl.PlcItemBuilder("ITEM_DB42").
                setItemDescription("SIM DB42 S7").
                setItemId(uuid).
                setItemUid(UUID.fromString(uuid)).
                build();

        assertNotNull(plcItem);
        assertNotNull(plcValue);

        S7DBValveAnalogFactory ValveAnalog = new S7DBValveAnalogFactory();
        ValveAng = ValveAnalog.create("ValveAnf_00");
        
        PVString pvStrOffset = ValveAng.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        plcItem.addItemListener(ValveAng);
        plcItem.setPlcValue(plcValue);        
        
    }

    //@AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    //@Order(1)
    public void DBRecordTest() {

        PVStructure udtHMI = ValveAng.getPVRecordStructure().getPVStructure().getStructureField("udtHMI");
        PVStructure udtError = ValveAng.getPVRecordStructure().getPVStructure().getStructureField("udtError");

        value = ValveAng.getPVRecordStructure().getPVStructure().getShortField("value");
        write_enable = ValveAng.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        //Read status values   
        Invalid = udtError.getBooleanField("Invalid");        
        
        //Read command values            
        iMode = udtHMI.getShortField("iMode");
        iErrorCode = udtHMI.getShortField("iErrorCode");
        iStatus = udtHMI.getShortField("iStatus");
        rManualSP = udtHMI.getFloatField("rManualSP");
        rAutoSP = udtHMI.getFloatField("rAutoSP");
        rEstopSP = udtHMI.getFloatField("rEstopSP");
        rActual = udtHMI.getFloatField("rActual");
        bPB_ResetError = udtHMI.getBooleanField("bPB_ResetError");
        bPBEN_ResetError = udtHMI.getBooleanField("bPBEN_ResetError");
        bError = udtHMI.getBooleanField("bError");
        bInterlock = udtHMI.getBooleanField("bInterlock");
        iEstopFunction = udtHMI.getShortField("iEstopFunction");

        assertEquals(true, Invalid.get());

        assertEquals(1234, iMode.get());
        assertEquals(4321, iErrorCode.get());
        assertEquals(1010, iStatus.get());
        assertEquals(3.1416F, rManualSP.get());
        assertEquals(6.2832F, rAutoSP.get());
        assertEquals(12.5664F, rEstopSP.get());
        assertEquals(18.8496F, rActual.get());
        assertEquals(false, bPB_ResetError.get());
        assertEquals(true, bPBEN_ResetError.get());
        assertEquals(false, bError.get());
        assertEquals(true, bInterlock.get());
        assertEquals(1234, iEstopFunction.get());
        
    }

    @Test
    //@Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = ValveAng.getFieldOffsets();
        
        assertEquals(9, fieldOffsets.size());

        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));
       
        //The offset is recalculated when the pvRecord is assigned to the Item.
//        assertEquals(0, fieldOffsets.get(2).left);
//        assertEquals((byte) -1, fieldOffsets.get(2).right);        
//        
//        assertEquals(2, fieldOffsets.get(3).left);
//        assertEquals((byte) -1, fieldOffsets.get(3).right);
//        assertEquals(8, fieldOffsets.get(4).left);
//        assertEquals((byte) -1, fieldOffsets.get(4).right);
//        assertEquals(12, fieldOffsets.get(5).left);
//        assertEquals((byte) -1, fieldOffsets.get(5).right);
//        assertEquals(16, fieldOffsets.get(6).left);
//        assertEquals((byte) -1, fieldOffsets.get(6).right); 
//        
//        assertEquals(24, fieldOffsets.get(7).left);
//        assertEquals((byte) 0, fieldOffsets.get(7).right);         
//        assertEquals(24, fieldOffsets.get(8).left);
//        assertEquals((byte) 1, fieldOffsets.get(8).right); 
    }
}
