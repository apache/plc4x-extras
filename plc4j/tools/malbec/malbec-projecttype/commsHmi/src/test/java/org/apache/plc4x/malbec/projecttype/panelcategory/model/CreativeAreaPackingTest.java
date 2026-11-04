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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreativeAreaPackingTest {

    private static final String AREA = "Temperaturas";

    private static CommConfigData.PvConfig pv(String offset, String type) {
        return new CommConfigData.PvConfig("pv-" + offset + "-" + type, "variable", type, AREA,
                offset, "", "1000", true, false, "", "", "", "", "", "", "", "", "md5_pv_hash",
                "");
    }

    private static String place(List<CommConfigData.PvConfig> pvs, int capacidad, DataType type) {
        String offset = CreativeAreaPacking.nextOffset(capacidad, pvs, type);
        if (offset != null) {
            pvs.add(pv(offset, type.label()));
        }
        return offset;
    }

    @Test
    void onlyByteFamilyOpensCreativeMode() {
        assertTrue(CreativeAreaPacking.supportsCreative("BYTE"));
        assertTrue(CreativeAreaPacking.supportsCreative("byte"));
        assertTrue(CreativeAreaPacking.supportsCreative(" byte "));
        assertFalse(CreativeAreaPacking.supportsCreative("WORD"));
        assertFalse(CreativeAreaPacking.supportsCreative(null));
    }

    @Test
    void emptyAreaStartsAtZero() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals("0", CreativeAreaPacking.nextOffset(16, pvs, DataType.BYTE));
        assertEquals("0.0", CreativeAreaPacking.nextOffset(16, pvs, DataType.BOOLEAN));
        assertEquals("0", CreativeAreaPacking.nextOffset(16, pvs, DataType.WORD));
    }

    @Test
    void mixedTypesFollowTheSequentialCursor() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals("0.0", place(pvs, 16, DataType.BOOLEAN));
        assertEquals("0.1", place(pvs, 16, DataType.BOOLEAN));
        assertEquals("2", place(pvs, 16, DataType.WORD));
        assertEquals("4", place(pvs, 16, DataType.BYTE));
        assertEquals("5", place(pvs, 16, DataType.BYTE));
        assertEquals("6", place(pvs, 16, DataType.INT));
        assertEquals("10.0", place(pvs, 16, DataType.BOOLEAN));
    }

    @Test
    void booleanSharesTheByteUntilItIsFull() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            assertEquals("0." + i, place(pvs, 4, DataType.BOOLEAN));
        }
        assertEquals("1.0", place(pvs, 4, DataType.BOOLEAN),
                "al llenar el byte hay que saltar al siguiente");
    }

    @Test
    void aByteCanStartOnAnOddPosition() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals("0", place(pvs, 4, DataType.BYTE));
        assertEquals("1", place(pvs, 4, DataType.BYTE));
        assertEquals("2", place(pvs, 4, DataType.BYTE));
        assertEquals("3", place(pvs, 4, DataType.BYTE));
    }

    @Test
    void anyOtherTypeRoundsUpToAnEvenPosition() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals("0", place(pvs, 8, DataType.BYTE));
        assertEquals("2", place(pvs, 8, DataType.WORD),
                "después de un byte impar el word va al par siguiente");
        assertEquals("4", place(pvs, 8, DataType.WORD));
    }

    @Test
    void twoByteTypesSkipTheOddGapLeftByBooleans() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals("0.0", place(pvs, 8, DataType.BOOLEAN));
        assertEquals("0.1", place(pvs, 8, DataType.BOOLEAN));
        assertEquals("2", place(pvs, 8, DataType.WORD),
                "el byte 1 es impar y va ocupado a medias por booleanos");
    }

    @Test
    void slotsCountHowManyFitOfTheSameType() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals(10, CreativeAreaPacking.slotsFor(10, pvs, DataType.BYTE, Integer.MAX_VALUE));
        assertEquals(5, CreativeAreaPacking.slotsFor(10, pvs, DataType.WORD, Integer.MAX_VALUE));
        assertEquals(2, CreativeAreaPacking.slotsFor(10, pvs, DataType.INT, Integer.MAX_VALUE));
        assertEquals(80, CreativeAreaPacking.slotsFor(10, pvs, DataType.BOOLEAN, Integer.MAX_VALUE));
    }

    @Test
    void slotsForStopsAtTheRequestedBound() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals(3, CreativeAreaPacking.slotsFor(10, pvs, DataType.BYTE, 3));
    }

    @Test
    void nothingFitsWhenTheAreaIsFull() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        place(pvs, 2, DataType.WORD);
        assertNull(CreativeAreaPacking.nextOffset(2, pvs, DataType.BYTE));
        assertEquals(0, CreativeAreaPacking.slotsFor(2, pvs, DataType.BYTE, Integer.MAX_VALUE));
        assertEquals(0, CreativeAreaPacking.leftoverBytes(2, pvs));
    }

    @Test
    void leftoverBytesFollowTheCursor() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertEquals(16, CreativeAreaPacking.leftoverBytes(16, pvs));
        place(pvs, 16, DataType.INT);
        assertEquals(12, CreativeAreaPacking.leftoverBytes(16, pvs));
        place(pvs, 16, DataType.BYTE);
        assertEquals(11, CreativeAreaPacking.leftoverBytes(16, pvs));
    }

    @Test
    void disabledAndNullTypesAreNeverPlaced() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertNull(CreativeAreaPacking.nextOffset(16, pvs, DataType.STRING));
        assertNull(CreativeAreaPacking.nextOffset(16, pvs, null));
        assertEquals(0, CreativeAreaPacking.slotsFor(16, pvs, DataType.STRING, 10));
    }

    @Test
    void unknownAreaCapacityHasNoRoom() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        assertNull(CreativeAreaPacking.nextOffset(0, pvs, DataType.BYTE));
        assertEquals(0, CreativeAreaPacking.slotsFor(-1, pvs, DataType.BYTE, 10));
    }

    @Test
    void unreadableOffsetDoesNotAdvanceTheCursor() {
        List<CommConfigData.PvConfig> pvs = new ArrayList<>();
        pvs.add(pv("no-es-un-offset", "byte"));
        assertEquals("0", CreativeAreaPacking.nextOffset(16, pvs, DataType.BYTE));
    }
}
