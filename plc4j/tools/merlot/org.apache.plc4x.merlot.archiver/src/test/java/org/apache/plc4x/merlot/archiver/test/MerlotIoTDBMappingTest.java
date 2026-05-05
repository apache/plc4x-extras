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

import org.apache.iotdb.pipe.api.type.Type;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble;
import org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarFloat;
import org.apache.plc4x.merlot.archiver.core.MerlotIoTDBMapping;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MerlotIoTDBMappingTest {

    private MerlotIoTDBMapping.EpicsMetadata testMeta;

    @BeforeEach
    public void setUp() {
        testMeta = new MerlotIoTDBMapping.EpicsMetadata(12345678, 999, 1, 2);
    }

    @Test
    public void testConvertFloat() {
        float value = 12.34f;
        Object result = MerlotIoTDBMapping.fromIotdb(Type.FLOAT).convert(value, testMeta);

        Assertions.assertTrue(result instanceof ScalarFloat);
        ScalarFloat scalar = (ScalarFloat) result;
        Assertions.assertEquals(value, scalar.getVal(), 0.001);
        Assertions.assertEquals(testMeta.nano, scalar.getNano());
    }

    @Test
    public void testConvertDouble() {
        double value = 99.999;
        Object result = MerlotIoTDBMapping.fromIotdb(Type.DOUBLE).convert(value, testMeta);

        Assertions.assertTrue(result instanceof ScalarDouble);
        ScalarDouble scalar = (ScalarDouble) result;
        Assertions.assertEquals(value, scalar.getVal(), 0.00001);
        Assertions.assertEquals(testMeta.status, scalar.getStatus());
    }

    @Test
    public void testUnsupportedType() {
        
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            MerlotIoTDBMapping.fromIotdb(Type.BLOB);
        });
    }

    
    
}
