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

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.PropertiesFactory;
import org.netbeans.api.project.Project;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class PropertiesAction  extends AbstractAction implements ContextAwareAction {
    private final Lookup context;
    public PropertiesAction() {
        this(Lookup.EMPTY);
    }

    private PropertiesAction(Lookup context) {
        super(Bundle.BTN_Props());
        this.context = context;
    }


    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new PropertiesAction(actionContext);
    }
    @NbBundle.Messages({
            "BTN_Props=Properties"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        S88Element targetEq = context.lookup(S88Element.class);

        if (project == null || targetEq == null) return;
        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null || plantModel.getModel() == null) return;



        JDialog props = PropertiesFactory.createDialog(targetEq, plantModel);
        props.setVisible(true);
    }
}
