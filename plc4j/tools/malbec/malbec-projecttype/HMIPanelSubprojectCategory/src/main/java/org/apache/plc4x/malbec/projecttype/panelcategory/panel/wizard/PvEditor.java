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
import java.util.UUID;
import javax.swing.JOptionPane;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PlantVariable;

final class PvEditor {

    private final CommunicationWizardController controller;
    private final PvFormPanel form;
    private final Component dialog;
    private final PvPresenter presenter;

    PvEditor(CommunicationWizardController controller, PvFormPanel form,
            Component dialog, PvPresenter presenter) {
        this.controller = controller;
        this.form = form;
        this.dialog = dialog;
        this.presenter = presenter;
    }

    void addPv() {
        if (!presenter.validateRequiredFields()) {
            return;
        }
        if (!presenter.validateBeforeSave()) {
            return;
        }
        CommConfigData.ItemConfig area = form.selectedArea();
        if (area == null) {
            presenter.warn("Seleccione un Area de memoria (PvId) para la variable.",
                    "Falta Area", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean custom = form.isCustomName();
        String baseName;
        String s88Path;
        if (custom) {
            baseName = form.customName();
            if (baseName.isEmpty()) {
                presenter.warn("Escriba un nombre para la variable personalizada.",
                        "Falta nombre", JOptionPane.WARNING_MESSAGE);
                return;
            }
            s88Path = "";
        } else {
            PlantVariable variable = form.selectedVariable();
            if (variable == null) {
                presenter.warn("Seleccione la variable de proceso del Area S88.",
                        "Falta variable", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (variable.isUsed()) {
                presenter.warn("La variable '" + variable.getKey()
                        + "' ya esta en uso y no puede configurarse de nuevo.",
                        "Variable ocupada", JOptionPane.WARNING_MESSAGE);
                return;
            }
            baseName = variable.getName();
            s88Path = variable.getPath();
        }
        boolean creativo = custom && form.isCreative()
                && controller.creativeAvailable(area.getName());
        DataType type = form.selectedType();
        if (creativo ? !controller.creativeTypeAllowed(type)
                : !controller.typeAllowed(area.getName(), type)) {
            presenter.warn(PvTexts.unsupportedType(), "Falta tipo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int quantity = custom ? form.quantity() : 1;
        int libres = creativo
                ? controller.creativeSlots(area.getName(), type)
                : controller.freeAreaSlots(area.getName(), type);
        int bytesLibres = creativo
                ? controller.creativeFreeBytes(area.getName())
                : controller.freeAreaBytes(area.getName());
        if (bytesLibres < 0) {
            presenter.warn(PvTexts.reasonForUnknownOffset(area),
                    "Offset no calculable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (quantity > 1 && libres >= 0 && quantity > libres) {
            presenter.warn(creativo
                    ? PvTexts.creativeNoFit(area, type, libres, quantity, bytesLibres)
                    : "En el Area '" + area.getName() + "' solo caben " + libres
                            + " variable(s) mas de tipo " + type.label()
                            + ", y se han pedido " + quantity + ".\n\n"
                            + "Quedan " + bytesLibres + " bytes libres en el Area.\n\n"
                            + "Baje la cantidad a " + libres + " o menos.",
                    "No cabe tanta cantidad", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String offset = creativo
                ? controller.creativeNextOffset(area.getName(), type)
                : controller.nextOffset(area.getName(), type);
        if (offset == null) {
            presenter.warn(creativo
                    ? PvTexts.creativeDoesNotFit(area, type,
                            controller.areaCapacityInBytes(area.getName()), bytesLibres)
                    : PvTexts.doesNotFitInArea(area, type,
                            controller.areaCapacityInBytes(area.getName()), bytesLibres),
                    "Se ha llegado al li­mite del Area", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!creativo && libres == 0) {
            presenter.warn(PvTexts.areaIsFull(area, type,
                    controller.areaCapacityInBytes(area.getName())),
                    "Area de memoria llena", JOptionPane.WARNING_MESSAGE);
            return;
        }
        presenter.warnAdjustments();
        for (int i = 0; i < quantity; i++) {
            String name = batchName(baseName, i, quantity);
            String slotOffset = i == 0 ? offset
                    : (creativo
                            ? controller.creativeNextOffset(area.getName(), type)
                            : controller.nextOffset(area.getName(), type));
            if (slotOffset == null) {
                break;
            }
            controller.addPv(createPv(name, s88Path, type, area, slotOffset));
        }
        if (creativo) {
            presenter.exitEditAfterAdd();
        } else {
            presenter.exitEdit();
        }
        presenter.refreshAfterEdit();
    }

    static String batchName(String baseName, int index, int quantity) {
        return quantity > 1 ? baseName + "_" + (index + 1) : baseName;
    }

    private CommConfigData.PvConfig createPv(String name, String s88Path, DataType type,
            CommConfigData.ItemConfig area, String offset) {
        return new CommConfigData.PvConfig(
                UUID.randomUUID().toString(),
                name,
                type.label(),
                area.getName(),
                offset,
                form.descriptorText(),
                String.valueOf(form.comboScanTime().getSelectedItem()),
                form.chkScanEnable().isSelected(),
                form.chkWriteEnable().isSelected(),
                PvFormPanel.limitFrom(form.spDisplayLimitLow()),
                PvFormPanel.limitFrom(form.spDisplayLimitHigh()),
                form.displayDescriptionText(),
                form.displayFormatText(),
                form.displayUnitsText(),
                PvFormPanel.limitFrom(form.spControlLimitLow()),
                PvFormPanel.limitFrom(form.spControlLimitHigh()),
                PvFormPanel.limitFrom(form.spControlMinStep()),
                CommunicationWizardController.pvMd5(),
                s88Path);
    }

    void updatePv() {
        CommConfigData.PvConfig pv = controller.getState().pvByUuid(presenter.editingPvUuid());
        if (pv == null) {
            presenter.exitEdit();
            return;
        }
        if (!presenter.validateRequiredFields()) {
            return;
        }
        if (!presenter.validateBeforeSave()) {
            return;
        }
        String name = pv.getName();
        if (form.isCustomName()) {
            name = form.customName();
            if (name.isEmpty()) {
                presenter.warn("Escriba un nombre para la variable personalizada.",
                        "Falta nombre", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        presenter.warnAdjustments();
        controller.updatePv(pv.getUuid(),
                name,
                form.descriptorText(),
                String.valueOf(form.comboScanTime().getSelectedItem()),
                form.chkScanEnable().isSelected(),
                form.chkWriteEnable().isSelected(),
                PvFormPanel.limitFrom(form.spDisplayLimitLow()),
                PvFormPanel.limitFrom(form.spDisplayLimitHigh()),
                form.displayDescriptionText(),
                form.displayFormatText(),
                form.displayUnitsText(),
                PvFormPanel.limitFrom(form.spControlLimitLow()),
                PvFormPanel.limitFrom(form.spControlLimitHigh()),
                PvFormPanel.limitFrom(form.spControlMinStep()));
        presenter.exitEdit();
        presenter.refreshAfterEdit();
    }

    void deletePv() {
        CommConfigData.PvConfig pv = controller.getState().pvByUuid(presenter.editingPvUuid());
        if (pv == null) {
            presenter.exitEdit();
            return;
        }
        CommConfigData.ItemConfig area = controller.getState().itemByName(pv.getAreaName());
        if (!presenter.validateBeforeSave()) {
            return;
        }
        int opcion = JOptionPane.showConfirmDialog(dialog,
                "Va a eliminar la variable '" + pv.getName() + "'"
                + (area == null ? "" : " del Area '" + area.getName() + "'") + ".\n\n"
                + "Se quitara de la lista de variables. Los cambios se guardaran"
                + " al pulsar Guardar.\n\n"
                + "¿Desea continuar?",
                "Eliminar variable", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }
        controller.deletePv(pv.getUuid());
        presenter.exitEdit();
        presenter.refreshAfterEdit();
    }
}
