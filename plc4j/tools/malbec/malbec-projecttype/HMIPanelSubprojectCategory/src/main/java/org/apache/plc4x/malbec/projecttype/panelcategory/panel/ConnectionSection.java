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

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceDynamicPanelBuilder;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.UrlDisassembler;

final class ConnectionSection {

    private final JLabel lblHost = new JLabel("Host/IP:");
    private final JLabel lblPort = new JLabel("Port:");
    private final JTextField txtHost = new JTextField();
    private final JComboBox<String> cbTransport = new JComboBox<>();
    private final JTextField txtPort = new JTextField();
    private final JComboBox<String> cbParameter = new JComboBox<>();
    private final JTextField txtParamValue = new JTextField();
    private final JTextField txtUrlPreview = new JTextField();

    private DeviceDynamicPanelBuilder activeBuilder;

    ConnectionSection() {
        txtUrlPreview.setEditable(false);
        txtParamValue.setEnabled(false);
        cbTransport.setEditable(false);
        lblPort.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
    }

    void reset() {
        setBuilder(null);
        txtHost.setText("");
        lblHost.setText("Host/IP:");
        cbTransport.removeAllItems();
        txtPort.setText("");
        lblPort.setEnabled(true);
        txtPort.setEnabled(true);
        cbParameter.removeAllItems();
        txtParamValue.setText("");
        txtParamValue.setEnabled(false);
        txtUrlPreview.setText("");
    }

    String hostLabel() {
        return lblHost.getText().replace(":", "");
    }

    String host() {
        return txtHost.getText().trim();
    }

    String port() {
        return txtPort.getText().trim();
    }

    void conectarListeners() {
        cbParameter.addActionListener(e -> {
            String key = (String) cbParameter.getSelectedItem();
            if (key == null) {
                txtParamValue.setEnabled(false);
                txtParamValue.setText("");
                return;
            }
            txtParamValue.setEnabled(true);
            txtParamValue.setText(activeBuilder == null
                    ? "" : activeBuilder.getParameterDefaults().get(key));
        });

        txtHost.getDocument().addDocumentListener(
                new SimpleDocumentListener(this::refreshUrlPreview));

        cbTransport.addActionListener(e -> onTransportChanged());
        JComponent transportEditor = (JComponent) cbTransport.getEditor().getEditorComponent();
        if (transportEditor instanceof JTextField transportField) {
            transportField.getDocument().addDocumentListener(
                    new SimpleDocumentListener(this::refreshUrlPreview));
        }

        txtPort.getDocument().addDocumentListener(
                new SimpleDocumentListener(this::refreshUrlPreview));
    }

    private static final class SimpleDocumentListener
            implements javax.swing.event.DocumentListener {

        private final Runnable action;

        SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }

    JLabel lblHost() {
        return lblHost;
    }

    JLabel lblPort() {
        return lblPort;
    }

    JTextField txtHost() {
        return txtHost;
    }

    JComboBox<String> cbTransport() {
        return cbTransport;
    }

    JTextField txtPort() {
        return txtPort;
    }

    JComboBox<String> cbParameter() {
        return cbParameter;
    }

    JTextField txtParamValue() {
        return txtParamValue;
    }

    JTextField txtUrlPreview() {
        return txtUrlPreview;
    }

    JPanel parameterButtons() {
        JButton btnAddParam = new JButton("Add");
        JButton btnRemoveParam = new JButton("Remove");
        JButton btnClear = new JButton("Clean");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        buttons.add(btnAddParam);
        buttons.add(btnRemoveParam);
        buttons.add(btnClear);

        btnAddParam.addActionListener(e -> {
            if (activeBuilder == null) {
                return;
            }
            String key = (String) cbParameter.getSelectedItem();
            if (key == null) {
                return;
            }
            activeBuilder.addParameter(key, txtParamValue.getText().trim());
            refreshUrlPreview();
        });
        btnRemoveParam.addActionListener(e -> {
            if (activeBuilder != null) {
                activeBuilder.removeLastParameter();
            }
            refreshUrlPreview();
        });
        btnClear.addActionListener(e -> {
            if (activeBuilder != null) {
                activeBuilder.clearParameters();
            }
            refreshUrlPreview();
        });
        return buttons;
    }

    void loadBuilderParams(DeviceModel modelo) {
        cbParameter.removeAllItems();
        txtParamValue.setEnabled(false);
        txtParamValue.setText("");
        if (activeBuilder == null) {
            refreshUrlPreview();
            return;
        }
        activeBuilder.initForModel(modelo);

        cbTransport.removeAllItems();
        cbTransport.setEditable(false);
        for (String option : activeBuilder.getTransportOptions()) {
            cbTransport.addItem(option);
        }
        selectTransport(activeBuilder.getDefaultTransport());
        if (cbTransport.getSelectedItem() == null && cbTransport.getItemCount() > 0) {
            cbTransport.setSelectedIndex(0);
        }
        onTransportChanged();
    }

