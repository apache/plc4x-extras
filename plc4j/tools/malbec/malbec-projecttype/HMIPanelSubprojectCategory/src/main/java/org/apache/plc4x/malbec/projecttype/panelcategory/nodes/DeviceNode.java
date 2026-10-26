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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.panel.CreateDeviceDialog;
import org.netbeans.api.project.Project;
import org.openide.actions.PropertiesAction;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.actions.SystemAction;

class DeviceNode extends AbstractNode {

    private static final List<String> PROPERTIES = List.of(
            "deviceName", "brand", "model", "protocol", "specificParameters",
            "s88Node", "s88Uuid", "deviceKey", "description", "uuid", "enable");

    private final String uuid;
    private final Project project;
    private final CommunicationNodeList list;
    private String displayNameBefore;

    DeviceNode(String uuid, Project project, CommunicationNodeList list) {
        super(Children.LEAF);
        this.uuid = uuid;
        this.project = project;
        this.list = list;
        DeviceConfigData inicial = list.device(uuid);
        if (inicial != null) {
            setLabelsFrom(inicial);
        }
        setIconBaseWithExtension(HMICategoryDefinition.COMUNICATION.getIconPath());
    }

    DeviceConfigData device() {
        return list.device(uuid);
    }

    private void setLabelsFrom(DeviceConfigData d) {
        String name = DeviceNodeLabels.nameOf(d);
        displayNameBefore = name;
        setName(name);
        setDisplayName(name);
        setShortDescription(DeviceNodeLabels.tooltipOf(d));
    }

    void refresh() {
        DeviceConfigData d = device();
        if (d == null) {
            return;
        }
        String name = DeviceNodeLabels.nameOf(d);
        if (!Objects.equals(name, displayNameBefore)) {
            setName(name);
            firePropertyChange(Node.PROP_DISPLAY_NAME, displayNameBefore, name);
            displayNameBefore = name;
        }
        setShortDescription(DeviceNodeLabels.tooltipOf(d));
        for (String property : PROPERTIES) {
            firePropertyChange(property, null, null);
        }
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> allActions = new ArrayList<>();
        allActions.add(new AbstractAction("Modify Device") {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> openEditForm());
            }
        });
        allActions.add(null);
        allActions.add(new AbstractAction("Properties") {
            @Override
            public void actionPerformed(ActionEvent e) {
                SystemAction.get(PropertiesAction.class).performAction();
            }
        });
        allActions.add(null);
        allActions.add(new AbstractAction("Delete Device") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteDevice();
            }
        });
        return allActions.toArray(new Action[0]);
    }

    private void openEditForm() {
        Frame ventana = DeleteDeviceDialog.mainWindow();
        try {
            DeviceConfigData actual = device();
            if (actual == null) {
                JOptionPane.showMessageDialog(ventana,
                        DeviceNodeLabels.unknownDeviceMessage(currentName()),
                        "It cannot be modified", JOptionPane.WARNING_MESSAGE);
                return;
            }
            CreateDeviceDialog.forEdit(ventana, project, actual).setVisible(true);
        } catch (RuntimeException ex) {
            Exceptions.printStackTrace(ex);
            JOptionPane.showMessageDialog(ventana,
                    "Could not open the device modification:\n\n" + ex.getMessage(),
                    "Error while modifying", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteDevice() {
        HMICommunicationModel model = project.getLookup().lookup(HMICommunicationModel.class);
        if (model == null) {
            return;
        }
        DeleteDeviceDialog.delete(uuid, currentName(), model,
                DeleteDeviceDialog.mainWindow());
    }

    private String currentName() {
        return DeviceNodeLabels.nameOf(device());
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set props = sheet.get(Sheet.PROPERTIES);
        if (props == null) {
            props = Sheet.createPropertiesSet();
            sheet.put(props);
        }

        props.put(text("deviceName", "Name", "Device Name", DeviceConfigData::getDeviceName));
        props.put(text("brand", "Brand", "Device Brand", DeviceConfigData::getBrand));
        props.put(text("model", "Model", "Device Model", DeviceConfigData::getModel));
        props.put(text("protocol", "Protocol", "Communication protocol", DeviceConfigData::getProtocol));
        props.put(text("specificParameters", "URL",
                "URL / specific connection parameters", DeviceConfigData::getSpecificParameters));
        props.put(text("s88Node", "S88 Node", "S88 Node (area) associated with the device",
                DeviceConfigData::getS88Node));
        props.put(text("s88Uuid", "S88 UUID", "UUID of the associated S88 area", DeviceConfigData::getS88Uuid));
        props.put(text("deviceKey", "Device Key", "Device Key", DeviceConfigData::getDeviceKey));
        props.put(text("description", "Description", "Device Description",
                DeviceConfigData::getDescription));
        props.put(text("uuid", "UUID", "Unique device identifier", DeviceConfigData::getUuid));
        props.put(booleano("enable", "Enabled", "Indicates whether the device is enabled",
                DeviceConfigData::isEnabled));
        return sheet;
    }

    private Property text(String name, String label, String description,
            Function<DeviceConfigData, String> getter) {
        return new PropertySupport.ReadOnly(name, String.class, label, description) {
            @Override
            public Object getValue() {
                return DeviceNodeLabels.textOf(device(), getter);
            }
        };
    }

    private Property booleano(String name, String label, String description,
            Function<DeviceConfigData, Boolean> getter) {
        return new PropertySupport.ReadOnly(name, Boolean.class, label, description) {
            @Override
            public Object getValue() {
                return DeviceNodeLabels.booleanOf(device(), getter);
            }
        };
    }
}