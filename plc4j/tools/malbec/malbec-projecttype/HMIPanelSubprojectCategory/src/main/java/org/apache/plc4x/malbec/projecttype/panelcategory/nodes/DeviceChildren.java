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

import java.util.List;
import org.netbeans.api.project.Project;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

final class DeviceChildren extends Children.Keys<Boolean> {

    private static final Boolean VARIABLES = Boolean.TRUE;

    private final String uuid;
    private final CommunicationNodeList list;
    private final VariablesNode variablesNode;
    private boolean showingVariables;

    DeviceChildren(String uuid, Project project, CommunicationNodeList list) {
        this.uuid = uuid;
        this.list = list;
        this.variablesNode = new VariablesNode(project, uuid, list);
        refreshVariables();
    }

    void refreshVariables() {
        variablesNode.refresh();
        boolean show = DeviceNodeLabels.hasVariables(list.variables(uuid));
        if (show == showingVariables) {
            return;
        }
        showingVariables = show;
        setKeys(show ? List.of(VARIABLES) : List.of());
    }

    @Override
    protected Node[] createNodes(Boolean key) {
        return VARIABLES.equals(key) ? new Node[]{variablesNode} : new Node[0];
    }
}
