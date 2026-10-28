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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

final class VariableTableModel extends AbstractTableModel {

    static final int COL_NUMBER = 0;
    static final int COL_NAME = 1;
    static final int COL_DEVICE = 2;
    static final int COL_TYPE = 3;
    static final int COL_TAG = 4;
    static final int COL_DESCRIPTOR = 5;
    static final int COL_SCAN_TIME = 6;
    static final int COL_SCAN_ENABLE = 7;
    static final int COL_WRITE_ENABLE = 8;
    static final int COL_DISPLAY_LIMIT_LOW = 9;
    static final int COL_DISPLAY_LIMIT_HIGH = 10;
    static final int COL_DISPLAY_DESCRIPTION = 11;
    static final int COL_DISPLAY_FORMAT = 12;
    static final int COL_DISPLAY_UNITS = 13;
    static final int COL_CONTROL_LIMIT_LOW = 14;
    static final int COL_CONTROL_LIMIT_HIGH = 15;
    static final int COL_CONTROL_MIN_STEP = 16;
    static final int COL_UUID = 17;

    private static final String[] COLUMN_NAMES = {
        "Number",
        "Name", "Conexión", "Data type", "Tag", "Descriptor",
        "Scan Time", "ScanEnable", "WriteEnable", "DisplayLimitLow",
        "DisplayLimitHigh", "DisplayDescription", "DisplayFormat",
        "DisplayUnits", "ControlLimitLow", "ControlLimitHigh",
        "ControlMinStep", "Uuid"
    };

    private final List<CreateVariableController.Row> rows = new ArrayList<>();

    List<CreateVariableController.Row> rows() {
        return List.copyOf(rows);
    }

    CreateVariableController.Row row(int index) {
        return rows.get(index);
    }

    void setRows(List<CreateVariableController.Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        renumber();
        fireTableDataChanged();
    }

    void addRow(CreateVariableController.Row row) {
        insertRows(rows.size(), List.of(row));
    }

    void insertRows(int index, List<CreateVariableController.Row> newRows) {
        if (newRows == null || newRows.isEmpty()) {
            return;
        }
        rows.addAll(index, newRows);
        renumber();
        fireTableDataChanged();
    }

    void removeRow(int index) {
        rows.remove(index);
        renumber();
        fireTableDataChanged();
    }

    private void renumber() {
        for (int index = 0; index < rows.size(); index++) {
            rows.set(index, rows.get(index).withRowNumber(index + 1));
        }
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    static String columnToolTip(int column) {
        return switch (column) {
            case COL_NUMBER -> "Row number.";
            case COL_NAME -> "Variable name. It must be unique within the selected connection/device.";
            case COL_DEVICE -> "Connection/device that owns the variable.";
            case COL_TYPE -> "Variable data type.";
            case COL_TAG -> "PLC address/tag for the variable.";
            case COL_DESCRIPTOR -> "Optional variable description.";
            case COL_SCAN_TIME -> "How often the variable is read.";
            case COL_SCAN_ENABLE -> "Enable reading this variable.";
            case COL_WRITE_ENABLE -> "Allow writing this variable.";
            case COL_DISPLAY_LIMIT_LOW -> "Low display limit.";
            case COL_DISPLAY_LIMIT_HIGH -> "High display limit.";
            case COL_DISPLAY_DESCRIPTION -> "Description shown by the HMI.";
            case COL_DISPLAY_FORMAT -> "Display format pattern.";
            case COL_DISPLAY_UNITS -> "Engineering units.";
            case COL_CONTROL_LIMIT_LOW -> "Low control limit.";
            case COL_CONTROL_LIMIT_HIGH -> "High control limit.";
            case COL_CONTROL_MIN_STEP -> "Minimum control increment.";
            case COL_UUID -> "Internal variable identifier.";
            default -> null;
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == COL_NUMBER) {
            return Integer.class;
        }
        if (columnIndex == COL_SCAN_ENABLE || columnIndex == COL_WRITE_ENABLE) {
            return Boolean.class;
        }
        return String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column != COL_UUID && column != COL_NUMBER;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        CreateVariableController.Row row = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_NUMBER -> row.rowNumber();
            case COL_NAME -> row.name();
            case COL_DEVICE -> row.deviceName();
            case COL_TYPE -> row.dataType();
            case COL_TAG -> row.itemTag();
            case COL_DESCRIPTOR -> row.descriptor();
            case COL_SCAN_TIME -> row.scanTime();
            case COL_SCAN_ENABLE -> row.scanEnable();
            case COL_WRITE_ENABLE -> row.writeEnable();
            case COL_DISPLAY_LIMIT_LOW -> row.displayLimitLow();
            case COL_DISPLAY_LIMIT_HIGH -> row.displayLimitHigh();
            case COL_DISPLAY_DESCRIPTION -> row.displayDescription();
            case COL_DISPLAY_FORMAT -> row.displayFormat();
            case COL_DISPLAY_UNITS -> row.displayUnits();
            case COL_CONTROL_LIMIT_LOW -> row.controlLimitLow();
            case COL_CONTROL_LIMIT_HIGH -> row.controlLimitHigh();
            case COL_CONTROL_MIN_STEP -> row.controlMinStep();
            case COL_UUID -> row.uuid();
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex == COL_NUMBER || columnIndex == COL_UUID) {
            return;
        }
        CreateVariableController.Row current = rows.get(rowIndex);
        rows.set(rowIndex, updateRow(current, columnIndex, value));
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    private static CreateVariableController.Row updateRow(
            CreateVariableController.Row row, int column, Object value) {
        String text = text(value);
        return new CreateVariableController.Row(
                row.rowNumber(),
                row.uuid(),
                column == COL_NAME ? text : row.name(),
                column == COL_DEVICE ? text : row.deviceName(),
                column == COL_TYPE ? text : row.dataType(),
                column == COL_TAG ? text : row.itemTag(),
                column == COL_DESCRIPTOR ? text : row.descriptor(),
                column == COL_SCAN_TIME ? text : row.scanTime(),
                column == COL_SCAN_ENABLE ? bool(value) : row.scanEnable(),
                column == COL_WRITE_ENABLE ? bool(value) : row.writeEnable(),
                column == COL_DISPLAY_LIMIT_LOW ? text : row.displayLimitLow(),
                column == COL_DISPLAY_LIMIT_HIGH ? text : row.displayLimitHigh(),
                column == COL_DISPLAY_DESCRIPTION ? text : row.displayDescription(),
                column == COL_DISPLAY_FORMAT ? text : row.displayFormat(),
                column == COL_DISPLAY_UNITS ? text : row.displayUnits(),
                column == COL_CONTROL_LIMIT_LOW ? text : row.controlLimitLow(),
                column == COL_CONTROL_LIMIT_HIGH ? text : row.controlLimitHigh(),
                column == COL_CONTROL_MIN_STEP ? text : row.controlMinStep());
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static String text(Object value) {
        return value == null ? "" : Objects.toString(value).trim();
    }
}
