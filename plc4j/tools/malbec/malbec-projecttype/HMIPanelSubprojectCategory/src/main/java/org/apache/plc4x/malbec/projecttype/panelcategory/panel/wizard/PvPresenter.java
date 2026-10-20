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

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PlantVariable;


final class PvPresenter {

    private static final Logger LOGGER = Logger.getLogger(PvPresenter.class.getName());

    private final CommunicationWizardController controller;
    private final PlantModelReader plantModelReader;
    private final PvFormPanel form;
    private final PvTablePanel table;
    private final PvLimites limites = new PvLimites();
    private PvEditor editor;
    private final Component dialog;

    private String editingPvUuid;
    private Supplier<Boolean> beforeSave;

    PvPresenter(CommunicationWizardController controller, PlantModelReader plantModelReader,
            PvFormPanel form, PvTablePanel table, Component dialog) {
        this.controller = controller;
        this.plantModelReader = plantModelReader;
        this.form = form;
        this.table = table;
        this.dialog = dialog;

        form.installAreaAndVariableRenderers();
        form.installRenderer(this::selectedAreaName, this::typeEligible);
        form.setOnAreaChanged(this::refreshVariables);
        form.setOnVariableSelected(this::onVariableSelected);
        form.setOnNameModeChanged(this::onNameModeChanged);
        form.setOnCreativeChanged(this::onCreativeChanged);
        table.setOnSelectionChanged(this::onRowSelected);
        editor = new PvEditor(controller, form, dialog, this);
        form.btnAddPv().addActionListener(e -> editor.addPv());
        form.btnUpdatePv().addActionListener(e -> editor.updatePv());
        form.btnDeletePv().addActionListener(e -> editor.deletePv());
        form.btnCancelPv().addActionListener(e -> exitEdit());
    }

    private String selectedAreaName() {
        CommConfigData.ItemConfig area = form.selectedArea();
        return area == null ? null : area.getName();
    }

    // ---- refresco ----------------------------------------------------------

    void refresh() {
        refreshAreas();
        refreshVariables();
        refreshTable();
        refreshState();
    }

    private List<CommConfigData.ItemConfig> areasWithGroup() {
        List<CommConfigData.ItemConfig> areas = new ArrayList<>();
        for (CommConfigData.ItemConfig i : controller.getState().getItems()) {
            if (!controller.getState().groupNameOf(i.getUuid()).isEmpty()) {
                areas.add(i);
            }
        }
        return areas;
    }

    private void refreshAreas() {
        CommConfigData.ItemConfig sel = form.selectedArea();
        form.fillAreas(areasWithGroup(), sel);
    }

    private void refreshVariables() {
        form.clearVariables();
        form.setCreativeAvailable(controller.creativeAvailable(selectedAreaName()));
        refreshTypes();

        if (controller.getState().getSelectedDevice() == null) {
            return;
        }
        String areaId = controller.getState().getSelectedDevice().getS88Node();
        if (areaId == null || areaId.isEmpty()) {
            return;
        }
        List<PlantVariable> variables;
        try {
            variables = plantModelReader.loadAreaVariables(areaId);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudieron cargar las variables de planta", ex);
            return;
        }
        DataType lockedType = controller.lockedType(selectedAreaName());
        Set<String> usedKeys = controller.getUsedPvKeys();
        CommConfigData.PvConfig enEdicion = controller.getState().pvByUuid(editingPvUuid);
        if (enEdicion != null) {
            usedKeys.remove(CommunicationWizardController.pvKey(
                    enEdicion.getS88Path(), enEdicion.getName()));
        }
        for (PlantVariable v : variables) {
            if (!controller.areaAcceptsType(selectedAreaName(), v.getType(), lockedType)) {
                continue;
            }
            if (usedKeys.contains(v.getKey())) {
                v.setUsed(true);
            }
            form.addVariable(v);
        }
    }

    private void refreshTypes() {
        boolean creative = form.isCreative() && controller.creativeAvailable(selectedAreaName());
        List<DataType> tipos = creative
                ? controller.creativeTypesForCombo()
                : controller.typesForCombo(selectedAreaName());
        List<DataType> permitidos = creative
                ? List.of()
                : controller.typesAllowed(selectedAreaName());
        form.fillTypes(tipos, permitidos.size() == 1 ? permitidos.get(0) : null);
        refreshTypeLock();
    }

    private boolean typeEligible(String areaName, DataType type) {
        if (form.isCreative() && controller.creativeAvailable(areaName)) {
            return controller.creativeTypeAllowed(type);
        }
        return controller.typeAllowed(areaName, type);
    }

    private void refreshTypeLock() {
        CommConfigData.ItemConfig area = form.selectedArea();
        if (area == null) {
            form.lockText(" ");
            return;
        }
        if (form.isCreative() && controller.creativeAvailable(area.getName())) {
            form.lockText(PvTexts.creativeLock(area,
                    controller.creativeFreeBytes(area.getName()),
                    controller.areaCapacityInBytes(area.getName())));
            return;
        }
        DataType elegido = form.selectedType();
        form.lockText(PvTexts.typeLock(
                area, elegido,
                controller.freeAreaSlots(area.getName(), elegido),
                controller.freeAreaBytes(area.getName()),
                controller.areaCapacityInBytes(area.getName())));
    }

