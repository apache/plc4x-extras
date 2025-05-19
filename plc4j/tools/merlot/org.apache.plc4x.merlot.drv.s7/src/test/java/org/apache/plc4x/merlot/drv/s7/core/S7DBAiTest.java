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
import org.apache.plc4x.merlot.db.core.DBBaseFactory;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S7DBAiTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBAiTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord AI;

    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;

    private PVShort iMode;
    private PVShort iErrorCode;
    private PVShort iStatus;

    private PVFloat rActiveValue;
    private PVFloat rInputValue;
    private PVFloat rManualValue;

    private PVBoolean bPB_ResetError;
    private PVBoolean bPBEN_ResetError;
    private PVBoolean bError;

    private PVBoolean bLowLowAlarm;
    private PVBoolean bHighHighAlarm;
    private PVBoolean bInvalid;

 
    //@BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the analog input class");
        logger.info("Test Analog inputs for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(100);
        byteBuf.setShort(0, 0b0000_0001_0000_0000); //0. bLowLowAlarm
                                                    //1. bHighHighAlarm
                                                    //2. bInvalid
                                                   
        byteBuf.setShort(2, 1234);                  //iMOde
        byteBuf.setShort(4, 4321);                  //iErrorCode
        byteBuf.setShort(6, 1010);                  //iStatus
        
        byteBuf.setFloat(8, 3.1416F);               //iActiveValue
        byteBuf.setFloat(12, 3.1416F * 2);          //rInputValue
        byteBuf.setFloat(16, 3.1416F * 4);          //rManualValue
        
        byteBuf.setShort(20, 0b0000_0001_0000_0000);//0. bPB_ResetError
                                                    //1. bPBEN_ResetError
                                                    //2. bError                                                         
    }

    //@AfterAll
    public static void tearDownClass() {
        logger.info("Ending the analog input class test");
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
        
        DBBaseFactory AIFactory = new S7DBAiFactory();
        AI = AIFactory.create("AI");
        PVString pvStrOffset = AI.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        
        AI.atach(plcItem);
    }

    //@AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    
    
    /**
     * 
     */
    @Test
    //@Order(2)
    public void FieldOffsetTest() {
        
        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = AI.getFieldOffsets();

        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));
       
        //The offset is recalculated when the pvRecord is assigned to the Item.
//        assertEquals(0, fieldOffsets.get(2).left);
//        assertEquals((byte) -1, fieldOffsets.get(3).right);        
//        
//        assertEquals(2, fieldOffsets.get(3).left);
//        assertEquals((byte) -1, fieldOffsets.get(3).right);
//        assertEquals(16, fieldOffsets.get(4).left);
//        assertEquals((byte) -1, fieldOffsets.get(4).right);
//        assertEquals(20, fieldOffsets.get(5).left);
//        assertEquals((byte) 0, fieldOffsets.get(5).right);
//        assertEquals(20, fieldOffsets.get(6).left);
//        assertEquals((byte) 1, fieldOffsets.get(6).right);
        
        PVString pvStrOffset = AI.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("1255");

        AI.ParseOffset("1255");

        assertEquals(AI.getBiteOffset(), -1);
        assertEquals(AI.getByteOffset(), 1255);

        AI.ParseOffset("255.7");
        assertEquals(AI.getBiteOffset(), 7);
        assertEquals(AI.getByteOffset(), 255);
        logger.info("TEST SUCCESSFULLY COMPLETED ");
    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
    @Test
    //@Order(1)
    public void DBRecordTest() {

       
        PVString pvStrOffset = AI.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");


        plcItem.addItemListener(AI);
        plcItem.setPlcValue(plcValue);

        PVStructure udtHMI = AI.getPVRecordStructure().getPVStructure().getStructureField("udtHMI");
        PVStructure udtError = AI.getPVRecordStructure().getPVStructure().getStructureField("udtError");

        value = AI.getPVRecordStructure().getPVStructure().getShortField("value");
        write_enable = AI.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        //Read command values            
        iMode = udtHMI.getShortField("iMode");
        iErrorCode = udtHMI.getShortField("iErrorCode");
        iStatus = udtHMI.getShortField("iStatus");
        rActiveValue = udtHMI.getFloatField("rActiveValue");
        rInputValue = udtHMI.getFloatField("rInputValue");
        rManualValue = udtHMI.getFloatField("rManualValue");
        bPB_ResetError = udtHMI.getBooleanField("bPB_ResetError");
        bPBEN_ResetError = udtHMI.getBooleanField("bPBEN_ResetError");
        bError = udtHMI.getBooleanField("bError");

        //Read status values
        bLowLowAlarm = udtError.getBooleanField("bLowLowAlarm");
        bHighHighAlarm = udtError.getBooleanField("bHighHighAlarm");
        bInvalid = udtError.getBooleanField("bInvalid");

        //Test bits        
        byteBuf.setByte(20, 0x07);               //bPB_resetError = bPBEN_ResetError = bError = true;      
        byteBuf.setByte(0, 0x07);               //LowLowAlarm = HighHighAlarm=Invalid
        
        plcItem.setPlcValue(plcValue);        
        
        //Write command and parameters values
        assertEquals(true, bLowLowAlarm.get());
        assertEquals(true, bHighHighAlarm.get());
        assertEquals(true, bInvalid.get());
                
        assertEquals(1234, iMode.get());
        assertEquals(4321, iErrorCode.get());
        assertEquals(1010, iStatus.get());
        assertEquals(3.1416F, rActiveValue.get());
        assertEquals(3.1416F * 2, rInputValue.get());
        assertEquals(3.1416F * 4, rManualValue.get());

        assertEquals(true, bPB_ResetError.get());
        assertEquals(true, bPBEN_ResetError.get());
        assertEquals(true, bError.get());
        
        byteBuf.setByte(20, 0x05);               //bPB_resetError =  bError = true; bPBEN_ResetError = false;        
        byteBuf.setByte(0, 0x05);               //LowLowAlarm = Invalid; HighHighAlarm= = false;
        
        plcItem.setPlcValue(plcValue);

        assertEquals(true, bLowLowAlarm.get());
        assertEquals(false, bHighHighAlarm.get());
        assertEquals(true, bInvalid.get());
        
        assertEquals(true, bPB_ResetError.get());
        assertEquals(false, bPBEN_ResetError.get());
        assertEquals(true, bError.get());
        
    }
}
