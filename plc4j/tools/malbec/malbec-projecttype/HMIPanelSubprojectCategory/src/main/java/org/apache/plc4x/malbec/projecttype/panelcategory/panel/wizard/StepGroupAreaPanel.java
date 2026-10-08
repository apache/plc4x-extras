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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.MemoryTag;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;

public class StepGroupAreaPanel extends JPanel {
    private final CommunicationWizardController controller;
    private final GroupAreaTables tablas;
    private final JTextField txtGroupName =
            WizardUi.textoLimitado(14, WizardUi.MAX_NOMBRE);
    private final JTextField txtGroupDescription =
            WizardUi.textoLimitado(14, WizardUi.MAX_DESCRIPCION);
    private final JComboBox<String> cbGroupScantime = new JComboBox<>();
    private final JCheckBox chkGroupEnable = new JCheckBox("Enable", true);
    private final JTextField txtItemName =
            WizardUi.textoLimitado(14, WizardUi.MAX_NOMBRE);
    private final JTextField txtItemDescription =
            WizardUi.textoLimitado(14, WizardUi.MAX_DESCRIPCION);
    private final TagField txtItemTag = new TagField(14);
    private final JComboBox<CommConfigData.GroupConfig> cbItemGroup = new JComboBox<>();
    private final JCheckBox chkItemEnable = new JCheckBox("Enable", true);
    private final JButton btnAddGroup = new JButton("Añadir");
    private final JButton btnUpdateGroup = new JButton("Modificar");
    private final JButton btnDeleteGroup = new JButton("Eliminar");
    private final JButton btnCancelGroup = new JButton("Cancelar");
    private final JLabel lblGroupFormTitle = WizardUi.formTitle("Nuevo grupo de escaneo");
    private final JButton btnAddItem = new JButton("Añadir");
    private final JButton btnUpdateItem = new JButton("Modificar");
    private final JButton btnDeleteItem = new JButton("Eliminar");
    private final JButton btnCancelItem = new JButton("Cancelar");
    private final JLabel lblItemFormTitle =
            WizardUi.formTitle("Nueva área de memoria (del dispositivo seleccionado)");
    private String editItemUuid;
    private String editGroupUuid;
    private Runnable onItemsChanged;

    public StepGroupAreaPanel(CommunicationWizardController controller) {
        this.controller = controller;
        this.tablas = new GroupAreaTables(controller);
        tablas.setAlElegirGrupo(this::onGroupRowSelected);
        tablas.setAlElegirItem(this::onItemRowSelected);
        buildUi();
        refresh();
    }

