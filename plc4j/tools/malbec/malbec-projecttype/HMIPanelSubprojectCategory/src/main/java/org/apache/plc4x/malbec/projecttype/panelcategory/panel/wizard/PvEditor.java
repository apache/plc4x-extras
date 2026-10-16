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
        PlantVariable variable = form.selectedVariable();
        if (area == null) {
            presenter.warn("Seleccione un Ã¡rea de memoria (PvId) para la variable.",
                    "Falta Ã¡rea", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (variable == null) {
            presenter.warn("Seleccione la variable de proceso del Ã¡rea S88.",
                    "Falta variable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (variable.isUsed()) {
            presenter.warn("La variable '" + variable.getKey()
                    + "' ya estÃ¡ en uso y no puede configurarse de nuevo.",
                    "Variable ocupada", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DataType type = form.selectedType();
        if (!controller.typeAllowed(area.getUuid(), type)) {
            presenter.warn(PvTexts.unsupportedType(), "Falta tipo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int libres = controller.freeAreaSlots(area.getUuid(), type);
        int bytesLibres = controller.freeAreaBytes(area.getUuid());
        if (bytesLibres < 0) {
            presenter.warn(PvTexts.reasonForUnknownOffset(area),
                    "Offset no calculable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String offset = controller.nextOffset(area.getUuid(), type);
        if (offset == null) {
            presenter.warn(PvTexts.doesNotFitInArea(area, type,
                    controller.areaCapacityInBytes(area.getUuid()), bytesLibres),
                    "Se ha llegado al lÃ­mite del Ã¡rea", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (libres == 0) {
            presenter.warn(PvTexts.areaIsFull(area, type,
                    controller.areaCapacityInBytes(area.getUuid())),
                    "Ãrea de memoria llena", JOptionPane.WARNING_MESSAGE);
            return;
        }
        presenter.warnAdjustments();
        controller.addPv(createPv(variable, type, area, offset));
        presenter.exitEdit();
        presenter.refreshAndSave("Variable '" + variable.getName() + "' guardada en '"
                + area.getName() + "' con offset " + offset + ".");
    }

    private CommConfigData.PvConfig createPv(PlantVariable variable, DataType type,
            CommConfigData.ItemConfig area, String offset) {
        return new CommConfigData.PvConfig(
                UUID.randomUUID().toString(),
                variable.getName(),
                type.label(),
                area.getUuid(),
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
                variable.getPath());
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
        presenter.warnAdjustments();
        controller.updatePv(pv.getUuid(),
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
        presenter.refreshAndSave("Variable '" + pv.getName() + "' actualizada.");
    }

    void deletePv() {
        CommConfigData.PvConfig pv = controller.getState().pvByUuid(presenter.editingPvUuid());
        if (pv == null) {
            presenter.exitEdit();
            return;
        }
        CommConfigData.ItemConfig area = controller.getState().itemByUuid(pv.getAreaUuid());
        if (!presenter.validateBeforeSave()) {
            return;
        }
        int opcion = JOptionPane.showConfirmDialog(dialog,
                "Va a eliminar la variable '" + pv.getName() + "'"
                + (area == null ? "" : " del Ã¡rea '" + area.getName() + "'") + ".\n\n"
                + "TambiÃ©n se quitarÃ¡ de comunicacion.xml y de boot.db.\n\n"
                + "Â¿Desea continuar?",
                "Eliminar variable", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }
        controller.deletePv(pv.getUuid());
        presenter.exitEdit();
        presenter.refreshAndSave("Variable '" + pv.getName() + "' eliminada.");
    }
}
