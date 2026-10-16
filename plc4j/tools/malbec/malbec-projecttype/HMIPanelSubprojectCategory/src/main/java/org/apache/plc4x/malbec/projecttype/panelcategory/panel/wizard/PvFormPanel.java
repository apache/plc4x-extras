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

import java.awt.GridBagConstraints;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatter;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.MemoryTag;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PlantVariable;

final class PvFormPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JComboBox<CommConfigData.ItemConfig> cbArea = new JComboBox<>();
    private final JComboBox<PlantVariable> cbVariable = new JComboBox<>();
    private final JComboBox<DataType> cbType = new JComboBox<>();
    private final JLabel lblTypeLock = new JLabel(" ");
    private final JLabel lblFormTitle = WizardUi.formTitle("Nueva variable de proceso");
    private final JTextField txtDescriptor =
            WizardUi.limitedText(14, WizardUi.MAX_DESCRIPCION);
    private final JComboBox<String> cbScanTime = new JComboBox<>();
    private final JCheckBox chkScanEnable = new JCheckBox("ScanEnable", true);
    private final JCheckBox chkWriteEnable = new JCheckBox("WriteEnable", false);
    private final JSpinner spDisplayLimitLow = limitSpinner();
    private final JSpinner spDisplayLimitHigh = limitSpinner();
    private final JTextField txtDisplayDescription =
            WizardUi.limitedText(14, WizardUi.MAX_DESCRIPCION);
    private final JTextField txtDisplayFormat = new JTextField(8);
    private final JTextField txtDisplayUnits = new JTextField(8);
    private final JSpinner spControlLimitLow = limitSpinner();
    private final JSpinner spControlLimitHigh = limitSpinner();
    private final JSpinner spControlMinStep = limitSpinner();
    private final JButton btnAddPv = new JButton("Añadir");
    private final JButton btnUpdatePv = new JButton("Modificar");
    private final JButton btnDeletePv = new JButton("Eliminar");
    private final JButton btnCancelPv = new JButton("Cancelar");

    private boolean populating;
    private boolean loadingEdit;

    private Runnable onAreaChanged = () -> { };
    private Runnable onVariableSelected = () -> { };

    PvFormPanel() {
        WizardUi.fixComboWidth(cbVariable, 340);
        WizardUi.fixComboWidth(cbArea, 300);
        for (int ms = PvScanTime.MIN; ms <= PvScanTime.MAX; ms += PvScanTime.PASO) {
            cbScanTime.addItem(String.valueOf(ms));
        }
        cbScanTime.setEditable(false);
        cbScanTime.setSelectedIndex(-1);
        cbScanTime.setMaximumRowCount(PvScanTime.options().height);
        WizardUi.fixComboWidth(cbScanTime, 110);
        cbType.setSelectedIndex(-1);

        cbVariable.addActionListener(e -> {
            if (!loadingEdit) {
                onVariableSelected.run();
            }
        });
        cbArea.addActionListener(e -> {
            if (!populating) {
                onAreaChanged.run();
            }
        });

        setLayout(new java.awt.BorderLayout());
        add(buildForm(), java.awt.BorderLayout.CENTER);
    }

    private JSpinner limitSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                PvLimites.INICIAL, PvLimites.MIN, PvLimites.MAX, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        ((DefaultFormatter) ((JFormattedTextField) editor.getTextField())
                .getFormatter()).setAllowsInvalid(false);
        spinner.setEditor(editor);
        return spinner;
    }

    private JPanel buildForm() {
        JPanel form = WizardUi.formGrid();
        GridBagConstraints g = WizardUi.formConstraintsTight();

        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 4;
        form.add(WizardUi.stepTitle("Paso 3 de 3 — Variable de proceso"), g);
        g.gridwidth = 1;

        int row = 0;
        WizardUi.addLabel(form, g, ++row, "Área (PvId):");
        g.gridx = 1;
        g.gridwidth = 3;
        form.add(cbArea, g);
        g.gridwidth = 1;

        WizardUi.addLabel(form, g, ++row, "Variable (del área S88):");
        g.gridx = 1;
        g.gridwidth = 3;
        form.add(cbVariable, g);
        g.gridwidth = 1;

        g.gridx = 0;
        g.gridy = ++row;
        g.gridwidth = 4;
        g.weightx = 1.0;
        form.add(lblTypeLock, g);
        g.gridwidth = 1;

        WizardUi.addLabel(form, g, ++row, "Tipo:");
        g.gridx = 1;
        form.add(cbType, g);

        WizardUi.addLabel(form, g, ++row, "Descriptor:");
        g.gridx = 1;
        form.add(txtDescriptor, g);
        g.gridx = 2;
        form.add(new JLabel("ScanTime:"), g);
        g.gridx = 3;
        form.add(cbScanTime, g);

        WizardUi.addLabel(form, g, ++row, "Opciones:");
        g.gridx = 1;
        form.add(chkScanEnable, g);
        g.gridx = 2;
        form.add(chkWriteEnable, g);

        WizardUi.addLabel(form, g, ++row, "Display Low/High:");
        g.gridx = 1;
        form.add(spDisplayLimitLow, g);
        g.gridx = 2;
        form.add(spDisplayLimitHigh, g);

        WizardUi.addLabel(form, g, ++row, "Display Description:");
        g.gridx = 1;
        g.gridwidth = 3;
        g.weightx = 0.0;
        g.fill = GridBagConstraints.NONE;
        form.add(txtDisplayDescription, g);
        g.gridwidth = 1;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;

        WizardUi.addLabel(form, g, ++row, "Display Format/Units:");
        g.gridx = 1;
        form.add(txtDisplayFormat, g);
        g.gridx = 2;
        form.add(txtDisplayUnits, g);

        WizardUi.addLabel(form, g, ++row, "Control Low/High:");
        g.gridx = 1;
        form.add(spControlLimitLow, g);
        g.gridx = 2;
        form.add(spControlLimitHigh, g);

        WizardUi.addLabel(form, g, ++row, "Control MinStep:");
        g.gridx = 1;
        form.add(spControlMinStep, g);

        g.gridx = 0;
        g.gridy = ++row;
        g.gridwidth = 4;
        g.weightx = 0.0;
        form.add(lblFormTitle, g);
        g.gridwidth = 1;

        WizardUi.addLabel(form, g, ++row, "Acciones:");
        g.gridx = 1;
        g.gridwidth = 3;
        g.weightx = 1.0;
        form.add(WizardUi.buttonRow(btnAddPv, btnUpdatePv, btnDeletePv, btnCancelPv), g);
        g.gridwidth = 1;

        return form;
    }

    void setOnAreaChanged(Runnable r) {
        this.onAreaChanged = r;
    }

    void setOnVariableSelected(Runnable r) {
        this.onVariableSelected = r;
    }

    void withoutTriggeringWarnings(Runnable bloque) {
        populating = true;
        try {
            bloque.run();
        } finally {
            populating = false;
        }
    }

    void withoutCountingAsSelection(Runnable bloque) {
        loadingEdit = true;
        try {
            bloque.run();
        } finally {
            loadingEdit = false;
        }
    }

    // ---- areas y variables -------------------------------------------------

    CommConfigData.ItemConfig selectedArea() {
        return (CommConfigData.ItemConfig) cbArea.getSelectedItem();
    }

    String selectedAreaUuid() {
        CommConfigData.ItemConfig area = selectedArea();
        return area == null ? null : area.getUuid();
    }

    void fillAreas(List<CommConfigData.ItemConfig> areas, CommConfigData.ItemConfig sel) {
        withoutTriggeringWarnings(() -> {
            cbArea.removeAllItems();
            for (CommConfigData.ItemConfig i : areas) {
                cbArea.addItem(i);
            }
            if (sel != null) {
                selectInCombo(cbArea, sel.getUuid());
            }
        });
    }

    void clearVariables() {
        cbVariable.removeAllItems();
        cbVariable.addItem(null);
    }

    void addVariable(PlantVariable v) {
        cbVariable.addItem(v);
    }

    void selectVariableByKey(String clave) {
        for (int i = 0; i < cbVariable.getItemCount(); i++) {
            PlantVariable v = cbVariable.getItemAt(i);
            if (v != null && clave.equals(v.getKey())) {
                cbVariable.setSelectedIndex(i);
                return;
            }
        }
    }

    PlantVariable selectedVariable() {
        return (PlantVariable) cbVariable.getSelectedItem();
    }

    void selectDefaultVariable() {
        cbVariable.setSelectedIndex(0);
    }

    // ---- tipos -------------------------------------------------------------

    void fillTypes(List<DataType> tipos, DataType unicoPermitido) {
        cbType.removeAllItems();
        for (DataType t : tipos) {
            cbType.addItem(t);
        }
        cbType.setSelectedIndex(-1);
        if (unicoPermitido != null) {
            cbType.setSelectedItem(unicoPermitido);
        }
    }

    DataType selectedType() {
        Object sel = cbType.getSelectedItem();
        return sel instanceof DataType t ? t : null;
    }

    void selectType(DataType type) {
        for (int i = 0; i < cbType.getItemCount(); i++) {
            if (cbType.getItemAt(i) == type) {
                cbType.setSelectedIndex(i);
                return;
            }
        }
    }

    void clearType() {
        cbType.setSelectedIndex(-1);
    }

    void installRenderer(java.util.function.Supplier<String> areaUuid,
            java.util.function.BiPredicate<String, DataType> admite) {
        cbType.setRenderer(PvRenderers.typeRenderer(areaUuid, admite));
    }

    void installAreaAndVariableRenderers() {
        cbArea.setRenderer(PvRenderers.areaRenderer());
        cbVariable.setRenderer(PvRenderers.variableRenderer());
    }

    void lockText(String text) {
        lblTypeLock.setText(text);
    }

    // ---- carga y limpieza del formulario -----------------------------------

    void title(String text) {
        lblFormTitle.setText(text);
    }

    void clear() {
        txtDescriptor.setText("");
        cbScanTime.setSelectedIndex(-1);
        chkScanEnable.setSelected(true);
        chkWriteEnable.setSelected(false);
        spDisplayLimitLow.setValue(PvLimites.INICIAL);
        spDisplayLimitHigh.setValue(PvLimites.INICIAL);
        txtDisplayDescription.setText("");
        txtDisplayFormat.setText("");
        txtDisplayUnits.setText("");
        spControlLimitLow.setValue(PvLimites.INICIAL);
        spControlLimitHigh.setValue(PvLimites.INICIAL);
        spControlMinStep.setValue(PvLimites.INICIAL);
        selectDefaultVariable();
        cbType.setSelectedIndex(-1);
        lblFormTitle.setText("Nueva variable de proceso");
    }

    void load(CommConfigData.PvConfig pv) {
        txtDescriptor.setText(pv.getDescriptorText());
        chkScanEnable.setSelected(pv.isScanEnable());
        chkWriteEnable.setSelected(pv.isWriteEnable());
        lblFormTitle.setText("Modificando variable: " + pv.getName());
    }

    void setAreas(List<CommConfigData.ItemConfig> areas, String uuidBuscado) {
        withoutTriggeringWarnings(() -> {
            cbArea.removeAllItems();
            for (CommConfigData.ItemConfig i : areas) {
                cbArea.addItem(i);
            }
            if (uuidBuscado != null) {
                selectInCombo(cbArea, uuidBuscado);
            }
        });
    }

    String setScanTime(String scanTime) {
        cbScanTime.setSelectedIndex(-1);
        if (PvScanTime.isInOptions(scanTime)) {
            cbScanTime.setSelectedItem(scanTime.trim());
            return null;
        }
        return PvScanTime.warningOutsideOptions(scanTime);
    }

    private static void selectInCombo(JComboBox<?> combo, String uuid) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object o = combo.getItemAt(i);
            if (o instanceof CommConfigData.ItemConfig item && item.getUuid().equals(uuid)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    // ---- estado ------------------------------------------------------------

    void refreshState(boolean hayArea, boolean editando) {
        btnAddPv.setEnabled(hayArea && !editando);
        btnUpdatePv.setEnabled(hayArea && editando);
        btnDeletePv.setEnabled(hayArea && editando);
        btnCancelPv.setEnabled(editando);

        cbArea.setEnabled(hayArea && !editando);
        cbVariable.setEnabled(hayArea && !editando);
        cbType.setEnabled(hayArea && !editando);

        txtDescriptor.setEnabled(hayArea);
        cbScanTime.setEnabled(hayArea);
        chkScanEnable.setEnabled(hayArea);
        chkWriteEnable.setEnabled(hayArea);
        spDisplayLimitLow.setEnabled(hayArea);
        spDisplayLimitHigh.setEnabled(hayArea);
        txtDisplayDescription.setEnabled(hayArea);
        txtDisplayFormat.setEnabled(hayArea);
        txtDisplayUnits.setEnabled(hayArea);
        spControlLimitLow.setEnabled(hayArea);
        spControlLimitHigh.setEnabled(hayArea);
        spControlMinStep.setEnabled(hayArea);
    }

    // ---- campos sueltos ----------------------------------------------------

    JButton btnAddPv() {
        return btnAddPv;
    }

    JButton btnUpdatePv() {
        return btnUpdatePv;
    }

    JButton btnDeletePv() {
        return btnDeletePv;
    }

    JButton btnCancelPv() {
        return btnCancelPv;
    }

    JComboBox<String> comboScanTime() {
        return cbScanTime;
    }

    JTextField txtDescriptor() {
        return txtDescriptor;
    }

    JTextField txtDisplayDescription() {
        return txtDisplayDescription;
    }

    JTextField txtDisplayFormat() {
        return txtDisplayFormat;
    }

    JTextField txtDisplayUnits() {
        return txtDisplayUnits;
    }

    JSpinner spDisplayLimitLow() {
        return spDisplayLimitLow;
    }

    JSpinner spDisplayLimitHigh() {
        return spDisplayLimitHigh;
    }

    JSpinner spControlLimitLow() {
        return spControlLimitLow;
    }

    JSpinner spControlLimitHigh() {
        return spControlLimitHigh;
    }

    JSpinner spControlMinStep() {
        return spControlMinStep;
    }

    JCheckBox chkScanEnable() {
        return chkScanEnable;
    }

    JCheckBox chkWriteEnable() {
        return chkWriteEnable;
    }

    String descriptorText() {
        return txtDescriptor.getText().trim();
    }

    String displayDescriptionText() {
        return txtDisplayDescription.getText().trim();
    }

    String displayFormatText() {
        return txtDisplayFormat.getText().trim();
    }

    String displayUnitsText() {
        return txtDisplayUnits.getText().trim();
    }

    static String limitFrom(JSpinner spinner) {
        return String.valueOf(((Number) spinner.getValue()).intValue());
    }

    void selectPlantType(PlantVariable v, List<DataType> permitidos, MemoryTag tag) {
        DataType exacto = DataType.find(v.getType());
        if (exacto != null && permitidos.contains(exacto)) {
            selectType(exacto);
            return;
        }
        if (tag != null) {
            for (DataType t : DataType.candidates(v.getType(), tag.codeBits())) {
                if (permitidos.contains(t)) {
                    selectType(t);
                    return;
                }
            }
        }
        selectType(permitidos.isEmpty() ? null : permitidos.get(0));
    }
}
