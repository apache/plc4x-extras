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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.netbeans.api.project.Project;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import java.util.LinkedHashSet;
import java.util.Set;

@ConvertAsProperties(
        dtd = "-//org.apache.plc4x.malbec.projecttype.panelcategory.panel//VariableCreation//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "CreateVariableTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@Messages({
    "CTL_CreateVariableTopComponent=Variables",
    "HINT_CreateVariableTopComponent=Window for editing and creating variables"
})
public final class CreateVariableTopComponent extends TopComponent {

    private static final String DEFAULT_DATA_TYPE = "int";
    private static final String DEFAULT_SCAN_TIME = "1000ms";
    private final Set<String> deletedPvUuids = new LinkedHashSet<>();

    private static final int COL_NAME = 0;
    private static final int COL_DEVICE = 1;
    private static final int COL_TYPE = 2;
    private static final int COL_TAG = 3;
    private static final int COL_DESCRIPTOR = 4;
    private static final int COL_SCAN_TIME = 5;
    private static final int COL_SCAN_ENABLE = 6;
    private static final int COL_WRITE_ENABLE = 7;
    private static final int COL_UUID = 16;

    private final ChangeListener modelListener = event -> refreshConnectionOptions();

    private JTable tableVariables;
    private DefaultTableModel tableModel;
    private JComboBox<String> cbConexion;
    private Project project;
    private HMICommunicationModel model;
    private boolean listening;

    public CreateVariableTopComponent() {
        initComponents();
        setName(Bundle.CTL_CreateVariableTopComponent());
        setToolTipText(Bundle.HINT_CreateVariableTopComponent());
    }

    public void initData(Project project) {
        detachModelListener();
        if (this.project != project) {
            deletedPvUuids.clear();
        }
        if (project == null) {
            return;
        }

        this.project = project;
        this.model = project.getLookup().lookup(HMICommunicationModel.class);
        attachModelListener();
        refreshConnectionOptions();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        JLabel lblTitle = new JLabel("Variables", JLabel.LEFT);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel panelHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panelHeader.add(lblTitle);
        add(panelHeader, BorderLayout.NORTH);

        String[] columnNames = {
            "Name", "Conexión", "Data type", "Tag", "Descriptor",
            "Scan Time", "ScanEnable", "WriteEnable", "DisplayLimitLow",
            "DisplayLimitHigh", "DisplayDescription", "DisplayFormat",
            "DisplayUnits", "ControlLimitLow", "ControlLimitHigh",
            "ControlMinStep", "Uuid"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != COL_UUID;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == COL_SCAN_ENABLE || columnIndex == COL_WRITE_ENABLE) {
                    return Boolean.class;
                }
                return super.getColumnClass(columnIndex);
            }
        };

        tableVariables = new JTable(tableModel);
        tableVariables.setRowHeight(24);
        tableVariables.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        cbConexion = new JComboBox<>();
        TableColumn colConexion = tableVariables.getColumnModel().getColumn(COL_DEVICE);
        colConexion.setCellEditor(new DefaultCellEditor(cbConexion));

        JComboBox<String> cbDataType = new JComboBox<>(new String[]{"boolean", "byte", "float", "int", "short"});
        TableColumn colDataType = tableVariables.getColumnModel().getColumn(COL_TYPE);
        colDataType.setCellEditor(new DefaultCellEditor(cbDataType));

        JComboBox<String> cbScanTime = new JComboBox<>(new String[]{
            "100ms", "200ms", "300ms", "400ms", "500ms",
            "600ms", "700ms", "800ms", "900ms", "1000ms"
        });
        TableColumn colScanTime = tableVariables.getColumnModel().getColumn(COL_SCAN_TIME);
        colScanTime.setCellEditor(new DefaultCellEditor(cbScanTime));

        TableColumn uuidColumn = tableVariables.getColumnModel().getColumn(COL_UUID);
        tableVariables.removeColumn(uuidColumn);

        tableModel.addTableModelListener(e -> {
            if (e.getType() != TableModelEvent.UPDATE
                    || e.getFirstRow() < 0
                    || e.getColumn() < 0) {
                return;
            }
            int row = e.getFirstRow();
            int column = e.getColumn();
            onCellUpdated(row, column, tableModel.getValueAt(row, column));
        });

        refreshConnectionOptions();

        addRow("Var_1", "%DB1.DBD0");

        JScrollPane scrollPane = new JScrollPane(tableVariables);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton btnAddRow = new JButton("Add Row");
        JButton btnDeleteRow = new JButton("Delete Row");
        JButton btnGuardar = new JButton("Save");
        JButton btnCancelar = new JButton("Cancel");

        btnAddRow.addActionListener(e -> addVariableRow());
        btnDeleteRow.addActionListener(e -> deleteSelectedRow());
        btnGuardar.addActionListener(e -> saveVariables());
        btnCancelar.addActionListener(e -> close());

        panelButtons.add(btnAddRow);
        panelButtons.add(btnDeleteRow);
        panelButtons.add(btnGuardar);
        panelButtons.add(btnCancelar);
        add(panelButtons, BorderLayout.SOUTH);
    }

    @Override
    public void componentOpened() {
        if (project == null) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Select a project before opening Variable Creation.",
                        "Create Variables",
                        JOptionPane.WARNING_MESSAGE);
                close();
            });
            return;
        }
        attachModelListener();
        refreshConnectionOptions();
    }

    @Override
    public void componentClosed() {
        detachModelListener();
        deletedPvUuids.clear();
    }

    private void refreshConnectionOptions() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshConnectionOptions);
            return;
        }
        if (tableModel == null || cbConexion == null) {
            return;
        }

        List<String> names = model == null
                ? List.of()
                : model.getDevices().stream()
                        .map(DeviceConfigData::getDeviceName)
                        .filter(name -> name != null && !name.isBlank())
                        .toList();

        cbConexion.removeAllItems();
        for (String name : names) {
            cbConexion.addItem(name);
        }

        String fallback = names.isEmpty() ? "" : names.get(0);
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            Object current = tableModel.getValueAt(row, COL_DEVICE);
            if (current == null || !names.contains(current.toString())) {
                tableModel.setValueAt(fallback, row, COL_DEVICE);
            }
        }
    }

    private String defaultConnectionName() {
        return cbConexion.getItemCount() > 0 ? cbConexion.getItemAt(0) : "";
    }

    private void addRow(String name, String tag) {
        tableModel.addRow(new Object[]{
            name,
            defaultConnectionName(),
            DEFAULT_DATA_TYPE,
            tag,
            "",
            DEFAULT_SCAN_TIME,
            Boolean.TRUE,
            Boolean.FALSE,
        });

        int row = tableModel.getRowCount() - 1;
        tableModel.setValueAt(UUID.randomUUID().toString(), row, COL_UUID);
    }

    void addVariableRow() {
        addRow("", "");
    }

    JTable getTableVariables() {
        return tableVariables;
    }

    private void deleteSelectedRow() {
        if (tableVariables.isEditing()
                && !tableVariables.getCellEditor().stopCellEditing()) {
            return;
        }

        int viewRow = tableVariables.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Select a row to delete.",
                    "Delete Row",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tableVariables.convertRowIndexToModel(viewRow);
        String name = istext(modelRow, COL_NAME);
        String tag = istext(modelRow, COL_TAG);

        Object[] options = {"Accept", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Are you sure you want to delete this row?\n\n"
                + "Row: " + (modelRow + 1) + "\n"
                + "Name: " + name + "\n"
                + "Tag: " + tag,
                "Delete Row",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);

        if (choice != 0) {
            return;
        }

        String uuid = istext(modelRow, COL_UUID);
        if (!uuid.isBlank()) {
            deletedPvUuids.add(uuid);
        }

        tableModel.removeRow(modelRow);
        tableVariables.setToolTipText(null);

        if (tableModel.getRowCount() > 0) {
            int nextModelRow = Math.min(modelRow, tableModel.getRowCount() - 1);
            int nextViewRow = tableVariables.convertRowIndexToView(nextModelRow);
            tableVariables.setRowSelectionInterval(nextViewRow, nextViewRow);
        }
    }

    private void attachModelListener() {
        if (model != null && !listening) {
            model.addChangeListener(modelListener);
            listening = true;
        }
    }

    private void detachModelListener() {
        if (model != null && listening) {
            model.removeChangeListener(modelListener);
            listening = false;
        }
    }

    private void onCellUpdated(int row, int col, Object newValue) {
        if (col == COL_TYPE || col == COL_TAG) {
            String error = CreateVariableController.validate(readRows(row));
            tableVariables.setToolTipText(
                    error == null ? null : "Row " + (row + 1) + ": " + error);
        }
    }

    private CreateVariableController.Row readRows(int row) {
        return new CreateVariableController.Row(
                row + 1,
                istext(row, COL_UUID),
                istext(row, COL_NAME),
                istext(row, COL_DEVICE),
                istext(row, COL_TYPE),
                istext(row, COL_TAG),
                istext(row, COL_DESCRIPTOR),
                istext(row, COL_SCAN_TIME),
                isboolean(row, 6),
                isboolean(row, 7),
                istext(row, 8),
                istext(row, 9),
                istext(row, 10),
                istext(row, 11),
                istext(row, 12),
                istext(row, 13),
                istext(row, 14),
                istext(row, 15));
    }

    private List<CreateVariableController.Row> readRows() {
        List<CreateVariableController.Row> rows = new ArrayList<>();

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (istext(row, COL_NAME).isBlank() && istext(row, COL_TAG).isBlank()) {
                continue;
            }
            rows.add(readRows(row));
        }

        return rows;
    }

    private void saveVariables() {
        if (project == null || model == null) {
            JOptionPane.showMessageDialog(this,
                    "There is no communication model.",
                    "Save variables",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        File carpeta = FileUtil.toFile(project.getProjectDirectory());
        if (carpeta == null) {
            JOptionPane.showMessageDialog(this,
                    "The project folder could not be retrieved.",
                    "Save variables",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        CreateVariableController.Result result
                = CreateVariableController.saveVariables(carpeta.getAbsolutePath(),
                        model.getDevices(),
                        readRows(),
                        deletedPvUuids);

        if (!result.success()) {
            JOptionPane.showMessageDialog(this,
                    String.join("\n", result.errors()),
                    "Errors in the variables",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        deletedPvUuids.clear();

        JOptionPane.showMessageDialog(this,
                "Variables saved successfully.",
                "Save variables",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private String istext(int row, int col) {
        Object value = tableModel.getValueAt(row, col);
        return value == null ? "" : value.toString().trim();
    }

    private boolean isboolean(int row, int col) {
        Object value = tableModel.getValueAt(row, col);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }
}
