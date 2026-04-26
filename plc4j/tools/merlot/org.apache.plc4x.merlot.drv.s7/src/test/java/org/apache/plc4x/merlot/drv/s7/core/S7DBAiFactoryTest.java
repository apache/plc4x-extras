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
 * Functional test for S7DBAiFactory (Analog Input).
 */
public class S7DBAiFactoryTest {

    private S7DBAiFactory factory;
    private DBRecord record;
    private PlcItem plcItem;
    private ByteBuf byteBuf;

    @Before
    public void setUp() {
        factory = new S7DBAiFactory();
        record = factory.create("AI_TEST");

        // Setup Buffer (BUFFER_SIZE = 64 in S7DBAiFactory)
        byteBuf = Unpooled.buffer(64);
        
        // Fill buffer based on S7DBAiFactory.update() logic:
        // Offset 0: iMode (short)
        byteBuf.setShort(0, (short) 2);
        // Offset 2: iErrorCode (short)
        byteBuf.setShort(2, (short) 0);
        // Offset 4: iStatus (short)
        byteBuf.setShort(4, (short) 100);
        // Offset 6: rActiveValue (float)
        byteBuf.setFloat(6, 12.34f);
        // Offset 10: rInputValue (float)
        byteBuf.setFloat(10, 56.78f);
        // Offset 14: rManualValue (float)
        byteBuf.setFloat(14, 90.12f);
        
        // Offset 18: byTemp (bits: 0:bPB_ResetError, 1:bPBEN_ResetError, 2:bError)
        // Set bits 0 and 2 to true (0b00000101 = 5)
        byteBuf.setByte(18, (byte) 5);

        // Offset 20: byTemp (bits: 0:bLowLowAlarm, 1:bHighHighAlarm, 2:bInvalid)
        // Set bit 1 to true (0b00000010 = 2)
        byteBuf.setByte(20, (byte) 2);

        // Offset 22: iSensorType (short)
        byteBuf.setShort(22, (short) 1);
        // Offset 24: rInEngUnitsMin (float)
        byteBuf.setFloat(24, 0.0f);
        // Offset 28: rInEngUnitsMax (float)
        byteBuf.setFloat(28, 100.0f);

        // Setup PlcItem
        String uuid = UUID.randomUUID().toString();
        plcItem = new PlcItemImpl.PlcItemBuilder("ITEM_AI_TEST")
                .setItemDescription("Test Item for S7 Analog Input")
                .setItemId(uuid)
                .setItemUid(UUID.fromString(uuid))
                .build();

        plcItem.setPlcValue(new PlcRawByteArray(byteBuf.array()));
    }

    @Test
    public void testStructureCreation() {
        assertNotNull("Record should not be null", record);
        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        
        assertNotNull("cmd structure should exist", pvStructure.getStructureField("cmd"));
        assertNotNull("sts structure should exist", pvStructure.getStructureField("sts"));
        assertNotNull("par structure should exist", pvStructure.getStructureField("par"));
        assertNotNull("id field should exist", pvStructure.getStringField("id"));
    }

    @Test
    public void testAttachAndParsing() {
        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        // S7DBAiFactory uses the 'id' field to parse the S7 address
        pvStructure.getStringField("id").put("s7:%DB100:0:BYTE[64]");
        
        record.atach(plcItem);
        
        assertEquals("Byte offset should be 0", 0, record.getByteOffset());
    }

    @Test
    public void testDataUpdateMapping() {
        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        pvStructure.getStringField("id").put("s7:%DB100:0:BYTE[64]");
        
        record.atach(plcItem);
        record.update();

        PVStructure cmd = pvStructure.getStructureField("cmd");
        PVStructure sts = pvStructure.getStructureField("sts");
        PVStructure par = pvStructure.getStructureField("par");

        // Verify Command values
        assertEquals("iMode should be 2", (short) 2, cmd.getShortField("iMode").get());
        assertEquals("rActiveValue should be 12.34", 12.34f, cmd.getFloatField("rActiveValue").get(), 0.001f);
        assertTrue("bPB_ResetError (bit 0) should be true", cmd.getBooleanField("bPB_ResetError").get());
        assertFalse("bPBEN_ResetError (bit 1) should be false", cmd.getBooleanField("bPBEN_ResetError").get());
        assertTrue("bError (bit 2) should be true", cmd.getBooleanField("bError").get());

        // Verify Status values
        assertFalse("bLowLowAlarm (bit 0) should be false", sts.getBooleanField("bLowLowAlarm").get());
        assertTrue("bHighHighAlarm (bit 1) should be true", sts.getBooleanField("bHighHighAlarm").get());

        // Verify Parameter values
        assertEquals("iSensorType should be 1", (short) 1, par.getShortField("iSensorType").get());
        assertEquals("rInEngUnitsMin should be 0.0", 0.0f, par.getFloatField("rInEngUnitsMin").get(), 0.001f);
        assertEquals("rInEngUnitsMax should be 100.0", 100.0f, par.getFloatField("rInEngUnitsMax").get(), 0.001f);
    }

    @Test
    public void testFieldOffsetsMapping() {
        assertNotNull("Field offsets should be initialized", record.getFieldOffsets());
        
        // iMode is at index 3, pointing to byte offset 0
        assertEquals("iMode byte offset should be 0", (Object) 0, record.getFieldOffsets().get(3).getLeft());
        
        // rManualValue is at index 4, pointing to byte offset 14
        assertEquals("rManualValue byte offset should be 14", (Object) 14, record.getFieldOffsets().get(4).getLeft());
        
        // bPB_ResetError is at index 5, pointing to byte offset 18, bit 0
        assertEquals("bPB_ResetError byte offset should be 18", (Object) 18, record.getFieldOffsets().get(5).getLeft());
        assertEquals("bPB_ResetError bit offset should be 0", (Object) (byte) 0, record.getFieldOffsets().get(5).getRight());
    }
}
