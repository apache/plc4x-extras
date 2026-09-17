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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdatePropertyUseCaseTest {

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
    void testSetsPropertyAndFiresUpdatedEvent() {
        UpdatePropertyUseCase.execute(model, element, "icon", "x.png");

        assertEquals("x.png", element.getProperty("icon"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.UPDATED, captor.getValue().type());
        assertEquals("icon", captor.getValue().propertyName());
        assertSame(element, captor.getValue().element());
    }

    @Test
    void testDoesNotFireEventWhenValueUnchanged() {
        element.setProperty("icon", "same");

        UpdatePropertyUseCase.execute(model, element, "icon", "same");

        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testDoesNotFireEventWhenMissingAndNull() {
        UpdatePropertyUseCase.execute(model, element, "missing", null);

        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testRemovesPropertyWhenValueIsNull() {
        element.setProperty("icon", "x.png");

        UpdatePropertyUseCase.execute(model, element, "icon", null);

        assertFalse(element.getProperties().containsKey("icon"));

        ArgumentCaptor<S88ChangeEvent> captor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(captor.capture());
        assertEquals(S88ChangeEvent.Type.UPDATED, captor.getValue().type());
        assertEquals("icon", captor.getValue().propertyName());
    }

    @Test
    void testIgnoresNullElement() {
        UpdatePropertyUseCase.execute(model, null, "icon", "x.png");

        verify(model, never()).fireChangeEvent(any());
    }

    @Test
    void testIgnoresNullAndEmptyKey() {
        UpdatePropertyUseCase.execute(model, element, null, "x.png");
        UpdatePropertyUseCase.execute(model, element, "", "x.png");

        verify(model, never()).fireChangeEvent(any());
        assertTrue(element.getProperties().isEmpty());
    }
}
