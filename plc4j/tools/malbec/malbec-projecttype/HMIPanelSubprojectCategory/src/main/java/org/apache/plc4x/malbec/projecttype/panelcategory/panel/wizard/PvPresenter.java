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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PlantVariable;

/**
 * El paso 3 del asistente: elegir, cargar, crear, modificar y borrar una variable.
 *
 * <p>Esta clase es la que hacia de detector del {@code StepPvPanel}: es quien pregunta
 * al controlador, rellena el formulario, avisa de los errores y decide cuando guardar.
 * No dibuja nada. Los componentes se los dan {@link PvFormPanel} y {@link PvTablePanel},
 * y los limites los aplica {@link PvLimites}.
 *
 * <p>El dialogo se recibe como {@link Component} en vez de heredar de un panel, para que
 * las pruebas puedan ejercitarlo sin montar Swing.
 */
final class PvPresenter {

    private static final Logger LOGGER = Logger.getLogger(PvPresenter.class.getName());

    private final CommunicationWizardController controller;
    private final PlantModelReader plantModelReader;
    private final PvFormPanel form;
    private final PvTablePanel tabla;
    private final PvLimites limites = new PvLimites();
    private PvEditor editor;
    private final Component dialogo;

    private String editPvUuid;
    private Supplier<Boolean> beforeSave;
    private Consumer<String> afterSaved;

    PvPresenter(CommunicationWizardController controller, PlantModelReader plantModelReader,
            PvFormPanel form, PvTablePanel tabla, Component dialogo) {
        this.controller = controller;
        this.plantModelReader = plantModelReader;
        this.form = form;
        this.tabla = tabla;
        this.dialogo = dialogo;

        form.instalarPintoresDeAreaYVariable();
        form.instalarPintor(this::areaSeleccionadaUuid, controller::typeAllowed);
        form.setAlCambiarArea(this::refrescarVariables);
        form.setAlElegirVariable(this::alElegirVariable);
        tabla.setAlCambiarSeleccion(this::alSeleccionarFila);
        // El editor se crea aqui y no como campo final porque necesita a este
        // presentador para, al terminar cada operacion, salir de edicion y refrescar.
        editor = new PvEditor(controller, form, dialogo, this);
        form.btnAddPv().addActionListener(e -> editor.agregarPv());
        form.btnUpdatePv().addActionListener(e -> editor.modificarPv());
        form.btnDeletePv().addActionListener(e -> editor.eliminarPv());
        form.btnCancelPv().addActionListener(e -> salirDeEdicion());
    }

    private String areaSeleccionadaUuid() {
        return form.areaSeleccionadaUuid();
    }

    // ---- refresco ----------------------------------------------------------

    void refrescar() {
        refrescarAreas();
        refrescarVariables();
        refrescarTabla();
        refrescarEstado();
    }

    private List<CommConfigData.ItemConfig> areasConGrupo() {
        List<CommConfigData.ItemConfig> areas = new ArrayList<>();
        for (CommConfigData.ItemConfig i : controller.getState().getItems()) {
            if (!controller.getState().groupNameOf(i.getUuid()).isEmpty()) {
                areas.add(i);
            }
        }
        return areas;
    }

    private void refrescarAreas() {
        CommConfigData.ItemConfig sel = form.areaSeleccionada();
        form.rellenarAreas(areasConGrupo(), sel);
    }

    private void refrescarVariables() {
        form.vaciarVariables();
        List<DataType> permitidos = controller.typesAllowed(areaSeleccionadaUuid());
        form.rellenarTipos(controller.typesForCombo(areaSeleccionadaUuid()),
                permitidos.size() == 1 ? permitidos.get(0) : null);
        refrescarBloqueoDeTipo();

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
        DataType lockedType = controller.lockedType(areaSeleccionadaUuid());
        Set<String> usedKeys = controller.getUsedPvKeys();
        CommConfigData.PvConfig enEdicion = controller.getState().pvByUuid(editPvUuid);
        if (enEdicion != null) {
            // La variable que se esta editando no cuenta como ocupada: si contara,
            // apareceria en gris justo en la fila que el usuario quiere modificar.
            usedKeys.remove(CommunicationWizardController.pvKey(
                    enEdicion.getS88Path(), enEdicion.getName()));
        }
        for (PlantVariable v : variables) {
            if (!controller.areaAdmiteTipo(areaSeleccionadaUuid(), v.getType(), lockedType)) {
                continue;
            }
            if (usedKeys.contains(v.getKey())) {
                v.setUsed(true);
            }
            form.anadirVariable(v);
        }
    }

    private void refrescarBloqueoDeTipo() {
        CommConfigData.ItemConfig area = form.areaSeleccionada();
        if (area == null) {
            form.textoDelBloqueo(" ");
            return;
        }
        DataType elegido = form.tipoElegido();
        form.textoDelBloqueo(PvTexts.bloqueoDeTipo(
                area, elegido,
                controller.areaFreeSlots(area.getUuid(), elegido),
                controller.areaFreeBytes(area.getUuid()),
                controller.areaCapacityBytes(area.getUuid())));
    }

    private void refrescarTabla() {
        tabla.refrescar(controller.getState().getPvs(), uuid -> {
            CommConfigData.ItemConfig area = controller.getState().itemByUuid(uuid);
            return area == null ? "—" : area.getName();
        });
    }

    private void refrescarEstado() {
        form.refrescarEstado(form.areaSeleccionada() != null, editPvUuid != null);
    }

