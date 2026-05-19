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

import org.mesa.xml.b2MML.EquipmentType;
import org.netbeans.api.project.Project;
import org.openide.nodes.Node;

/**
 * Utility to instantiate the correct specialized node based on B2MML EquipmentLevel.
 */
public class PlantNodeFactoryUtil {

    public static Node createNode(Project project, EquipmentType equipment) {
        String level = equipment.isSetEquipmentLevel() ? equipment.getEquipmentLevel().getStringValue() : "";
        level = level.trim();
        
        switch (level) {
            case "Area":
                return new AreaNode(project, equipment);
            case "ProcessCell":
                return new ProcessCellNode(project, equipment);
            case "Unit":
                return new UnitNode(project, equipment);
            case "EquipmentModule":
                return new EquipmentModuleNode(project, equipment);
            case "ControlModule":
                return new ControlModuleNode(project, equipment);
            default:
                // Fallback to generic node
                return new PlantElementNode(project, equipment);
        }
    }
}
