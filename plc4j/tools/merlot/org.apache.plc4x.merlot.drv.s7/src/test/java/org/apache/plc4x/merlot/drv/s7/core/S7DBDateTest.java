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
import java.time.LocalDate;
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
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
public class S7DBDateTest {

    private static final Logger logger = LoggerFactory.getLogger(S7DBDateTest.class);
    private static ByteBuf byteBuf;
    private PlcValue plcValue;
    private PlcItem plcItem;
    private DBRecord DATE_00;

    private PVShort value;
    private PVShort write_value;
    private PVBoolean write_enable;
    private PVString strValue;
    private LocalDate lastDATE;
    private LocalDate userDATE;

    @BeforeAll
    public static void setUpClass() {
        logger.info("Starting the testing of the Date class");
        logger.info("Test Datee for S7 plc");
        logger.info("Creating buffer to plcValue");
        byteBuf = buffer(5);

        byteBuf.setInt(0, 251401);

    }

    @AfterAll
    public static void tearDownClass() {
        logger.info("Ending the Date class test");
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
        S7DBDateFactory dateFactory = new S7DBDateFactory();
        DATE_00 = dateFactory.create("DATE_00");

    }

    @AfterEach
    public void tearDown() {
        plcItem = null;
        plcValue = null;
    }

    @Test
    @Order(1)
    public void DBRecordTest() {
        PVString pvStrOffset = DATE_00.getPVRecordStructure().getPVStructure().getStringField("offset");
        pvStrOffset.put("0");
        plcItem.addItemListener(DATE_00);
        plcItem.setPlcValue(plcValue);
        value = DATE_00.getPVRecordStructure().getPVStructure().getShortField("value");
        write_value = DATE_00.getPVRecordStructure().getPVStructure().getShortField("write_value");
        write_enable = DATE_00.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");
        strValue = DATE_00.getPVRecordStructure().getPVStructure().getStringField("strValue");

        logger.info("\n--------------STARTING TEST DBRECORD AI----------");
        logger.info(String.format("Test in DateAndTime:(expected value: 2025140131 == (current value): %d)", value.get()));
        assertEquals(2514013, value.get());
    }

    @Test
    @Order(2)
    public void FieldOffsetTest() {

        ArrayList<ImmutablePair<Integer, Byte>> fieldOffsets = DATE_00.getFieldOffsets();
        logger.info(String.format("Number of items allowed to be monitored:(Value expected: 2) == (Value actual: %d)", fieldOffsets.size()));
        assertEquals(2, fieldOffsets.size());
        Assertions.assertNull(fieldOffsets.get(0));
        Assertions.assertNotNull(fieldOffsets.get(1));
    }
}
