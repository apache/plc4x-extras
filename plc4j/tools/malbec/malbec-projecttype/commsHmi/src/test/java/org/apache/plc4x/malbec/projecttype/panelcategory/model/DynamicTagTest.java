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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DynamicTagTest {

    @Test
    void specialTypeOccupiesItsFullByteBlockOnDbb() {
        DynamicTag tag = DynamicTag.parse("%DB1.DBB10", DataType.S7AI);

        assertEquals(10, tag.startByte());
        assertEquals(73, tag.endByte());
        assertEquals(64, tag.byteCapacity());
        assertTrue(tag.code() == DynamicTag.Code.B);
    }

    @Test
    void specialTypeOverlapsAnyByteInsideItsBlock() {
        DynamicTag special = DynamicTag.parse("%DB1.DBB10", DataType.S7AI);

        assertTrue(special.overlaps(DynamicTag.parse("%DB1.DBB10", DataType.BYTE)));
        assertTrue(special.overlaps(DynamicTag.parse("%DB1.DBB73", DataType.BYTE)));
        assertTrue(DynamicTag.parse("%DB1.DBB73", DataType.BYTE).overlaps(special));
        assertEquals(false, special.overlaps(DynamicTag.parse("%DB1.DBB74", DataType.BYTE)));
        assertEquals(false, DynamicTag.parse("%DB2.DBB10", DataType.BYTE).overlaps(special));
    }

    @Test
    void specialTypeRejectsWordAndDwordCodes() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamicTag.parse("%DB1.DBW0", DataType.S7AI));
        assertThrows(IllegalArgumentException.class,
                () -> DynamicTag.parse("%DB1.DBD0", DataType.S7AI));
    }

    @Test
    void specialTypeRequiresAnEvenStartByte() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamicTag.parse("%DB1.DBB1", DataType.S7AI));
        assertEquals(0, DynamicTag.parse("%DB1.DBB0", DataType.S7AI).startByte());
    }
}
