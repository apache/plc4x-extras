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
package org.apache.plc4x.malbec.s88.plant.nodes;

import java.awt.Image;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.apache.plc4x.malbec.api.s88.EquipmentXmlManager;
import org.apache.plc4x.malbec.s88.plant.actions.CreatePlantElementAction;
import org.apache.plc4x.malbec.s88.plant.actions.DeletePlantElementAction;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentPropertyType;
import org.mesa.xml.b2MML.EquipmentType;
import org.mesa.xml.b2MML.ValueType;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * Node representing an ISA-88 Plant Element from the master manifest.
 */
public class PlantElementNode extends AbstractNode {

    private final Project project;
    private final String equipmentID;
    private final String equipmentLevel;
    private final PlantElementChildrenFactory factory;
    private FileObject plantXml;
    private final FileChangeAdapter fileListener;

    public PlantElementNode(Project project, EquipmentType equipment) {
        this(project, equipment, new PlantElementChildrenFactory(project, equipment));
    }

    private PlantElementNode(Project project, EquipmentType equipment, PlantElementChildrenFactory factory) {
        super(Children.create(factory, true), 
                new ProxyLookup(Lookups.singleton(equipment), Lookups.singleton(project)));
        this.project = project;
        this.factory = factory;
        this.equipmentID = equipment.getID().getStringValue();
        this.equipmentLevel = equipment.isSetEquipmentLevel() ? equipment.getEquipmentLevel().getStringValue() : "";
        
        this.fileListener = new FileChangeAdapter() {
            @Override
            public void fileChanged(FileEvent fe) {
                refreshNode();
            }
        };
        
        setupListener();
    }

    private void setupListener() {
        if (plantXml == null) {
            plantXml = project.getProjectDirectory().getFileObject("plant.xml");
            if (plantXml != null) {
                plantXml.addFileChangeListener(fileListener);
            }
        }
    }

    private void refreshNode() {
        // Refresh children
        FileObject freshXml = project.getProjectDirectory().getFileObject("plant.xml");
        if (freshXml != null) {
            try (InputStream is = freshXml.getInputStream()) {
                EquipmentDocument doc = EquipmentXmlManager.loadDocument(is);
                EquipmentType freshEq = findEquipment(doc.getEquipment(), equipmentID);
                if (freshEq != null) {
                    factory.updateParent(freshEq);
                }
            } catch (Exception ex) {
                // Ignore parsing errors
            }
        }
    }

    @Override
    public String getDisplayName() {
        return equipmentID + (equipmentLevel.isEmpty() ? "" : " [" + equipmentLevel + "]");
    }

    @Override
    public Image getIcon(int type) {
        return ImageUtilities.loadImage("org/apache/plc4x/malbec/s88/plant/nodes/PlantNode.png");
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        actions.add(new CreatePlantElementAction().createContextAwareInstance(getLookup()));
        actions.add(new DeletePlantElementAction().createContextAwareInstance(getLookup()));
        actions.add(null);
        actions.addAll(Utilities.actionsForPath("Projects/org-plc4x-plant-element/Actions"));
        return actions.toArray(new Action[0]);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set set = Sheet.createPropertiesSet();

        set.put(new PropertySupport.ReadOnly<String>("id", String.class, "ID", "B2MML ID") {
            @Override
            public String getValue() {
                return equipmentID;
            }
        });

        set.put(new PropertySupport.ReadOnly<String>("level", String.class, "Level", "ISA-88 Level") {
            @Override
            public String getValue() {
                return equipmentLevel;
            }
        });

        set.put(new PropertySupport.ReadWrite<String>("plc4xAddress", String.class, "PLC4X Address", "Address of the real tag") {
            @Override
            public String getValue() {
                return getB2MMLProperty("plc4xAddress");
            }

            @Override
            public void setValue(String val) {
                setB2MMLProperty("plc4xAddress", val);
            }
        });

        sheet.put(set);
        return sheet;
    }

    private String getB2MMLProperty(String name) {
        EquipmentType eq = getLookup().lookup(EquipmentType.class);
        if (eq != null) {
            for (EquipmentPropertyType prop : eq.getEquipmentPropertyArray()) {
                if (prop.getID().getStringValue().equals(name)) {
                    if (prop.sizeOfValueArray() > 0) {
                        ValueType val = prop.getValueArray(0);
                        if (val.getValueString() != null) {
                            return val.getValueString().getStringValue();
                        }
                    }
                }
            }
        }
        return "";
    }

    private void setB2MMLProperty(String name, String value) {
        FileObject xml = project.getProjectDirectory().getFileObject("plant.xml");
        if (xml != null) {
            try {
                EquipmentDocument doc;
                try (InputStream is = xml.getInputStream()) {
                    doc = EquipmentXmlManager.loadDocument(is);
                }

                EquipmentType target = findEquipment(doc.getEquipment(), equipmentID);
                if (target != null) {
                    boolean found = false;
                    for (EquipmentPropertyType prop : target.getEquipmentPropertyArray()) {
                        if (prop.getID().getStringValue().equals(name)) {
                            ValueType val = prop.sizeOfValueArray() > 0 ? prop.getValueArray(0) : prop.addNewValue();
                            if (val.getValueString() != null) {
                                val.getValueString().setStringValue(value);
                            } else {
                                val.addNewValueString().setStringValue(value);
                            }
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        EquipmentPropertyType newProp = target.addNewEquipmentProperty();
                        newProp.addNewID().setStringValue(name);
                        newProp.addNewValue().addNewValueString().setStringValue(value);
                    }

                    try (OutputStream os = xml.getOutputStream()) {
                        EquipmentXmlManager.saveDocument(doc, os);
                    }
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
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

    private static class PlantElementChildrenFactory extends ChildFactory<EquipmentType> {
        private final Project project;
        private EquipmentType parent;

        public PlantElementChildrenFactory(Project project, EquipmentType parent) {
            this.project = project;
            this.parent = parent;
        }
        
        public void updateParent(EquipmentType newParent) {
            this.parent = newParent;
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<EquipmentType> list) {
            list.addAll(parent.getEquipmentChildList());
            return true;
        }

        @Override
        protected Node createNodeForKey(EquipmentType key) {
            return new PlantElementNode(project, key);
        }
    }
}