    private void refreshTable() {
        table.refresh(controller.getState().getPvs());
    }

    private void refreshState() {
        form.refreshState(form.selectedArea() != null, editingPvUuid != null);
    }

    // ---- seleccion ---------------------------------------------------------

    private void onRowSelected() {
        CommConfigData.PvConfig pv = pvFromSelectedRow();
        if (pv == null || pv.getUuid().equals(editingPvUuid)) {
            exitEdit();
            return;
        }
        editingPvUuid = pv.getUuid();
        loadIntoForm(pv);
        refreshState();
    }

    private CommConfigData.PvConfig pvFromSelectedRow() {
        List<CommConfigData.PvConfig> pvs = controller.getState().getPvs();
        int fila = table.selectedRow();
        return fila >= 0 && fila < pvs.size() ? pvs.get(fila) : null;
    }

    private void onNameModeChanged() {
        if (form.isCustomName()) {
            form.clearType();
            refreshTypeLock();
            return;
        }
        onVariableSelected();
    }

    private void onCreativeChanged() {
        form.clearType();
        refreshTypes();
    }

    private void onVariableSelected() {
        PlantVariable v = form.selectedVariable();
        if (v == null) {
            form.clearType();
            refreshTypeLock();
            return;
        }
        if (v.isUsed()) {
            warn("La variable '" + v.getKey() + "' ya está en uso.",
                    "Variable ocupada", JOptionPane.WARNING_MESSAGE);
            form.selectDefaultVariable();
            return;
        }
        List<DataType> permitidos = controller.typesAllowed(selectedAreaName());
        form.selectPlantType(v, permitidos, controller.areaTag(selectedAreaName()));
        refreshTypeLock();
    }

    void loadIntoForm(CommConfigData.PvConfig pv) {
        CommConfigData.ItemConfig area = controller.getState().itemByName(pv.getAreaName());
        form.setAreas(areasWithGroup(), area == null ? null : area.getUuid());
        limites.clear();
        form.withoutCountingAsSelection(() -> {
            form.load(pv);
            form.selectVariableByKey(
                    CommunicationWizardController.pvKey(pv.getS88Path(), pv.getName()));
            form.selectType(DataType.find(pv.getType()));
        });
        String avisoScanTime = form.setScanTime(pv.getScanTime());
        if (avisoScanTime != null) {
            limites.annotate(avisoScanTime);
        }
        form.spDisplayLimitLow().setValue(
                limites.clampToLimit(pv.getDisplayLimitLow(), "Display límite bajo"));
        form.spDisplayLimitHigh().setValue(
                limites.clampToLimit(pv.getDisplayLimitHigh(), "Display límite alto"));
        form.txtDisplayDescription().setText(pv.getDisplayDescription());
        form.txtDisplayFormat().setText(pv.getDisplayFormat());
        form.txtDisplayUnits().setText(pv.getDisplayUnits());
        form.spControlLimitLow().setValue(
                limites.clampToLimit(pv.getControlLimitLow(), "Control límite bajo"));
        form.spControlLimitHigh().setValue(
                limites.clampToLimit(pv.getControlLimitHigh(), "Control límite alto"));
        form.spControlMinStep().setValue(
                limites.clampToLimit(pv.getControlMinStep(), "Control MinStep"));
        refreshVariables();
        refreshTypeLock();
    }

    void exitEdit() {
        editingPvUuid = null;
        table.clearSelection();
        limites.clear();
        form.clear();
        refreshState();
    }

    void exitEditAfterAdd() {
        editingPvUuid = null;
        table.clearSelection();
        limites.clear();
        form.clearForNextAdd();
        refreshState();
    }

    void refreshAfterEdit() {
        refreshVariables();
        refreshTable();
        refreshState();
    }

    // ---- avisos y validacion ----------------------------------------------

    boolean validateRequiredFields() {
        List<String> vacios = PvCamposObligatorios.missingFields(
                form.txtDescriptor(), form.comboScanTime().getSelectedItem(),
                form.txtDisplayDescription(), form.txtDisplayFormat(), form.txtDisplayUnits());
        if (vacios.isEmpty()) {
            return true;
        }
        warn(PvTexts.incompleteFields(vacios),
                "Campos incompletos", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    void warnAdjustments() {
        if (!limites.hasAdjustments()) {
            return;
        }
        warn(PvTexts.adjustmentsOnLoad(limites.adjustments()),
                "Valores corregidos", JOptionPane.WARNING_MESSAGE);
    }

    boolean validateBeforeSave() {
        return beforeSave == null || Boolean.TRUE.equals(beforeSave.get());
    }

    void warn(String text, String title, int type) {
        JOptionPane.showMessageDialog(dialog, text, title, type);
    }

    void setBeforeSave(Supplier<Boolean> beforeSave) {
        this.beforeSave = beforeSave;
    }

    String editingPvUuid() {
        return editingPvUuid;
    }

    List<String> adjustmentsOnLoad() {
        return limites.adjustments();
    }

    String reasonForUnknownOffset(CommConfigData.ItemConfig area) {
        return PvTexts.reasonForUnknownOffset(area);
    }
}
