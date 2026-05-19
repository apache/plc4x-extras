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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.s88.plant.actions.CreatePlantElementAction;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.mesa.xml.b2MML.EquipmentPropertyType;
import org.mesa.xml.b2MML.EquipmentType;
import org.mesa.xml.b2MML.ValueType;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

@Messages({
    "# {0} - element id",
    "ERR_DuplicateID=Element ID ''{0}'' already exists.",
    "ERR_EmptyID=Element ID cannot be empty."
})

/**
 * Node representing an ISA-88 Plant Element from the master manifest.
 */
public class PlantElementNode extends AbstractNode implements ChangeListener {

    private final Project project;
    private final Plc4xPlantModel model;
    private String equipmentID;
    private final String equipmentLevel;
    private final PlantElementChildrenFactory factory;
    private final InstanceContent content;
    private EquipmentType currentEquipment;

    public PlantElementNode(Project project, EquipmentType equipment) {
        this(project, equipment, new InstanceContent());
    }

    protected PlantElementNode(Project project, EquipmentType equipment, InstanceContent content) {
        this(project, equipment, new PlantElementChildrenFactory(project, equipment), content);
    }

    private PlantElementNode(Project project, EquipmentType equipment, PlantElementChildrenFactory factory, InstanceContent content) {
        super(Children.create(factory, true), new AbstractLookup(content));
        this.project = project;
        this.model = project.getLookup().lookup(Plc4xPlantModel.class);
        this.factory = factory;
        this.content = content;
        this.currentEquipment = equipment;
        this.content.add(project);
        this.content.add(currentEquipment);
        this.content.add(this);

        this.equipmentID = equipment.getID().getStringValue();
        this.equipmentLevel = equipment.isSetEquipmentLevel() ? equipment.getEquipmentLevel().getStringValue() : "";

        if (!this.equipmentLevel.isEmpty()) {
            this.content.add(new PlantElementOpenCookie(this.equipmentID, this.equipmentLevel));
        }

        if (model != null) {
            model.addChangeListener(this);
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        refreshNode();
    }

    private void refreshNode() {
        if (model != null) {
            EquipmentType freshEq = model.getElementByID(equipmentID);
            if (freshEq != null) {
                updateEquipment(freshEq);
            }
        }
    }

    private void updateEquipment(EquipmentType freshEq) {
        content.remove(currentEquipment);
        currentEquipment = freshEq;
        content.add(currentEquipment);
        factory.updateParent(currentEquipment);
        this.equipmentID = freshEq.getID().getStringValue();
        fireDisplayNameChange(null, getDisplayName());
        fireIconChange();
        fireOpenedIconChange();
    }

    @Override
    public String getDisplayName() {
        return equipmentID + (equipmentLevel.isEmpty() ? "" : " [" + equipmentLevel + "]");
    }

    @Override
    public Image getIcon(int type) {
        String iconPath = getB2MMLProperty("icon");
        if (iconPath != null && !iconPath.isEmpty()) {
            Image img = null;

            // 1. Try as a file path if it looks absolute
            try {
                File f = new File(iconPath);
                if (f.isAbsolute() && f.exists()) {
                    img = ImageIO.read(f);
                }
            } catch (Exception e) {
                // Ignore and fall back
            }

            if (img == null) {
                // 2. Try as a classpath resource
                String resourcePath = iconPath;
                if (resourcePath.startsWith("/")) {
                    resourcePath = resourcePath.substring(1);
                }
                img = ImageUtilities.loadImage(resourcePath, true);
            }

            if (img != null) {
                return img;
            }
        }
        return ImageUtilities.loadImage("org/apache/plc4x/malbec/s88/plant/nodes/PlantNode.png");
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        actions.add(org.openide.util.actions.SystemAction.get(org.openide.actions.OpenAction.class));
        actions.add(null);
        actions.add(new CreatePlantElementAction().createContextAwareInstance(getLookup()));
        actions.add(null);
        actions.addAll(Utilities.actionsForPath("Projects/org-plc4x-plant-element/Actions"));
        actions.add(null);
        actions.add(org.openide.util.actions.SystemAction.get(org.openide.actions.RenameAction.class));
        actions.add(org.openide.util.actions.SystemAction.get(org.openide.actions.PropertiesAction.class));
        return actions.toArray(new Action[0]);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set set = Sheet.createPropertiesSet();

        Sheet.Set connectionSet = new Sheet.Set();
        connectionSet.setName("conection");
        connectionSet.setDisplayName("Connections");
        connectionSet.setShortDescription("External communication.");

        Sheet.Set metadataSet = new Sheet.Set();
        metadataSet.setName("metadata");
        metadataSet.setDisplayName("Metadata");
        metadataSet.setShortDescription("Complementary information");

        metadataSet.put(new PropertySupport.ReadOnly<String>("author", String.class, "Author", "Element author.") {
            @Override
            public String getValue() {
                return getB2MMLProperty("author");
            }
        });

        metadataSet.put(new PropertySupport.ReadOnly<String>("id", String.class, "ID", "B2MML ID") {
            @Override
            public String getValue() {
                return equipmentID;
            }
        });

        metadataSet.put(new PropertySupport.ReadWrite<String>("icon", String.class, "Icon", "Path to the icon") {
            @Override
            public String getValue() {
                return getB2MMLProperty("icon");
            }

            @Override
            public void setValue(String val) {
                setB2MMLProperty("icon", val);
            }
        });

        metadataSet.put(new PropertySupport.ReadWrite<String>("description", String.class, "Description", "Element description.") {
            @Override
            public String getValue() throws IllegalAccessException, InvocationTargetException {
                return "";
            }

            @Override
            public void setValue(String t) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {

            }

        });

        metadataSet.put(new PropertySupport.ReadOnly<String>("version", String.class, "Version", "Equipment version.") {
            @Override
            public String getValue() throws IllegalAccessException, InvocationTargetException {
                return getB2MMLProperty("version");
            }

        });

        metadataSet.put(new PropertySupport.ReadOnly<String>("level", String.class, "Level", "ISA-88 Level") {
            @Override
            public String getValue() {
                return equipmentLevel;
            }
        });

        connectionSet.put(new PropertySupport.ReadWrite<String>("plc4xAddress", String.class, "PLC4X Address", "Address of the real tag") {
            @Override
            public String getValue() {
                return getB2MMLProperty("plc4xAddress");
            }

            @Override
            public void setValue(String val) {
                setB2MMLProperty("plc4xAddress", val);
            }
        });

        connectionSet.put(new PropertySupport.ReadWrite<String>("driver", String.class, "Driver", "Communication driver.") {
            @Override
            public String getValue() {
                return getB2MMLProperty("commDriver");
            }

            @Override
            public void setValue(String val) {
                setB2MMLProperty("commDriver", val);
            }
        });

        connectionSet.put(new PropertySupport.ReadWrite<String>("pollingRate", String.class, "Polling Interval", "Update interval.") {
            @Override
            public String getValue() {
                return getB2MMLProperty("pollingRate");
            }

            @Override
            public void setValue(String val) {
                setB2MMLProperty("pollingRate", val);
            }
        });

        sheet.put(metadataSet);
        sheet.put(connectionSet);
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
        if ("id".equalsIgnoreCase(name)) {
            updateEquipmentID(value);
            return;
        }
        if (model != null) {
            try {
                EquipmentType target = model.getElementByID(equipmentID);
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

                    model.save();
                    updateEquipment(target);
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private void updateEquipmentID(String newID) {
        if (model != null) {
            try {
                EquipmentType target = model.getElementByID(equipmentID);
                if (target != null) {

                    if (model.getElementByID(newID) != null) {
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_DuplicateID(newID), NotifyDescriptor.ERROR_MESSAGE));
                        return;
                    }

                    if (newID == null || newID.trim().isEmpty()) {
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(Bundle.ERR_EmptyID(), NotifyDescriptor.ERROR_MESSAGE));
                        return;
                    }

                    target.getID().setStringValue(newID);
                    model.save();

                    // Update internal ID and refresh UI
                    this.equipmentID = newID;
                    updateEquipment(target);
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private static class PlantElementChildrenFactory extends ChildFactory<String> implements ChangeListener {

        private final Project project;
        private final Plc4xPlantModel model;
        private EquipmentType parent;

        public PlantElementChildrenFactory(Project project, EquipmentType parent) {
            this.project = project;
            this.model = project.getLookup().lookup(Plc4xPlantModel.class);
            this.parent = parent;
            if (model != null) {
                model.addChangeListener(this);
            }
        }

        public void updateParent(EquipmentType newParent) {
            this.parent = newParent;
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            for (EquipmentType child : parent.getEquipmentChildList()) {
                list.add(child.getID().getStringValue());
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            if (model != null) {
                EquipmentType et = model.getElementByID(key);
                if (et != null) {
                    return PlantNodeFactoryUtil.createNode(project, et);
                }
            }
            return null;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            refresh(true);
        }
    }

    @Override
    public boolean canRename() {
        return true;
    }

    @Override
    public void setName(String newName) {
        updateEquipmentID(newName);
        super.setName(newName);
    }
}
