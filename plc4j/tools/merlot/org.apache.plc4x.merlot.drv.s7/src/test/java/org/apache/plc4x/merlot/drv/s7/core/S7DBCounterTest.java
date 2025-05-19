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
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
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
public class S7DBCounterTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBCounterTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord CNT;
    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;
    short b, c, d, bcd;

    //@BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the counter class");
        logger.info("Test Counters for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(10);

        byteBuf.setShort(0, 0b0000_0001_0010_0011); //BCD
    }

    //@AfterAll
    public static void tearDownClass() {
        logger.info("Ending the counter class test");
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
        S7DBCounterFactory CounterFactory = new S7DBCounterFactory();
        CNT = CounterFactory.create("CNT");
        
        PVString pvStrOffset = CNT.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");

        plcItem.addItemListener(CNT);
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
        value = CNT.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = CNT.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = CNT.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");
        assertEquals(123, value.get());
    }

    @Test
    //@Order(2)
    public void FieldOffsetTest() {
        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = CNT.getFieldOffsets();

        assertEquals(3, fieldOffsets.size());
        assertNull(fieldOffsets.get(0));
        
        //The offset is recalculated when the pvRecord is assigned to the Item.
//        assertEquals(0, fieldOffsets.get(1).left);
//        assertEquals((byte) -1, fieldOffsets.get(1).right);         
//        assertEquals(0, fieldOffsets.get(2).left);
//        assertEquals((byte) -1, fieldOffsets.get(2).right);         

    }
}
