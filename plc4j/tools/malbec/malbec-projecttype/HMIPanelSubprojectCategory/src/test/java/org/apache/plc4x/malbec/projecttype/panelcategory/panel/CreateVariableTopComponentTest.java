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
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
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
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        deviceProperties.setProperty("protocol", "S7");
        deviceProperties.setProperty("specificParameters", "ipa=10.0.0.1");
        deviceProperties.setProperty("enable", "true");
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

    @Test
    void detectsUnsavedChangesForCancelConfirmation(@TempDir Path folder) throws Exception {
        Properties deviceProperties = new Properties();
        deviceProperties.setProperty("deviceName", "PLC-1");
        deviceProperties.setProperty("deviceKey", "key-1");
        deviceProperties.setProperty("uuid", "device-1");
        deviceProperties.setProperty("protocol", "S7");
        deviceProperties.setProperty("specificParameters", "ipa=10.0.0.1");
        deviceProperties.setProperty("enable", "true");
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
        assertFalse(component.hasUnsavedChanges());

        JTable table = component.getTableVariables();
        SwingUtilities.invokeAndWait(() -> table.setValueAt("Oven", 0, 5));
        assertTrue(component.hasUnsavedChanges());

        SwingUtilities.invokeAndWait(() -> table.setValueAt("Oven temperature", 0, 5));
        assertFalse(component.hasUnsavedChanges());

        SwingUtilities.invokeAndWait(component::addVariableRow);
        assertTrue(component.hasUnsavedChanges());

        SwingUtilities.invokeAndWait(() -> component.loadVariablesFromXml(folder.toFile()));
        assertFalse(component.hasUnsavedChanges());
    }

    @Test
    void loadsDuplicateNamesAndMarksThem(@TempDir Path folder) throws Exception {
        Properties deviceProperties = new Properties();
        deviceProperties.setProperty("deviceName", "PLC-1");
        deviceProperties.setProperty("deviceKey", "key-1");
        deviceProperties.setProperty("uuid", "device-1");
        deviceProperties.setProperty("protocol", "S7");
        deviceProperties.setProperty("specificParameters", "ipa=10.0.0.1");
        deviceProperties.setProperty("enable", "true");
        DeviceConfigData device = new DeviceConfigData(deviceProperties);
        PvConfigData first = new PvConfigData(
                "pv-1", "Temperature", "real", "PLC-1", "%DB1.DBD0",
                "", "1000ms", true, false,
                "0", "100", "", "", "", "", "", "", "");
        PvConfigData second = new PvConfigData(
                "pv-2", "Temperature_Copy", "real", "PLC-1", "%DB1.DBD4",
                "", "1000ms", true, false,
                "0", "100", "", "", "", "", "", "", "");

        DynamicVariableXmlMapper.write(folder.toFile(), List.of(device), List.of(first, second));
        Path xmlFile = folder.resolve(DynamicVariableXmlMapper.FILE_NAME);
        String xml = Files.readString(xmlFile);
        Files.writeString(xmlFile, xml.replace("name=\"Temperature_Copy\"", "name=\"Temperature\""));

        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));
        CreateVariableTopComponent component = componentRef.get();

        SwingUtilities.invokeAndWait(() -> component.loadVariablesFromXml(folder.toFile()));

        JTable table = component.getTableVariables();
        assertEquals(2, table.getRowCount());
        assertTrue(tooltipAt(table, 0, 1).contains("used more than once"));
        assertTrue(tooltipAt(table, 1, 1).contains("used more than once"));
    }

    @Test
    void duplicateCountSpinnerStartsAtOne() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        SpinnerNumberModel model = (SpinnerNumberModel)
                component.getDuplicateCountSpinner().getModel();

        assertEquals(1, model.getMinimum());
        assertEquals(100, model.getMaximum());
        assertEquals(1, model.getValue());
        assertEquals(1, model.getStepSize());
    }

    @Test
    void duplicatesSelectedRowWithNamesTagsAndCopiedValues() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        JTable table = component.getTableVariables();

        SwingUtilities.invokeAndWait(() -> {
            table.setValueAt("PLC-1", 0, 2);
            table.setValueAt("int", 0, 3);
            table.setValueAt("Temperature", 0, 1);
            table.setValueAt("%DB1.DBD0", 0, 4);
            table.setValueAt("Oven", 0, 5);
            component.duplicateRow(0, 3);
        });

        assertEquals(4, table.getRowCount());
        assertEquals("Temperature_1", table.getValueAt(1, 1));
        assertEquals("Temperature_2", table.getValueAt(2, 1));
        assertEquals("Temperature_3", table.getValueAt(3, 1));
        assertEquals("%DB1.DBD4", table.getValueAt(1, 4));
        assertEquals("%DB1.DBD8", table.getValueAt(2, 4));
        assertEquals("%DB1.DBD12", table.getValueAt(3, 4));
        assertEquals("Oven", table.getValueAt(1, 5));
        assertEquals("Oven", table.getValueAt(2, 5));
        assertEquals("Oven", table.getValueAt(3, 5));

        Object uuid1 = table.getModel().getValueAt(1, 17);
        Object uuid2 = table.getModel().getValueAt(2, 17);
        Object uuid3 = table.getModel().getValueAt(3, 17);
        assertNotEquals(table.getModel().getValueAt(0, 17), uuid1);
        assertNotEquals(uuid1, uuid2);
        assertNotEquals(uuid2, uuid3);
        assertNotEquals(uuid1, uuid3);
    }

    @Test
    void duplicatesRowsWithBlankTags() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        JTable table = component.getTableVariables();

        SwingUtilities.invokeAndWait(() -> {
            table.setValueAt("Temperature", 0, 1);
            table.setValueAt("", 0, 4);
            component.duplicateRow(0, 2);
        });

        assertEquals(3, table.getRowCount());
        assertEquals("Temperature_1", table.getValueAt(1, 1));
        assertEquals("Temperature_2", table.getValueAt(2, 1));
        assertEquals("", table.getValueAt(1, 4));
        assertEquals("", table.getValueAt(2, 4));
    }

    @Test
    void duplicateUsesNextFreeNameSuffix() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        JTable table = component.getTableVariables();

        SwingUtilities.invokeAndWait(() -> {
            table.setValueAt("Temperature", 0, 1);
            table.setValueAt("%DB1.DBD0", 0, 4);
            component.addVariableRow();
            table.setValueAt("Temperature_1", 1, 1);
            component.duplicateRow(0, 2);
        });

        assertEquals(4, table.getRowCount());
        assertEquals("Temperature_2", table.getValueAt(1, 1));
        assertEquals("Temperature_3", table.getValueAt(2, 1));
        assertEquals("Temperature_1", table.getValueAt(3, 1));
    }

    @Test
    void invalidDuplicateTagDoesNotInsertRows() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        JTable table = component.getTableVariables();
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            table.setValueAt("Temperature", 0, 1);
            table.setValueAt("%DB1.DBB0[0..9]", 0, 4);
            try {
                component.duplicateRow(0, 1);
            } catch (IllegalArgumentException ex) {
                thrown.set(ex);
            }
        });

        assertTrue(thrown.get() instanceof IllegalArgumentException);
        assertEquals(1, table.getRowCount());
    }

    @Test
    void marksDuplicateNamesAndShowsTooltipWithoutSaving() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        CreateVariableTopComponent component = componentRef.get();
        JTable table = component.getTableVariables();

        SwingUtilities.invokeAndWait(() -> {
            table.setValueAt("PLC-1", 0, 2);
            table.setValueAt("int", 0, 3);
            table.setValueAt("Var_1", 0, 1);
            table.setValueAt("%DB1.DBD0", 0, 4);
            component.addVariableRow();
            table.setValueAt("PLC-1", 1, 2);
            table.setValueAt("int", 1, 3);
            table.setValueAt("Var_1", 1, 1);
            table.setValueAt("%DB1.DBD4", 1, 4);
        });

        String firstToolTip = tooltipAt(table, 0, 1);
        String secondToolTip = tooltipAt(table, 1, 1);
        assertTrue(firstToolTip.contains("used more than once"));
        assertTrue(secondToolTip.contains("used more than once"));

        table.clearSelection();
        for (int column = 0; column < table.getColumnCount(); column++) {
            if (column == VariableTableModel.COL_NAME) {
                continue;
            }
            Component renderer = table.prepareRenderer(
                    table.getCellRenderer(0, column), 0, column);
            assertEquals(table.getBackground(), renderer.getBackground(),
                    "column " + column + " must not be highlighted");
        }

        Component firstRenderer = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        Component secondRenderer = table.prepareRenderer(table.getCellRenderer(1, 1), 1, 1);
        assertNotEquals(table.getBackground(), firstRenderer.getBackground());
        assertNotEquals(table.getBackground(), secondRenderer.getBackground());

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        Component selectedError = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        assertEquals(VariableTablePanel.ERROR_BACKGROUND, selectedError.getBackground());
        assertEquals(VariableTablePanel.ERROR_FOREGROUND, selectedError.getForeground());

        SwingUtilities.invokeAndWait(() -> table.clearSelection());
        SwingUtilities.invokeAndWait(() -> table.setValueAt("Var_2", 1, 1));

        Component fixedFirst = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        Component fixedSecond = table.prepareRenderer(table.getCellRenderer(1, 1), 1, 1);
        assertEquals(table.getBackground(), fixedFirst.getBackground());
        assertEquals(table.getBackground(), fixedSecond.getBackground());
    }

    @Test
    void tableHeaderShowsColumnTooltips() throws Exception {
        AtomicReference<CreateVariableTopComponent> componentRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> componentRef.set(new CreateVariableTopComponent()));

        JTableHeader header = componentRef.get().getTableVariables().getTableHeader();
        Rectangle rect = header.getHeaderRect(VariableTableModel.COL_NAME);
        MouseEvent event = new MouseEvent(header, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, rect.x + 2, rect.y + 2, 0, false);

        assertTrue(header.getToolTipText(event).contains("unique"));
    }

    private static String tooltipAt(JTable table, int row, int column) {
        Rectangle rect = table.getCellRect(row, column, true);
        MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, rect.x + 2, rect.y + 2, 0, false);
        return table.getToolTipText(event);
    }

    private static void assertMaxLength(JTable table, int column, int maxLength) {
        TableCellEditor editor = table.getCellEditor(0, column);
        JTextField textField = (JTextField) editor.getTableCellEditorComponent(
                table, "", true, 0, column);

        textField.setText("x".repeat(maxLength + 10));

        assertEquals(maxLength, textField.getText().length());
    }
}
