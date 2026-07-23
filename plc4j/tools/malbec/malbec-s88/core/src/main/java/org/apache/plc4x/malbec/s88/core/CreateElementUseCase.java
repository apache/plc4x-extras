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
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

/**
 * Use Case for creating a new S88 Element.
 */
public class CreateElementUseCase {

    public void execute(S88PlantModel model, S88Element parent, String id, S88ElementClass s88ElementClass) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be empty");
        }
        
        if (model.findById(id).isPresent()) {
            throw new IllegalStateException("Element with ID '" + id + "' already exists.");
        }

        S88Element targetParent = parent != null ? parent : model.getRoot();
        S88Element child = new S88Element();
        child.setId(id);
        child.setLevel(targetParent.getLevel().getChildLevel());
        child.setProperty("version", "0.1");
        child.setClass(s88ElementClass);
        
        targetParent.addChild(child);
        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.ADDED, child));
    }
}
