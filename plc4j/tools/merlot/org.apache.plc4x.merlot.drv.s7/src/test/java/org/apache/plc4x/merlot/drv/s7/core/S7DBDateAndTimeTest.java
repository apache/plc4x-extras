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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.spi.values.PlcRawByteArray;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVLong;
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
public class S7DBDateAndTimeTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBDateAndTimeTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord DATEANDTIME;
    private PVLong value;
    private PVBoolean write_enable;
    private PVString strValue;
    private LocalDateTime lastDAT;
    private LocalDateTime userDAT;

    //@BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the DateAndTime class");
        logger.info("Test DateAndTime for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(100);
        
        byteBuf.setByte(0, 0x25);
        byteBuf.setByte(1, 0x03);
        byteBuf.setByte(2, 0x20);        
        byteBuf.setByte(3, 0x04);
        byteBuf.setByte(4, 0x06);
        byteBuf.setByte(5, 0x30);
        byteBuf.setByte(6, 0x7b);        
        byteBuf.setByte(7, 0x01);                
    }

    //@AfterAll
    public static void tearDownClass() {
        logger.info("Ending the DateAndTime class test");
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

        //DBRecord associated with each particular test
        S7DBDateAndTimeFactory timeDFactory = new S7DBDateAndTimeFactory();
        DATEANDTIME = timeDFactory.create("DATEANDTIME");

        PVString pvStrOffset = DATEANDTIME.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        
        DATEANDTIME.atach(plcItem);
        plcItem.addItemListener(DATEANDTIME);
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

        value = DATEANDTIME.getPVRecordStructure().getPVStructure().getLongField("value");
        write_enable = DATEANDTIME.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");
        strValue = DATEANDTIME.getPVRecordStructure().getPVStructure().getStringField("strValue");

        assertEquals(2667010605989264129L, value.get());
    }

    @Test
    //@Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = DATEANDTIME.getFieldOffsets();

        assertEquals(3, fieldOffsets.size());
        assertNull(fieldOffsets.get(0));
        assertNull(fieldOffsets.get(1));        
//        assertEquals(0, fieldOffsets.get(2).left);
//        assertEquals((byte) -1, fieldOffsets.get(2).right); 
    }
}
