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
import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 * Action to delete an ISA-88 Plant Element.
 */
@ActionID(category = "Project", id = "org.apache.plc4x.malbec.s88.plant.actions.DeletePlantElementAction")
@ActionRegistration(displayName = "#CTL_DeletePlantElementAction", lazy = false)
@ActionReference(path = "Projects/org-plc4x-plant-element/Actions", position = 200)
@Messages({
    "CTL_DeletePlantElementAction=Delete Plant Element",
    "MSG_ConfirmDelete=Are you sure you want to delete element ''{0}'' and all its children?"
})
public class DeletePlantElementAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public DeletePlantElementAction() {
        this(Lookup.EMPTY);
    }

    private DeletePlantElementAction(Lookup context) {
        super(Bundle.CTL_DeletePlantElementAction());
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        S88Element targetEq = context.lookup(S88Element.class);

        if (project == null || targetEq == null) return;
        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null || plantModel.getModel() == null) return;
        
        String id = targetEq.getId();
        NotifyDescriptor.Confirmation confirm = new NotifyDescriptor.Confirmation(
                Bundle.MSG_ConfirmDelete(id),
                Bundle.CTL_DeletePlantElementAction(),
                NotifyDescriptor.YES_NO_OPTION);
        
        if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.YES_OPTION) return;

        try {
            S88Element parent = targetEq.getParent();
            if (parent != null) {
                parent.removeChild(targetEq);
                plantModel.getModel().fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.REMOVED, targetEq));
                plantModel.save();
            }
        } catch (Exception ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new DeletePlantElementAction(lkp);
    }
}
