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
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.plc4x.malbec.s88.api.*;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantSubProjectProviderImpl;
import org.apache.plc4x.malbec.s88.plant.services.S88ProjectServices;
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

        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(Bundle.LBL_ProjectName(), Bundle.LBL_CreatePlantProject());
        input.setInputText("");
        if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = input.getInputText();

        try {
            FileObject dir = project.getProjectDirectory().createFolder(name);
            FileObject plantXml = dir.createData("plant.xml");

            S88PlantModel model = new S88PlantModel(new S88Element());
            model.getRoot().setId(name);
            model.getRoot().setProperty("author", System.getProperty("user.name"));
            model.getRoot().setProperty("version", "0.1");
            model.getRoot().setLevel(S88Level.AREA);

            S88Repository repo = S88ProjectServices.createRepository("xml", new FileObjectStorage(plantXml));
            repo.savePlant(model);

            project.getProjectDirectory().refresh();
            dir.refresh();

            org.netbeans.api.project.ProjectManager.getDefault().findProject(dir);

            Plc4xPlantSubProjectProviderImpl provider = project.getLookup().lookup(Plc4xPlantSubProjectProviderImpl.class);
            if (provider != null) {
                java.awt.EventQueue.invokeLater(provider::fireChange);
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

    private static class FileObjectStorage implements S88Storage {
        private final FileObject fo;
        FileObjectStorage(FileObject fo) { this.fo = fo; }
        @Override public InputStream openInput() throws IOException { return fo.getInputStream(); }
        @Override public OutputStream openOutput() throws IOException { return fo.getOutputStream(); }
    }
}
