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
import org.junit.Ignore;

@Ignore
public class S7DBAoFactoryTest {

    private S7DBAoFactory factory;
    private DBRecord record;
    private PlcItem plcItem;
    private ByteBuf byteBuf;

    @Before
    public void setUp() {
        factory = new S7DBAoFactory();
        record = factory.create("AO_TEST");

        // Setup Buffer
        byteBuf = Unpooled.buffer(64);
        // Initialize buffer with some data

        // iMode (short) at 0
        byteBuf.setShort(0, 1);
        // iErrorCode (short) at 2
        byteBuf.setShort(2, 0);

        // rValue (float) at 4
        byteBuf.setFloat(4, 10.5f);
        // rAutoValue (float) at 8
        byteBuf.setFloat(8, 20.5f);
        // rManualValue (float) at 12
        byteBuf.setFloat(12, 30.5f);
        // rEstopValue (float) at 16
        byteBuf.setFloat(16, 40.5f);

        // Byte 20: bPB_ResetError (0), bPBEN_ResetError (1), bError (2), bInterlock (3)
        byteBuf.setByte(20, 0b00001111); // All true

        // iEstopFunction (short) at 22
        byteBuf.setShort(22, 99);

        // Byte 24: bOutOfRange (0), bConfigurationError (1)
        byteBuf.setByte(24, 0b00000011); // All true

        // iSensorType (short) at 26
        byteBuf.setShort(26, 5);
        // rInEngUnitsMin (float) at 28
        byteBuf.setFloat(28, 0.0f);
        // rInEngUnitsMax (float) at 32
        byteBuf.setFloat(32, 100.0f);

        // Setup PlcItem
        String uuid = UUID.randomUUID().toString();
        plcItem = new PlcItemImpl.PlcItemBuilder("ITEM_TEST")
                .setItemDescription("Test Item")
                .setItemId(uuid)
                .setItemUid(UUID.fromString(uuid))
                .build();

        plcItem.setPlcValue(new PlcRawByteArray(byteBuf.array()));
    }

    @Test
    public void testCreate() {
        assertNotNull(record);
        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        assertNotNull(pvStructure);

        assertNotNull(pvStructure.getStructureField("cmd"));
        assertNotNull(pvStructure.getStructureField("sts"));
        assertNotNull(pvStructure.getStructureField("par"));
        assertNotNull(pvStructure.getStringField("id"));
    }

    @Test
    public void testAttach() {
        PVString id = record.getPVRecordStructure().getPVStructure().getStringField("id");
        id.put("s7:%DB100:0:BYTE[64]");

        record.atach(plcItem);

        assertEquals(0, record.getByteOffset());
    }

    @Test
    public void testUpdate() {
        PVString id = record.getPVRecordStructure().getPVStructure().getStringField("id");
        id.put("s7:%DB100:0:BYTE[64]");
        record.atach(plcItem);

        record.update();

        PVStructure pvStructure = record.getPVRecordStructure().getPVStructure();
        PVStructure cmd = pvStructure.getStructureField("cmd");
        PVStructure sts = pvStructure.getStructureField("sts");
        PVStructure par = pvStructure.getStructureField("par");

        assertEquals(1, cmd.getShortField("iMode").get());
        assertEquals(0, cmd.getShortField("iErrorCode").get());

        assertEquals(10.5f, cmd.getFloatField("rValue").get(), 0.001f);
        assertEquals(20.5f, cmd.getFloatField("rAutoValue").get(), 0.001f);
        assertEquals(30.5f, cmd.getFloatField("rManualValue").get(), 0.001f);
        assertEquals(40.5f, cmd.getFloatField("rEstopValue").get(), 0.001f);

        assertEquals(true, cmd.getBooleanField("bPB_ResetError").get());
        assertEquals(true, cmd.getBooleanField("bPBEN_ResetError").get());
        assertEquals(true, cmd.getBooleanField("bError").get());
        assertEquals(true, cmd.getBooleanField("bInterlock").get());

        assertEquals(99, cmd.getShortField("iEstopFunction").get());

        assertEquals(true, sts.getBooleanField("bOutOfRange").get());
        assertEquals(true, sts.getBooleanField("bConfigurationError").get());

        assertEquals(5, par.getShortField("iSensorType").get());
        assertEquals(0.0f, par.getFloatField("rInEngUnitsMin").get(), 0.001f);
        assertEquals(100.0f, par.getFloatField("rInEngUnitsMax").get(), 0.001f);
    }

    @Test
    public void testProcess() {
        PVString id = record.getPVRecordStructure().getPVStructure().getStringField("id");
        id.put("s7:%DB100:0:BYTE[64]");
        record.atach(plcItem);

        // Enable write
        PVBoolean writeEnable = record.getPVRecordStructure().getPVStructure().getBooleanField("write_enable");
        writeEnable.put(true);

        // Change a value in PV
        PVStructure cmd = record.getPVRecordStructure().getPVStructure().getStructureField("cmd");
        cmd.getShortField("iMode").put((short) 5);

        // Call process
        record.process();

        // In a real scenario, this would trigger a write to the PLC.
        // Here we just verify no exception is thrown and the method completes.
    }

    @Test
    public void testGetFieldsToMonitor() {
        String fields = record.getFieldsToMonitor();
        assertNotNull(fields);
        assertTrue(fields.contains("cmd{iMode"));
        assertTrue(fields.contains("par{iSensorType"));
    }
}
