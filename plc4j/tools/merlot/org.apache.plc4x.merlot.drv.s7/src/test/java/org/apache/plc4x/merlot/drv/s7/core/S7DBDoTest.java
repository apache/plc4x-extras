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
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author lerb
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S7DBDoTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBDoTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord DO_00;

    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;

    private PVShort iMode;
    private PVBoolean bOn;
    private PVBoolean bOnActual;
    private PVBoolean bPB_On;
    private PVBoolean bPB_Off;
    private PVBoolean bPBEN_On;
    private PVBoolean bPBEN_Off;

    private PVShort out_iMode;
    private PVBoolean out_bPB_On;
    private PVBoolean out_bPB_Off;

    @BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the digital output class");
        logger.info("Test Digital Output for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(5);
        byteBuf.setShort(0, 1234);
        byteBuf.setByte(2, 63);
    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the Digital Output class test");
    }

    @BeforeEach
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

        //DBRecord associated with each particular test
        S7DBDoFactory DoFactory = new S7DBDoFactory();
        DO_00 = DoFactory.create("DO_00");
    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    @Order(1)
    public void DBRecordTest() {
        PVString pvStrOffset = DO_00.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        plcItem.addItemListener(DO_00);
        plcItem.setPlcValue(plcValue);

        value = DO_00.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = DO_00.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = DO_00.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        PVStructure pvStructureCmd = DO_00.getPVRecordStructure().getPVStructure().getStructureField("cmd");
        PVStructure pvStructureSts = DO_00.getPVRecordStructure().getPVStructure().getStructureField("sts");
        PVStructure pvStructureOut = DO_00.getPVRecordStructure().getPVStructure().getStructureField("out");

        //cmd
        iMode = pvStructureCmd.getShortField("iMode");
        bOn = pvStructureCmd.getBooleanField("bOn");
        bOnActual = pvStructureCmd.getBooleanField("bOnActual");
        bPB_On = pvStructureCmd.getBooleanField("bPB_On");
        bPB_Off = pvStructureCmd.getBooleanField("bPB_Off");
        bPBEN_On = pvStructureCmd.getBooleanField("bPBEN_On");
        bPBEN_Off = pvStructureCmd.getBooleanField("bPBEN_Off");

        //sts
        //out
        out_iMode = pvStructureOut.getShortField("iMode");
        out_bPB_On = pvStructureOut.getBooleanField("bPB_On");
        out_bPB_Off = pvStructureOut.getBooleanField("bPB_Off");

        logger.info("\n--------------STARTING TEST DBRECORD Do----------");
        logger.info(String.format("Test in Digital Output:(expected iMode: 1234 == (current iMode): %d)", iMode.get()));
        assertEquals(1234, iMode.get());
        logger.info(String.format("Test in Digital Output:(expected bOn: true == (current bOn): %b)", bOn.get()));
        assertEquals(true, bOn.get());
        logger.info(String.format("Test in Digital Output:(expected bOnActual: true == (current bOnActual): %b)", bOnActual.get()));
        assertEquals(true, bOnActual.get());
        logger.info(String.format("Test in Digital Output:(expected bPB_On: true == (current bPB_On): %b)", bPB_On.get()));
        assertEquals(true, bPB_On.get());
        logger.info(String.format("Test in Digital Output:(expected bPB_Off: true == (current bPB_Off): %b)", bPB_Off.get()));
        assertEquals(true, bPB_Off.get());
        logger.info(String.format("Test in Digital Output:(expected bPBEN_On: true == (current bPBEN_On): %b)", bPBEN_On.get()));
        assertEquals(true, bPBEN_On.get());
        logger.info(String.format("Test in Digital Output:(expected bPBEN_Off: true == (current bPBEN_Off): %b)", bPBEN_Off.get()));
        assertEquals(true, bPBEN_Off.get());

        logger.info(String.format("Test in Digital Output:(expected out_iMode: true == (current out_iMode): %b)", out_iMode.get()));
        assertEquals(true, out_iMode.get());
        logger.info(String.format("Test in Digital Output:(expected out_bPB_On: true == (current out_bPB_On): %b)", out_bPB_On.get()));
        assertEquals(true, out_bPB_On.get());
        logger.info(String.format("Test in Digital Output:(expected out_bPB_Off: true == (current out_bPB_Off): %b)", out_bPB_Off.get()));
        assertEquals(true, out_bPB_Off.get());

    }

    @Test
    @Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = DO_00.getFieldOffsets();
        logger.info(String.format("Number of items allowed to be monitored:(Value expected: 3) == (Value actual: %d)", fieldOffsets.size()));
        assertEquals(3, fieldOffsets.size());
        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));
        assertNotNull(fieldOffsets.get(2));

    }

}
