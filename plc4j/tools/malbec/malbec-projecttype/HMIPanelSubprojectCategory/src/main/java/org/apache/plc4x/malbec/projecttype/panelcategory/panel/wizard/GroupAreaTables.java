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
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.MemoryTag;

final class GroupAreaTables extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String SIN_GRUPO = "SIN GRUPO";

    private final CommunicationWizardController controller;
    private final NonEditableTableModel groupModel = new NonEditableTableModel(new String[]{
        "Nombre", "Descripción", "Scantime", "Enable", "UUID"
    });
    private final JTable tbGroups = new JTable(groupModel);
    private final NonEditableTableModel itemModel = new NonEditableTableModel(new String[]{
        "Nombre", "Descripción", "Tag", "Tipo", "Capacidad", "Grupo", "Enable", "UUID"
    });
    private final JTable tbItems = new JTable(itemModel);

    private boolean groupColumnsSized;
    private boolean itemColumnsSized;
    private boolean rebuildingTables;
    private Runnable alElegirGrupo = () -> { };
    private Runnable alElegirItem = () -> { };

    GroupAreaTables(CommunicationWizardController controller) {
        this.controller = controller;

        tbGroups.setIntercellSpacing(new Dimension(6, 2));
        tbItems.setIntercellSpacing(new Dimension(6, 2));
        WizardUi.configColumns(tbItems);
        WizardUi.configColumns(tbGroups);

        JScrollPane groupScroll = new JScrollPane(tbGroups);
        WizardUi.limitTableSpace(groupScroll, 700, 240);
        WizardUi.rellenarAnchoVisible(tbGroups);

        JScrollPane itemScroll = new JScrollPane(tbItems);
        WizardUi.limitTableSpace(itemScroll, 700, 240);
        WizardUi.rellenarAnchoVisible(tbItems);

        JPanel groupTable = new JPanel(new BorderLayout(0, 4));
        groupTable.setBorder(BorderFactory.createTitledBorder("Grupos de escaneo"));
        groupTable.add(groupScroll, BorderLayout.CENTER);

        JPanel itemTable = new JPanel(new BorderLayout(0, 4));
        itemTable.setBorder(BorderFactory.createTitledBorder("Áreas de memoria"));
        itemTable.add(itemScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, groupTable, itemTable);
        split.setResizeWeight(0.5);
        split.setDividerLocation(0.5);
        split.setContinuousLayout(true);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);

        tbGroups.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !rebuildingTables) {
                alElegirGrupo.run();
            }
        });
        tbItems.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !rebuildingTables) {
                alElegirItem.run();
            }
        });
    }

    void setAlElegirGrupo(Runnable r) {
        this.alElegirGrupo = r;
    }

    void setAlElegirItem(Runnable r) {
        this.alElegirItem = r;
    }

    JTable tablaDeGrupos() {
        return tbGroups;
    }

    JTable tablaDeItems() {
        return tbItems;
    }

    void refrescar() {
        groupModel.setRowCount(0);
        for (CommConfigData.GroupConfig g : controller.getState().getGroups()) {
            groupModel.addRow(new Object[]{
                g.getName(), g.getDescription(), g.getScantime(),
                g.isEnable() ? "TRUE" : "FALSE", g.getUuid()
            });
        }
        itemModel.setRowCount(0);
        for (CommConfigData.ItemConfig i : controller.getState().getItems()) {
            String groupName = controller.getState().groupNameOf(i.getUuid());
            itemModel.addRow(new Object[]{
                i.getName(), i.getDescription(), i.getTag(),
                tipoDelArea(i), capacidadDe(i),
                groupName.isEmpty() ? SIN_GRUPO : groupName,
                i.isEnable() ? "TRUE" : "FALSE", i.getUuid()
            });
        }
        ajustarAnchos();
    }

    private void ajustarAnchos() {
        if (!groupColumnsSized && groupModel.getRowCount() > 0) {
            WizardUi.sizeColumnsToContent(tbGroups, 90, 260);
            groupColumnsSized = true;
        }
        if (!itemColumnsSized && itemModel.getRowCount() > 0) {
            WizardUi.sizeColumnsToContent(tbItems, 90, 260);
            itemColumnsSized = true;
        }
        WizardUi.rellenarAnchoVisible(tbGroups);
        WizardUi.rellenarAnchoVisible(tbItems);
    }

    private String tipoDelArea(CommConfigData.ItemConfig item) {
        DataType type = controller.lockedType(item.getUuid());
        if (type == null) {
            return "—";
        }
        return type.bitAddressed() ? type.label() + " (1 bit)" : type.label();
    }

    private String capacidadDe(CommConfigData.ItemConfig item) {
        MemoryTag tag = MemoryTag.parse(item.getTag());
        if (tag == null) {
            return "sin tag";
        }
        int bytes = tag.byteCapacity();
        return bytes > 0 ? bytes + " bytes" : "sin límite";
    }
}