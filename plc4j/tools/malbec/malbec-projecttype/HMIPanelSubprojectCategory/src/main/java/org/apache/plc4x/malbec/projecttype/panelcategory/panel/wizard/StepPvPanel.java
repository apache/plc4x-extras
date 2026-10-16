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

public class StepPvPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final PvFormPanel form = new PvFormPanel();
    private final PvTablePanel table = new PvTablePanel();
    private final PvPresenter presenter;

    public StepPvPanel(CommunicationWizardController controller, PlantModelReader plantModelReader) {
        this.presenter = new PvPresenter(controller, plantModelReader, form, table, this);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        formScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formScroll, table);
        split.setResizeWeight(0.0);
        split.setDividerLocation(380);
        split.setContinuousLayout(true);

        setLayout(new BorderLayout(8, 8));
        add(split, BorderLayout.CENTER);
    }

    public void setSaveHooks(Supplier<Boolean> beforeSave, Consumer<String> afterSaved) {
        presenter.setSaveHooks(beforeSave, afterSaved);
    }

    public void refresh() {
        presenter.refresh();
    }

    public void refreshData() {
        refresh();
    }

    JTable getPvTable() {
        return table.table();
    }

    JComboBox<String> getScanTimeCombo() {
        return form.comboScanTime();
    }

    JTextField getDescriptorText() {
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

    List<String> getAdjustmentsOnLoad() {
        return presenter.adjustmentsOnLoad();
    }

    void loadIntoForm(CommConfigData.PvConfig pv) {
        presenter.loadIntoForm(pv);
    }

    String reasonForUnknownOffset(CommConfigData.ItemConfig area) {
        return presenter.reasonForUnknownOffset(area);
    }
}