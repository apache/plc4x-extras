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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author lerb
 */
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S7DBValveSolenoidTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBValveSolenoidTest.class);
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

    //@BeforeAll
    public static void setUpClass() {
        /*
        Create an object Bytebuf
         */
        logger.info("Starting the testing of the solenoid valve class");
        logger.info("Test Solenoid valve for S7 plc");
        logger.info("Creating buffer to plcValue to solenoid valve");
        byteBuf = buffer(100);
        byteBuf.setByte(0, 0b0000_1010);    //NoHomeFeedback
                                            //NoWorkFeedback
                                            //HomeFeedbackStillActive
                                            //WorkFeedbackStillActive
        byteBuf.setShort(2, 1234);          //imode
        byteBuf.setShort(4, 4321);          //iErrorCode
        byteBuf.setShort(6, 2345);          //iStatus
        byteBuf.setByte(8, 0b1010_0101);    //bPB_ResetError
                                            //bPB_Home
                                            //bPB_Work
                                            //bPBEN_ResetError
                                            //bPBEN_Home
                                            //bPBEN_Work
                                            //bHomeOn
                                            //bWorkOn   
        byteBuf.setByte(9, 0b0000_1011);    //bSignalHome
                                            //bSignalWork
                                            //bError
                                            //bInterlock
    }

    //@AfterAll
    public static void tearDownClass() {
        logger.info("Ending the solenoid valve class test");
    }

    //@BeforeEach
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

    //@AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

//    @Test
    //@Order(1)
    public void dbAoRecord() {

        S7DBValveSolenoidFactory ValveSolenoid = new S7DBValveSolenoidFactory();

        DBRecord ValveSold = ValveSolenoid.create("ValveSold_00");

        PVString pvStrOffset = ValveSold.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");

        plcItem.addItemListener(ValveSold);
        plcItem.setPlcValue(plcValue);

        PVStructure udtHMI = ValveSold.getPVRecordStructure().getPVStructure().getStructureField("udtHMI");
        PVStructure udtError = ValveSold.getPVRecordStructure().getPVStructure().getStructureField("udtError");

        value = ValveSold.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = ValveSold.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = ValveSold.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        iMode = udtHMI.getShortField("iMode");
        iErrorCode = udtHMI.getShortField("iErrorCode");
        iStatus = udtHMI.getShortField("iStatus");
        bPB_ResetError = udtHMI.getBooleanField("bPB_ResetError");
        bPB_Home = udtHMI.getBooleanField("bPB_Home");
        bPB_Work = udtHMI.getBooleanField("bPB_Work");
        bPBEN_ResetError = udtHMI.getBooleanField("bPBEN_ResetError");
        bPBEN_Home = udtHMI.getBooleanField("bPBEN_Home");
        bPBEN_Work = udtHMI.getBooleanField("bPBEN_Work");
        bHomeOn = udtHMI.getBooleanField("bHomeOn");
        bWorkOn = udtHMI.getBooleanField("bWorkOn");
        bSignalHome = udtHMI.getBooleanField("bSignalHome");
        bSignalWork = udtHMI.getBooleanField("bSignalWork");
        bError = udtHMI.getBooleanField("bError");
        bInterlock = udtHMI.getBooleanField("bInterlock");

        bNoHomeFeedback = udtError.getBooleanField("bNoHomeFeedback");
        bNoWorkFeedback = udtError.getBooleanField("bNoWorkFeedback");
        bHomeFeedbackStillActive = udtError.getBooleanField("bHomeFeedbackStillActive");
        bWorkFeedbackStillActive = udtError.getBooleanField("bWorkFeedbackStillActive");

        //Assertions        
        assertEquals(false, bNoHomeFeedback.get()); 
        assertEquals(true, bNoWorkFeedback.get()); 
        assertEquals(false, bHomeFeedbackStillActive.get()); 
        assertEquals(true, bWorkFeedbackStillActive.get());         

        assertEquals(1234, iMode.get());
        assertEquals(4321, iErrorCode.get());
        assertEquals(2345, iStatus.get());
        
        assertEquals(true, bPB_ResetError.get()); 
        assertEquals(false, bPB_Home.get()); 
        assertEquals(true, bPB_Work.get()); 
        assertEquals(false, bPBEN_ResetError.get()); 
        assertEquals(false, bPBEN_Home.get()); 
        assertEquals(true, bPBEN_Work.get()); 
        assertEquals(false, bHomeOn.get()); 
        assertEquals(true, bWorkOn.get()); 
        
        assertEquals(true, bSignalHome.get()); 
        assertEquals(true, bSignalWork.get()); 
        assertEquals(false, bError.get()); 
        assertEquals(true, bInterlock.get()); 
        

       
    }

}
