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
package org.apache.plc4x.malbec.projecttype.nodes;

import java.awt.Image;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.netbeans.api.annotations.common.StaticResource;
import org.openide.actions.DeleteAction;
import org.openide.actions.NewAction;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;

public class Plc4xHMINode extends AbstractNode {
    
    @StaticResource
    public static final String MODULE_HMI_ICON = "org/apache/plc4x/malbec/projecttype/nodes/AvisosBit.png";     
        
    public Plc4xHMINode(Children children) {
        super(children);
    }

    @Override
    public Image getIcon(int type) {
        return ImageUtilities.loadImage(MODULE_HMI_ICON, true);
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[] {
            new CreateModuleAction()
        };
    }
        
    private class CreateModuleAction extends AbstractAction {
        @Messages("BTN_create_module=Create New Image...")
        CreateModuleAction() {
            super(Bundle.BTN_create_module());
        }        

        @Override
        public void actionPerformed(ActionEvent e) {
            //
        }
    }
    
    
}
