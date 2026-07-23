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
import org.netbeans.api.project.Project;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;

/**
 * Specialized node for ISA-88 Control Module.
 */
public class ControlModuleNode extends PlantElementNode {

    public ControlModuleNode(Project project, S88Element element) {
        super(project, element);
    }

    @Override
    protected String getDefaultIconResource() {
        return "org/apache/plc4x/malbec/s88/plant/nodes/ControlModule.png";
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        sheet.put(createConnectionSet());
        return sheet;
    }

    private Sheet.Set createConnectionSet() {
        Sheet.Set set = new Sheet.Set();
        set.setName("connection");
        set.setDisplayName("Connections");
        set.setShortDescription("External communication.");

        set.put(new PropertySupport.ReadWrite<String>("Address", String.class, "Address", "Address of the real tag") {
            @Override public String getValue() { return currentElement.getProperty("plc4xAddress"); }
            @Override public void setValue(String val) { updateProperty("plc4xAddress", val); }
        });

        set.put(new PropertySupport.ReadWrite<String>("driver", String.class, "Driver", "Communication driver.") {
            @Override public String getValue() { return currentElement.getProperty("commDriver"); }
            @Override public void setValue(String val) { updateProperty("commDriver", val); }
        });

        set.put(new PropertySupport.ReadWrite<String>("pollingRate", String.class, "Polling Interval", "Update interval.") {
            @Override public String getValue() { return currentElement.getProperty("pollingRate"); }
            @Override public void setValue(String val) { updateProperty("pollingRate", val); }
        });

        return set;
    }
}
