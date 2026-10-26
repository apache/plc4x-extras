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

import java.awt.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateVariableTopComponentTest {

    @Test
    void addRowUpdatesTheVisibleTable() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        assertEquals(1, component.getTableVariables().getRowCount());

        SwingUtilities.invokeAndWait(component::addVariableRow);

        assertEquals(2, component.getTableVariables().getRowCount());
    }

    @Test
    void numbersRowsAndRenumbersAfterDelete() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < 9; i++) {
                component.addVariableRow();
            }
        });

        for (int row = 0; row < 10; row++) {
            assertEquals(row + 1, component.getTableVariables().getValueAt(row, 0));
        }
        assertFalse(component.getTableVariables().isCellEditable(0, 0));

        SwingUtilities.invokeAndWait(() -> component.removeRow(4));

        assertEquals(9, component.getTableVariables().getRowCount());
        for (int row = 0; row < 9; row++) {
            assertEquals(row + 1, component.getTableVariables().getValueAt(row, 0));
        }
    }

    @Test
    void limitColumnsUseSpinnerEditorsFromOneTo65535() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        var table = component.getTableVariables();
        int[] spinnerColumns = {9, 10, 14, 15, 16};

        for (int column : spinnerColumns) {
            TableCellEditor editor = table.getCellEditor(0, column);
            Component componentOfEditor = editor.getTableCellEditorComponent(
                    table, null, true, 0, column);

            assertTrue(componentOfEditor instanceof JSpinner);
            SpinnerNumberModel spinnerModel = (SpinnerNumberModel) ((JSpinner) componentOfEditor).getModel();
            assertEquals(1, spinnerModel.getMinimum());
            assertEquals(65535, spinnerModel.getMaximum());
            assertEquals(1, spinnerModel.getValue());
        }
    }

    @Test
    void columnsCannotBeReorderedAndHaveMinimumWidths() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        JTable table = componentRef.get().getTableVariables();
        assertFalse(table.getTableHeader().getReorderingAllowed());

        int[] minimumWidths = {
            60, 120, 120, 90, 110, 200, 80, 80, 80,
            100, 110, 200, 90, 100, 100, 110, 100
        };
        for (int column = 0; column < minimumWidths.length; column++) {
            assertTrue(table.getColumnModel().getColumn(column).getMinWidth()
                    >= minimumWidths[column]);
        }

        DefaultTableCellRenderer renderer =
                (DefaultTableCellRenderer) table.getDefaultRenderer(String.class);
        assertEquals(SwingConstants.CENTER, renderer.getHorizontalAlignment());
    }

    @Test
    void textColumnsLimitTheirCharacterCount() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        JTable table = componentRef.get().getTableVariables();
        assertMaxLength(table, 1, 20);
        assertMaxLength(table, 4, 15);
        assertMaxLength(table, 5, 40);
    }

    @Test
    void loadsExistingVariablesFromXml(@TempDir Path folder) throws Exception {
        Properties deviceProperties = new Properties();
        deviceProperties.setProperty("deviceName", "PLC-1");
        deviceProperties.setProperty("deviceKey", "key-1");
        deviceProperties.setProperty("uuid", "device-1");
        DeviceConfigData device = new DeviceConfigData(deviceProperties);
        PvConfigData pv = new PvConfigData(
                "pv-1", "Temperature", "real", "PLC-1", "%DB1.DBD0",
                "Oven temperature", "1000ms", true, false,
                "0", "100", "", "", "", "", "", "", "");

        DynamicVariableXmlMapper.write(
                folder.toFile(), List.of(device), List.of(pv));

        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));
        CreateVariableTopComponent component = componentRef.get();

        SwingUtilities.invokeAndWait(() -> component.loadVariablesFromXml(folder.toFile()));

        JTable table = component.getTableVariables();
        assertEquals(1, table.getRowCount());
        assertEquals("Temperature", table.getValueAt(0, 1));
        assertEquals("PLC-1", table.getValueAt(0, 2));
        assertEquals("real", table.getValueAt(0, 3));
        assertEquals("%DB1.DBD0", table.getValueAt(0, 4));
        assertEquals("Oven temperature", table.getValueAt(0, 5));
    }

    private static void assertMaxLength(JTable table, int column, int maxLength) {
        TableCellEditor editor = table.getCellEditor(0, column);
        JTextField textField = (JTextField) editor.getTableCellEditorComponent(
                table, "", true, 0, column);

        textField.setText("x".repeat(maxLength + 10));

        assertEquals(maxLength, textField.getText().length());
    }
}