    private void buildUi() {
        for (int ms = 100; ms <= 1000; ms += 100) {
            cbGroupScantime.addItem(String.valueOf(ms));
        }
        cbGroupScantime.setEditable(false);
        cbGroupScantime.setSelectedIndex(-1);

        WizardUi.fixComboWidth(cbItemGroup, 300);
        JPanel forms = WizardUi.formGrid();
        GridBagConstraints g = WizardUi.formConstraints();

        g.gridx = 0; g.gridy = 0; g.gridwidth = 4; g.weightx = 0.0;
        forms.add(WizardUi.stepTitle("Paso 2 de 3 — Grupo de escaneo y área de memoria"), g);

        g.gridy = 1; g.gridwidth = 4;
        forms.add(lblGroupFormTitle, g);

        g.gridy = 2; g.gridwidth = 1;
        g.gridx = 0; g.weightx = 0.0;
        forms.add(new JLabel("Nombre:"), g);
        g.gridx = 1; g.weightx = 0.5;
        forms.add(txtGroupName, g);
        g.gridx = 2; g.weightx = 0.0;
        forms.add(new JLabel("Descripción:"), g);
        g.gridx = 3; g.weightx = 0.5;
        forms.add(txtGroupDescription, g);

        g.gridy = 3;
        g.gridx = 0; g.weightx = 0.0;
        forms.add(new JLabel("Scantime (ms):"), g);
        g.gridx = 1; g.weightx = 0.5;
        forms.add(cbGroupScantime, g);
        g.gridx = 2; g.weightx = 0.0;
        g.fill = GridBagConstraints.NONE;
        forms.add(chkGroupEnable, g);
        g.gridx = 3; g.weightx = 0.0;
        g.anchor = GridBagConstraints.EAST;
        forms.add(WizardUi.buttonRow(
                btnAddGroup, btnUpdateGroup, btnDeleteGroup, btnCancelGroup), g);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        g.gridy = 4; g.gridx = 0; g.gridwidth = 4; g.weightx = 0.0;
        forms.add(lblItemFormTitle, g);

        g.gridy = 5; g.gridwidth = 1;
        g.gridx = 0; g.weightx = 0.0;
        forms.add(new JLabel("Nombre:"), g);
        g.gridx = 1; g.weightx = 0.5;
        forms.add(txtItemName, g);
        g.gridx = 2; g.weightx = 0.0;
        forms.add(new JLabel("Descripción:"), g);
        g.gridx = 3; g.weightx = 0.5;
        forms.add(txtItemDescription, g);

        g.gridy = 6;
        g.gridx = 0; g.weightx = 0.0;
        forms.add(new JLabel("Tag:"), g);
        g.gridx = 1; g.weightx = 0.5;
        forms.add(txtItemTag, g);
        g.gridx = 2; g.weightx = 0.0;
        forms.add(new JLabel("Grupo:"), g);
        g.gridx = 3; g.weightx = 0.5;
        forms.add(cbItemGroup, g);

        g.gridy = 7;
        g.gridx = 1; g.weightx = 0.5;
        forms.add(chkItemEnable, g);
        g.gridx = 3; g.weightx = 0.0;
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.EAST;
        forms.add(WizardUi.buttonRow(
                btnAddItem, btnUpdateItem, btnDeleteItem, btnCancelItem), g);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        cbItemGroup.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setText(value instanceof CommConfigData.GroupConfig grp
                        ? grp.getName() + "  (scan " + grp.getScantime() + " ms)"
                        : " ");
                return label;
            }
        });

        btnAddGroup.addActionListener(e -> addGroup());
        btnUpdateGroup.addActionListener(e -> updateGroup());
        btnDeleteGroup.addActionListener(e -> deleteGroup());
        btnCancelGroup.addActionListener(e -> salirDeEdicionGrupo());

        btnAddItem.addActionListener(e -> addItem());
        btnUpdateItem.addActionListener(e -> updateItem());
        btnDeleteItem.addActionListener(e -> deleteItem());
        btnCancelItem.addActionListener(e -> salirDeEdicionItem());

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(forms, BorderLayout.CENTER);

        JSplitPane areaSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, north, tablas);
        areaSplit.setResizeWeight(0.35);
        areaSplit.setDividerLocation(250);
        areaSplit.setContinuousLayout(true);

        setLayout(new BorderLayout(8, 8));
        add(areaSplit, BorderLayout.CENTER);
    }

    JTable getGroupTable() {
        return tablas.tablaDeGrupos();
    }

    JTable getItemTable() {
        return tablas.tablaDeItems();
    }

    JTextField getGroupName() {
        return txtGroupName;
    }

    JTextField getGroupDescription() {
        return txtGroupDescription;
    }

    JTextField getItemName() {
        return txtItemName;
    }

    JTextField getItemDescription() {
        return txtItemDescription;
    }

    JTextField getItemTag() {
        return txtItemTag;
    }

    public void refresh() {
        tablas.refrescar();
        refreshGroupCombo();
        refreshItemFormState();
        refreshGroupFormState();
        warnMissingGroups();
    }

    private void refreshItemFormState() {
        boolean hayGrupo = !controller.getState().getGroups().isEmpty();
        boolean editando = editItemUuid != null;
        btnAddItem.setEnabled(hayGrupo && !editando);
        btnUpdateItem.setEnabled(hayGrupo && editando);
        btnCancelItem.setEnabled(editando);
        btnDeleteItem.setEnabled(editando);
        cbItemGroup.setEnabled(hayGrupo && !editando);
        txtItemName.setEnabled(hayGrupo && !editando);
        txtItemDescription.setEnabled(hayGrupo);
        txtItemTag.setEnabled(hayGrupo);
        chkItemEnable.setEnabled(hayGrupo);
    }

    private void refreshGroupFormState() {
        boolean editando = editGroupUuid != null;
        btnAddGroup.setEnabled(!editando);
        btnUpdateGroup.setEnabled(editando);
        btnDeleteGroup.setEnabled(editando);
        btnCancelGroup.setEnabled(editando);
        txtGroupName.setEnabled(!editando);
        txtGroupDescription.setEnabled(true);
        cbGroupScantime.setEnabled(!editando);
        chkGroupEnable.setEnabled(true);
    }

    private void refreshGroupCombo() {
        CommConfigData.GroupConfig sel = (CommConfigData.GroupConfig) cbItemGroup.getSelectedItem();
        cbItemGroup.removeAllItems();
        for (CommConfigData.GroupConfig g : controller.getState().getGroups()) {
            cbItemGroup.addItem(g);
        }
        if (sel != null) {
            for (int i = 0; i < cbItemGroup.getItemCount(); i++) {
                if (cbItemGroup.getItemAt(i).getUuid().equals(sel.getUuid())) {
                    cbItemGroup.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (cbItemGroup.getItemCount() > 0) {
            cbItemGroup.setSelectedIndex(0);
        }
    }

    // --- grupos --------------------------------------------------------------

    private void addGroup() {
        String name = txtGroupName.getText().trim();
        String scantime = (String) cbGroupScantime.getSelectedItem();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese el nombre del grupo de escaneo.",
                    "Campo incompleto", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (scantime == null) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione el scantime del grupo (100 ms a 1000 ms).",
                    "Campo incompleto", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.addGroup(UUID.randomUUID().toString(), name,
                txtGroupDescription.getText().trim(), scantime, chkGroupEnable.isSelected());
        limpiarFormularioGrupo();
        refresh();
    }

    private void onGroupRowSelected() {
        int row = tablas.tablaDeGrupos().getSelectedRow();
        if (row < 0 || row >= controller.getState().getGroups().size()) {
            salirDeEdicionGrupo();
            return;
        }
        CommConfigData.GroupConfig group = controller.getState().getGroups().get(row);
        editGroupUuid = group.getUuid();
        txtGroupName.setText(group.getName());
        txtGroupDescription.setText(group.getDescription());
        cbGroupScantime.setSelectedItem(group.getScantime());
        chkGroupEnable.setSelected(group.isEnable());
        lblGroupFormTitle.setText("Modificando grupo: " + group.getName());
        refreshGroupFormState();
    }

    private void salirDeEdicionGrupo() {
        editGroupUuid = null;
        tablas.tablaDeGrupos().clearSelection();
        limpiarFormularioGrupo();
        refreshGroupFormState();
    }

    private void limpiarFormularioGrupo() {
        txtGroupName.setText("");
        txtGroupDescription.setText("");
        cbGroupScantime.setSelectedIndex(-1);
        chkGroupEnable.setSelected(true);
        lblGroupFormTitle.setText("Nuevo grupo de escaneo");
    }

    private void updateGroup() {
        if (controller.getState().groupByUuid(editGroupUuid) == null) {
            salirDeEdicionGrupo();
            return;
        }
        String scantime = (String) cbGroupScantime.getSelectedItem();
        controller.updateGroup(editGroupUuid, txtGroupDescription.getText().trim(),
                scantime, chkGroupEnable.isSelected());
        salirDeEdicionGrupo();
        refresh();
    }

    private void deleteGroup() {
        CommConfigData.GroupConfig group = controller.getState().groupByUuid(editGroupUuid);
        if (group == null) {
            salirDeEdicionGrupo();
            return;
        }
        List<String> areasDelGrupo = controller.deleteGroup(group.getUuid());
        if (!areasDelGrupo.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "El grupo '" + group.getName() + "' tiene " + areasDelGrupo.size()
                            + " área(s) de memoria asociada(s):\n\n"
                            + String.join(", ", areasDelGrupo)
                            + "\n\nUn área siempre debe pertenecer a un grupo, por lo que "
                            + "primero debe reasignar o eliminar esas áreas.",
                    "No se puede eliminar", JOptionPane.WARNING_MESSAGE);
            return;
        }
        salirDeEdicionGrupo();
        refresh();
    }

    // --- items ---------------------------------------------------------------
    String problemaDelTag(String tag, String uuidEnEdicion) {
        if (tag.isEmpty()) {
            return "Indique el tag del área. Por ejemplo: "
                    + "%DB231.DBB0[0..1848]:BOOL, DB1.DBX0.0, %MW66:WORD, MW2 "
                    + "o 40001.\n\n"
                    + "El símbolo % sólo hace falta con direccionamiento absoluto "
                    + "en S7-1200; con tags optimizados, S7 clásico, Modbus u OPC UA "
                    + "no se pone. El rango [0..9] y el tipo son opcionales.\n\n"
                    + "Tipos admitidos: " + DataType.nombres() + ".";
        }
        MemoryTag t = MemoryTag.parse(tag);
        if (t != null && !t.rangoValido()) {
            return "El rango del tag '" + tag + "' va al revés: empieza en "
                    + t.firstByte() + " y acaba en " + t.lastByte() + ".";
        }
        CommConfigData.ItemConfig mismoTag = controller.areaConMismoTag(tag, uuidEnEdicion);
        if (mismoTag != null) {
            return "El tag '" + tag + "' ya lo usa el área '" + mismoTag.getName() + "'.\n\n"
                    + "Es un tag sin dirección, así que no se puede comprobar si se"
                    + " pisan comparando bytes: se toma que son la misma area.\n\n"
                    + "Si de verdad son areas distintas, dótalos de una dirección con"
                    + " rango, del tipo %DB22.DBB0[0..10], y el panel lo comprobará.";
        }
        CommConfigData.ItemConfig choca = controller.areaQueChoca(tag, uuidEnEdicion);
        if (choca != null) {
            MemoryTag otro = MemoryTag.parse(choca.getTag());
                return "El tag '" + tag + "' ocupa los bytes " + t.startByteEffective()
                        + " a " + t.endByteEffective() + " y se pisa con el área '" + choca.getName()
                        + "', que ocupa los bytes " + otro.startByteEffective()
                        + " a " + otro.endByteEffective() + ".";
        }
        return null;
    }

    private void addItem() {
        if (controller.getState().getGroups().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Debe crear al menos un grupo de escaneo antes de agregar un área de memoria.",
                    "Falta grupo de escaneo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CommConfigData.GroupConfig group = (CommConfigData.GroupConfig) cbItemGroup.getSelectedItem();
        if (group == null) {
            JOptionPane.showMessageDialog(this,
                    "Cree primero un grupo de escaneo; el área debe pertenecer a un grupo.",
                    "Falta grupo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = txtItemName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese el nombre del área de memoria.",
                    "Campo incompleto", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String problemaTag = problemaDelTag(txtItemTag.getText().trim(), null);
        if (problemaTag != null) {
            JOptionPane.showMessageDialog(this, problemaTag,
                    "Tag no válido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.addItem(UUID.randomUUID().toString(), name,
                txtItemDescription.getText().trim(), txtItemTag.getText().trim(),
                chkItemEnable.isSelected(), group.getUuid());
        limpiarFormularioItem();
        refresh();
        avisarItemsChanged();
    }
    
    private void onItemRowSelected() {
        int row = tablas.tablaDeItems().getSelectedRow();
        if (row < 0 || row >= controller.getState().getItems().size()) {
            salirDeEdicionItem();
            return;
        }
        CommConfigData.ItemConfig item = controller.getState().getItems().get(row);
        editItemUuid = item.getUuid();
        txtItemName.setText(item.getName());
        txtItemDescription.setText(item.getDescription());
        txtItemTag.setText(item.getTag());
        chkItemEnable.setSelected(item.isEnable());
        CommConfigData.GroupConfig group = controller.getState().groupByUuid(item.getGroupUuid());
        if (group != null) {
            cbItemGroup.setSelectedItem(group);
        }
        lblItemFormTitle.setText("Modificando área: " + item.getName());
        refreshItemFormState();
    }

    private void salirDeEdicionItem() {
        editItemUuid = null;
        tablas.tablaDeItems().clearSelection();
        limpiarFormularioItem();
        refreshItemFormState();
    }

    private void limpiarFormularioItem() {
        txtItemName.setText("");
        txtItemDescription.setText("");
        txtItemTag.setText("");
        chkItemEnable.setSelected(true);
        lblItemFormTitle.setText("Nueva área de memoria (del dispositivo seleccionado)");
    }

    private void updateItem() {
        if (controller.getState().itemByUuid(editItemUuid) == null) {
            salirDeEdicionItem();
            return;
        }
        String problemaTag = problemaDelTag(txtItemTag.getText().trim(), editItemUuid);
        if (problemaTag != null) {
            JOptionPane.showMessageDialog(this, problemaTag,
                    "Tag no válido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.updateItem(editItemUuid, txtItemDescription.getText().trim(),
                txtItemTag.getText().trim(), chkItemEnable.isSelected());
        salirDeEdicionItem();
        refresh();
        avisarItemsChanged();
    }

    private void deleteItem() {
        CommConfigData.ItemConfig item = controller.getState().itemByUuid(editItemUuid);
        if (item == null) {
            salirDeEdicionItem();
            return;
        }
        List<String> variables = new ArrayList<>();
        for (CommConfigData.PvConfig pv : controller.getState().pvsOfArea(item.getUuid())) {
            variables.add(pv.getName());
        }
        if (!variables.isEmpty()) {
            int opcion = JOptionPane.showConfirmDialog(this,
                    "El área '" + item.getName() + "' tiene " + variables.size()
                            + " variable(s) configurada(s):\n\n" + String.join(", ", variables)
                            + "\n\nSi elimina el área, también se eliminan esas variables. "
                            + "¿Desea continuar?",
                    "Eliminar área y variables", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (opcion != JOptionPane.YES_OPTION) {
                return;
            }
        }
        controller.deleteItem(item.getUuid());
        salirDeEdicionItem();
        refresh();
        avisarItemsChanged();
    }

    private void warnMissingGroups() {
        if (controller.getState().isMissingGroupsWarned()) {
            return;
        }
        List<String> sinGrupo = controller.areasSinGrupoNames();
        if (sinGrupo.isEmpty()) {
            return;
        }
        controller.getState().setMissingGroupsWarned(true);
        JOptionPane.showMessageDialog(this,
                "Estas áreas de memoria quedaron guardadas sin grupo de escaneo:\n\n"
                        + String.join(", ", sinGrupo)
                        + "\n\nPor seguridad la columna \"Grupo\" no se puede editar: "
                        + "hay que volver a crear esas áreas eligiendo su grupo.",
                "Áreas sin grupo de escaneo", JOptionPane.WARNING_MESSAGE);
    }

    public boolean hasItems() {
        return !controller.getState().getItems().isEmpty();
    }

    public void onDeviceChanged() {
        salirDeEdicionGrupo();
        salirDeEdicionItem();
        refresh();
    }

    public void setOnItemsChanged(Runnable listener) {
        onItemsChanged = listener;
    }

    private void avisarItemsChanged() {
        if (onItemsChanged != null) {
            onItemsChanged.run();
        }
    }
}
