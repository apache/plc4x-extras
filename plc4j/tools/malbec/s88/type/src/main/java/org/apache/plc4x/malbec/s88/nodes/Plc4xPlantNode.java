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
package org.apache.plc4x.malbec.s88.nodes;

import java.awt.Image;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.netbeans.api.annotations.common.StaticResource;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;

/**
 * Node representing the Plant View in the S88 project structure.
 */
public class Plc4xPlantNode extends AbstractNode {
    
    @StaticResource
    public static final String PLANT_NODE_ICON = "org/apache/plc4x/malbec/s88/nodes/AvisosBit.png";     
        
    public Plc4xPlantNode(Children children) {
        super(children);
        setName("PlantView");
        setDisplayName("Plant View");
    }

    @Override
    public Image getIcon(int type) {
        return ImageUtilities.loadImage(PLANT_NODE_ICON, true);
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[] {
            new CreatePlantElementAction()
        };
    }
        
    private class CreatePlantElementAction extends AbstractAction {
        @Messages("BTN_create_plant_element=Create New Plant Element...")
        CreatePlantElementAction() {
            super(Bundle.BTN_create_plant_element());
        }        

        @Override
        public void actionPerformed(ActionEvent e) {
            // TODO: Implement the creation of a new plant element
        }
    }
}
