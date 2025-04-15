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
public class S7DBDiTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBDiTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord DI;

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

    private PVShort par_iMode;
    private PVBoolean par_bPB_On;
    private PVBoolean par_bPB_Off;

    @BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the digital input class");
        logger.info("Test Digital Input for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(5);
        byteBuf.setShort(0, 1234);
        byteBuf.setByte(2, 63);

    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the Digital Input class test");
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
        S7DBDiFactory DiFactory = new S7DBDiFactory();
        DI = DiFactory.create("DI");
        
        PVString pvStrOffset = DI.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0"); 
        
        DI.atach(plcItem);
        
        plcItem.addItemListener(DI);
        plcItem.setPlcValue(plcValue);        

    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

//    @Test
//    @Order(1)
    public void DBRecordTest() {
        
        value = DI.getPVRecordStructure().getPVStructure().getShortField("value");
        write_enable = DI.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");

        PVStructure udtHMI = DI.getPVRecordStructure().getPVStructure().getStructureField("udtHMI");
        PVStructure udtError = DI.getPVRecordStructure().getPVStructure().getStructureField("udtError");

        iMode = udtHMI.getShortField("iMode");
        bOn = udtHMI.getBooleanField("bOn");
        bOnActual = udtHMI.getBooleanField("bOnActual");
        bPB_On = udtHMI.getBooleanField("bPB_On");
        bPB_Off = udtHMI.getBooleanField("bPB_Off");
        bPBEN_On = udtHMI.getBooleanField("bPBEN_On");
        bPBEN_Off = udtHMI.getBooleanField("bPBEN_Off");

        assertEquals(1234, iMode.get());
        assertEquals(true, bOn.get());
        assertEquals(true, bOnActual.get());
        assertEquals(true, bPB_On.get());
        assertEquals(true, bPB_Off.get());
        assertEquals(true, bPBEN_On.get());
        assertEquals(true, bPBEN_Off.get());
     
    }

//    @Test
//    @Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = DI.getFieldOffsets();
        assertEquals(8, fieldOffsets.size());
        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));
        assertNotNull(fieldOffsets.get(2));
       
    }
}
