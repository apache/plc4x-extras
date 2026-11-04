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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.junit.jupiter.api.Test;
import org.openide.nodes.Node;

class DeviceChildrenTest {

    @Test
    void deviceWithoutVariablesHasNoChildren() {
        DeviceChildren children = new DeviceChildren("uuid", null,
                new StubNodeList(variables(0)));

        assertEquals(0, children.getNodes().length);
    }

    @Test
    void deviceWithVariablesShowsTheVariablesNode() {
        DeviceChildren children = new DeviceChildren("uuid", null,
                new StubNodeList(variables(1)));

        Node[] nodes = children.getNodes();
        assertEquals(1, nodes.length);
        assertEquals("Variables", nodes[0].getDisplayName());
    }

    @Test
    void variablesNodeAppearsAndDisappearsOnRefresh() {
        StubNodeList list = new StubNodeList(variables(0));
        DeviceChildren children = new DeviceChildren("uuid", null, list);

        assertEquals(0, children.getNodes().length);

        list.variables = variables(2);
        children.refreshVariables();
        assertEquals(1, children.getNodes().length);

        list.variables = variables(0);
        children.refreshVariables();
        assertEquals(0, children.getNodes().length);
    }

    private static List<PvConfigData> variables(int variables) {
        List<PvConfigData> pvs = new ArrayList<>();
        for (int i = 0; i < variables; i++) {
            pvs.add(new PvConfigData("p" + i, "P" + i, "INT", "PLC", "%DB1.DBB0:INT",
                    "d", "500", true, true, "0", "1", "d", "%.2f", "u", "0", "1", "1", "m"));
        }
        return pvs;
    }

    private static final class StubNodeList extends CommunicationNodeList {

        private List<PvConfigData> variables;

        StubNodeList(List<PvConfigData> variables) {
            super(null, null);
            this.variables = variables;
        }

        @Override
        List<PvConfigData> variables(String uuid) {
            return variables;
        }
    }
}
