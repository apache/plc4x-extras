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

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.OutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.api.s88.EquipmentXmlManager;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantSubProjectProviderImpl;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentPropertyType;
import org.mesa.xml.b2MML.EquipmentType;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 * Action to create a new Plant Sub-project.
 */
@ActionID(category = "Project", id = "org.apache.plc4x.malbec.s88.plant.actions.CreatePlantProjectAction")
@ActionRegistration(displayName = "#CTL_CreatePlantProjectAction", lazy = false)
@ActionReference(path = "Projects/org-plc4x-s88-project/Actions", position = 150)
@Messages({
    "CTL_CreatePlantProjectAction=Create New Plant Sub-project...",
    "LBL_CreatePlantProject=Create Plant Sub-project",
    "LBL_ProjectName=Project Name:"
})
public class CreatePlantProjectAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public CreatePlantProjectAction() {
        this(Lookup.EMPTY);
    }

    private CreatePlantProjectAction(Lookup context) {
        super(Bundle.CTL_CreatePlantProjectAction());
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) {
            return;
        }

        //TODO: Make a wizard panel for this
        //Properties to configure relevant features from B2MML
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(Bundle.LBL_ProjectName(), Bundle.LBL_CreatePlantProject());
        input.setInputText("");
        if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = input.getInputText();

        try {
            FileObject dir = project.getProjectDirectory().createFolder(name);
            FileObject plantXml = dir.createData("plant.xml");

            EquipmentDocument doc = EquipmentDocument.Factory.newInstance();
            EquipmentType root = doc.addNewEquipment();

            EquipmentPropertyType newProp = root.addNewEquipmentProperty();
            newProp.addNewID().setStringValue("author");
            newProp.addNewValue().addNewValueString().setStringValue(System.getProperty("user.name"));

            root.addNewID().setStringValue(name);
            root.addNewEquipmentLevel().setStringValue("Area");
            root.addNewVersion().setStringValue("0.1");

            try (OutputStream os = plantXml.getOutputStream()) {
                EquipmentXmlManager.saveDocument(doc, os);
            }

            project.getProjectDirectory().refresh();

            dir.refresh();

            org.netbeans.api.project.ProjectManager.getDefault().findProject(dir);

            Plc4xPlantSubProjectProviderImpl provider = project.getLookup().lookup(Plc4xPlantSubProjectProviderImpl.class);
            if (provider != null) {

                java.awt.EventQueue.invokeLater(() -> {
                    provider.fireChange();
                });
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        project.getProjectDirectory().refresh();
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new CreatePlantProjectAction(lkp);
    }
}
