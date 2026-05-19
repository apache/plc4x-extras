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

import java.awt.Image;
import javax.swing.Action;
import org.mesa.xml.b2MML.EquipmentType;
import org.netbeans.api.project.Project;
import org.openide.util.ImageUtilities;

/**
 * Specialized node for ISA-88 Area.
 */
public class AreaNode extends PlantElementNode {

    public AreaNode(Project project, EquipmentType equipment) {
        super(project, equipment);
    }

    @Override
    public Image getIcon(int type) {
        return super.getIcon(type);
    }

    @Override
    public Action[] getActions(boolean context) {
        Action[] actions = super.getActions(context);
        
        return actions;
    }
    
    // implement custom OpenCookie for a specific Area Editor
}
