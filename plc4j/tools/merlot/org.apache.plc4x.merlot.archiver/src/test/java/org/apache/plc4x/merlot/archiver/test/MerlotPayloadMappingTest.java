/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.archiver.test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.apache.plc4x.merlot.api.PB.EPICSEvent;
import org.apache.plc4x.merlot.archiver.core.MerlotPayloadMapping;
import org.epics.vtype.Alarm;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;
import org.epics.vtype.VDoubleArray;
import org.epics.vtype.VInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerlotPayloadMappingTest {

    @Test
    @DisplayName("You must correctly map a VDouble to a ScalarDouble in Protobuf")
    void testCreateScalarDouble() {
        
        double testValue = 123.456;
        Instant now = Instant.now();
        VDouble vDouble = VDouble.of(testValue, Alarm.none(), Time.of(now), Display.none());

        Object result = MerlotPayloadMapping.createEvent(vDouble);

        assertNotNull(result);
        assertTrue(result instanceof EPICSEvent.ScalarDouble);
        
        EPICSEvent.ScalarDouble pbEvent = (EPICSEvent.ScalarDouble) result;
        assertEquals(testValue, pbEvent.getVal(), 0.0001);
        assertEquals(now.getNano(), pbEvent.getNano());
        assertTrue(pbEvent.getSecondsintoyear() >= 0);
    }

    @Test
    @DisplayName("You must correctly map a VDoubleArray to a Protobuf VectorDouble")
    void testCreateVectorDouble() {
        
        List<Double> testData = Arrays.asList(1.0, 2.0, 3.0);
        VDoubleArray vArray = VDoubleArray.of(org.epics.util.array.ArrayDouble.of(1.0, 2.0, 3.0), 
                                             Alarm.none(), Time.now(), Display.none());

        Object result = MerlotPayloadMapping.createEvent(vArray);

        assertTrue(result instanceof EPICSEvent.VectorDouble);
        EPICSEvent.VectorDouble vector = (EPICSEvent.VectorDouble) result;
        assertEquals(3, vector.getValCount());
        assertEquals(1.0, vector.getVal(0));
    }

    @Test
    @DisplayName("You must identify the MerlotPayloadMapping type from an instance")
    void testFromVType() {
        VInt vInt = VInt.of(10, Alarm.none(), Time.now(), Display.none());
        MerlotPayloadMapping mapping = MerlotPayloadMapping.fromVType(vInt);
        
        assertEquals(MerlotPayloadMapping.SCALAR_INT, mapping);
    }

    @Test
    @DisplayName("It must return null for unsupported or null types")
    void testUnsupportedTypes() {
        assertNull(MerlotPayloadMapping.createEvent(null));
    }
}