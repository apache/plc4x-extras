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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.ParseException;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

final class VariableTablePanel extends JPanel {

    private static final int[] SEARCH_COLUMNS = {
        -1, VariableTableModel.COL_NAME, VariableTableModel.COL_DEVICE,
        VariableTableModel.COL_TYPE, VariableTableModel.COL_TAG
    };
    private static final int SEARCH_DEVICE = 2;
    private static final int[] SEARCH_ALL_COLUMNS = {
        VariableTableModel.COL_NUMBER, VariableTableModel.COL_NAME,
        VariableTableModel.COL_DEVICE, VariableTableModel.COL_TYPE,
        VariableTableModel.COL_TAG, VariableTableModel.COL_DESCRIPTOR,
        VariableTableModel.COL_SCAN_TIME, VariableTableModel.COL_SCAN_ENABLE,
        VariableTableModel.COL_WRITE_ENABLE, VariableTableModel.COL_DISPLAY_LIMIT_LOW,
        VariableTableModel.COL_DISPLAY_LIMIT_HIGH, VariableTableModel.COL_DISPLAY_DESCRIPTION,
        VariableTableModel.COL_DISPLAY_FORMAT, VariableTableModel.COL_DISPLAY_UNITS,
        VariableTableModel.COL_CONTROL_LIMIT_LOW, VariableTableModel.COL_CONTROL_LIMIT_HIGH,
        VariableTableModel.COL_CONTROL_MIN_STEP
    };

    private final VariableTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<TableModel> sorter;
    private final JComboBox<String> cbConexion;
    private final JComboBox<String> cbSearch;
    private final JTextField txtSearch;

    VariableTablePanel() {
        setLayout(new BorderLayout(0, 8));

        tableModel = new VariableTableModel();
        table = new JTable(tableModel);
        sorter = new TableRowSorter<TableModel>(tableModel);
        cbConexion = new JComboBox<>();
        cbSearch = new JComboBox<>(new String[]{
            "All", "Name", "Conexión", "Data type", "Tag"
        });
        txtSearch = new JTextField();

        configureTable();
        add(createFilterPanel(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void configureTable() {
        table.setRowSorter(sorter);
        table.setRowHeight(24);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer centeredRenderer = new DefaultTableCellRenderer();
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(String.class, centeredRenderer);
        table.setDefaultRenderer(Integer.class, centeredRenderer);
        table.setDefaultRenderer(Boolean.class, new CenteredBooleanRenderer());

        JTextField centeredTextField = new JTextField();
        centeredTextField.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultEditor(String.class, new DefaultCellEditor(centeredTextField));

        JCheckBox centeredCheckBox = new JCheckBox();
        centeredCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultEditor(Boolean.class, new DefaultCellEditor(centeredCheckBox));

        table.getColumnModel().getColumn(VariableTableModel.COL_NAME)
                .setCellEditor(new MaxLengthCellEditor(20));
        table.getColumnModel().getColumn(VariableTableModel.COL_TAG)
                .setCellEditor(new MaxLengthCellEditor(15));
        table.getColumnModel().getColumn(VariableTableModel.COL_DESCRIPTOR)
                .setCellEditor(new MaxLengthCellEditor(40));

        centerComboBox(cbConexion);
        TableColumn colConexion = table.getColumnModel()
                .getColumn(VariableTableModel.COL_DEVICE);
        colConexion.setCellEditor(new DefaultCellEditor(cbConexion));

        JComboBox<String> cbDataType = new JComboBox<>(
                new String[]{"boolean", "byte", "float", "int", "short"});
        centerComboBox(cbDataType);
        TableColumn colDataType = table.getColumnModel()
                .getColumn(VariableTableModel.COL_TYPE);
        colDataType.setCellEditor(new DefaultCellEditor(cbDataType));

        JComboBox<String> cbScanTime = new JComboBox<>(new String[]{
            "100ms", "200ms", "300ms", "400ms", "500ms",
            "600ms", "700ms", "800ms", "900ms", "1000ms"
        });
        centerComboBox(cbScanTime);
        TableColumn colScanTime = table.getColumnModel()
                .getColumn(VariableTableModel.COL_SCAN_TIME);
        colScanTime.setCellEditor(new DefaultCellEditor(cbScanTime));

        for (int column : new int[]{
            VariableTableModel.COL_DISPLAY_LIMIT_LOW,
            VariableTableModel.COL_DISPLAY_LIMIT_HIGH,
            VariableTableModel.COL_CONTROL_LIMIT_LOW,
            VariableTableModel.COL_CONTROL_LIMIT_HIGH,
            VariableTableModel.COL_CONTROL_MIN_STEP}) {
            table.getColumnModel().getColumn(column)
                    .setCellEditor(new SpinnerCellEditor(1, 65535));
        }

        TableColumn uuidColumn = table.getColumnModel()
                .getColumn(VariableTableModel.COL_UUID);
        table.removeColumn(uuidColumn);

        configureColumnWidths();

        tableModel.addTableModelListener(e -> {
            if (e.getType() != TableModelEvent.UPDATE
                    || e.getFirstRow() < 0
                    || e.getColumn() < 0) {
                return;
            }
            onCellUpdated(e.getFirstRow(), e.getColumn());
        });
    }

    private JPanel createFilterPanel() {
        cbSearch.setPreferredSize(new Dimension(130, 24));
        txtSearch.setPreferredSize(new Dimension(200, 24));

        JPanel panelLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panelLeft.add(new JLabel("Filter:"));
        panelLeft.add(cbSearch);
        panelLeft.add(new JLabel("Search:"));
        panelLeft.add(txtSearch);

        JPanel panelSearch = new JPanel(new BorderLayout());
        panelSearch.add(panelLeft, BorderLayout.WEST);

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateFilter();
            }
        });
        cbSearch.addActionListener(e -> updateFilter());

        return panelSearch;
    }

