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

/**
 * El nodo de un dispositivo dentro de la carpeta de Comunicacion.
 *
 * <p>Esta clase es solo pegamento con la plataforma: nodo de NetBeans, acciones Swing y hoja
 * de propiedades. Lo que era logica -el nombre que se muestra, el tooltip y el texto de
 * confirmacion de borrado- esta en {@link DeviceNodeLabels}, y el borrado en
 * {@link DeleteDeviceDialog}.
 *
 * <p>El nombre se recalcula en cada refresco y, si ha cambiado, se avisa con
 * {@code firePropertyChange} para que el arbol repinte la etiqueta.
 */
class DeviceNode extends AbstractNode {

    /** Propiedades que hay que avisar cuando cambia el modelo. */
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

    /** Dispositivo vigente, o null si fue borrado. */
    DeviceConfigData device() {
        return list.device(uuid);
    }

    private void setLabelsFrom(DeviceConfigData d) {
        String name = DeviceNodeLabels.nombreDe(d);
        displayNameBefore = name;
        setName(name);
        setDisplayName(name);
        setShortDescription(DeviceNodeLabels.tooltipDe(d));
    }

    /** Relee el dispositivo y avisa de lo que haya cambiado. */
    void refresh() {
        DeviceConfigData d = device();
        if (d == null) {
            return;
        }
        String name = DeviceNodeLabels.nombreDe(d);
        if (!Objects.equals(name, displayNameBefore)) {
            setName(name);
            firePropertyChange(Node.PROP_DISPLAY_NAME, displayNameBefore, name);
            displayNameBefore = name;
        }
        setShortDescription(DeviceNodeLabels.tooltipDe(d));
        for (String property : PROPERTIES) {
            firePropertyChange(property, null, null);
        }
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> allActions = new ArrayList<>();
        allActions.add(new AbstractAction("Modificar Dispositivo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> openEditForm());
            }
        });
        allActions.add(null);
        allActions.add(new AbstractAction("Propiedades") {
            @Override
            public void actionPerformed(ActionEvent e) {
                SystemAction.get(PropertiesAction.class).performAction();
            }
        });
        allActions.add(null);
        allActions.add(new AbstractAction("Eliminar Dispositivo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteDevice();
            }
        });
        return allActions.toArray(new Action[0]);
    }

    private void openEditForm() {
        Frame ventana = DeleteDeviceDialog.ventanaPrincipal();
        try {
            DeviceConfigData actual = device();
            if (actual == null) {
                JOptionPane.showMessageDialog(ventana,
                        DeviceNodeLabels.mensajeDispositivoInexistente(currentName()),
                        "No se puede modificar", JOptionPane.WARNING_MESSAGE);
                return;
            }
            CreateDeviceDialog.forEdit(ventana, project, actual).setVisible(true);
        } catch (RuntimeException ex) {
            Exceptions.printStackTrace(ex);
            JOptionPane.showMessageDialog(ventana,
                    "No se pudo abrir la modificación del dispositivo:\n\n" + ex.getMessage(),
                    "Error al modificar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteDevice() {
        HMICommunicationModel model = project.getLookup().lookup(HMICommunicationModel.class);
        if (model == null) {
            return;
        }
        DeleteDeviceDialog.eliminar(uuid, currentName(), model,
                DeleteDeviceDialog.ventanaPrincipal());
    }

    /** Nombre del dispositivo, o un texto utilizable si viniera vacío. */
    private String currentName() {
        return DeviceNodeLabels.nombreDe(device());
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set props = sheet.get(Sheet.PROPERTIES);
        if (props == null) {
            props = Sheet.createPropertiesSet();
            sheet.put(props);
        }
        // Cada propiedad vuelve a leer el device al consultarse: la ventana de Propiedades
        // muestra el valor que devuelve getValue() cuando se abre o cuando el nodo avisa que
        // cambió.
        props.put(texto("deviceName", "Nombre", "Nombre del dispositivo", DeviceConfigData::getDeviceName));
        props.put(texto("brand", "Marca", "Marca del dispositivo", DeviceConfigData::getBrand));
        props.put(texto("model", "Modelo", "Modelo del dispositivo", DeviceConfigData::getModel));
        props.put(texto("protocol", "Protocolo", "Protocolo de comunicación", DeviceConfigData::getProtocol));
        props.put(texto("specificParameters", "URL",
                "URL / parámetros específicos de conexión", DeviceConfigData::getSpecificParameters));
        props.put(texto("s88Node", "S88 Node", "Nodo S88 (área) asociado al dispositivo",
                DeviceConfigData::getS88Node));
        props.put(texto("s88Uuid", "S88 UUID", "UUID del área S88 asociada", DeviceConfigData::getS88Uuid));
        props.put(texto("deviceKey", "Device Key", "Clave del dispositivo", DeviceConfigData::getDeviceKey));
        props.put(texto("description", "Descripción", "Descripción del dispositivo",
                DeviceConfigData::getDescription));
        props.put(texto("uuid", "UUID", "Identificador único del dispositivo", DeviceConfigData::getUuid));
        props.put(booleano("enable", "Habilitado", "Indica si el dispositivo está habilitado",
                DeviceConfigData::isEnabled));
        return sheet;
    }

    private Property texto(String name, String label, String description,
            Function<DeviceConfigData, String> getter) {
        return new PropertySupport.ReadOnly(name, String.class, label, description) {
            @Override
            public Object getValue() {
                return DeviceNodeLabels.textoDe(device(), getter);
            }
        };
    }

    private Property booleano(String name, String label, String description,
            Function<DeviceConfigData, Boolean> getter) {
        return new PropertySupport.ReadOnly(name, Boolean.class, label, description) {
            @Override
            public Object getValue() {
                return DeviceNodeLabels.booleanoDe(device(), getter);
            }
        };
    }
}