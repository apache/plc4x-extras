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
public class S7DBMotorTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBMotorTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;
    private DBRecord Motor;

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

    //@BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the motor class");
        logger.info("Test motor for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(100);
        byteBuf.setByte(0, 0b0001_0101);    //MotorProtectorTripped
                                            //LocalDisconnectOff
                                            //ClutchTripped
                                            //NoSignalForward
                                            //NoSignalReverse
                                            //MotorNotStopped        
        byteBuf.setShort(2, 1234);          //iMOde
        byteBuf.setShort(4, 4321);          //iErrorCode
        byteBuf.setShort(6, 1010);          //iStatus
        byteBuf.setByte(8, 0b1010_1010);    //bPB_ResetError
                                            //bPB_Forward
                                            //bPB_Reverse
                                            //bPB_Stop
                                            //bPBEN_ResetError
                                            //bPBEN_Forward
                                            //bPBEN_Reverse
                                            //bPBEN_Stop
        byteBuf.setByte(9, 0b0011_0101);    //bForwardOn
                                            //bReverseOn
                                            //bSignalForward
                                            //bSignalReverse
                                            //bError
                                            //bInterlock
    }

    //@AfterAll
    public static void tearDownClass() {
        logger.info("Ending the motor class test");
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
        S7DBMotorFactory MotorFactory = new S7DBMotorFactory();
        Motor = MotorFactory.create("Motor");
    }

    //@AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

//    @Test
    //@Order(1)
    public void DBRecordTest() {

        PVString pvStrOffset = Motor.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");

        plcItem.addItemListener(Motor);
        plcItem.setPlcValue(plcValue);

        PVStructure udtHMI = Motor.getPVRecordStructure().getPVStructure().getStructureField("udtHMI");
        PVStructure udtError = Motor.getPVRecordStructure().getPVStructure().getStructureField("udtError");

        value = Motor.getPVRecordStructure().getPVStructure().getShortField("value");
        write_enable = Motor.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        //Read command values            
        iMode = udtHMI.getShortField("iMode");
        iErrorCode = udtHMI.getShortField("iErrorCode");
        iStatus = udtHMI.getShortField("iStatus");
        bPB_ResetError = udtHMI.getBooleanField("bPB_ResetError");
        bPB_Forward = udtHMI.getBooleanField("bPB_Forward");
        bPB_Reverse = udtHMI.getBooleanField("bPB_Reverse");
        bPB_Stop = udtHMI.getBooleanField("bPB_Stop");
        bPBEN_ResetError = udtHMI.getBooleanField("bPBEN_ResetError");
        bPBEN_Forward = udtHMI.getBooleanField("bPBEN_Forward");
        bPBEN_Reverse = udtHMI.getBooleanField("bPBEN_Reverse");
        bPBEN_Stop = udtHMI.getBooleanField("bPBEN_Stop");
        bForwardOn = udtHMI.getBooleanField("bForwardOn");
        bReverseOn = udtHMI.getBooleanField("bReverseOn");
        bSignalForward = udtHMI.getBooleanField("bSignalForward");
        bSignalReverse = udtHMI.getBooleanField("bSignalReverse");
        bError = udtHMI.getBooleanField("bError");
        bInterlock = udtHMI.getBooleanField("bInterlock");

        //Read status values
        bMotorProtectorTripped = udtError.getBooleanField("bMotorProtectorTripped");
        bLocalDisconnectOff = udtError.getBooleanField("bLocalDisconnectOff");
        bClutchTripped = udtError.getBooleanField("bClutchTripped");
        bNoSignalForward = udtError.getBooleanField("bNoSignalForward");
        bNoSignalReverse = udtError.getBooleanField("bNoSignalReverse");
        bMotorNotStopped = udtError.getBooleanField("bMotorNotStopped");

        //Write command and parameters values

        assertEquals(true, bMotorProtectorTripped.get());
        assertEquals(false, bLocalDisconnectOff.get());
        assertEquals(true, bClutchTripped.get());
        assertEquals(false, bNoSignalForward.get());
        assertEquals(true, bNoSignalReverse.get());
        assertEquals(false, bMotorNotStopped.get());        
        
        assertEquals(1234, iMode.get());
        assertEquals(4321, iErrorCode.get());
        assertEquals(1010, iStatus.get());
        assertEquals(false, bPB_ResetError.get());
        assertEquals(true, bPB_Forward.get());
        assertEquals(false, bPB_Reverse.get());
        assertEquals(true, bPB_Stop.get());
        assertEquals(false, bPBEN_ResetError.get());
        assertEquals(true, bPBEN_Forward.get());
        assertEquals(false, bPBEN_Reverse.get());
        assertEquals(true, bPBEN_Stop.get());
        assertEquals(true, bForwardOn.get());
        assertEquals(false, bReverseOn.get());
        assertEquals(true, bSignalForward.get());
        assertEquals(false, bSignalReverse.get());
        assertEquals(true, bError.get());
        assertEquals(true, bInterlock.get());



    }

//    @Test
    //@Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = Motor.getFieldOffsets();
        assertEquals(12, fieldOffsets.size());
        
        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));
        assertNull(fieldOffsets.get(2));
        assertNotNull(fieldOffsets.get(3));
        assertNotNull(fieldOffsets.get(4));
        assertNotNull(fieldOffsets.get(5));
        assertNotNull(fieldOffsets.get(6));
        assertNotNull(fieldOffsets.get(7));

    }
}
