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

/**
 * Crear, modificar y borrar una variable de proceso.
 *
 * <p>Lo que ocurre al pulsar Anadir, Modificar o Eliminar. Ocupaba mas que todo lo demas
 * junto dentro de {@code StepPvPanel}, porque son tres metodos largos y cada uno lleva
 * una lista de motivos de rechazo.
 *
 * <p>El orden de las comprobaciones no se ha tocado, y es lo importante: campos
 * obligatorios, permiso para escribir, area elegida, variable ocupada, tipo admitido,
 * tamano del area y huecos libres. Ese orden es el que evita escribir un PV con un offset
 * que no existe, y cambiarlo seria cambiar el comportamiento, no ordenar el codigo.
 *
 * <p>Quien decide sigue siendo {@link PvPresenter}; esta clase ejecuta y avisa.
 */
final class PvEditor {

    private final CommunicationWizardController controller;
    private final PvFormPanel form;
    private final Component dialogo;
    private final PvPresenter presenter;

    PvEditor(CommunicationWizardController controller, PvFormPanel form,
            Component dialogo, PvPresenter presenter) {
        this.controller = controller;
        this.form = form;
        this.dialogo = dialogo;
        this.presenter = presenter;
    }

    void agregarPv() {
        if (!presenter.validarCamposObligatorios()) {
            return;
        }
        if (!presenter.validarAntesDeEscribir()) {
            return;
        }
        CommConfigData.ItemConfig area = form.areaSeleccionada();
        PlantVariable variable = form.variableSeleccionada();
        if (area == null) {
            presenter.avisar("Seleccione un Ã¡rea de memoria (PvId) para la variable.",
                    "Falta Ã¡rea", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (variable == null) {
            presenter.avisar("Seleccione la variable de proceso del Ã¡rea S88.",
                    "Falta variable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (variable.isUsed()) {
            presenter.avisar("La variable '" + variable.getKey()
                    + "' ya estÃ¡ en uso y no puede configurarse de nuevo.",
                    "Variable ocupada", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DataType type = form.tipoElegido();
        if (!controller.typeAllowed(area.getUuid(), type)) {
            presenter.avisar(PvTexts.tipoNoAdmitido(), "Falta tipo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int libres = controller.areaFreeSlots(area.getUuid(), type);
        int bytesLibres = controller.areaFreeBytes(area.getUuid());
        if (bytesLibres < 0) {
            presenter.avisar(PvTexts.motivoDeOffsetDesconocido(area),
                    "Offset no calculable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String offset = controller.nextOffset(area.getUuid(), type);
        if (offset == null) {
            presenter.avisar(PvTexts.noCabeEnElArea(area, type,
                    controller.areaCapacityBytes(area.getUuid()), bytesLibres),
                    "Se ha llegado al lÃ­mite del Ã¡rea", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (libres == 0) {
            presenter.avisar(PvTexts.areaLlena(area, type,
                    controller.areaCapacityBytes(area.getUuid())),
                    "Ãrea de memoria llena", JOptionPane.WARNING_MESSAGE);
            return;
        }
        presenter.avisarAjustes();
        controller.addPv(crearPv(variable, type, area, offset));
        presenter.salirDeEdicion();
        presenter.refrescarYPersistir("Variable '" + variable.getName() + "' guardada en '"
                + area.getName() + "' con offset " + offset + ".");
    }

    private CommConfigData.PvConfig crearPv(PlantVariable variable, DataType type,
            CommConfigData.ItemConfig area, String offset) {
        return new CommConfigData.PvConfig(
                UUID.randomUUID().toString(),
                variable.getName(),
                type.label(),
                area.getUuid(),
                offset,
                form.descriptor(),
                String.valueOf(form.comboScanTime().getSelectedItem()),
                form.chkScanEnable().isSelected(),
                form.chkWriteEnable().isSelected(),
                PvFormPanel.limiteDe(form.spDisplayLimitLow()),
                PvFormPanel.limiteDe(form.spDisplayLimitHigh()),
                form.descripcionDisplay(),
                form.formatoDisplay(),
                form.unidadesDisplay(),
                PvFormPanel.limiteDe(form.spControlLimitLow()),
                PvFormPanel.limiteDe(form.spControlLimitHigh()),
                PvFormPanel.limiteDe(form.spControlMinStep()),
                CommunicationWizardController.pvMd5(),
                variable.getPath());
    }

    void modificarPv() {
        CommConfigData.PvConfig pv = controller.getState().pvByUuid(presenter.editPvUuid());
        if (pv == null) {
            presenter.salirDeEdicion();
            return;
        }
        if (!presenter.validarCamposObligatorios()) {
            return;
        }
        if (!presenter.validarAntesDeEscribir()) {
            return;
        }
        presenter.avisarAjustes();
        controller.updatePv(pv.getUuid(),
                form.descriptor(),
                String.valueOf(form.comboScanTime().getSelectedItem()),
                form.chkScanEnable().isSelected(),
                form.chkWriteEnable().isSelected(),
                PvFormPanel.limiteDe(form.spDisplayLimitLow()),
                PvFormPanel.limiteDe(form.spDisplayLimitHigh()),
                form.descripcionDisplay(),
                form.formatoDisplay(),
                form.unidadesDisplay(),
                PvFormPanel.limiteDe(form.spControlLimitLow()),
                PvFormPanel.limiteDe(form.spControlLimitHigh()),
                PvFormPanel.limiteDe(form.spControlMinStep()));
        presenter.salirDeEdicion();
        presenter.refrescarYPersistir("Variable '" + pv.getName() + "' actualizada.");
    }

    void eliminarPv() {
        CommConfigData.PvConfig pv = controller.getState().pvByUuid(presenter.editPvUuid());
        if (pv == null) {
            presenter.salirDeEdicion();
            return;
        }
        CommConfigData.ItemConfig area = controller.getState().itemByUuid(pv.getAreaUuid());
        if (!presenter.validarAntesDeEscribir()) {
            return;
        }
        int opcion = JOptionPane.showConfirmDialog(dialogo,
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
        presenter.salirDeEdicion();
        presenter.refrescarYPersistir("Variable '" + pv.getName() + "' eliminada.");
    }
}
