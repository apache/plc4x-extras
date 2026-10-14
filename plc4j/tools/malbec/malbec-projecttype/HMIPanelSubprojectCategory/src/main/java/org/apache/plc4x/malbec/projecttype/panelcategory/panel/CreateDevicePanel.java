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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.w3c.dom.Node;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationStore;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceDynamicPanelBuilder;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PanelBuilderRegistry;

public final class CreateDevicePanel extends JPanel {

    private static final String SELECCIONE = "-- Seleccione --";
    private static final int ANCHO_CAMPO = 220;
    private static final int ANCHO_CAMPO_CORTO = 100;
    private static final int ANCHO_MINIMO_ETIQUETA = 100;

    private static void anchoMinimoEtiqueta(JLabel label) {
        label.setMinimumSize(new Dimension(ANCHO_MINIMO_ETIQUETA, label.getPreferredSize().height));
        label.setPreferredSize(new Dimension(ANCHO_MINIMO_ETIQUETA, label.getPreferredSize().height));
    }

    private static final java.util.logging.Logger LOGGER
            = java.util.logging.Logger.getLogger(CreateDevicePanel.class.getName());
    private final Path projectDir;
    private final CommunicationStore store;
    private final JComboBox<String> cbMarca
            = new JComboBox<>(new String[]{SELECCIONE, "Siemens", "Allen Bradley", "Modbus"});
    private final JComboBox<DeviceModel> cbModelo = new JComboBox<>();
    private final JTextField txtProtocol = new JTextField();
    private final JTextField txtDeviceName = new JTextField();
    private final JTextField txtDeviceKey = new JTextField();
    private final JTextField txtDescription = new JTextField();
    private final JTextField txtUUID = new JTextField();
    private final JCheckBox chkEnable = new JCheckBox("Enable");
    private final JComboBox<String> cbS88Node = new JComboBox<>();
    private final JTextField txtS88UUID = new JTextField();
    private final ConnectionSection conexion = new ConnectionSection();

    private DeviceDynamicPanelBuilder activeBuilder;
    private final PanelBuilderRegistry registry = PanelBuilderRegistry.porDefecto();
    private final Map<String, List<DeviceModel>> devicesMap = new HashMap<>();
    private final Map<String, String> s88NodesMap = new HashMap<>();
    private final Set<String> usedS88Nodes = new HashSet<>();
    private final transient DeviceConfigData editing;
    private final String modeloAlAbrir;
    private final boolean editando;

    public CreateDevicePanel(Path projectDir, CommunicationStore store) {
        this(projectDir, store, null);
    }

    public CreateDevicePanel(Path projectDir, CommunicationStore store, DeviceConfigData edit) {
        this.projectDir = projectDir;
        this.store = store;
        this.editing = edit;
        this.editando = edit != null;
        this.modeloAlAbrir = edit == null ? null : edit.getModel();
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        initData();
        darAnchoALosCampos();
        buildUi();
        loadS88Nodes();
        if (editando) {
            cargarParaEditar(edit);
        } else {
            resetForm();
        }
    }

    private static JLabel labelCorto(String text, int px) {
        JLabel lbl = new JLabel(text);
        lbl.setPreferredSize(new Dimension(px, lbl.getPreferredSize().height));
        lbl.setMinimumSize(new Dimension(px, lbl.getPreferredSize().height));
        return lbl;
    }

    private static void ancho(JComponent campo, int px) {
        campo.setPreferredSize(new Dimension(px, campo.getPreferredSize().height));
        campo.setMaximumSize(new Dimension(px, campo.getPreferredSize().height));
    }

    private void darAnchoALosCampos() {
        for (JComponent campo : List.of(txtProtocol, txtDeviceName, txtDeviceKey,
                txtDescription, txtUUID, txtS88UUID,
                conexion.txtHost(), conexion.txtParamValue(),
                conexion.txtUrlPreview(), cbMarca, cbModelo, cbS88Node,
                conexion.cbParameter())) {
            ancho(campo, ANCHO_CAMPO);
        }
        for (JComponent campo : List.of(conexion.txtPort(), conexion.cbTransport())) {
            ancho(campo, ANCHO_CAMPO_CORTO);
        }
    }

