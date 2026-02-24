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
package org.apache.plc4x.malbec.services.core;

import org.apache.plc4x.malbec.services.model.Plc4xDeviceNode;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.apache.plc4x.malbec.api.DeviceRecord;
import org.apache.plc4x.malbec.api.Plc4xDialog;
import org.apache.plc4x.malbec.api.TagGroupRecord;
import org.apache.plc4x.malbec.services.impl.DeviceRecordImpl;
import org.apache.plc4x.malbec.services.impl.TagGroupRecordImpl;
import org.apache.plc4x.malbec.services.view.Plc4xAddTagGroupDialog;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author cgarcia
 */
public class Plc4xAddTagGroupAction extends AbstractAction {

    private final Plc4xDeviceNode node;

    public Plc4xAddTagGroupAction(Plc4xDeviceNode node) {
        this.node = node;
        this.putValue(AbstractAction.NAME, "Add TagGroup...");          
    }
    
    @Override
    public void actionPerformed(ActionEvent ae) {
        Lookup lk = Lookups.forPath("Plc4xDriver/taggroup");
       
        if ( lk != null) {
            final Plc4xDialog dialog = lk.lookup(Plc4xDialog.class);
            dialog.setNode(node);                       
            ((JDialog) dialog).setVisible(true);            
        }        
        
    }
    
}
