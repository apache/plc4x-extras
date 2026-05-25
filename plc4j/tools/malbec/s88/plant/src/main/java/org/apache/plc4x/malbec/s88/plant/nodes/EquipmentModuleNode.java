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

import javax.swing.Action;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.netbeans.api.project.Project;

/**
 * Specialized node for ISA-88 Equipment Module.
 */
public class EquipmentModuleNode extends PlantElementNode {

    public EquipmentModuleNode(Project project, S88Element element) {
        super(project, element);
    }

    @Override
    protected String getDefaultIconResource() {
        return "org/apache/plc4x/malbec/s88/plant/nodes/EquipmentModule.png";
    }

    @Override
    public Action[] getActions(boolean context) {
        Action[] actions = super.getActions(context);
        return actions;
    }
}
