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
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.core.CreateElementUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 * Action to create a new ISA-88 Plant Element.
 */
public class CreatePlantElementAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;
    private final CreateElementUseCase createElementUseCase = new CreateElementUseCase();

    public CreatePlantElementAction() {
        this(Lookup.EMPTY);
    }

    private CreatePlantElementAction(Lookup context) {
        super(Bundle.BTN_create_plant_element());
        this.context = context;
    }

    @Messages({
        "BTN_create_plant_element=Create New Plant Element...",
        "LBL_CreatePlantElement=Create Plant Element",
        "LBL_ElementID=Element ID:",
        "# {0} - element id",
        "ERR_DuplicateID=Element ID ''{0}'' already exists.",
        "ERR_EmptyID=Element ID cannot be empty."
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        S88Element parentEq = context.lookup(S88Element.class);

        if (project == null) {
            Node node = context.lookup(Node.class);
            if (node != null) {
                project = node.getLookup().lookup(Project.class);
            }
        }

        if (project == null) return;
        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null) return;
        
        NotifyDescriptor.InputLine idInput = new NotifyDescriptor.InputLine(Bundle.LBL_ElementID(), Bundle.LBL_CreatePlantElement());
        if (DialogDisplayer.getDefault().notify(idInput) != NotifyDescriptor.OK_OPTION) return;
        String id = idInput.getInputText();
        
        try {
            if (plantModel.getModel() == null) {
                S88Element plantRoot = plantModel.createRoot(project.getProjectDirectory().getName());
                plantRoot.setProperty("author", System.getProperty("user.name"));
                plantRoot.setProperty("version", "0.1");
            }

            createElementUseCase.execute(plantModel.getModel(), parentEq, id);
            plantModel.save();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new CreatePlantElementAction(lkp);
    }
}
