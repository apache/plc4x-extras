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

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.projecttype.panelcategory.CategoryIcons;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.CreateVariable;
import org.netbeans.api.project.Project;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

final class VariablesNode extends AbstractNode {

    private static final String NAME = "Variables";

    private final Project project;
    private final String uuid;
    private final CommunicationNodeList list;
    private final Action openAction;

    VariablesNode(Project project, String uuid, CommunicationNodeList list) {
        super(Children.LEAF);
        this.project = project;
        this.uuid = uuid;
        this.list = list;
        this.openAction = new AbstractAction("Open Variables") {
            @Override
            public void actionPerformed(ActionEvent e) {
                openVariables();
            }
        };

        setName(NAME);
        setDisplayName(NAME);
        setIconBaseWithExtension(CategoryIcons.VARIABLES);
        refresh();
    }

    void refresh() {
        setShortDescription(DeviceNodeLabels.variablesTooltip(list.variables(uuid)));
    }

    @Override
    public Action getPreferredAction() {
        return openAction;
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[]{openAction};
    }

    private void openVariables() {
        new CreateVariable(project).actionPerformed(
                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "openVariables"));
    }
}
