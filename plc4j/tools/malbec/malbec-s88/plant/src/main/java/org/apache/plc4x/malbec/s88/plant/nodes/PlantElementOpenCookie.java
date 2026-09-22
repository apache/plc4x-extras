/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.plant.nodes;

import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.EditorTopComponent;
import org.openide.cookies.OpenCookie;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * OpenCookie that opens a specialized editor for a plant element.
 */
public class PlantElementOpenCookie implements OpenCookie {

    private final Plc4xPlantModel model;
    private final S88Element element;

    public PlantElementOpenCookie(Plc4xPlantModel model, S88Element element) {
        this.model = model;
        this.element = element;
    }

    @Override
    public void open() {
        EditorTopComponent editor = findExistingEditor();
        
        if (editor == null) {
            editor = new EditorTopComponent(model, element);
            editor.open();
        }
        
        editor.requestActive();
    }

    private EditorTopComponent findExistingEditor() {
        Set<TopComponent> opened = WindowManager.getDefault().getRegistry().getOpened();
        for (TopComponent tc : opened) {
            if (tc instanceof EditorTopComponent etc) {
                if (etc.getElement() != null && etc.getElement().getId().equals(element.getId())) {
                    return etc;
                }
            }
        }
        return null;
    }
}
