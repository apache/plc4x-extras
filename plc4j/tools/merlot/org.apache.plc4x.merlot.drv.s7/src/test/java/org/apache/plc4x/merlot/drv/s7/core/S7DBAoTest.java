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
import org.epics.pvdata.pv.PVByte;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author lerb
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S7DBAoTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBAoTest.class);
    private PlcValue plcValue;
    private static ByteBuf byteBuf;
    private PlcItem plcItem;
    private DBRecord AO;
    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;

    private PVShort iMode;
    private PVShort iErrorCode;

    private PVFloat rValue;
    private PVFloat rAutoValue;
    private PVFloat rManualValue;
    private PVFloat rEstopValue;

    private PVBoolean bPB_ResetError;
    private PVBoolean bPBEN_ResetError;
    private PVBoolean bError;
    private PVBoolean bInterlock;

    private PVShort iEstopFunction;

    private PVBoolean bOutOfRange;
    private PVBoolean bConfigurationError;

    private PVByte bySpare;

    @BeforeAll
    public static void setUpClass() {
        /*
        Create an object Bytebuf
         */
        logger.info("Starting the testing of the analog output class");
        logger.info("Test Analog output for S7 plc");
        logger.info("Creating buffer to plcValue to Ao");
        byteBuf = buffer(100);

        byteBuf.setByte(0, 0b0000_0011); //Out_Of_Range
                                                    //Configuration_Error
        byteBuf.setShort(2, 1234);          //iMode                                                   
        byteBuf.setShort(4, 4321);          //iErrorCode
        byteBuf.setFloat(6, 3.1416F);       //rValue
        byteBuf.setFloat(10, 3.1416F * 2);  //rAutoValue
        byteBuf.setFloat(14, 3.1416F * 3);  //rManualValue
        byteBuf.setFloat(18, 3.1416F * 4);  //rEstopValue
        byteBuf.setByte(22, 0b0000_0101);   //bPB_ResetError
                                            //bPBEN_ResetError
                                            //bError
                                            //bInterLock
        byteBuf.setShort(24, 2345);         //iEstopFunction

    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the analog outputs class test");
    }

    @BeforeEach
    public void setUp() {
        /*
        defining an id and PlcItem
         */
        String uuid = UUID.randomUUID().toString();
        plcValue = new PlcRawByteArray(byteBuf.array());
        plcItem = new PlcItemImpl.PlcItemBuilder("ITEM_DB42").
                setItemDescription("SIM DB42 S7").
                setItemId(uuid).
                setItemUid(UUID.fromString(uuid)).
                build();
        
        assertNotNull(plcItem);
        assertNotNull(plcValue);
        
        S7DBAoFactory AIFactory = new S7DBAoFactory();
        AO = AIFactory.create("AO");
        
        PVString pvStrOffset = AO.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        
        AO.atach(plcItem);
        
        plcItem.addItemListener(AO);
        plcItem.setPlcValue(plcValue);        
        
    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    @Order(1)
    public void DBRecordTest() {

        PVStructure udtHMI = AO.getPVRecordStructure().getPVStructure().getStructureField("udtHMI");
        PVStructure udtError = AO.getPVRecordStructure().getPVStructure().getStructureField("udtError");

        value = AO.getPVRecordStructure().getPVStructure().getShortField("value");
        write_enable = AO.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        bOutOfRange = udtError.getBooleanField("bOutOfRange");
        bConfigurationError = udtError.getBooleanField("bConfigurationError");        
        
        iMode = udtHMI.getShortField("iMode");
        iErrorCode = udtHMI.getShortField("iErrorCode");
        rValue = udtHMI.getFloatField("rValue");
        rAutoValue = udtHMI.getFloatField("rAutoValue");
        rManualValue = udtHMI.getFloatField("rManualValue");
        rEstopValue = udtHMI.getFloatField("rEstopValue");
        bPB_ResetError = udtHMI.getBooleanField("bPB_ResetError");
        bPBEN_ResetError = udtHMI.getBooleanField("bPBEN_ResetError");
        bError = udtHMI.getBooleanField("bError");
        bInterlock = udtHMI.getBooleanField("bInterlock");
        iEstopFunction = udtHMI.getShortField("iEstopFunction");



        //Assertions
        assertEquals(true, bOutOfRange.get());
        assertEquals(true, bConfigurationError.get());        
        
        assertEquals(1234, iMode.get());
        assertEquals(4321, iErrorCode.get());
        assertEquals(3.1416F, rValue.get());
        assertEquals(3.1416F * 2, rAutoValue.get());
        assertEquals(3.1416F * 3, rManualValue.get());
        assertEquals(3.1416F * 4, rEstopValue.get());
        assertEquals(true, bPB_ResetError.get());
        assertEquals(false, bPBEN_ResetError.get());
        assertEquals(true, bError.get());
        assertEquals(false, bInterlock.get());
        assertEquals(2345, iEstopFunction.get());
    }

    @Test
    @Order(2)
    public void FieldOffsetTest() {
        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = AO.getFieldOffsets();
        assertEquals(7, fieldOffsets.size());

        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));
       
        //The offset is recalculated when the pvRecord is assigned to the Item.
        assertEquals(0, fieldOffsets.get(2).left);
        assertEquals((byte) -1, fieldOffsets.get(2).right);        
        
        assertEquals(2, fieldOffsets.get(3).left);
        assertEquals((byte) -1, fieldOffsets.get(3).right);
        assertEquals(14, fieldOffsets.get(4).left);
        assertEquals((byte) -1, fieldOffsets.get(4).right);
        assertEquals(22, fieldOffsets.get(5).left);
        assertEquals((byte) 0, fieldOffsets.get(5).right);
        assertEquals(22, fieldOffsets.get(6).left);
        assertEquals((byte) 1, fieldOffsets.get(6).right);        
        
    }
}
