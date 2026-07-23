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
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.core.CreateElementUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.NewElementDialog;
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
        "BTN_create_plant_element=New Element",
        "LBL_CreatePlantElement=Create Element",
        "LBL_ElementID=Element ID:",
        "# {0} - element id",
        "ERR_DuplicateID=Element ID ''{0}'' already exists.",
        "ERR_EmptyID=Element ID cannot be empty."
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) {
            Node node = context.lookup(Node.class);
            if (node != null) {
                project = node.getLookup().lookup(Project.class);
            }
        }
        if (project == null) return;

        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null) return;


        S88Element parentEq = context.lookup(S88Element.class);
        if (parentEq == null) {
            if (plantModel.getModel() == null) return;
            parentEq = plantModel.getModel().getRoot();
        }


        List<S88ElementClass> definedClasses = parentEq.getElementClasses();

        NewElementDialog newElementDialog = new NewElementDialog(plantModel, parentEq, definedClasses);
        newElementDialog.setVisible(true);
        

    }



    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new CreatePlantElementAction(lkp);
    }
}