    private void updateFilter() {
        int selected = cbSearch.getSelectedIndex();
        int[] columns = selected > 0 && selected < SEARCH_COLUMNS.length
                ? new int[]{SEARCH_COLUMNS[selected]}
                : SEARCH_ALL_COLUMNS;

        sorter.setRowFilter(CreateVariableController.buildRowFilter(txtSearch.getText(), columns));
    }

    private void configureColumnWidths() {
        int[] minimumWidths = {
            60, 120, 120, 90, 110, 200, 80, 80, 80,
            100, 110, 200, 90, 100, 100, 110, 100
        };
        for (int column = 0; column < minimumWidths.length; column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            tableColumn.setMinWidth(minimumWidths[column]);
            tableColumn.setPreferredWidth(minimumWidths[column]);
        }
    }

    private static void centerComboBox(JComboBox<String> comboBox) {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.CENTER);
        comboBox.setRenderer(renderer);
        if (comboBox.getEditor().getEditorComponent() instanceof JTextField textField) {
            textField.setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    private void onCellUpdated(int row, int column) {
        if (column == VariableTableModel.COL_TYPE || column == VariableTableModel.COL_TAG) {
            String error = CreateVariableController.validate(tableModel.row(row));
            table.setToolTipText(error == null ? null : "Row " + (row + 1) + ": " + error);
        }
    }

    JTable table() {
        return table;
    }

    List<CreateVariableController.Row> rows() {
        return tableModel.rows();
    }

    CreateVariableController.Row row(int modelRow) {
        return tableModel.row(modelRow);
    }

    void setRows(List<CreateVariableController.Row> rows) {
        tableModel.setRows(rows);
    }

    void addRow(String name, String tag) {
        tableModel.addRow(CreateVariableController.defaultRow(
                name, tag, defaultDeviceName()));
    }

    void duplicateRow(int modelRow, int copies) {
        List<CreateVariableController.Row> copiedRows
                = CreateVariableController.duplicateRows(tableModel.rows(), modelRow, copies);
        tableModel.insertRows(modelRow + 1, copiedRows);
        table.setToolTipText(null);
        table.clearSelection();
        for (int i = 0; i < copies; i++) {
            int newViewRow = table.convertRowIndexToView(modelRow + 1 + i);
            if (newViewRow >= 0) {
                table.addRowSelectionInterval(newViewRow, newViewRow);
            }
        }
    }

    CreateVariableController.Row removeRow(int modelRow) {
        CreateVariableController.Row removed = tableModel.row(modelRow);
        tableModel.removeRow(modelRow);
        table.setToolTipText(null);

        if (tableModel.getRowCount() > 0) {
            int nextModelRow = Math.min(modelRow, tableModel.getRowCount() - 1);
            int nextViewRow = table.convertRowIndexToView(nextModelRow);
            if (nextViewRow >= 0) {
                table.setRowSelectionInterval(nextViewRow, nextViewRow);
            }
        }
        return removed;
    }

    void refreshDeviceOptions(Supplier<List<String>> deviceNames) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshDeviceOptions(deviceNames));
            return;
        }

        List<String> names = deviceNames.get();
        if (names == null) {
            names = List.of();
        }

        cbConexion.removeAllItems();
        for (String name : names) {
            cbConexion.addItem(name);
        }

        String fallback = names.isEmpty() ? "" : names.get(0);
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            Object current = tableModel.getValueAt(row, VariableTableModel.COL_DEVICE);
            if (current == null || !names.contains(current.toString())) {
                tableModel.setValueAt(fallback, row, VariableTableModel.COL_DEVICE);
            }
        }
    }

    void filterByDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            clearFilter();
            return;
        }

        cbSearch.setSelectedIndex(SEARCH_DEVICE);
        txtSearch.setText(deviceName);
        sorter.setRowFilter(CreateVariableController.buildExactRowFilter(
                deviceName, VariableTableModel.COL_DEVICE));
    }

    void clearFilter() {
        cbSearch.setSelectedIndex(0);
        txtSearch.setText("");
        sorter.setRowFilter(null);
    }

    boolean stopEditing() {
        return !table.isEditing() || table.getCellEditor().stopCellEditing();
    }

    int selectedModelRow() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
    }

    private String defaultDeviceName() {
        return cbConexion.getItemCount() > 0 ? cbConexion.getItemAt(0) : "";
    }

    private static final class MaxLengthCellEditor extends DefaultCellEditor {

        private MaxLengthCellEditor(int maxLength) {
            super(new JTextField());
            JTextField textField = (JTextField) getComponent();
            textField.setHorizontalAlignment(SwingConstants.CENTER);
            ((AbstractDocument) textField.getDocument())
                    .setDocumentFilter(maxLengthFilter(maxLength));
        }
    }

    private static DocumentFilter maxLengthFilter(int maxLength) {
        return new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text,
                    AttributeSet attrs) throws BadLocationException {
                String replacement = text == null ? "" : text;
                int available = maxLength - (fb.getDocument().getLength() - length);
                if (available <= 0) {
                    return;
                }
                if (replacement.length() > available) {
                    replacement = replacement.substring(0, available);
                }
                super.replace(fb, offset, length, replacement, attrs);
            }
        };
    }

    private static final class CenteredBooleanRenderer extends JCheckBox implements TableCellRenderer {

        private CenteredBooleanRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(Boolean.TRUE.equals(value));
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    private static final class SpinnerCellEditor extends AbstractCellEditor implements TableCellEditor {

        private final JSpinner spinner;

        private SpinnerCellEditor(int min, int max) {
            spinner = new JSpinner(new SpinnerNumberModel(min, min, max, 1));
            JSpinner.NumberEditor spinnerEditor = new JSpinner.NumberEditor(spinner, "0");
            spinnerEditor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
            spinner.setEditor(spinnerEditor);
        }

        @Override
        public Object getCellEditorValue() {
            return ((Number) spinner.getValue()).intValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            if (value instanceof Number number) {
                spinner.setValue(number.intValue());
            } else {
                spinner.setValue(1);
            }
            return spinner;
        }

        @Override
        public boolean stopCellEditing() {
            try {
                spinner.commitEdit();
            } catch (ParseException ex) {
                return false;
            }
            return super.stopCellEditing();
        }
    }
}
