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

import java.util.Optional;
import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.impl.S88ElementImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CreateElementUseCaseTest {

    private CreateElementUseCase useCase;
    private S88PlantModel model;
    private S88Element root;

    @BeforeEach
    void setUp() {
        useCase = new CreateElementUseCase();
        model = mock(S88PlantModel.class);
        root = new S88ElementImpl();
        root.setId("RootArea");
        root.setLevel(S88Level.AREA);
        
        when(model.getRoot()).thenReturn(root);
        when(model.findById(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void testExecuteCreatesChildWithCorrectLevel() {
        useCase.execute(model, root, "NewPC");

        assertEquals(1, root.getChildren().size());
        S88Element child = root.getChildren().get(0);
        assertEquals("NewPC", child.getId());
        assertEquals(S88Level.PROCESSCELL, child.getLevel());
    }

    @Test
    void testExecuteFiresEvent() {
        useCase.execute(model, root, "NewPC");

        ArgumentCaptor<S88ChangeEvent> eventCaptor = ArgumentCaptor.forClass(S88ChangeEvent.class);
        verify(model).fireChangeEvent(eventCaptor.capture());
        
        S88ChangeEvent event = eventCaptor.getValue();
        assertEquals(S88ChangeEvent.Type.ADDED, event.getType());
        assertEquals("NewPC", event.getElement().getId());
    }

    @Test
    void testExecuteThrowsOnDuplicateId() {
        when(model.findById("Existing")).thenReturn(Optional.of(mock(S88Element.class)));

        assertThrows(IllegalStateException.class, () -> {
            useCase.execute(model, root, "Existing");
        });
    }

    @Test
    void testExecuteThrowsOnEmptyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            useCase.execute(model, root, "");
        });
    }
}