    // ---- seleccion ---------------------------------------------------------

    private void alSeleccionarFila() {
        CommConfigData.PvConfig pv = pvDeLaFilaSeleccionada();
        if (pv == null || pv.getUuid().equals(editPvUuid)) {
            salirDeEdicion();
            return;
        }
        editPvUuid = pv.getUuid();
        cargarEnFormulario(pv);
        refrescarEstado();
    }

    private CommConfigData.PvConfig pvDeLaFilaSeleccionada() {
        List<CommConfigData.PvConfig> pvs = controller.getState().getPvs();
        int fila = tabla.filaSeleccionada();
        return fila >= 0 && fila < pvs.size() ? pvs.get(fila) : null;
    }

    private void alElegirVariable() {
        PlantVariable v = form.variableSeleccionada();
        if (v == null) {
            form.limpiarTipo();
            refrescarBloqueoDeTipo();
            return;
        }
        if (v.isUsed()) {
            avisar("La variable '" + v.getKey() + "' ya está en uso.",
                    "Variable ocupada", JOptionPane.WARNING_MESSAGE);
            form.elegirVariablePorDefecto();
            return;
        }
        List<DataType> permitidos = controller.typesAllowed(areaSeleccionadaUuid());
        form.seleccionarTipoDePlanta(v, permitidos, controller.areaTag(areaSeleccionadaUuid()));
        refrescarBloqueoDeTipo();
    }

    /**
     * Pone una variable guardada en el formulario.
     *
     * <p>Los limites se pasan por {@link PvLimites}, que deja constancia de lo que no
     * cuadra. El ScanTime se selecciona aqui y no dentro del formulario porque su aviso
     * es un ajuste mas, y tiene que acabar en la misma lista que el resto.
     */
    void cargarEnFormulario(CommConfigData.PvConfig pv) {
        CommConfigData.ItemConfig area = controller.getState().itemByUuid(pv.getAreaUuid());
        form.ponerAreas(areasConGrupo(), area == null ? null : area.getUuid());
        limites.limpiar();
        form.sinContarComoEleccion(() -> {
            form.cargar(pv);
            form.elegirVariablePorClave(
                    CommunicationWizardController.pvKey(pv.getS88Path(), pv.getName()));
            form.seleccionarTipo(DataType.find(pv.getType()));
        });
        String avisoScanTime = form.ponerScanTime(pv.getScanTime());
        if (avisoScanTime != null) {
            limites.anotar(avisoScanTime);
        }
        form.spDisplayLimitLow().setValue(
                limites.aLimite(pv.getDisplayLimitLow(), "Display límite bajo"));
        form.spDisplayLimitHigh().setValue(
                limites.aLimite(pv.getDisplayLimitHigh(), "Display límite alto"));
        form.txtDisplayDescription().setText(pv.getDisplayDescription());
        form.txtDisplayFormat().setText(pv.getDisplayFormat());
        form.txtDisplayUnits().setText(pv.getDisplayUnits());
        form.spControlLimitLow().setValue(
                limites.aLimite(pv.getControlLimitLow(), "Control límite bajo"));
        form.spControlLimitHigh().setValue(
                limites.aLimite(pv.getControlLimitHigh(), "Control límite alto"));
        form.spControlMinStep().setValue(
                limites.aLimite(pv.getControlMinStep(), "Control MinStep"));
        refrescarVariables();
        refrescarBloqueoDeTipo();
    }

    void salirDeEdicion() {
        editPvUuid = null;
        tabla.limpiarSeleccion();
        limites.limpiar();
        form.limpiar();
        refrescarEstado();
    }

    void refrescarYPersistir(String what) {
        refrescarVariables();
        refrescarTabla();
        refrescarEstado();
        if (afterSaved != null) {
            afterSaved.accept(what);
        }
    }

    // ---- avisos y validacion ----------------------------------------------

    boolean validarCamposObligatorios() {
        List<String> vacios = PvCamposObligatorios.queFaltan(
                form.txtDescriptor(), form.comboScanTime().getSelectedItem(),
                form.txtDisplayDescription(), form.txtDisplayFormat(), form.txtDisplayUnits());
        if (vacios.isEmpty()) {
            return true;
        }
        avisar(PvTexts.camposIncompletos(vacios),
                "Campos incompletos", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    void avisarAjustes() {
        if (!limites.hayAjustes()) {
            return;
        }
        avisar(PvTexts.ajustesAlCargar(limites.ajustes()),
                "Valores corregidos", JOptionPane.WARNING_MESSAGE);
    }

    boolean validarAntesDeEscribir() {
        return beforeSave == null || Boolean.TRUE.equals(beforeSave.get());
    }

    void avisar(String texto, String titulo, int tipo) {
        JOptionPane.showMessageDialog(dialogo, texto, titulo, tipo);
    }

    // ---- lo que usan el ensamblador y las pruebas --------------------------

    void setSaveHooks(Supplier<Boolean> beforeSave, Consumer<String> afterSaved) {
        this.beforeSave = beforeSave;
        this.afterSaved = afterSaved;
    }

    String editPvUuid() {
        return editPvUuid;
    }

    List<String> ajustesAlCargar() {
        return limites.ajustes();
    }

    String motivoDeOffsetDesconocido(CommConfigData.ItemConfig area) {
        return PvTexts.motivoDeOffsetDesconocido(area);
    }
}
