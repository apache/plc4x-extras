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
 * Use Case to add a new entry to a structured property of an S88 element.
 * <p>
 * A {@code containerKey} of {@code null} adds a top level property (e.g. a Unit attribute),
 * otherwise the entry is added to the nested map stored under {@code containerKey}
 * (e.g. a parameter inside "Parameters").
 */
public class AddStructEntryUseCase {
    private AddStructEntryUseCase() {
        /* This utility class should not be instantiated */
    }

    public static void execute(S88PlantModel model, S88Element element, String containerKey,
                               String entryKey, Object entryValue) {
        if (element == null || entryKey == null || entryKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Entry name cannot be empty");
        }
        if (entryValue == null) {
            throw new IllegalArgumentException("Entry value cannot be null");
        }
        if (StructEntrySupport.containsEntry(element, containerKey, entryKey)) {
            throw new IllegalStateException("Entry '" + entryKey + "' already exists for this element.");
        }

        StructEntrySupport.writeEntry(element, containerKey, entryKey, entryValue);

        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.ADDED, element,
                containerKey != null ? containerKey : entryKey));
    }
}
