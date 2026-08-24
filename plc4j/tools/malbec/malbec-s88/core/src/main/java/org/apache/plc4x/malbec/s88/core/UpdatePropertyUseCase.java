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
 * Use Case to update a property of an S88 element.
 */
public class UpdatePropertyUseCase {

    public static void execute(S88PlantModel model, S88Element element, String key, Object value) {
        if (element == null || key == null || key.isEmpty()) return;
        
        Object oldValue = element.getProperty(key);
        if ((value == null && oldValue == null) || (value != null && value.equals(oldValue))) {
            return; // No change
        }
        
        element.setProperty(key, value);
        
        // Notify changes so the UI can refresh if needed
        model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.UPDATED, element));
    }
}
