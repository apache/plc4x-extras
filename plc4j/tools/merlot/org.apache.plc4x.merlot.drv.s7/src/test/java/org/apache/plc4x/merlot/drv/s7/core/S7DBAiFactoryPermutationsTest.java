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
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 * Functional test for S7DBAiFactory with a high number of permutations.
 * Focus: verify mapping buffer->PV fields in DBS7AiRecord.update(), across many
 * combinations of booleans, numeric values, and slice offsets.
 */
@Ignore
public class S7DBAiFactoryPermutationsTest {

    private static final int FRAME_SIZE = 64; // As used by DBS7AiRecord

    private static class Context {
        final S7DBAiFactory factory = new S7DBAiFactory();
        final DBRecord record = factory.create("AI_PERMUT");
        final ByteBuf plcBuf = Unpooled.buffer(512); // big PLC buffer; we will slice in atach()
        final PlcItem item;
        final PVStructure root;
        final PVStructure cmd;
        final PVStructure sts;
        final PVStructure par;

        Context(int baseOffset) {
            // Build PlcItem
            String uuid = UUID.randomUUID().toString();
            item = new PlcItemImpl.PlcItemBuilder("ITEM_AI_PERMUT")
                .setItemDescription("AI Permutations")
                .setItemId(uuid)
                .setItemUid(UUID.fromString(uuid))
                .build();
            item.setPlcValue(new PlcRawByteArray(plcBuf.array()));

            // Set ID with S7 address including base offset and size 64 bytes
            PVString id = record.getPVRecordStructure().getPVStructure().getStringField("id");
            id.put("s7:%DB1:" + baseOffset + ":BYTE[" + FRAME_SIZE + "]");

            // Attach (this will slice innerBuffer = plcBuf.slice(baseOffset, 64))
            record.atach(item);

            // Fetch PV structures
            root = record.getPVRecordStructure().getPVStructure();
            cmd = root.getStructureField("cmd");
            sts = root.getStructureField("sts");
            par = root.getStructureField("par");
        }
    }

    private static int boolsToBits(boolean... flags) {
        int b = 0;
        for (int i = 0; i < flags.length; i++) {
            if (flags[i]) b |= (1 << i);
        }
        return b;
    }

    private static void writeFrame(ByteBuf buf, int base,
                                   short iMode, short iErrorCode, short iStatus,
                                   float rActive, float rInput, float rManual,
                                   boolean bPB_ResetError, boolean bPBEN_ResetError, boolean bError,
                                   boolean bLowLow, boolean bHighHigh, boolean bInvalid,
                                   short iSensorType,
                                   float min, float max,
                                   float lowlow, float low, float high, float highhigh,
                                   float dbLL, float dbL, float dbH, float dbHH) {
        buf.setShort(base + 0, iMode);
        buf.setShort(base + 2, iErrorCode);
        buf.setShort(base + 4, iStatus);
        buf.setFloat(base + 6, rActive);
        buf.setFloat(base + 10, rInput);
        buf.setFloat(base + 14, rManual);
        int cmdBits = boolsToBits(bPB_ResetError, bPBEN_ResetError, bError);
        buf.setByte(base + 18, cmdBits);
        int stsBits = boolsToBits(bLowLow, bHighHigh, bInvalid);
        buf.setByte(base + 20, stsBits);
        buf.setShort(base + 22, iSensorType);
        buf.setFloat(base + 24, min);
        buf.setFloat(base + 28, max);
        buf.setFloat(base + 32, lowlow);
        buf.setFloat(base + 36, low);
        buf.setFloat(base + 40, high);
        buf.setFloat(base + 44, highhigh);
        buf.setFloat(base + 48, dbLL);
        buf.setFloat(base + 52, dbL);
        buf.setFloat(base + 56, dbH);
        buf.setFloat(base + 60, dbHH);
    }

    @Test
    public void testStructureCreated() {
        S7DBAiFactory factory = new S7DBAiFactory();
        DBRecord rec = factory.create("AI_STRUCT");
        PVStructure pv = rec.getPVRecordStructure().getPVStructure();
        assertNotNull(pv.getStructureField("cmd"));
        assertNotNull(pv.getStructureField("sts"));
        assertNotNull(pv.getStructureField("par"));
        assertNotNull(pv.getBooleanField("write_enable"));
        assertNotNull(pv.getStringField("id"));
    }

