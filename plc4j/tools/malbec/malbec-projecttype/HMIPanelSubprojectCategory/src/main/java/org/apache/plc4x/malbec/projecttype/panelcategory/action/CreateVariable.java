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
package org.apache.plc4x.malbec.projecttype.panelcategory.action;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.panel.CreateVariableTopComponent;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(
        category = "Project",
        id = "org.apache.plc4x.malbec.projecttype.panelcategory.action.CreateVariable"
)
@ActionRegistration(
        displayName = "#CTL_CreateVariableAction",
        lazy = false
)
@ActionReferences({
    @ActionReference(path = "Projects/Actions", position = 500)
})
@Messages("CTL_CreateVariableAction=Crear Variables")
public final class CreateVariable extends AbstractAction implements ContextAwareAction {

    private final Project project;
    private final String deviceUuid;

    public CreateVariable() {
        this(null);
    }

    public CreateVariable(Project project) {
        this(project, null);
    }

    public CreateVariable(Project project, String deviceUuid) {
        super(Bundle.CTL_CreateVariableAction());
        this.project = project;
        this.deviceUuid = deviceUuid;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (project == null) {
            showWarning("Seleccione un proyecto antes de crear variables.");
            return;
        }
        if (project.getLookup().lookup(HMICommunicationModel.class) == null) {
            showWarning("El proyecto seleccionado no tiene configuración de comunicaciones.");
            return;
        }

        TopComponent tc = WindowManager.getDefault()
                .findTopComponent("CreateVariableTopComponent");

        CreateVariableTopComponent variableTc =
                tc instanceof CreateVariableTopComponent existing
                        ? existing
                        : new CreateVariableTopComponent();

        variableTc.initData(project);
        if (deviceUuid == null || deviceUuid.isBlank()) {
            variableTc.clearFilter();
        } else {
            variableTc.filterByDevice(deviceUuid);
        }
        variableTc.open();
        variableTc.requestActive();
    }

    private static void showWarning(String message) {
        JOptionPane.showMessageDialog(
                WindowManager.getDefault().getMainWindow(),
                message,
                "Crear Variables",
                JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public Action createContextAwareInstance(Lookup context) {
        Project contextProject = context.lookup(Project.class);
        return new CreateVariable(contextProject);
    }
}