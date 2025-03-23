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
import java.util.UUID;
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
public class S7DBSolenoidValveTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBSolenoidValveTest.class);
    private PlcValue plcValue;
    private static ByteBuf byteBuf;
    private PlcItem plcItem;
    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;

    /*
        cmd
     */
    private PVShort iMode;
    private PVShort iErrorCode;
    private PVShort iStatus;
    private PVBoolean bPB_ResetError;
    private PVBoolean bPB_Home;
    private PVBoolean bPB_Work;
    private PVBoolean bPBEN_ResetError;
    private PVBoolean bPBEN_Home;
    private PVBoolean bPBEN_Work;
    private PVBoolean bHomeOn;
    private PVBoolean bWorkOn;
    private PVBoolean bSignalHome;
    private PVBoolean bSignalWork;
    private PVBoolean bError;
    private PVBoolean bInterlock;

    /*
        sts
     */
    private PVBoolean bNoHomeFeedback;
    private PVBoolean bNoWorkFeedback;
    private PVBoolean bHomeFeedbackStillActive;
    private PVBoolean bWorkFeedbackStillActive;

    /*
        par
     */
    private PVInt tTimeOut;

    @BeforeAll
    public static void setUpClass() {
        /*
        Create an object Bytebuf
         */
        logger.info("Starting the testing of the solenoid valve class");
        logger.info("Test Solenoid valve for S7 plc");
        logger.info("Creating buffer to plcValue to solenoid valve");
        byteBuf = buffer(100);
        byteBuf.setShort(0, 1234);   //imode
        byteBuf.setShort(2, 4321);   //iErrorCode
        byteBuf.setShort(4, 1234);   //iStatus
        byteBuf.setByte(6, 255);     //bPB_ResetError, bPB_Home, bPB_Work, bPBEN_ResetError,,bPBEN_Home, bPBEN_Work, bHomeOn, bWorkOn   
        byteBuf.setByte(7, 15);      //bSignalHome, bSignalWork, bError, bInterlock
        byteBuf.setByte(8, 15);      //bNoHomeFeedback, bNoWorkFeedback, bHomeFeedbackStillActive, bWorkFeedbackStillActive
        byteBuf.setInt(10, 1330);    //tTimeOut
    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the solenoid valve class test");
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
    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    @Order(1)
    public void dbAoRecord() {

        S7DBValveSolenoidFactory ValveSolenoid = new S7DBValveSolenoidFactory();

        DBRecord ValveSold = ValveSolenoid.create("ValveSold_00");

        PVString pvStrOffset = ValveSold.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");

        plcItem.addItemListener(ValveSold);
        plcItem.setPlcValue(plcValue);

        PVStructure pvStructureCmd = ValveSold.getPVRecordStructure().getPVStructure().getStructureField("cmd");
        PVStructure pvStructureSts = ValveSold.getPVRecordStructure().getPVStructure().getStructureField("sts");
        PVStructure pvStructurePar = ValveSold.getPVRecordStructure().getPVStructure().getStructureField("par");

        value = ValveSold.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = ValveSold.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = ValveSold.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        iMode = pvStructureCmd.getShortField("iMode");
        iErrorCode = pvStructureCmd.getShortField("iErrorCode");
        iStatus = pvStructureCmd.getShortField("iStatus");
        bPB_ResetError = pvStructureCmd.getBooleanField("bPB_ResetError");
        bPB_Home = pvStructureCmd.getBooleanField("bPB_Home");
        bPB_Work = pvStructureCmd.getBooleanField("bPB_Work");
        bPBEN_ResetError = pvStructureCmd.getBooleanField("bPBEN_ResetError");
        bPBEN_Home = pvStructureCmd.getBooleanField("bPBEN_Home");
        bPBEN_Work = pvStructureCmd.getBooleanField("bPBEN_Work");
        bHomeOn = pvStructureCmd.getBooleanField("bHomeOn");
        bWorkOn = pvStructureCmd.getBooleanField("bWorkOn");
        bSignalHome = pvStructureCmd.getBooleanField("bSignalHome");
        bSignalWork = pvStructureCmd.getBooleanField("bSignalWork");
        bError = pvStructureCmd.getBooleanField("bError");
        bInterlock = pvStructureCmd.getBooleanField("bInterlock");

        bNoHomeFeedback = pvStructureSts.getBooleanField("bNoHomeFeedback");
        bNoWorkFeedback = pvStructureSts.getBooleanField("bNoWorkFeedback");
        bHomeFeedbackStillActive = pvStructureSts.getBooleanField("bHomeFeedbackStillActive");
        bWorkFeedbackStillActive = pvStructureSts.getBooleanField("bWorkFeedbackStillActive");

        tTimeOut = pvStructurePar.getIntField("tTimeOut");

        //Assertions
        logger.info("\n--------------STARTING TEST DBRECORD   Solenoid Valve----------");
        logger.info(String.format("Test in Valve Solenoid:(expected iMode: 1234 == (current iMode): %d)", iMode.get()));
        assertEquals(1234, iMode.get());
        logger.info(String.format("Test in Valve Solenoid:(expected iErrorCode: 4321 == (current iErrorCode): %d)", iErrorCode.get()));
        assertEquals(4321, iErrorCode.get());
        logger.info(String.format("Test in Valve Solenoid:(expected iStatus: 4321 == (current iStatus): %d)", iStatus.get()));
        assertEquals(1234, iStatus.get());
       logger.info(String.format("Test on Valve solenoid:(bPB_ResetError expected: true == (bPB_ResetError actual): %b)", bPB_ResetError.get()));
        assertEquals(true, bPB_ResetError.get()); 
        logger.info(String.format("Test in Valve solenoid:(bPB_Home expected: true == (bPB_Home actual): %b)", bPB_Home.get()));
        assertEquals(true, bPB_Home.get()); 
        logger.info(String.format("Test in Valve solenoid:(expected bPB_Work: true == (actual bPB_Work): %b)", bPB_Work.get()));
        assertEquals(true, bPB_Work.get()); 
        logger.info(String.format("Test on Valve solenoid:(bPBEN_ResetError expected: true == (bPBEN_ResetError actual): %b)", bPBEN_ResetError.get()));
        assertEquals(true, bPBEN_ResetError.get()); 
        logger.info(String.format("Test in Valve solenoid:(expected bPBEN_Home: true == (current bPBEN_Home): %b)", bPBEN_Home.get()));
        assertEquals(true, bPBEN_Home.get()); 
        logger.info(String.format("Test in Valve solenoid:(bPBEN_Work expected: true == (bPBEN_Work actual): %b)", bPBEN_Work.get()));
        assertEquals(true, bPBEN_Work.get()); 
        logger.info(String.format("Test in Valve solenoid:(expected bHomeOn: true == (current bHomeOn): %b)", bHomeOn.get()));
        assertEquals(true, bHomeOn.get()); 
        logger.info(String.format("Test in Valve solenoid:(expected bWorkOn: true == (current bWorkOn): %b)", bWorkOn.get()));
        assertEquals(true, bWorkOn.get()); 
        
        logger.info(String.format("Test in Valve solenoid:(bSignalHome expected: true == (bSignalHome actual): %b)", bSignalHome.get()));
        assertEquals(true, bSignalHome.get()); 
        logger.info(String.format("Test in Valve solenoid:(bSignalWork expected: true == (bSignalWork actual): %b)", bSignalWork.get()));
        assertEquals(true, bSignalWork.get()); 
        logger.info(String.format("Test in Valve solenoid:(bError expected: true == (bError actual): %b)", bError.get()));
        assertEquals(true, bError.get()); 
        logger.info(String.format("Test in Valve solenoid:(expected bInterlock: true == (current bInterlock): %b)", bInterlock.get()));
        assertEquals(true, bInterlock.get()); 
        
        logger.info(String.format("Test in Valve solenoid:(bNoHomeFeedback expected: true == (bNoHomeFeedback actual): %b)", bNoHomeFeedback.get()));
        assertEquals(true, bNoHomeFeedback.get()); 
        logger.info(String.format("Test in Valve solenoid:(bNoWorkFeedback expected: true == (bNoWorkFeedback actual): %b)", bNoWorkFeedback.get()));
        assertEquals(true, bNoWorkFeedback.get()); 
        logger.info(String.format("Test in Valve solenoid:(bHomeFeedbackStillActive expected: true == (bHomeFeedbackStillActive actual): %b)", bHomeFeedbackStillActive.get()));
        assertEquals(true, bHomeFeedbackStillActive.get()); 
        logger.info(String.format("Test in Valve solenoid:(bWorkFeedbackStillActive expected: true == (bWorkFeedbackStillActive actual): %b)", bWorkFeedbackStillActive.get()));
        assertEquals(true, bWorkFeedbackStillActive.get()); 
        
        logger.info(String.format("Test on Valve solenoid:(expected tTimeOut: true == (current tTimeOut): %d)", tTimeOut.get()));
        assertEquals(1330, tTimeOut.get()); 
         logger.info("\nTEST Solenoid SUCCESSFULLY COMPLETED ");
        
    }

}