    private void selectTransport(String transporte) {
        if (transporte == null || transporte.isBlank()) {
            return;
        }
        for (int i = 0; i < cbTransport.getItemCount(); i++) {
            if (transporte.equals(cbTransport.getItemAt(i))) {
                cbTransport.setSelectedIndex(i);
                return;
            }
        }
        cbTransport.addItem(transporte);
        cbTransport.setSelectedItem(transporte);
    }

    void setBuilder(DeviceDynamicPanelBuilder activeBuilder) {
        this.activeBuilder = activeBuilder;
    }

    DeviceDynamicPanelBuilder builder() {
        return activeBuilder;
    }

    boolean transporteEditable() {
        return cbTransport.isEditable();
    }

    String transporteActual() {
        if (cbTransport.isEditable()) {
            Component editor = cbTransport.getEditor().getEditorComponent();
            if (editor instanceof JTextField campo) {
                String tipeado = campo.getText().trim();
                if (!tipeado.isEmpty()) {
                    return tipeado;
                }
            }
        }
        Object selected = cbTransport.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    void onTransportChanged() {
        if (activeBuilder == null) {
            return;
        }
        String transportValue = transporteActual();
        activeBuilder.setTransport(transportValue);

        lblHost.setText(activeBuilder.getHostLabel());
        boolean portApplicable = activeBuilder.isPortApplicable();
        lblPort.setEnabled(portApplicable);
        txtPort.setEnabled(portApplicable);
        if (!portApplicable) {
            txtPort.setText("");
        }

        refreshParameterCatalog(transportValue);
        refreshUrlPreview();
    }

    private void refreshParameterCatalog(String transport) {
        if (activeBuilder == null) {
            cbParameter.removeAllItems();
            txtParamValue.setEnabled(false);
            txtParamValue.setText("");
            return;
        }
        LinkedHashMap<String, String> catalog = activeBuilder.getParameterDefaults(transport);
        activeBuilder.retainParameters(catalog.keySet());

        String selectedKey = (String) cbParameter.getSelectedItem();
        cbParameter.removeAllItems();
        for (String key : catalog.keySet()) {
            cbParameter.addItem(key);
        }
        if (selectedKey != null && catalog.containsKey(selectedKey)) {
            cbParameter.setSelectedItem(selectedKey);
        }
        Object current = cbParameter.getSelectedItem();
        if (current == null) {
            txtParamValue.setEnabled(false);
            txtParamValue.setText("");
        } else {
            txtParamValue.setEnabled(true);
            txtParamValue.setText(activeBuilder.getParameters()
                    .getOrDefault(current.toString(), catalog.getOrDefault(current.toString(), "")));
        }
    }

    void refreshUrlPreview() {
        if (activeBuilder == null) {
            txtUrlPreview.setText("");
            return;
        }
        activeBuilder.setHost(txtHost.getText().trim());
        activeBuilder.setTransport(transporteActual());
        activeBuilder.setPort(txtPort.getText().trim());
        txtUrlPreview.setText(activeBuilder.getSpecificParametersAsString());
    }

    void loadConnection(String url, java.awt.Component padre) {
        if (activeBuilder == null || url == null || url.isBlank()) {
            return;
        }
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble(url);
        if (partes == null) {
            txtUrlPreview.setText(url.trim());
            JOptionPane.showMessageDialog(padre,
                    "No se pudo interpretar la URL de conexión guardada:\n\n" + url.trim()
                    + "\n\nRevisá host, puerto y parámetros antes de guardar.",
                    "Conexión a revisar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        selectTransport(partes.transport());
        onTransportChanged();
        txtHost.setText(partes.host());
        txtPort.setText(partes.port());
        activeBuilder.clearParameters();
        partes.params().forEach(activeBuilder::addParameter);
        refreshUrlPreview();
    }

    void clearConnection() {
        txtHost.setText("");
        txtPort.setText("");
        if (activeBuilder != null) {
            activeBuilder.clearParameters();
        }
        cbTransport.removeAllItems();
        cbParameter.removeAllItems();
        txtParamValue.setEnabled(false);
        txtParamValue.setText("");
        txtUrlPreview.setText("");
    }

    String urlActual() {
        refreshUrlPreview();
        return txtUrlPreview.getText();
    }

}
