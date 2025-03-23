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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 * @author lerb
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S7DBValveAnalogTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBValveAnalogTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord ValveAng_00;

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

    private PVBoolean InvalidFeedback;
    private PVInt tTimeOut;

    @BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the analog valve class");
        logger.info("Test analog valve for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(50);
        byteBuf.setShort(0, 1234);               //iMOde
        byteBuf.setShort(2, 4321);               //iErrorCode
        byteBuf.setShort(4, 1010);               //iStatus
        byteBuf.setFloat(6, 3.1416F);            //rManualSP
        byteBuf.setFloat(10, 3.1416F * 2);       //rAutoSP
        byteBuf.setFloat(14, 3.1416F * 4);       //rEstopSP
        byteBuf.setFloat(18, 3.1416F * 6);       //rActual
        byteBuf.setByte(22, 15);                 //bPB_ResetError, bPBEN_ResetError, bError, bInterlock
        byteBuf.setShort(24, 1234);                //iEstopFunction
        byteBuf.setByte(26, 1);                  //InvalidFeedback
        byteBuf.setInt(28, 1330);                //tTimeOut
    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the analog valve class test");
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

        S7DBValveAnalogFactory ValveAnalog = new S7DBValveAnalogFactory();
        ValveAng_00 = ValveAnalog.create("ValveAnf_00");
    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    @Order(1)
    public void DBRecordTest() {

        PVString pvStrOffset = ValveAng_00.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        plcItem.addItemListener(ValveAng_00);
        plcItem.setPlcValue(plcValue);

        PVStructure pvStructureCmd = ValveAng_00.getPVRecordStructure().getPVStructure().getStructureField("cmd");
        PVStructure pvStructureSts = ValveAng_00.getPVRecordStructure().getPVStructure().getStructureField("sts");
        PVStructure pvStructurePar = ValveAng_00.getPVRecordStructure().getPVStructure().getStructureField("par");

        value = ValveAng_00.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = ValveAng_00.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = ValveAng_00.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        //Read command values            
        iMode = pvStructureCmd.getShortField("iMode");
        iErrorCode = pvStructureCmd.getShortField("iErrorCode");
        iStatus = pvStructureCmd.getShortField("iStatus");
        rManualSP = pvStructureCmd.getFloatField("rManualSP");
        rAutoSP = pvStructureCmd.getFloatField("rAutoSP");
        rEstopSP = pvStructureCmd.getFloatField("rEstopSP");
        rActual = pvStructureCmd.getFloatField("rActual");
        bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
        bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
        bError = pvStructureCmd.getBooleanField("bError");
        bInterlock = pvStructureCmd.getBooleanField("bInterlock");
        iEstopFunction = pvStructureCmd.getShortField("iEstopFunction");

        //Read status values   
        InvalidFeedback = pvStructureSts.getBooleanField("InvalidFeedback");

        //Write command values   
        tTimeOut = pvStructurePar.getIntField("tTimeOut");

        logger.info("\n--------------STARTING TEST DBRECORD  Valve Analog----------");
        logger.info(String.format("Test in Valve Analog:(expected iMode: 1234 == (current iMode): %d)", iMode.get()));
        assertEquals(1234, iMode.get());
        logger.info(String.format("Test in Valve Analog:(expected iErrorCode: 4321 == (current iErrorCode): %d)", iErrorCode.get()));
        assertEquals(4321, iErrorCode.get());
        logger.info(String.format("Test in Valve Analog:(expected iStatus: 1010 == (current iStatus): %d)", iStatus.get()));
        assertEquals(1010, iStatus.get());
        logger.info(String.format("Test in Valve Analog:(expected rManualSP: 3.1416F == (actual rManualSP): %f)", rManualSP.get()));
        assertEquals(3.1416F, rManualSP.get());
        logger.info(String.format("Test in Valve Analog:(expected rAutoSP: 6.2832F == (actual rAutoSP): %f)", rAutoSP.get()));
        assertEquals(6.2832F, rAutoSP.get());
        logger.info(String.format("Test in Valve Analog:(expected rEstopSP: 12.5664F == (actual rEstopSP): %f)", rEstopSP.get()));
        assertEquals(12.5664F, rEstopSP.get());
        logger.info(String.format("Test in Valve Analog:(rActual expected: 18.8496F == (rActual actual): %f)", rActual.get()));
        assertEquals(18.8496F, rActual.get());

        logger.info(String.format("Test in Valve Analog:(bPB_ResetError expected: true == (bPB_ResetError actual): %b)", bPB_ResetError.get()));
        assertEquals(true, bPB_ResetError.get());
        logger.info(String.format("Test in Valve Analog:(bPBEN_ResetError expected: true == (bPBEN_ResetError actual): %b)", bPBEN_ResetError.get()));
        assertEquals(true, bPBEN_ResetError.get());
        logger.info(String.format("Test in Valve Analog:(bError expected: true == (bError actual): %b)", bError.get()));
        assertEquals(true, bError.get());
        logger.info(String.format("Test in Valve Analog:(bInterlock expected: true == (bInterlock actual): %b)", bInterlock.get()));
        assertEquals(true, bInterlock.get());

        logger.info(String.format("Test in Valve Analog:(expected iEstopFunction: 1234 == (current iEstopFunction): %d)", iEstopFunction.get()));
        assertEquals(1234, iEstopFunction.get());

        logger.info(String.format("Test in Valve Analog:(Expected InvalidFeedback: true == (Current InvalidFeedback): %b)", InvalidFeedback.get()));
        assertEquals(true, InvalidFeedback.get());

        logger.info(String.format("Test on Valve Analog:(expected tTimeOut: 1330 == (actual tTimeOut): %d)", tTimeOut.get()));
        assertEquals(1330, tTimeOut.get());

        logger.info("\nTEST Analog Valve SUCCESSFULLY COMPLETED ");
    }

    @Test
    @Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = ValveAng_00.getFieldOffsets();
        logger.info(String.format("Number of items allowed to be monitored:(Value expected: 2) == (Value actual: %d)", fieldOffsets.size()));
        assertEquals(2, fieldOffsets.size());
        Assertions.assertNull(fieldOffsets.get(0));
        Assertions.assertNull(fieldOffsets.get(1));
        Assertions.assertNull(fieldOffsets.get(2));
        Assertions.assertNotNull(fieldOffsets.get(3));
        Assertions.assertNotNull(fieldOffsets.get(4));
        Assertions.assertNotNull(fieldOffsets.get(5));
        Assertions.assertNotNull(fieldOffsets.get(6));
        logger.info(String.format("Monitoring fields were validated, a total of %s", String.valueOf(fieldOffsets.size())));

    }
}
