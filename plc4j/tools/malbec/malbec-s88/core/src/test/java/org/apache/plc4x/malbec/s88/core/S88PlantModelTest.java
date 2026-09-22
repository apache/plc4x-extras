/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.Test;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;

import static org.junit.jupiter.api.Assertions.*;

class S88PlantModelTest {

    @Test
    void registerClassAllowsNewNames() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88ElementClass ec = new S88ElementClass();
        ec.setName("MotorClass");

        model.registerClass(ec);

        assertSame(ec, model.findClass("MotorClass"));
    }

    @Test
    void registerClassThrowsOnDuplicateName() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88ElementClass first = new S88ElementClass();
        first.setName("MotorClass");
        model.registerClass(first);

        S88ElementClass second = new S88ElementClass();
        second.setName("MotorClass");

        assertThrows(IllegalStateException.class, () -> model.registerClass(second));
    }

    @Test
    void registerClassThrowsOnNullOrEmptyName() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88ElementClass ec = new S88ElementClass();

        assertThrows(IllegalArgumentException.class, () -> model.registerClass(ec));
        assertThrows(IllegalArgumentException.class, () -> model.registerClass(null));
    }

    @Test
    void registerEnumerationAllowsNewNames() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        S88Enumeration e = new S88Enumeration("EnumA");
        e.setValue("X", 0);
        e.setValue("Y", 1);

        model.registerEnumeration(e);

        assertNotNull(model.findEnumeration("EnumA"));
    }

    @Test
    void registerEnumerationThrowsOnDuplicateName() {
        S88PlantModel model = new S88PlantModel(new S88Element());
        model.registerEnumeration(new S88Enumeration("EnumA"));

        assertThrows(IllegalStateException.class,
                () -> model.registerEnumeration(new S88Enumeration("EnumA")));
    }

    @Test
    void registerEnumerationThrowsOnNullOrEmptyName() {
        S88PlantModel model = new S88PlantModel(new S88Element());

        assertThrows(IllegalArgumentException.class, () -> model.registerEnumeration(null));
        assertThrows(IllegalArgumentException.class, () -> model.registerEnumeration(new S88Enumeration("")));
    }
}