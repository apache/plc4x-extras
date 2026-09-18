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

package org.apache.plc4x.malbec.s88.plant.actions;

import org.apache.plc4x.malbec.s88.plant.panels.ExportDialog;
import org.netbeans.spi.project.SubprojectProvider;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.netbeans.api.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Set;

/**
 * Action to export a plant model to another environment
 */
@ActionID(category = "Project", id = "org.apache.plc4x.malbec.s88.plant.actions.ExportAction")
@ActionRegistration(displayName = "#CTL_ExportAction", lazy = false)
@ActionReference(path = "Projects/org-plc4x-s88-project/Actions", position = 200)
@NbBundle.Messages({
    "CTL_ExportAction=Export"
})
public class ExportAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public ExportAction() {
        this(Lookup.EMPTY);
    }

    private ExportAction(Lookup context) {
        super(Bundle.CTL_ExportAction());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ExportAction(actionContext);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) return;
        SubprojectProvider provider = project.getLookup().lookup(SubprojectProvider.class);
        if (provider == null) return;
        Set<? extends Project> projectList = provider.getSubprojects();
        if (projectList.isEmpty()) return;
        Project[] projects = projectList.toArray(new Project[0]);
        ExportDialog dialog = new ExportDialog(projects);
        dialog.setVisible(true);
    }
}
