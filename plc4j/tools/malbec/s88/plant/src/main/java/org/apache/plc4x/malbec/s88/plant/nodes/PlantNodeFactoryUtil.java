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

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.netbeans.api.project.Project;
import org.openide.nodes.Node;

/**
 * Utility to instantiate the correct specialized node based on ISA-88 Level.
 */
public class PlantNodeFactoryUtil {

    public static Node createNode(Project project, S88Element element) {
        S88Level level = element.getLevel() != null ? element.getLevel() : null;
        
        switch (level) {
            case S88Level.AREA:
                return new AreaNode(project, element);
            case S88Level.PROCESSCELL:
                return new ProcessCellNode(project, element);
            case S88Level.UNIT:
                return new UnitNode(project, element);
            case S88Level.EQUIPMENTMODULE:
                return new EquipmentModuleNode(project, element);
            case S88Level.CONTROLMODULE:
                return new ControlModuleNode(project, element);
            default:
                // Fallback to generic node
                return new PlantElementNode(project, element);
        }
    }
}
