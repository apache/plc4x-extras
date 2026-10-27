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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DynamicTag;

final class TagAllocator {

    static final int MAX_DB_BYTES = 65_536;

    private TagAllocator() {
    }

    static List<String> allocate(String originalTag, String dataTypeName, int copies,
            String deviceName, List<CreateVariableController.Row> rows) {
        if (copies < 1) {
            throw new IllegalArgumentException("The number of copies must be at least 1.");
        }

        List<String> result = new ArrayList<>(copies);
        if (originalTag == null || originalTag.isBlank()) {
            for (int i = 0; i < copies; i++) {
                result.add("");
            }
            return result;
        }

        DataType type = DataType.findIgnoreCase(dataTypeName);
        if (type == null) {
            throw new IllegalArgumentException("The data type is missing or invalid.");
        }

        DynamicTag base;
        try {
            base = DynamicTag.parse(originalTag, type);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        if (base == null) {
            throw new IllegalArgumentException("The tag is not valid: " + originalTag);
        }

        List<CreateVariableController.Row> sourceRows = rows == null ? List.of() : rows;
        List<DynamicTag> occupied = new ArrayList<>();
        for (CreateVariableController.Row row : sourceRows) {
            if (!Objects.equals(row.deviceName(), deviceName)
                    || row.itemTag() == null
                    || row.itemTag().isBlank()) {
                continue;
            }
            DynamicTag tag = DynamicTag.parse(row.itemTag());
            if (tag != null) {
                occupied.add(tag);
            }
        }

        if (base.isBit()) {
            allocateBits(base, copies, occupied, result);
        } else {
            allocateBytes(base, copies, occupied, result);
        }
        return result;
    }

    private static void allocateBits(DynamicTag base, int copies, List<DynamicTag> occupied,
            List<String> result) {
        if (base.byteOffset() >= MAX_DB_BYTES) {
            throw new IllegalArgumentException("The tag is outside the 64 KB data block.");
        }

        int bit = base.bit() + 1;
        for (int i = 0; i < copies; i++) {
            while (bit <= 7 && overlaps(base.withAddress(base.byteOffset(), bit), occupied)) {
                bit++;
            }
            if (bit > 7) {
                throw new IllegalArgumentException(
                        "There is not enough space in byte " + base.byteOffset()
                        + " to duplicate the boolean tag.");
            }
            DynamicTag candidate = base.withAddress(base.byteOffset(), bit);
            occupied.add(candidate);
            result.add(candidate.address());
            bit++;
        }
    }

    private static void allocateBytes(DynamicTag base, int copies, List<DynamicTag> occupied,
            List<String> result) {
        int size = base.byteCapacity();
        if (size <= 0) {
            throw new IllegalArgumentException("The tag size is not supported.");
        }
        if (base.byteOffset() + (long) size > MAX_DB_BYTES) {
            throw new IllegalArgumentException("The tag is outside the 64 KB data block.");
        }

        long offset = nextEven(base.byteOffset() + (long) size);
        for (int i = 0; i < copies; i++) {
            while (true) {
                if (offset + size > MAX_DB_BYTES) {
                    throw new IllegalArgumentException(
                            "There is not enough space in the data block to duplicate the tag.");
                }
                DynamicTag candidate = base.withAddress((int) offset, -1);
                if (!overlaps(candidate, occupied)) {
                    occupied.add(candidate);
                    result.add(candidate.address());
                    offset = nextEven(offset + size);
                    break;
                }
                offset += 2;
            }
        }
    }

    private static boolean overlaps(DynamicTag candidate, List<DynamicTag> occupied) {
        for (DynamicTag tag : occupied) {
            if (tag.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static long nextEven(long value) {
        return (value & 1L) == 0 ? value : value + 1;
    }
}
