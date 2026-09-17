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

import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddStructEntryUseCaseTest {

    private S88PlantModel model;
    private S88Element element;

    @BeforeEach
    void setUp() {
        model = mock(S88PlantModel.class);
        element = new S88Element()
                .setId("Unit1")
                .setLevel(S88Level.UNIT);
    }

    @Test
    void testAddsRootLevelEntryAndFiresAddedEvent() {
        AddStructEntryUseCase.execute(model, element, null, "icon", "x.png");

        assertEquals("x.png", element.getProperty("icon"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.ADDED, captor.getValue().type());
        assertEquals("icon", captor.getValue().propertyName());
        assertSame(element, captor.getValue().element());
    }

    @Test
    void testAddsNestedEntryAndCreatesContainer() {
        AddStructEntryUseCase.execute(model, element, "Parameters", "P1", "v1");

        Map<String, Object> parameters = element.getStructuredProperty("Parameters");
        assertNotNull(parameters);
        assertEquals("v1", parameters.get("P1"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.ADDED, captor.getValue().type());
        assertEquals("Parameters", captor.getValue().propertyName());
    }

    @Test
    void testAddingSecondNestedEntryIsCopyOnWrite() {
        AddStructEntryUseCase.execute(model, element, "Parameters", "P1", "v1");
        Map<String, Object> previouslyReadContainer = element.getStructuredProperty("Parameters");

        AddStructEntryUseCase.execute(model, element, "Parameters", "P2", "v2");
        Map<String, Object> currentContainer = element.getStructuredProperty("Parameters");

        assertTrue(currentContainer.containsKey("P1"));
        assertTrue(currentContainer.containsKey("P2"));
        assertFalse(previouslyReadContainer.containsKey("P2"),
                "Previously captured container must not be mutated in place");
    }

    @Test
    void testThrowsOnDuplicateRootEntry() {
        element.setProperty("icon", "x.png");

        assertThrows(IllegalStateException.class,
                () -> AddStructEntryUseCase.execute(model, element, null, "icon", "y.png"));
        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testThrowsOnDuplicateNestedEntry() {
        AddStructEntryUseCase.execute(model, element, "Parameters", "P1", "v1");

        assertThrows(IllegalStateException.class,
                () -> AddStructEntryUseCase.execute(model, element, "Parameters", "P1", "v2"));
    }

    @Test
    void testThrowsOnEmptyEntryKey() {
        assertThrows(IllegalArgumentException.class,
                () -> AddStructEntryUseCase.execute(model, element, null, "   ", "v"));
    }

    @Test
    void testThrowsOnNullEntryValue() {
        assertThrows(IllegalArgumentException.class,
                () -> AddStructEntryUseCase.execute(model, element, null, "key", null));
    }

    @Test
    void testThrowsOnNullElement() {
        assertThrows(IllegalArgumentException.class,
                () -> AddStructEntryUseCase.execute(model, null, null, "key", "v"));
    }
}
