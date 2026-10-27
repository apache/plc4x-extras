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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagAllocatorTest {

    private static CreateVariableController.Row row(String deviceName, String dataType,
            String tag) {
        return new CreateVariableController.Row(
                1,
                "",
                "Var",
                deviceName,
                dataType,
                tag,
                "",
                "1000ms",
                true,
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");
    }

    @Test
    void allocatesForwardSkippingOccupiedEvenOffsets() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-1", "int", "%DB1.DBD0"),
                row("PLC-1", "int", "%DB1.DBD4"),
                row("PLC-1", "int", "%DB1.DBD8"),
                row("PLC-1", "short", "%DB1.DBW12"));

        assertEquals(
                List.of("%DB1.DBD14", "%DB1.DBD18", "%DB1.DBD22"),
                TagAllocator.allocate("%DB1.DBD8", "int", 3, "PLC-1", rows));
    }

    @Test
    void doesNotCrossDataBlockBoundaries() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-1", "int", "%DB1.DBD0"),
                row("PLC-1", "int", "%DB2.DBD0"));

        assertEquals(
                List.of("%DB1.DBD4"),
                TagAllocator.allocate("%DB1.DBD0", "int", 1, "PLC-1", rows));
        assertEquals(
                List.of("%DB2.DBD4"),
                TagAllocator.allocate("%DB2.DBD0", "int", 1, "PLC-1", rows));
    }

    @Test
    void usesEvenOffsetsForByteTags() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-1", "byte", "%DB1.DBB0"),
                row("PLC-1", "byte", "%DB1.DBB2"));

        assertEquals(
                List.of("%DB1.DBB4", "%DB1.DBB6"),
                TagAllocator.allocate("%DB1.DBB0", "byte", 2, "PLC-1", rows));
    }

    @Test
    void skipsEvenCandidatesThatOverlap() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-1", "int", "%DB1.DBD8"),
                row("PLC-1", "int", "%DB1.DBD12"),
                row("PLC-1", "int", "%DB1.DBD14"));

        assertEquals(
                List.of("%DB1.DBD18", "%DB1.DBD22"),
                TagAllocator.allocate("%DB1.DBD8", "int", 2, "PLC-1", rows));
    }

    @Test
    void allocatesBooleanBitsWithinTheSameByte() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-1", "boolean", "%DB1.DBX0.0"),
                row("PLC-1", "boolean", "%DB1.DBX0.1"));

        assertEquals(
                List.of("%DB1.DBX0.2", "%DB1.DBX0.3"),
                TagAllocator.allocate("%DB1.DBX0.0", "boolean", 2, "PLC-1", rows));
    }

    @Test
    void cancelsBooleanDuplicationWhenTheByteIsFull() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-1", "boolean", "%DB1.DBX0.6"));

        assertThrows(IllegalArgumentException.class,
                () -> TagAllocator.allocate("%DB1.DBX0.6", "boolean", 2, "PLC-1", rows));
    }

    @Test
    void cancelsBooleanDuplicationAtBitSeven() {
        assertThrows(IllegalArgumentException.class,
                () -> TagAllocator.allocate("%DB1.DBX0.7", "boolean", 1, "PLC-1",
                        List.of(row("PLC-1", "boolean", "%DB1.DBX0.7"))));
    }

    @Test
    void keepsBlankTagsBlank() {
        assertEquals(
                List.of("", "", ""),
                TagAllocator.allocate("", "int", 3, "PLC-1", List.of()));
    }

    @Test
    void rejectsInvalidTags() {
        assertThrows(IllegalArgumentException.class,
                () -> TagAllocator.allocate("Motor1_Speed", "int", 1, "PLC-1", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TagAllocator.allocate("%DB1.DBB0[0..9]", "byte", 1, "PLC-1",
                        List.of()));
    }

    @Test
    void ignoresTagsFromOtherDevices() {
        List<CreateVariableController.Row> rows = List.of(
                row("PLC-2", "int", "%DB1.DBD0"));

        assertEquals(
                List.of("%DB1.DBD4"),
                TagAllocator.allocate("%DB1.DBD0", "int", 1, "PLC-1", rows));
    }

    @Test
    void respectsThe64KilobyteLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> TagAllocator.allocate("%DB1.DBD65532", "int", 1, "PLC-1",
                        List.of(row("PLC-1", "int", "%DB1.DBD65532"))));

        assertEquals(
                List.of("%DB1.DBD65532"),
                TagAllocator.allocate("%DB1.DBD65528", "int", 1, "PLC-1",
                        List.of(row("PLC-1", "int", "%DB1.DBD65528"))));
    }

    @Test
    void rejectsIncompatibleDataType() {
        assertThrows(IllegalArgumentException.class,
                () -> TagAllocator.allocate("%DB1.DBD0", "short", 1, "PLC-1",
                        List.of(row("PLC-1", "short", "%DB1.DBD0"))));
    }
}
