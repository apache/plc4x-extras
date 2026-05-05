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
import org.apache.plc4x.merlot.api.PB.EPICSEvent.PayloadType;
import org.apache.plc4x.merlot.archiver.core.MerlotTypeMapping;
import org.epics.vtype.Alarm;
import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;
import org.epics.vtype.VType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 *
 * @author cgarcia
 */
public class MerlotTypeMappingTest {

    public MerlotTypeMappingTest() {
    }

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    @DisplayName("It must return the correct mapping for SCALAR_DOUBLE")
    void testScalarDoubleMapping() {
        MerlotTypeMapping mapping = MerlotTypeMapping.fromProto(PayloadType.SCALAR_DOUBLE);

        assertNotNull(mapping, "The mapping should not be null");
        assertEquals(VDouble.class, mapping.getVTypeClass(),
                "SCALAR_DOUBLE must map to the VDouble interface");
    }

    @ParameterizedTest
    @EnumSource(MerlotTypeMapping.class)
    @DisplayName("Validate the integrity of all defined mappings")
    void testAllMappings(MerlotTypeMapping mapping) {

        assertTrue(VType.class.isAssignableFrom(mapping.getVTypeClass()),
                "The mapped class " + mapping.getVTypeClass().getName() + " must be a VType");

        assertNotNull(mapping.getProtoType(),
                "The Protobuf type for " + mapping.name() + " must not be null");
    }

    @Test
    @DisplayName("It should handle unknown payload types by returning null")
    void testUnknownPayloadType() {

        assertNull(MerlotTypeMapping.fromProto(null),
                "Mapping a null type must result in null");
    }

    @Test
    @DisplayName("Verify the consistency of the inverse static map")
    void testReverseMapConsistency() {
        for (MerlotTypeMapping mapping : MerlotTypeMapping.values()) {
            MerlotTypeMapping retrieved = MerlotTypeMapping.fromProto(mapping.getProtoType());
            assertEquals(mapping, retrieved,
                    "The mapping retrieved by PayloadType must match the Enum instance");
        }
    }

    @Test
    @DisplayName("It must detect the correct PayloadType from a specific instance")
    void testFromVTypeInstance() {

        VDouble myValue = VDouble.of(10.5, Alarm.none(), Time.now(), Display.none());

        PayloadType detectedType = MerlotTypeMapping.fromVType(myValue);

        assertEquals(PayloadType.SCALAR_DOUBLE, detectedType,
                "An instance of VDouble must be recognized as SCALAR_DOUBLE");
    }

}
