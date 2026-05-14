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
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.api.s88.EquipmentXmlManager;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentType;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 * Action to create a new ISA-88 Plant Element in the master manifest.
 */
public class CreatePlantElementAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

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
        EquipmentType parentEq = context.lookup(EquipmentType.class);

        if (project == null) {
            Node node = context.lookup(Node.class);
            if (node != null) {
                project = node.getLookup().lookup(Project.class);
            }
        }

        if (project == null) return;
        FileObject dir = project.getProjectDirectory();
        FileObject plantXml = dir.getFileObject("plant.xml");
        
        //TODO: Make a wizard panel for this
        //Configure the relevant properties from B2MML
        NotifyDescriptor.InputLine idInput = new NotifyDescriptor.InputLine(Bundle.LBL_ElementID(), Bundle.LBL_CreatePlantElement());
        if (DialogDisplayer.getDefault().notify(idInput) != NotifyDescriptor.OK_OPTION) return;
        String id = idInput.getInputText();
        if (id == null || id.trim().isEmpty()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_EmptyID(), NotifyDescriptor.ERROR_MESSAGE));
            return;
        }
        id = id.trim();
           
        
       
        try {
            EquipmentDocument doc;
            if (plantXml == null) {
                if (id.equals(project.getProjectDirectory().getName())) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_DuplicateID(id), NotifyDescriptor.ERROR_MESSAGE));
                    return;
                }
                // Initialize new manifest with a hidden "Area" container root
                plantXml = dir.createData("plant.xml");
                doc = EquipmentDocument.Factory.newInstance();
                EquipmentType plantRoot = doc.addNewEquipment();
                plantRoot.addNewID().setStringValue(project.getProjectDirectory().getName());
                plantRoot.addNewEquipmentLevel().setStringValue("Area");
                
                // Add the first element as a ProcessCell child of the Area root
                EquipmentType pc = plantRoot.addNewEquipmentChild();
                pc.addNewID().setStringValue(id);
                pc.addNewEquipmentLevel().setStringValue("ProcessCell");
            } else {
                try (InputStream is = plantXml.getInputStream()) {
                    doc = EquipmentXmlManager.loadDocument(is);
                }
                
                EquipmentType root = doc.getEquipment();
                
                if (idExists(root, id)) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_DuplicateID(id), NotifyDescriptor.ERROR_MESSAGE));
                    return;
                }
                
                if (parentEq == null || parentEq.getID().getStringValue().equals(root.getID().getStringValue())) {
                    // Adding a new ProcessCell at the top level (child of the Area container)
                    EquipmentType child = root.addNewEquipmentChild();
                    child.addNewID().setStringValue(id);
                    child.addNewEquipmentLevel().setStringValue("ProcessCell");
                } else {
                    // Find parent in doc and add child based on hierarchy
                    EquipmentType target = findEquipment(root, parentEq.getID().getStringValue());
                    if (target != null) {
                        EquipmentType child = target.addNewEquipmentChild();
                        child.addNewID().setStringValue(id);
                        String parentLevel = target.isSetEquipmentLevel() ? target.getEquipmentLevel().getStringValue() : "";
                        child.addNewEquipmentLevel().setStringValue(inferChildLevel(parentLevel));
                    }
                }
            }

            try (OutputStream os = plantXml.getOutputStream()) {
                EquipmentXmlManager.saveDocument(doc, os);
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    private boolean idExists(EquipmentType root, String id) {
        if (root.getID() != null && root.getID().getStringValue().equals(id)) {
            return true;
        }
        for (EquipmentType child : root.getEquipmentChildArray()) {
            if (idExists(child, id)) {
                return true;
            }
        }
        return false;
    }

    private String inferChildLevel(String parentLevel) {
        switch (parentLevel) {
            case "Area": return "ProcessCell";
            case "ProcessCell": return "Unit";
            case "Unit": return "EquipmentModule";
            case "EquipmentModule": return "ControlModule";
            default: return "ControlModule";
        }
    }

    private EquipmentType findEquipment(EquipmentType root, String id) {
        if (root.getID().getStringValue().equals(id)) return root;
        for (EquipmentType child : root.getEquipmentChildArray()) {
            EquipmentType found = findEquipment(child, id);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new CreatePlantElementAction(lkp);
    }
}
