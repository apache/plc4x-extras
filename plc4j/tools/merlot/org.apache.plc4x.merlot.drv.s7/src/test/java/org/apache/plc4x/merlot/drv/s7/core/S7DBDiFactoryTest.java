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
import io.netty.buffer.Unpooled;
import org.apache.plc4x.java.spi.values.PlcRawByteArray;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;
import org.apache.plc4x.merlot.db.api.DBRecord;
import org.epics.pvdata.pv.*;
import org.junit.Before;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

/**
 * Functional test for S7DBDiFactory.
 */
public class S7DBDiFactoryTest {

    private S7DBDiFactory factory;
    private DBRecord record;
    private PlcItem plcItem;
    private ByteBuf byteBuf;

    @Before
    public void setUp() {
        factory = new S7DBDiFactory();
        record = factory.create("DI_TEST");

        // Setup Buffer (BUFFER_SIZE = 3 in S7DBDiFactory)
        byteBuf = Unpooled.buffer(3);
        
        // 1. Set iMode (short) at offset 0 -> Value: 1234
        byteBuf.setShort(0, (short) 1234);
        
        // 2. Set byTemp (byte) at offset 2
        // Bit mapping in S7DBDiFactory:
        // Bit 0: bOn, Bit 1: bOnActual, Bit 2: bPB_On, Bit 3: bPB_Off, Bit 4: bPBEN_On, Bit 5: bPBEN_Off
        // Let's set bits 0, 2, 4 to true (0b00010101 = 21 decimal)
        byteBuf.setByte(2, (byte) 21);

        // Setup PlcItem with the buffer
        String uuid = UUID.randomUUID().toString();
        plcItem = new PlcItemImpl.PlcItemBuilder("ITEM_TEST")
                .setItemDescription("Test Item for S7 Digital Input")
                .setItemId(uuid)
                .setItemUid(UUID.fromString(uuid))
                .build();

        plcItem.setPlcValue(new PlcRawByteArray(byteBuf.array()));
    }

    @Test
    public void testStructureCreation() {
        assertNotNull("Record should not be null", record);
        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        
        assertNotNull("udtHMI structure should exist", pvStructure.getStructureField("udtHMI"));
        assertNotNull("udtError structure should exist", pvStructure.getStructureField("udtError"));
        assertNotNull("write_enable field should exist", pvStructure.getBooleanField("write_enable"));
        assertNotNull("offset field should exist", pvStructure.getStringField("offset"));
    }

    @Test
    public void testDataUpdateMapping() {
        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        
        // Set offset to 0 so it reads from the beginning of our buffer
        pvStructure.getStringField("offset").put("0");
        
        // Attach and trigger update
        record.atach(plcItem);
        record.update();

        PVStructure udtHMI = pvStructure.getStructureField("udtHMI");
        
        // Verify iMode
        assertEquals("iMode should match buffer value", (short) 1234, udtHMI.getShortField("iMode").get());
        
        // Verify Bits (based on byte value 21 -> 0b00010101)
        assertTrue("bOn (bit 0) should be true", udtHMI.getBooleanField("bOn").get());
        assertFalse("bOnActual (bit 1) should be false", udtHMI.getBooleanField("bOnActual").get());
        assertTrue("bPB_On (bit 2) should be true", udtHMI.getBooleanField("bPB_On").get());
        assertFalse("bPB_Off (bit 3) should be false", udtHMI.getBooleanField("bPB_Off").get());
        assertTrue("bPBEN_On (bit 4) should be true", udtHMI.getBooleanField("bPBEN_On").get());
        assertFalse("bPBEN_Off (bit 5) should be false", udtHMI.getBooleanField("bPBEN_Off").get());
        
        // Verify write_enable is automatically enabled after first run
        assertTrue("write_enable should be true after update", pvStructure.getBooleanField("write_enable").get());
    }

    @Test
    public void testFieldOffsetsMapping() {
        // Verify that the factory correctly initialized the field offsets for writing
        assertNotNull("Field offsets should be initialized", record.getFieldOffsets());
        
        // iMode is at index 3 in the list, pointing to byte offset 2 (as per S7DBDiFactory source)
        // Note: The factory uses fieldOffsets.add(3, new ImmutablePair(2, (byte) -1))
        assertEquals("iMode offset should be 2", (Object) 2, record.getFieldOffsets().get(3).getLeft());
        
        // bPB_On is at index 4, pointing to byte offset 4, bit 2
        assertEquals("bPB_On byte offset should be 4", (Object) 4, record.getFieldOffsets().get(4).getLeft());
        assertEquals("bPB_On bit offset should be 2", (Object) (byte) 2, record.getFieldOffsets().get(4).getRight());
    }
}
