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

/**
 * Use Case for renaming an S88 Element.
 */
public class RenameElementUseCase {
    private RenameElementUseCase() {
        /* This utility class should not be instantiated */
    }


    public static void execute(S88PlantModel model, S88Element element, String newId) {
        if (newId == null || newId.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be empty");
        }

        if (model.findById(newId).isPresent()) {
            throw new IllegalStateException("Element with ID '" + newId + "' already exists.");
        }

        element.setId(newId);

        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.RELOADED, element));
    }
}
