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
import java.util.Properties;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableTablePanelTest {

    private static DeviceConfigData device(String name, String protocol) {
        Properties properties = new Properties();
        properties.setProperty("deviceName", name);
        properties.setProperty("deviceKey", "key-" + name);
        properties.setProperty("uuid", "uuid-" + name);
        properties.setProperty("protocol", protocol);
        properties.setProperty("specificParameters", "ipa=10.0.0.1");
        properties.setProperty("enable", "true");
        return new DeviceConfigData(properties);
    }

    private static CreateVariableController.Row row(String name, String device,
            String dataType, String tag) {
        return new CreateVariableController.Row(
                1,
                "",
                name,
                device,
                dataType,
                tag,
                "",
                "1000ms",
                true,
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "");
    }

    private static List<String> items(VariableTablePanel panel, int row) {
        TableCellEditor editor = panel.table().getCellEditor(
                row, VariableTableModel.COL_TYPE);
        JComboBox<?> comboBox = (JComboBox<?>) ((DefaultCellEditor) editor).getComponent();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            values.add(String.valueOf(comboBox.getItemAt(i)));
        }
        return values;
    }

    @Test
    void selectedModelRowsConvertsSortedViewRowsToModelRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            VariableTablePanel panel = new VariableTablePanel();
            panel.setRows(List.of(
                    row("B", "PLC", "int", "%DB1.DBD0"),
                    row("A", "PLC", "int", "%DB1.DBD4"),
                    row("C", "PLC", "int", "%DB1.DBD8")));
            TableRowSorter<?> sorter = (TableRowSorter<?>) panel.table().getRowSorter();
            sorter.setSortKeys(List.of(new RowSorter.SortKey(
                    VariableTableModel.COL_NAME, SortOrder.ASCENDING)));

            panel.table().setRowSelectionInterval(0, 1);

            assertEquals(List.of(1, 0), panel.selectedModelRows());
        });
    }

    @Test
    void typeEditorIsFilteredByTheProtocolOfEachRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            VariableTablePanel panel = new VariableTablePanel();
            panel.setRows(List.of(
                    row("Var_S7", "PLC-S7", "int", "%DB1.DBB0"),
                    row("Var_Modbus", "PLC-MODBUS", "int", "%DB1.DBB0")));
            panel.refreshDeviceOptions(() -> List.of(
                    device("PLC-S7", "S7"),
                    device("PLC-MODBUS", "MODBUS")));

            List<String> s7Types = items(panel, 0);
            List<String> modbusTypes = items(panel, 1);

            assertTrue(s7Types.contains("s7ai"));
            assertTrue(s7Types.contains("byte"));
            assertFalse(s7Types.contains("string"));

            assertFalse(modbusTypes.contains("s7ai"));
            assertTrue(modbusTypes.contains("byte"));
            assertFalse(modbusTypes.contains("string"));
        });
    }
}
