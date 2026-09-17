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
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateClassUseCaseTest {

    private S88PlantModel model;
    private S88Element parent;
    private S88Element root;

    @BeforeEach
    void setUp() {
        model = mock(S88PlantModel.class);
        parent = new S88Element()
                .setId("Unit1")
                .setLevel(S88Level.UNIT);
        root = new S88Element()
                .setId("Root")
                .setLevel(S88Level.AREA);

        when(model.getClasses()).thenReturn(new LinkedHashMap<>());
        when(model.getRoot()).thenReturn(root);
    }

    @Test
    void testCreatesClassAttachesToParentAndRegisters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("Speed", "REAL");

        CreateClassUseCase.execute(model, parent, "MotorClass", properties);

        ArgumentCaptor<S88ElementClass> captor = ArgumentCaptor.forClass(S88ElementClass.class);
        verify(model).registerClass(captor.capture());

        S88ElementClass created = captor.getValue();
        assertEquals("MotorClass", created.getName());
        assertEquals(S88Level.EQUIPMENTMODULE, created.getTargetLevel());
        assertEquals("REAL", created.getProperty("Speed"));
        assertTrue(parent.getElementClasses().contains(created));
    }

    @Test
    void testUsesRootAndNoTargetLevelWhenParentIsNull() {
        CreateClassUseCase.execute(model, null, "RootClass", null);

        ArgumentCaptor<S88ElementClass> captor = ArgumentCaptor.forClass(S88ElementClass.class);
        verify(model).registerClass(captor.capture());

        S88ElementClass created = captor.getValue();
        assertEquals("RootClass", created.getName());
        assertNull(created.getTargetLevel());
        assertTrue(root.getElementClasses().contains(created));
    }

    @Test
    void testThrowsOnEmptyName() {
        assertThrows(IllegalArgumentException.class,
                () -> CreateClassUseCase.execute(model, parent, "  ", null));
    }

    @Test
    void testThrowsOnReservedPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> CreateClassUseCase.execute(model, parent, S88PlantModel.ENUM_CLASS_PREFIX + "Color", null));
    }

    @Test
    void testThrowsOnDuplicateName() {
        Map<String, S88ElementClass> classes = new LinkedHashMap<>();
        classes.put("Duplicate", new S88ElementClass());
        when(model.getClasses()).thenReturn(classes);

        assertThrows(IllegalStateException.class,
                () -> CreateClassUseCase.execute(model, parent, "Duplicate", null));
        verify(model, never()).registerClass(any());
    }
}
