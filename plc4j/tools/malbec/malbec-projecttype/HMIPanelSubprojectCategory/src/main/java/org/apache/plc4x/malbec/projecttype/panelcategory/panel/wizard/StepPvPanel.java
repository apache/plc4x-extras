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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.JViewport;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;

/**
 * El paso 3 del asistente: variables de proceso.
 *
 * <p>Antes eran 1.042 lineas con los treinta controles, la tabla, la validacion y los
 * avisos de Swing todo en la misma clase. Ahora esto solo monta las piezas:
 *
 * <ul>
 *   <li>{@link PvFormPanel}: los controles y su colocacion.
 *   <li>{@link PvTablePanel}: la tabla de variables configuradas.
 *   <li>{@link PvPresenter}: quien decide, pregunta al controlador y avisa.
 *   <li>{@link PvLimites}: el recorte de los limites numericos, sin Swing.
 *   <li>{@link PvRenderers}: los pintores de los desplegables.
 *   <li>{@link PvTexts}: los textos largos, fuera de las clases que dibujan.
 * </ul>
 *
 * <p>El reparto no es estetico: lo que se puede probar sin montar Swing, se prueba sin
 * Swing, y los textos se pueden revisar sin abrir el asistente.
 *
 * <p>Los metodos que se ven aqui sin cuerpo propio siguen existiendo porque son la
 * superficie que usan las pruebas y el ensamblador del asistente. No reenvian a nada:
 * cada uno significa algo.
 */
public class StepPvPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final PvFormPanel form = new PvFormPanel();
    private final PvTablePanel tabla = new PvTablePanel();
    private final PvPresenter presenter;

    public StepPvPanel(CommunicationWizardController controller, PlantModelReader plantModelReader) {
        this.presenter = new PvPresenter(controller, plantModelReader, form, tabla, this);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        formScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formScroll, tabla);
        split.setResizeWeight(0.0);
        split.setDividerLocation(380);
        split.setContinuousLayout(true);

        setLayout(new BorderLayout(8, 8));
        add(split, BorderLayout.CENTER);
    }

    // ---- lo que usa el asistente -------------------------------------------

    public void setSaveHooks(Supplier<Boolean> beforeSave, Consumer<String> afterSaved) {
        presenter.setSaveHooks(beforeSave, afterSaved);
    }

    public void refresh() {
        presenter.refrescar();
    }

    public void refreshData() {
        refresh();
    }

    // ---- lo que usan las pruebas -------------------------------------------

    JTable getPvTable() {
        return tabla.tabla();
    }

    JComboBox<String> getScanTimeCombo() {
        return form.comboScanTime();
    }

    JTextField getDescriptor() {
        return form.txtDescriptor();
    }

    JTextField getDisplayDescription() {
        return form.txtDisplayDescription();
    }

    JTextField getDisplayFormat() {
        return form.txtDisplayFormat();
    }

    JTextField getDisplayUnits() {
        return form.txtDisplayUnits();
    }

    JSpinner getDisplayLimitLow() {
        return form.spDisplayLimitLow();
    }

    JSpinner getDisplayLimitHigh() {
        return form.spDisplayLimitHigh();
    }

    JSpinner getControlLimitLow() {
        return form.spControlLimitLow();
    }

    JSpinner getControlLimitHigh() {
        return form.spControlLimitHigh();
    }

    JSpinner getControlMinStep() {
        return form.spControlMinStep();
    }

    List<String> getAjustesAlCargar() {
        return presenter.ajustesAlCargar();
    }

    void cargarEnFormulario(CommConfigData.PvConfig pv) {
        presenter.cargarEnFormulario(pv);
    }

    String motivoDeOffsetDesconocido(CommConfigData.ItemConfig area) {
        return presenter.motivoDeOffsetDesconocido(area);
    }
}