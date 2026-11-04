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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;

public class StepDevicePanel extends JPanel {

    private final CommunicationWizardController controller;
    private final JComboBox<DeviceConfigData> cbDevice = new JComboBox<>();
    private final JLabel lblDeviceInfo = new JLabel(" ");
    private boolean populating;
    private Runnable onDeviceChanged;

    public StepDevicePanel(CommunicationWizardController controller) {
        this.controller = controller;
        buildUi();
        refreshDevices();
    }

    private void buildUi() {
        cbDevice.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setText(value instanceof DeviceConfigData d
                        ? d.getDeviceName() + "  [" + d.getProtocol() + "]"
                        : " ");
                return label;
            }
        });
        cbDevice.addActionListener(e -> onDeviceSelected());
        WizardUi.fixComboWidth(cbDevice, 340);

        JPanel inner = WizardUi.formGrid();
        GridBagConstraints g = WizardUi.formConstraints();

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        inner.add(WizardUi.stepTitle("Paso 1 de 3 — Dispositivo"), g);

        g.gridy = 1;
        inner.add(new JLabel("Seleccione el dispositivo para la comunicación:"), g);

        g.gridy = 2; g.gridwidth = 2; g.weightx = 1.0;
        inner.add(cbDevice, g);
        g.gridwidth = 1;

        g.gridy = 3; g.gridx = 0; g.gridwidth = 2; g.weightx = 1.0;
        inner.add(lblDeviceInfo, g);

        g.gridy = 4;
        inner.add(new JLabel("<html><i>Si no aparece su dispositivo, créelo con "
                + "«Crear Dispositivo», primero cierre este asistente y vuelva a "
                + "abrirlo.</i></html>"), g);

        setLayout(new BorderLayout(8, 8));
        add(inner, BorderLayout.CENTER);
    }

    public void refresh() {
        refreshDevices();
    }

    private void refreshDevices() {
        DeviceConfigData prev = controller.getState().getSelectedDevice();
        populating = true;
        try {
            cbDevice.removeAllItems();
            for (DeviceConfigData device : controller.getAvailableDevices()) {
                cbDevice.addItem(device);
            }
            if (prev != null) {
                for (int i = 0; i < cbDevice.getItemCount(); i++) {
                    if (cbDevice.getItemAt(i).getUuid().equals(prev.getUuid())) {
                        cbDevice.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            populating = false;
        }
        onDeviceSelected();
    }

    private void onDeviceSelected() {
        if (populating) {
            return;
        }
        DeviceConfigData device = (DeviceConfigData) cbDevice.getSelectedItem();
        if (device == null) {
            lblDeviceInfo.setText(" ");
        } else {
            lblDeviceInfo.setText(device.getBrand() + " / " + device.getModel()
                    + " / " + device.getProtocol());
        }
        controller.selectDevice(device);
        if (onDeviceChanged != null) {
            onDeviceChanged.run();
        }
    }

    public void selectDeviceByUuid(String uuid) {
        if (uuid == null) {
            return;
        }
        for (int i = 0; i < cbDevice.getItemCount(); i++) {
            if (cbDevice.getItemAt(i).getUuid().equals(uuid)) {
                populating = true;
                try {
                    cbDevice.setSelectedIndex(i);
                } finally {
                    populating = false;
                }
                onDeviceSelected();
                return;
            }
        }
    }

    public void setOnDeviceChanged(Runnable onDeviceChanged) {
        this.onDeviceChanged = onDeviceChanged;
    }
}
