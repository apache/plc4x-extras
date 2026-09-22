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

class UpdateStructEntryUseCaseTest {

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
    void testUpdatesRootLevelEntryAndFiresUpdatedEvent() {
        element.setProperty("icon", "old.png");

        UpdateStructEntryUseCase.execute(model, element, null, "icon", "new.png");

        assertEquals("new.png", element.getProperty("icon"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.UPDATED, captor.getValue().type());
        assertEquals("icon", captor.getValue().propertyName());
        assertSame(element, captor.getValue().element());
    }

    @Test
    void testUpdatesNestedEntryAndFiresUpdatedEvent() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("P1", "v1");
        element.setProperty("Parameters", parameters);

        UpdateStructEntryUseCase.execute(model, element, "Parameters", "P1", "v2");

        assertEquals("v2", element.getStructuredProperty("Parameters").get("P1"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.UPDATED, captor.getValue().type());
        assertEquals("Parameters", captor.getValue().propertyName());
    }

    @Test
    void testNestedUpdateIsCopyOnWrite() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("P1", "v1");
        element.setProperty("Parameters", parameters);
        Map<String, Object> previouslyReadContainer = element.getStructuredProperty("Parameters");

        UpdateStructEntryUseCase.execute(model, element, "Parameters", "P1", "v2");

        assertEquals("v1", previouslyReadContainer.get("P1"),
                "Previously captured container must not be mutated in place");
        assertEquals("v2", element.getStructuredProperty("Parameters").get("P1"));
    }

    @Test
    void testDoesNotFireEventWhenRootValueUnchanged() {
        element.setProperty("icon", "same");

        UpdateStructEntryUseCase.execute(model, element, null, "icon", "same");

        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testDoesNotFireEventWhenNestedValueUnchanged() {
        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("Type", "REAL");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("P1", bag);
        element.setProperty("Parameters", parameters);

        UpdateStructEntryUseCase.execute(model, element, "Parameters", "P1", bag);

        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testThrowsWhenRootEntryMissing() {
        assertThrows(IllegalStateException.class,
                () -> UpdateStructEntryUseCase.execute(model, element, null, "missing", "v"));
        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testThrowsWhenNestedEntryMissing() {
        assertThrows(IllegalStateException.class,
                () -> UpdateStructEntryUseCase.execute(model, element, "Parameters", "P1", "v"));
    }

    @Test
    void testThrowsOnEmptyEntryKey() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateStructEntryUseCase.execute(model, element, null, " ", "v"));
    }

    @Test
    void testThrowsOnNullEntryValue() {
        element.setProperty("icon", "x.png");
        assertThrows(IllegalArgumentException.class,
                () -> UpdateStructEntryUseCase.execute(model, element, null, "icon", null));
    }
}
