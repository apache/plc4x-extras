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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoveStructEntryUseCaseTest {

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
    void testRemovesRootLevelEntryAndFiresRemovedEvent() {
        element.setProperty("icon", "x.png");

        RemoveStructEntryUseCase.execute(model, element, null, "icon");

        assertFalse(element.getProperties().containsKey("icon"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.REMOVED, captor.getValue().type());
        assertEquals("icon", captor.getValue().propertyName());
        assertSame(element, captor.getValue().element());
    }

    @Test
    void testRemovesNestedEntryAndKeepsContainer() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("P1", "v1");
        parameters.put("P2", "v2");
        element.setProperty("Parameters", parameters);

        RemoveStructEntryUseCase.execute(model, element, "Parameters", "P1");

        Map<String, Object> remaining = element.getStructuredProperty("Parameters");
        assertNotNull(remaining);
        assertFalse(remaining.containsKey("P1"));
        assertTrue(remaining.containsKey("P2"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.REMOVED, captor.getValue().type());
        assertEquals("Parameters", captor.getValue().propertyName());
    }

    @Test
    void testNestedRemovalIsCopyOnWrite() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("P1", "v1");
        element.setProperty("Parameters", parameters);
        Map<String, Object> previouslyReadContainer = element.getStructuredProperty("Parameters");

        RemoveStructEntryUseCase.execute(model, element, "Parameters", "P1");

        assertTrue(previouslyReadContainer.containsKey("P1"),
                "Previously captured container must not be mutated in place");
        assertFalse(element.getStructuredProperty("Parameters").containsKey("P1"));
    }

    @Test
    void testThrowsWhenRootEntryMissing() {
        assertThrows(IllegalStateException.class,
                () -> RemoveStructEntryUseCase.execute(model, element, null, "missing"));
        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testThrowsWhenNestedEntryMissing() {
        assertThrows(IllegalStateException.class,
                () -> RemoveStructEntryUseCase.execute(model, element, "Parameters", "P1"));
    }

    @Test
    void testThrowsOnEmptyEntryKey() {
        assertThrows(IllegalArgumentException.class,
                () -> RemoveStructEntryUseCase.execute(model, element, null, "  "));
    }
}
