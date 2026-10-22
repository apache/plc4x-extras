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
import java.util.List;
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
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

@ConvertAsProperties(
        dtd = "-//org.apache.plc4x.malbec.projecttype.panelcategory.panel//VariableCreation//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "VariableCreationTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@Messages({
    "CTL_VariableCreationTopComponent=Creación de Variables",
    "HINT_VariableCreationTopComponent=Ventana para la edición y creación masiva de variables"
})
public final class VariableCreationTopComponent extends TopComponent {

    private static final String DEFAULT_DATA_TYPE = "int";
    private static final String DEFAULT_SCAN_TIME = "1 s";

    private final ChangeListener modelListener = event -> refreshConnectionOptions();

    private JTable tableVariables;
    private DefaultTableModel tableModel;
    private JComboBox<String> cbConexion;
    private Project project;
    private HMICommunicationModel model;
    private boolean listening;

    public VariableCreationTopComponent() {
        initComponents();
        setName(Bundle.CTL_VariableCreationTopComponent());
        setToolTipText(Bundle.HINT_VariableCreationTopComponent());
    }

    public void initData(Project project) {
        if (project == null) {
            return;
        }

        detachModelListener();
        this.project = project;
        this.model = project.getLookup().lookup(HMICommunicationModel.class);
        attachModelListener();
        refreshConnectionOptions();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        JLabel lblTitle = new JLabel("Creación de Variable", JLabel.LEFT);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel panelHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panelHeader.add(lblTitle);
        add(panelHeader, BorderLayout.NORTH);

        String[] columnNames = {
            "Name",
            "Conexión",
            "Data type",
            "Tag",
            "PvDescriptor",
            "Scan Time",
            "PvScanEnable",
            "PvWriteEnable",
            "PvDisplayLimitLow",
            "PvDisplayLimitHigh",
            "PvDisplayDescription",
            "PvDisplayFormat",
            "PvDisplayUnits",
            "PvControlLimitLow",
            "PvControlLimitHigh",
            "PvControlMinStep",
            
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        tableVariables = new JTable(tableModel);
        tableVariables.setRowHeight(24);

        cbConexion = new JComboBox<>();
        TableColumn colConexion = tableVariables.getColumnModel().getColumn(1);
        colConexion.setCellEditor(new DefaultCellEditor(cbConexion));

        JComboBox<String> cbTipoDato
                = new JComboBox<>(new String[]{"boolean", "byte", "double", "float", "int", "long", "short"});
        TableColumn colTipoDato = tableVariables.getColumnModel().getColumn(2);
        colTipoDato.setCellEditor(new DefaultCellEditor(cbTipoDato));

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

        tableModel.addRow(new Object[]{
            "Var_1", defaultConnectionName(), DEFAULT_DATA_TYPE, "%DB1.DBW0", DEFAULT_SCAN_TIME
        });
        tableModel.addRow(new Object[]{
            "", defaultConnectionName(), DEFAULT_DATA_TYPE, "", DEFAULT_SCAN_TIME
        });

        JScrollPane scrollPane = new JScrollPane(tableVariables);
        add(scrollPane, BorderLayout.CENTER);

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton btnAddRow = new JButton("Add Row");
        JButton btnGuardar = new JButton("Save");
        JButton btnCancelar = new JButton("Cancel");

        btnAddRow.addActionListener(e -> addVariableRow());
        btnGuardar.addActionListener(e -> guardarVariables());
        btnCancelar.addActionListener(e -> close());

        panelButtons.add(btnAddRow);
        panelButtons.add(btnGuardar);
        panelButtons.add(btnCancelar);
        add(panelButtons, BorderLayout.SOUTH);
    }

    @Override
    public void componentOpened() {
        if (project == null) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Seleccione un proyecto antes de abrir Creación de Variables.",
                        "Crear Variables",
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
            Object current = tableModel.getValueAt(row, 1);
            if (current == null || !names.contains(current.toString())) {
                tableModel.setValueAt(fallback, row, 1);
            }
        }
    }

    private String defaultConnectionName() {
        return cbConexion.getItemCount() > 0 ? cbConexion.getItemAt(0) : "";
    }

    private void addVariableRow() {
        tableModel.addRow(new Object[]{
            "", defaultConnectionName(), DEFAULT_DATA_TYPE, "", DEFAULT_SCAN_TIME
        });
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
        String colName = tableModel.getColumnName(col);

        if ("Tipo de Dato".equals(colName)) {
            System.out.println("Fila " + row + " cambió su tipo a: " + newValue);
        } else if ("Dirección".equals(colName)) {
            System.out.println("Nueva dirección ingresada: " + newValue);
        }
    }

    private void guardarVariables() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String name = (String) tableModel.getValueAt(i, 0);
            String conexion = (String) tableModel.getValueAt(i, 1);
            String datatype = (String) tableModel.getValueAt(i, 2);
            String tag = (String) tableModel.getValueAt(i, 3);
            String scantime = (String) tableModel.getValueAt(i, 4);
        }
        JOptionPane.showMessageDialog(this, "Variables guardadas exitosamente.");
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }
}
