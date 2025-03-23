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
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
public class S7DBMotorTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBMotorTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;
    private DBRecord Motor_00;

    private PVShort iMode;
    private PVShort iErrorCode;
    private PVShort iStatus;

    private PVBoolean bPB_ResetError;
    private PVBoolean bPB_Forward;
    private PVBoolean bPB_Reverse;
    private PVBoolean bPB_Stop;
    private PVBoolean bPBEN_ResetError;
    private PVBoolean bPBEN_Forward;
    private PVBoolean bPBEN_Reverse;
    private PVBoolean bPBEN_Stop;
    private PVBoolean bForwardOn;
    private PVBoolean bReverseOn;
    private PVBoolean bSignalForward;
    private PVBoolean bSignalReverse;
    private PVBoolean bError;
    private PVBoolean bInterlock;

    private PVBoolean bMotorProtectorTripped;
    private PVBoolean bLocalDisconnectOff;
    private PVBoolean bClutchTripped;
    private PVBoolean bNoSignalForward;
    private PVBoolean bNoSignalReverse;
    private PVBoolean bMotorNotStopped;

    private PVInt tTimeOut;

    @BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the motor class");
        logger.info("Test motor for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(100);
        byteBuf.setShort(0, 1234);               //iMOde
        byteBuf.setShort(2, 4321);               //iErrorCode
        byteBuf.setShort(4, 1010);               //iStatus
        byteBuf.setByte(6, 255);                 //bPB_ResetError, bPB_Forward, bPB_Reverse, bPB_Stop, bPBEN_ResetError, bPBEN_Forward, bPBEN_Reverse, bPBEN_Stop
        byteBuf.setByte(7, 63);                 //bForwardOn, bReverseOn, bSignalForward, bSignalReverse, bError, bInterlock
        byteBuf.setByte(8, 63);                 //bMotorProtectorTripped, bLocalDisconnectOff, bClutchTripped, bNoSignalForward, bNoSignalReverse, bMotorNotStopped 
        byteBuf.setInt(10, 1330);           //tTimeOut

    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the motor class test");
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
        S7DBMotorFactory MotorFactory = new S7DBMotorFactory();
        Motor_00 = MotorFactory.create("Motor_00");
    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    @Order(1)
    public void DBRecordTest() {

        PVString pvStrOffset = Motor_00.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");

        plcItem.addItemListener(Motor_00);
        plcItem.setPlcValue(plcValue);

        PVStructure pvStructureCmd = Motor_00.getPVRecordStructure().getPVStructure().getStructureField("cmd");
        PVStructure pvStructureSts = Motor_00.getPVRecordStructure().getPVStructure().getStructureField("sts");
        PVStructure pvStructurePar = Motor_00.getPVRecordStructure().getPVStructure().getStructureField("par");

        value = Motor_00.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = Motor_00.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = Motor_00.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        //Read command values            
        iMode = pvStructureCmd.getShortField("iMode");
        iErrorCode = pvStructureCmd.getShortField("iErrorCode");
        iStatus = pvStructureCmd.getShortField("iStatus");
        bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
        bPB_Forward = pvStructureCmd.getBooleanField("bPB_Forward");
        bPB_Reverse = pvStructureCmd.getBooleanField("bPB_Reverse");
        bPB_Stop = pvStructureCmd.getBooleanField("bPB_Stop");
        bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
        bPBEN_Forward = pvStructureCmd.getBooleanField("bPBEN_Forward");
        bPBEN_Reverse = pvStructureCmd.getBooleanField("bPBEN_Reverse");
        bPBEN_Stop = pvStructureCmd.getBooleanField("bPBEN_Stop");
        bForwardOn = pvStructureCmd.getBooleanField("bForwardOn");
        bReverseOn = pvStructureCmd.getBooleanField("bReverseOn");
        bSignalForward = pvStructureCmd.getBooleanField("bSignalForward");
        bSignalReverse = pvStructureCmd.getBooleanField("bSignalReverse");
        bError = pvStructureCmd.getBooleanField("bError");
        bInterlock = pvStructureCmd.getBooleanField("bInterlock");

        //Read status values
        bMotorProtectorTripped = pvStructureSts.getBooleanField("bMotorProtectorTripped");
        bLocalDisconnectOff = pvStructureSts.getBooleanField("bLocalDisconnectOff");
        bClutchTripped = pvStructureSts.getBooleanField("bClutchTripped");
        bNoSignalForward = pvStructureSts.getBooleanField("bNoSignalForward");
        bNoSignalReverse = pvStructureSts.getBooleanField("bNoSignalReverse");
        bMotorNotStopped = pvStructureSts.getBooleanField("bMotorNotStopped");

        //Write command and parameters values
        tTimeOut = pvStructurePar.getIntField("tTimeOut");

        logger.info("\n--------------STARTING TEST DBRECORD MOTOR----------");
        logger.info(String.format("Test on Engine:n (expected iMode: 1234 == (current iMode): %d)", iMode.get()));
        assertEquals(1234, iMode.get());
        logger.info(String.format("Test on Engine:(expected iErrorCode: 4321 == (current iErrorCode): %d)", iErrorCode.get()));
        assertEquals(4321, iErrorCode.get());
        logger.info(String.format("Test on Engine:\\n (expected iStatus: 1010 == (current iStatus): %d)", iStatus.get()));
        assertEquals(1010, iStatus.get());
        logger.info(String.format("Test on Engine:(bPB_ResetError expected: true == (bPB_ResetError actual): %b)", bPB_ResetError.get()));
        assertEquals(true, bPB_ResetError.get());
        logger.info(String.format("Test in Engine:(expected bPB_Forward: true == (current bPB_Forward): %b)", bPB_Forward.get()));
        assertEquals(true, bPB_Forward.get());
        logger.info(String.format("Test on Engine:(expected bPB_Reverse: true == (actual bPB_Reverse): %b)", bPB_Reverse.get()));
        assertEquals(true, bPB_Reverse.get());
        logger.info(String.format("Test on Engine:(expected bPB_Stop: true == (actual bPB_Stop): %b)", bPB_Stop.get()));
        assertEquals(true, bPB_Stop.get());
        logger.info(String.format("Test on Engine:(bPBEN_ResetError expected: true == (bPBEN_ResetError actual): %b)", bPBEN_ResetError.get()));
        assertEquals(true, bPBEN_ResetError.get());
        logger.info(String.format("Test on Engine:(expected bPBEN_Forward: true == (current bPBEN_Forward): %b)", bPBEN_Forward.get()));
        assertEquals(true, bPBEN_Forward.get());
        logger.info(String.format("Test on Engine:(expected bPBEN_Reverse: true == (current bPBEN_Reverse): %b)", bPBEN_Reverse.get()));
        assertEquals(true, bPBEN_Reverse.get());
        logger.info(String.format("Test on Engine:(expected bPBEN_Stop: true == (actual bPBEN_Stop): %b)", bPBEN_Stop.get()));
        assertEquals(true, bPBEN_Stop.get());
        logger.info(String.format("Test in Engine:(expected bForwardOn: true == (current bForwardOn): %b)", bForwardOn.get()));
        assertEquals(true, bForwardOn.get());
        logger.info(String.format("Test on Engine:(expected bReverseOn: true == (current bReverseOn): %b)", bReverseOn.get()));
        assertEquals(true, bReverseOn.get());
        logger.info(String.format("Test on Engine:(bSignalForward expected: true == (bSignalForward actual): %b)", bSignalForward.get()));
        assertEquals(true, bSignalForward.get());
        logger.info(String.format("Test on Engine:n (bSignalReverse expected: true == (bSignalReverse actual): %b)", bSignalReverse.get()));
        assertEquals(true, bSignalReverse.get());
        logger.info(String.format("Test on Engine:(expected bError: true == (actual bError): %b)", bError.get()));
        assertEquals(true, bError.get());
        logger.info(String.format("Test on Engine:(expected bInterlock: true == (current bInterlock): %b)", bInterlock.get()));
        assertEquals(true, bInterlock.get());

        logger.info(String.format("Test on Engine:(bEngineProtectorTripped expected: true == (bEngineProtectorTripped actual): %b)", bMotorProtectorTripped.get()));
        assertEquals(true, bMotorProtectorTripped.get());
        logger.info(String.format("Test on Engine:(bLocalDisconnectOff expected: true == (bLocalDisconnectOff actual): %b)", bLocalDisconnectOff.get()));
        assertEquals(true, bLocalDisconnectOff.get());
        logger.info(String.format("Test on Engine:(expected bClutchTripped: true == (actual bClutchTripped): %b)", bClutchTripped.get()));
        assertEquals(true, bClutchTripped.get());
        logger.info(String.format("Test on Engine:(bNoSignalForward expected: true == (bNoSignalForward actual): %b)", bNoSignalForward.get()));
        assertEquals(true, bNoSignalForward.get());
        logger.info(String.format("Test on Engine:(bNoSignalReverse expected: true == (bNoSignalReverse actual): %b)", bNoSignalReverse.get()));
        assertEquals(true, bNoSignalReverse.get());
        logger.info(String.format("Test on Engine:(expected bEngineNotStopped: true == (current bEngineNotStopped): %b)", bMotorNotStopped.get()));
        assertEquals(true, bMotorNotStopped.get());

        logger.info(String.format("Test on Engine:(expected tTimeOut: 1330 == (current tTimeOut): %d)", tTimeOut.get()));
        assertEquals(1330, tTimeOut.get());

        logger.info("\nTEST Motor SUCCESSFULLY COMPLETED ");
    }

    @Test
    @Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = Motor_00.getFieldOffsets();
        logger.info(String.format("Number of items allowed to be monitored:(Value expected: 8) == (Value actual: %d)", fieldOffsets.size()));

        assertEquals(8, fieldOffsets.size());
        
        Assertions.assertNull(fieldOffsets.get(0));
        Assertions.assertNull(fieldOffsets.get(1));
        Assertions.assertNull(fieldOffsets.get(2));
        Assertions.assertNotNull(fieldOffsets.get(3));
        Assertions.assertNotNull(fieldOffsets.get(4));
        Assertions.assertNotNull(fieldOffsets.get(5));
        Assertions.assertNotNull(fieldOffsets.get(6));
        Assertions.assertNotNull(fieldOffsets.get(7));
        logger.info(String.format("Monitoring fields were validated, a total of %s",String.valueOf(fieldOffsets.size())));
    }
}
