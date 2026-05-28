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
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.impl.S88ElementImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DeleteElementUseCaseTest {

    private DeleteElementUseCase useCase;
    private S88PlantModel model;
    private S88Element root;

    @BeforeEach
    void setUp() {
        useCase = new DeleteElementUseCase();
        model = mock(S88PlantModel.class);
        root = new S88ElementImpl();
        root.setId("Root");
        when(model.getRoot()).thenReturn(root);
    }

    @Test
    void testExecuteRemovesChild() {
        S88Element child = new S88ElementImpl();
        child.setId("Child");
        root.addChild(child);
        
        assertEquals(1, root.getChildren().size());
        
        useCase.execute(model, child);

        assertEquals(0, root.getChildren().size());
        verify(model).fireChangeEvent(any(S88ChangeEvent.class));
    }

    @Test
    void testExecuteDoesNotDeleteRoot() {
        useCase.execute(model, root);
        verify(model, never()).fireChangeEvent(any(S88ChangeEvent.class));
    }
}