    @Test
    public void testPermutationsMapping() {
        // Offsets to exercise innerBuffer slicing
        int[] baseOffsets = {0, 4, 8};
        // Shorts and floats sample values (kept small to keep runtime reasonable)
        short[] modes = {0, 32767};
        short[] statuses = {0, 100};
        float[] activeVals = {-1.0f, 1.0f};
        float[] inputVals = {0.0f, 3.14f};
        float[] manualVals = {2.71f, -2.71f};

        // Fixed parameters (par) values
        short sensorType = 2;
        float pMin = 0.0f, pMax = 100.0f;
        float pLL = 1.0f, pL = 5.0f, pH = 90.0f, pHH = 99.0f;
        float dbLL = 0.1f, dbL = 0.2f, dbH = 0.3f, dbHH = 0.4f;

        // Iterate offsets
        for (int base : baseOffsets) {
            Context cx = new Context(base);

            // Iterate boolean combinations for cmd flags (3 bits) and sts flags (3 bits)
            for (int cmdBits = 0; cmdBits < 8; cmdBits++) {
                boolean bPB = (cmdBits & 0x1) != 0;
                boolean bPBEN = (cmdBits & 0x2) != 0;
                boolean bErr = (cmdBits & 0x4) != 0;

                for (int stsBits = 0; stsBits < 8; stsBits++) {
                    boolean bLL = (stsBits & 0x1) != 0;
                    boolean bHH = (stsBits & 0x2) != 0;
                    boolean bInv = (stsBits & 0x4) != 0;

                    for (short mode : modes) {
                        for (short st : statuses) {
                            for (float act : activeVals) {
                                for (float in : inputVals) {
                                    for (float man : manualVals) {
                                        // Write a complete frame at base offset
                                        writeFrame(cx.plcBuf, base,
                                                mode, (short) 7, st,
                                                act, in, man,
                                                bPB, bPBEN, bErr,
                                                bLL, bHH, bInv,
                                                sensorType,
                                                pMin, pMax,
                                                pLL, pL, pH, pHH,
                                                dbLL, dbL, dbH, dbHH);

                                        // Update PVs from buffer
                                        cx.record.update();

                                        // Validate PVs reflect buffer
                                        PVStructure cmd = cx.cmd;
                                        PVStructure sts = cx.sts;
                                        PVStructure par = cx.par;

                                        assertEquals(mode, cmd.getShortField("iMode").get());
                                        assertEquals(7, cmd.getShortField("iErrorCode").get());
                                        assertEquals(st, cmd.getShortField("iStatus").get());

                                        assertEquals(act, cmd.getFloatField("rActiveValue").get(), 1e-4);
                                        assertEquals(in, cmd.getFloatField("rInputValue").get(), 1e-4);
                                        assertEquals(man, cmd.getFloatField("rManualValue").get(), 1e-4);

                                        assertEquals(bPB, cmd.getBooleanField("bPB_ResetError").get());
                                        assertEquals(bPBEN, cmd.getBooleanField("bPBEN_ResetError").get());
                                        assertEquals(bErr, cmd.getBooleanField("bError").get());

                                        assertEquals(bLL, sts.getBooleanField("bLowLowAlarm").get());
                                        assertEquals(bHH, sts.getBooleanField("bHighHighAlarm").get());
                                        assertEquals(bInv, sts.getBooleanField("bInvalid").get());

                                        assertEquals(sensorType, par.getShortField("iSensorType").get());
                                        assertEquals(pMin, par.getFloatField("rInEngUnitsMin").get(), 1e-4);
                                        assertEquals(pMax, par.getFloatField("rInEngUnitsMax").get(), 1e-4);
                                        assertEquals(pLL, par.getFloatField("rInLowLow").get(), 1e-4);
                                        assertEquals(pL, par.getFloatField("rInLow").get(), 1e-4);
                                        assertEquals(pH, par.getFloatField("rInHigh").get(), 1e-4);
                                        assertEquals(pHH, par.getFloatField("rInHighHigh").get(), 1e-4);
                                        assertEquals(dbLL, par.getFloatField("rInLowLowDeadband").get(), 1e-4);
                                        assertEquals(dbL, par.getFloatField("rInLowDeadband").get(), 1e-4);
                                        assertEquals(dbH, par.getFloatField("rInHighDeadband").get(), 1e-4);
                                        assertEquals(dbHH, par.getFloatField("rInHighHighDeadband").get(), 1e-4);

                                        // After first update, write_enable should be true (set inside update())
                                        PVBoolean we = cx.root.getBooleanField("write_enable");
                                        assertTrue(we.get());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Also check fields to monitor string contains key tokens
            String toMonitor = cx.record.getFieldsToMonitor();
            assertNotNull(toMonitor);
            assertTrue(toMonitor.contains("cmd{iMode"));
            assertTrue(toMonitor.contains("par{iSensorType"));
        }
    }
}
