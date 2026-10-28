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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableTableModelTest {

    private static CreateVariableController.Row row(String name, String tag, String uuid) {
        return CreateVariableController.defaultRow(name, tag, "PLC-1").withUuid(uuid);
    }

    @Test
    void setRowsRenumeraLasFilas() {
        VariableTableModel model = new VariableTableModel();

        model.setRows(List.of(
                row("Var_1", "%DB1.DBD0", "uuid-1"),
                row("Var_2", "%DB1.DBD4", "uuid-2")));

        assertEquals(2, model.getRowCount());
        assertEquals(1, model.row(0).rowNumber());
        assertEquals(2, model.row(1).rowNumber());
        assertEquals("uuid-1", model.row(0).uuid());
        assertEquals("uuid-2", model.row(1).uuid());
    }

    @Test
    void setValueAtActualizaLaFilaYRecortaElTexto() {
        VariableTableModel model = new VariableTableModel();
        model.setRows(List.of(row("Var_1", "%DB1.DBD0", "uuid-1")));

        model.setValueAt("  Temperature  ", 0, VariableTableModel.COL_NAME);

        assertEquals("Temperature", model.getValueAt(0, VariableTableModel.COL_NAME));
        assertEquals("Temperature", model.row(0).name());
    }

    @Test
    void insertRowsRenumeraLasFilasSiguientes() {
        VariableTableModel model = new VariableTableModel();
        model.setRows(List.of(
                row("Var_1", "%DB1.DBD0", "uuid-1"),
                row("Var_2", "%DB1.DBD4", "uuid-2")));

        model.insertRows(1, List.of(
                row("Var_3", "%DB1.DBD8", "uuid-3"),
                row("Var_4", "%DB1.DBD12", "uuid-4")));

        assertEquals(4, model.getRowCount());
        assertEquals(1, model.row(0).rowNumber());
        assertEquals(2, model.row(1).rowNumber());
        assertEquals(3, model.row(2).rowNumber());
        assertEquals(4, model.row(3).rowNumber());
    }

    @Test
    void removeRowRenumeraLasFilasRestantes() {
        VariableTableModel model = new VariableTableModel();
        model.setRows(List.of(
                row("Var_1", "%DB1.DBD0", "uuid-1"),
                row("Var_2", "%DB1.DBD4", "uuid-2"),
                row("Var_3", "%DB1.DBD8", "uuid-3")));

        model.removeRow(1);

        assertEquals(2, model.getRowCount());
        assertEquals("Var_1", model.row(0).name());
        assertEquals("Var_3", model.row(1).name());
        assertEquals(1, model.row(0).rowNumber());
        assertEquals(2, model.row(1).rowNumber());
    }

    @Test
    void actualizaBooleanosConBooleanOConTexto() {
        VariableTableModel model = new VariableTableModel();
        model.setRows(List.of(row("Var_1", "%DB1.DBD0", "uuid-1")));

        model.setValueAt(Boolean.FALSE, 0, VariableTableModel.COL_SCAN_ENABLE);
        assertFalse(model.row(0).scanEnable());

        model.setValueAt("true", 0, VariableTableModel.COL_SCAN_ENABLE);
        assertTrue(model.row(0).scanEnable());
    }
}