    private static GridBagConstraints formConstraints() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8);
        g.anchor = GridBagConstraints.WEST;
        g.weightx = 0.0;
        g.fill = GridBagConstraints.NONE;
        return g;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        anchoMinimoEtiqueta(label);
        return label;
    }

    private static JLabel bold(String text, float size) {
        JLabel result = new JLabel(text);
        result.setFont(result.getFont().deriveFont(Font.BOLD, size));
        return result;
    }

    private int addSectionTitle(JPanel form, GridBagConstraints g, int y, String text) {
        g.gridx = 0;
        g.gridy = y;
        g.gridwidth = 4;
        g.weightx = 0.0;
        g.fill = GridBagConstraints.NONE;
        form.add(bold(text, 12f), g);
        g.gridwidth = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        return y + 1;
    }

    private int addWide(JPanel form, GridBagConstraints g, int y, JLabel label, JComponent field) {
        g.gridy = y;
        g.gridwidth = 1;
        g.gridx = 0;
        g.weightx = 0.0;
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        form.add(label, g);

        g.gridx = 1;
        g.gridwidth = 3;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;
        form.add(field, g);

        g.gridwidth = 1;
        return y + 1;
    }

    private int addPair(JPanel form, GridBagConstraints g, int y,
            JLabel label1, JComponent field1, JLabel label2, JComponent field2) {
        g.gridy = y;
        g.gridwidth = 1;
        g.gridx = 0;
        g.weightx = 0.0;
        form.add(label1, g);
        g.gridx = 1;
        g.weightx = 0.0;
        form.add(field1, g);
        g.gridx = 2;
        g.weightx = 0.0;
        form.add(label2, g);
        g.gridx = 3;
        g.weightx = 0.0;
        form.add(field2, g);
        return y + 1;
    }

    private int addSpanning(JPanel form, GridBagConstraints g, int y, JComponent field) {
        g.gridy = y;
        g.gridx = 1;
        g.gridwidth = 3;
        g.weightx = 0.0;
        form.add(field, g);
        g.gridwidth = 1;
        return y + 1;
    }

    private void buildUi() {
        txtProtocol.setEditable(false);
        txtUUID.setEditable(false);
        txtS88UUID.setEditable(false);

        cbS88Node.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel rendered = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof String node && usedS88Nodes.contains(node)) {
                    rendered.setEnabled(false);
                    rendered.setText(node + "  (en uso)");
                } else {
                    rendered.setEnabled(true);
                }
                return rendered;
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = formConstraints();

        int y = 0;
        g.gridx = 0;
        g.gridy = y++;
        g.gridwidth = 4;
        form.add(bold(editando ? "Modificar dispositivo de comunicación"
                : "Nuevo dispositivo de comunicación", 14f), g);
        g.gridwidth = 1;

        y = addSectionTitle(form, g, y, "Marca y modelo");
        y = addWide(form, g, y, label("Marca"), cbMarca);
        y = addWide(form, g, y, label("Modelo"), cbModelo);
        y = addWide(form, g, y, label("Protocol"), txtProtocol);
        y = addSpanning(form, g, y, chkEnable);

        y = addSectionTitle(form, g, y, "Dispositivo");
        y = addWide(form, g, y, label("Name"), txtDeviceName);
        y = addWide(form, g, y, label("Key"), txtDeviceKey);
        y = addWide(form, g, y, label("Description"), txtDescription);
        y = addWide(form, g, y, label("UUID"), txtUUID);

        y = addSectionTitle(form, g, y, "S88 tree");
        y = addWide(form, g, y, label("Node"), cbS88Node);
        y = addWide(form, g, y, label("UUID"), txtS88UUID);

        y = addSectionTitle(form, g, y, "Parámetros de conexión");
        y = addWide(form, g, y, conexion.lblHost(), conexion.txtHost());
