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
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.core.RenameElementUseCase;
import org.apache.plc4x.malbec.s88.core.UpdatePropertyUseCase;
import org.apache.plc4x.malbec.s88.plant.actions.CreatePlantElementAction;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
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
 * Node representing an ISA-88 Plant Element.
 */
public class PlantElementNode extends AbstractNode implements ChangeListener {

    protected final Project project;
    protected final Plc4xPlantModel model;
    private String equipmentID;
    private final S88Level equipmentLevel;
    private final PlantElementChildrenFactory factory;
    private final InstanceContent content;
    protected S88Element currentElement;
    
    private final RenameElementUseCase renameUseCase = new RenameElementUseCase();
    private final UpdatePropertyUseCase updatePropertyUseCase = new UpdatePropertyUseCase();

    public PlantElementNode(Project project, S88Element element) {
        this(project, element, new InstanceContent());
    }

    protected PlantElementNode(Project project, S88Element element, InstanceContent content) {
        this(project, element, new PlantElementChildrenFactory(project, element), content);
    }

    private PlantElementNode(Project project, S88Element element, PlantElementChildrenFactory factory, InstanceContent content) {
        super(Children.create(factory, true), new AbstractLookup(content));
        this.project = project;
        this.model = project.getLookup().lookup(Plc4xPlantModel.class);
        this.factory = factory;
        this.content = content;
        this.currentElement = element;
        this.content.add(project);
        this.content.add(currentElement);
        this.content.add(this);

        this.equipmentID = element.getId();
        this.equipmentLevel = element.getLevel();

        if (equipmentLevel != S88Level.NULL) {
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
            S88Element freshEq = model.getElementByID(equipmentID);
            if (freshEq != null) {
                updateElement(freshEq);
            }
        }
    }

    private void updateElement(S88Element freshEq) {
        content.remove(currentElement);
        currentElement = freshEq;
        content.add(currentElement);
        factory.updateParent(currentElement);
        this.equipmentID = freshEq.getId();
        fireDisplayNameChange(null, getDisplayName());
        fireIconChange();
        fireOpenedIconChange();
    }

    @Override
    public String getDisplayName() {
        return equipmentID + (equipmentLevel == S88Level.NULL ? "" : " [" + equipmentLevel + "]");
    }

    @Override
    public Image getIcon(int type) {
        return S88NodeIconUtil.resolveIcon(currentElement.getProperty("icon"), getDefaultIconResource());
    }

    protected String getDefaultIconResource() {
        return "org/apache/plc4x/malbec/s88/plant/nodes/PlantNode.png";
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
        sheet.put(createGeneralSet());
        return sheet;
    }

    protected Sheet.Set createGeneralSet() {
        Sheet.Set set = Sheet.createPropertiesSet();
        set.setName("general");
        set.setDisplayName("General");

        set.put(new PropertySupport.ReadOnly<String>("id", String.class, "ID", "B2MML ID") {
            @Override public String getValue() { return equipmentID; }
        });

        set.put(new PropertySupport.ReadOnly<String>("level", String.class, "Level", "ISA-88 Level") {
            @Override public String getValue() { return equipmentLevel.name(); }
        });

        set.put(new PropertySupport.ReadWrite<String>("author", String.class, "Author", "Element author.") {
            @Override public String getValue() { return currentElement.getProperty("author"); }
            @Override public void setValue(String val) { updateProperty("author", val); }
        });

        set.put(new PropertySupport.ReadWrite<String>("icon", String.class, "Icon", "Path to the icon") {
            @Override public String getValue() { return currentElement.getProperty("icon"); }
            @Override public void setValue(String val) { updateProperty("icon", val); }
        });

        return set;
    }

    protected void updateProperty(String key, String value) {
        if (model != null) {
            try {
                updatePropertyUseCase.execute(model.getModel(), currentElement, key, value);
                model.save();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private void updateEquipmentID(String newID) {
        if (model != null) {
            try {
                renameUseCase.execute(model.getModel(), currentElement, newID);
                model.save();
                this.equipmentID = currentElement.getId();
                updateElement(currentElement);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private static class PlantElementChildrenFactory extends ChildFactory<String> implements ChangeListener {

        private final Project project;
        private final Plc4xPlantModel model;
        private S88Element parent;

        public PlantElementChildrenFactory(Project project, S88Element parent) {
            this.project = project;
            this.model = project.getLookup().lookup(Plc4xPlantModel.class);
            this.parent = parent;
            if (model != null) {
                model.addChangeListener(this);
            }
        }

        public void updateParent(S88Element newParent) {
            this.parent = newParent;
            refresh(true);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            for (S88Element child : parent.getChildren()) {
                list.add(child.getId());
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            if (model != null) {
                S88Element et = model.getElementByID(key);
                if (et != null) {
                    try {
                        return PlantNodeFactoryUtil.createNode(project, et);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
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