//        y = addPair(form, g, y, label("Transport"), conexion.cbTransport(),
//                conexion.lblPort(), conexion.txtPort());
//        y = addPair(form, g, y, label("Parámetro"), conexion.cbParameter(),
//                label("Valor"), conexion.txtParamValue());
        y = addPair(form, g, y, label("Transport"), conexion.cbTransport(),
                labelCorto("Port:", 80), conexion.txtPort());

        y = addPair(form, g, y, label("Parámetro"), conexion.cbParameter(),
                labelCorto("Valor:", 80), conexion.txtParamValue());
        y = addSpanning(form, g, y, conexion.parameterButtons());
        y = addWide(form, g, y, label("URL Preview"), conexion.txtUrlPreview());

        GridBagConstraints gFillerY = new GridBagConstraints();
        gFillerY.gridx = 0;
        gFillerY.gridy = y;
        gFillerY.gridwidth = 4;
        gFillerY.weighty = 1.0;
        form.add(new JPanel(), gFillerY);

        add(form, BorderLayout.CENTER);

        setupBrandAndModelListeners();
        setupNodeListener();
        setupConnectionListeners();
    }

    private JPanel parameterButtons() {
        return conexion.parameterButtons();
    }

    private void setupBrandAndModelListeners() {
        cbMarca.addActionListener(e -> {
            cbModelo.removeAllItems();
            txtProtocol.setText("");
            if (!editando) {
                txtUUID.setText("");
            }
            activeBuilder = null;
            conexion.setBuilder(null);
            conexion.reiniciar();

            String selectedBrand = (String) cbMarca.getSelectedItem();
            if (devicesMap.containsKey(selectedBrand)) {
                for (DeviceModel dm : devicesMap.get(selectedBrand)) {
                    cbModelo.addItem(dm);
                }
                cbModelo.setSelectedIndex(0);
                generarUuidSiCorresponde();
            }
        });

        cbModelo.addActionListener(e -> {
            DeviceModel selectedDevice = (DeviceModel) cbModelo.getSelectedItem();
            if (selectedDevice == null) {
                return;
            }
            txtProtocol.setText(selectedDevice.getProtocol());
            generarUuidSiCorresponde();

            if (editando && !Objects.equals(modeloAlAbrir, selectedDevice.getModel())) {
                limpiarConexion();
            }

            activeBuilder = registry.para(selectedDevice.getBrand());
            conexion.setBuilder(activeBuilder);

            loadBuilderParams();
        });
    }

    private void generarUuidSiCorresponde() {
        if (!editando) {
            txtUUID.setText(UUID.randomUUID().toString());
        }
    }

    private void setupNodeListener() {
        cbS88Node.addActionListener(e -> {
            String selectedNode = (String) cbS88Node.getSelectedItem();
            if (s88NodesMap.containsKey(selectedNode)) {
                if (usedS88Nodes.contains(selectedNode)) {
                    JOptionPane.showMessageDialog(this,
                            "El área '" + selectedNode + "' ya está asignada a otro dispositivo.",
                            "Área en uso", JOptionPane.WARNING_MESSAGE);
                    cbS88Node.setSelectedIndex(0);
                    txtS88UUID.setText("");
                    return;
                }
                txtS88UUID.setText(s88NodesMap.get(selectedNode));
            } else {
                txtS88UUID.setText("");
            }
        });
    }

    private void setupConnectionListeners() {
        conexion.conectarListeners();
    }

    private void initData() {
        for (String marca : DeviceCatalog.marcas()) {
            devicesMap.put(marca, DeviceCatalog.de(marca));
        }
        if (devicesMap.size() != cbMarca.getItemCount() - 1) {
            LOGGER.warning("DeviceCatalog tiene " + devicesMap.size()
                    + " marcas y el desplegable " + (cbMarca.getItemCount() - 1)
                    + "; revisalo antes de tocar cualquiera de los dos.");
        }
    }

    private void loadS88Nodes() {
        cbS88Node.removeAllItems();
        cbS88Node.addItem(SELECCIONE);
        cbS88Node.setEnabled(false);
        txtS88UUID.setText("");

        usedS88Nodes.clear();
        if (store != null) {
            for (DeviceConfigData device : store.getDevices()) {
                String s88Node = device.getS88Node();
                if (s88Node != null && !s88Node.isEmpty()) {
                    if (Objects.equals(device.getUuid(),
                            editing == null ? null : editing.getUuid())) {
                        continue;
                    }
                    usedS88Nodes.add(s88Node);
                }
            }
        }

        Map<String, String> areas = PlantNodeLoader.areasDe(projectDir);
        if (areas == null) {
            return;
        }
        for (Map.Entry<String, String> area : areas.entrySet()) {
            cbS88Node.addItem(area.getKey());
            s88NodesMap.put(area.getKey(), area.getValue());
        }
        cbS88Node.setEnabled(true);
    }

    private void loadBuilderParams() {
        conexion.cargarBuilderParams((DeviceModel) cbModelo.getSelectedItem());
    }

    private String transportActual() {
        return conexion.transporteActual();
    }

    boolean transporteEditable() {
        return conexion.transporteEditable();
    }

    private void onTransportChanged() {
        conexion.alCambiarTransporte();
    }

    /**
     * Delegado: el catalogo de parametros lo lleva la seccion de conexion.
     */
    private void refreshParameterCatalog(String transport) {
        conexion.alCambiarTransporte();
    }

    private void refreshUrlPreview() {
        conexion.refrescarUrlPreview();
    }

    private void cargarParaEditar(DeviceConfigData d) {
        cbMarca.setSelectedItem(d.getBrand());
        for (int i = 0; i < cbModelo.getItemCount(); i++) {
            if (cbModelo.getItemAt(i).getModel().equals(d.getModel())) {
                cbModelo.setSelectedIndex(i);
                break;
            }
        }
        cargarConexion(d.getSpecificParameters());
        restaurarNodoS88(d);
        txtS88UUID.setText(d.getS88Uuid());
        txtUUID.setText(d.getUuid());
        txtDeviceName.setText(d.getDeviceName());
        txtDeviceKey.setText(d.getDeviceKey());
        txtDescription.setText(d.getDescription());
        chkEnable.setSelected(d.isEnabled());

        cbMarca.setEnabled(false);
        cbS88Node.setEnabled(false);
    }

    private void cargarConexion(String url) {
        conexion.cargarConexion(url, this);
    }

    private void restaurarNodoS88(DeviceConfigData d) {
        String node = d.getS88Node();
        if (node == null || node.isBlank()) {
            return;
        }
        boolean yaEsta = false;
        for (int i = 0; i < cbS88Node.getItemCount(); i++) {
            if (node.equals(cbS88Node.getItemAt(i))) {
                yaEsta = true;
                break;
            }
        }
        if (!yaEsta) {
            cbS88Node.addItem(node);
        }
        cbS88Node.setSelectedItem(node);
    }

    private void limpiarConexion() {
        conexion.limpiarConexion();
    }

    public void resetForm() {
        cbMarca.setSelectedIndex(0);
        cbModelo.removeAllItems();
        txtProtocol.setText("");
        txtDeviceName.setText("");
        txtDeviceKey.setText("");
        txtDescription.setText("");
        txtUUID.setText("");
        chkEnable.setSelected(false);
        cbS88Node.setSelectedIndex(0);
        txtS88UUID.setText("");
        activeBuilder = null;
        conexion.reiniciar();
    }

    String nodoS88Seleccionado() {
        Object selected = cbS88Node.getSelectedItem();
        String text = selected == null ? "" : selected.toString().trim();
        return text.isEmpty() || SELECCIONE.equals(text) ? null : text;
    }

    List<String> camposObligatoriosVacios() {
        List<String> faltantes = new ArrayList<>();
        if (txtDeviceName.getText().trim().isEmpty()) {
            faltantes.add("Name");
        }
        if (txtDeviceKey.getText().trim().isEmpty()) {
            faltantes.add("Key");
        }
        if (cbModelo.getSelectedItem() == null) {
            faltantes.add("Marca y modelo");
        }
        if (conexion.host().isEmpty()) {
            faltantes.add(conexion.etiquetaHost());
        }
        if (nodoS88Seleccionado() == null) {
            faltantes.add("Node");
        }
        if (txtUUID.getText().trim().isEmpty()) {
            faltantes.add("UUID");
        }
        return faltantes;
    }

    public DeviceConfigData getDeviceConfigData() {
        List<String> faltantes = camposObligatoriosVacios();
        if (!faltantes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Revisá estos campos antes de guardar:\n\n  • " + String.join("\n  • ", faltantes),
                    "Faltan datos", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String host = conexion.host();
        if (activeBuilder != null) {
            activeBuilder.setHost(host);
            activeBuilder.setTransport(transportActual());
            activeBuilder.setPort(conexion.puerto());
        }

        Properties pDevice = new Properties();
        pDevice.put("brand", cbMarca.getSelectedItem());
        pDevice.put("model", ((DeviceModel) cbModelo.getSelectedItem()).getModel());
        pDevice.put("protocol", txtProtocol.getText());
        pDevice.put("deviceName", txtDeviceName.getText().trim());
        pDevice.put("deviceKey", txtDeviceKey.getText().trim());
        pDevice.put("description", txtDescription.getText());
        pDevice.put("uuid", txtUUID.getText());
        pDevice.put("enable", chkEnable.isSelected());
        pDevice.put("s88Node", nodoS88Seleccionado());
        pDevice.put("s88Uuid", txtS88UUID.getText());
        pDevice.put("specificParameters", activeBuilder == null
                ? "" : activeBuilder.getSpecificParametersAsString());

        return new DeviceConfigData(pDevice);
    }

    void registrarNodoS88(String id, String uuid) {
        cbS88Node.addItem(id);
        s88NodesMap.put(id, uuid);
        cbS88Node.setEnabled(true);
    }

    JComboBox<String> getCbMarca() {
        return cbMarca;
    }

    JComboBox<DeviceModel> getCbModelo() {
        return cbModelo;
    }

    JComboBox<String> getCbS88Node() {
        return cbS88Node;
    }

    JTextField getTxtDeviceName() {
        return txtDeviceName;
    }

    JTextField getTxtDeviceKey() {
        return txtDeviceKey;
    }

    JTextField getTxtHost() {
        return conexion.txtHost();
    }

    JTextField getTxtPort() {
        return conexion.txtPort();
    }

    JTextField getTxtUUID() {
        return txtUUID;
    }

    String urlActual() {
        return conexion.urlActual();
    }

    private static final class SimpleDocumentListener implements DocumentListener {

        private final Runnable action;

        SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            action.run();
        }
    }
}
