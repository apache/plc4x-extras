/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardState;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.NoopCommunicationStore;
import java.lang.reflect.Method;
import java.util.List;

class StepGroupAreaPanelTest {

    private StepGroupAreaPanel panelWithOneGroupAndOneArea() {
        CommunicationWizardController controller =
                new CommunicationWizardController(new NoopCommunicationStore(), new CommunicationWizardState());
        controller.addGroup("uuid-grupo", "Rápido", "variables rápidas", "100", true);
        controller.addItem("uuid-area", "Area", "descripción del área",
                "%DB22.DBB4[10..16]:REAL", true, "uuid-grupo");
        StepGroupAreaPanel panel = new StepGroupAreaPanel(controller);
        panel.refresh();
        return panel;
    }

    @Test
    void columnsCannotBeReordered() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        assertFalse(panel.getGroupTable().getTableHeader().getReorderingAllowed(),
                "las columnas de grupos se pueden mover");
        assertFalse(panel.getItemTable().getTableHeader().getReorderingAllowed(),
                "las columnas de áreas se pueden mover");
    }

    @Test
    void wideningOneColumnDoesNotMoveTheOthers() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        assertEquals(JTable.AUTO_RESIZE_OFF, panel.getGroupTable().getAutoResizeMode(),
                "con el autoajuste por defecto el ancho se reparte entre las columnas siguientes");
        assertEquals(JTable.AUTO_RESIZE_OFF, panel.getItemTable().getAutoResizeMode(),
                "con el autoajuste por defecto el ancho se reparte entre las columnas siguientes");
    }

    @Test
    void allColumnsCanBeResized() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        assertRedimensionables(panel.getGroupTable(), "grupos");
        assertRedimensionables(panel.getItemTable(), "áreas");
    }

    private void assertRedimensionables(JTable table, String cual) {
        TableColumnModel columnas = table.getColumnModel();
        for (int i = 0; i < columnas.getColumnCount(); i++) {
            assertTrue(columnas.getColumn(i).getResizable(),
                    "la columna " + table.getColumnName(i) + " de " + cual
                            + " no se puede redimensionar");
        }
        assertTrue(table.getTableHeader().getResizingAllowed(),
                "la cabecera de " + cual + " no deja redimensionar");
    }

    @Test
    void oneColumnCanBeWidenedUpWithoutCeiling() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        TableColumnModel columnas = panel.getItemTable().getColumnModel();
        TableColumn descripcion = columnas.getColumn(1);
        int preferredInicial = descripcion.getPreferredWidth();
        descripcion.setWidth(preferredInicial + 300);
        assertEquals(preferredInicial + 300, descripcion.getWidth(),
                "no se deja ensanchar la columna más allá del ajuste automático");
    }

    @Test
    void ensancharUnaColumnaNoCambiaElAnchoDeLasOtras() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        TableColumnModel columnas = panel.getItemTable().getColumnModel();
        int[] antes = new int[columnas.getColumnCount()];
        for (int i = 0; i < columnas.getColumnCount(); i++) {
            antes[i] = columnas.getColumn(i).getWidth();
        }

        columnas.getColumn(2).setWidth(antes[2] + 250);

        for (int i = 0; i < columnas.getColumnCount(); i++) {
            if (i == 2) {
                continue;
            }
            assertEquals(antes[i], columnas.getColumn(i).getWidth(),
                    "la columna " + panel.getItemTable().getColumnName(i)
                            + " se movió al ensanchar otra");
        }
    }

    @Test
    void cellsAreNotEditedDirectly() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        JTable grupos = panel.getGroupTable();
        assertFalse(grupos.getModel().isCellEditable(0, 0));
        assertFalse(grupos.getModel().isCellEditable(0, 3), "ni la columna Enable");
        assertFalse(grupos.getModel().isCellEditable(0, 4), "ni el UUID");

        JTable areas = panel.getItemTable();
        assertFalse(areas.getModel().isCellEditable(0, 2), "ni el Tag");
        assertFalse(areas.getModel().isCellEditable(0, 7), "ni el UUID");
    }

    @Test
    void userCannotTypeIntoTheCells() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();

        JTable grupos = panel.getGroupTable();
        assertEquals("Rápido", grupos.getValueAt(0, 0));
        assertFalse(grupos.editCellAt(0, 0),
                "arranca un editor en la tabla de grupos: el usuario podría escribir");
        assertEquals("Rápido", grupos.getValueAt(0, 0),
                "el nombre del grupo cambió al intentar editarlo");

        JTable areas = panel.getItemTable();
        assertEquals("%DB22.DBB4[10..16]:REAL", areas.getValueAt(0, 2));
        assertFalse(areas.editCellAt(0, 2),
                "arranca un editor en la tabla de áreas: el usuario podría escribir");
        assertEquals("%DB22.DBB4[10..16]:REAL", areas.getValueAt(0, 2),
                "el tag del área cambió al intentar editarlo");
    }

    @Test
    void badlyWrittenTagIsMarkedInRed() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        panel.getItemTag().setText("%DB1.DBB0[.0.9]:BYTE");
        assertNotEquals(edgeOf(panel), panel.getItemTag().getBorder(),
                "un rango mal escrito tiene que marcar el campo");
    }

    @Test
    void wellWrittenTagIsNotMarked() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        panel.getItemTag().setText("%DB1.DBB0[0..9]:BYTE");
        assertEquals(edgeOf(panel), panel.getItemTag().getBorder(),
                "un tag correcto no puede quedar marcado");
    }

    @Test
    void warningDisappearsWhenFixed() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        panel.getItemTag().setText("%DB1.DBB0[.0.9]:BYTE");
        assertNotEquals(edgeOf(panel), panel.getItemTag().getBorder());
        panel.getItemTag().setText("%DB1.DBB0[0..9]:BYTE");
        assertEquals(edgeOf(panel), panel.getItemTag().getBorder(),
                "el aviso tiene que irse al corregir el tag");
    }

    @Test
    @EnabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    void unAvisoDeTagNoRompeSinPantalla() throws Exception {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        Method mostrarAviso = TagField.class.getDeclaredMethod("mostrarAvisoTag", List.class);
        mostrarAviso.setAccessible(true);
        assertDoesNotThrow(() -> mostrarAviso.invoke(panel.getItemTag(), List.of()),
                "sin pantalla no se puede colocar el globo, pero tampoco debe fallar");
    }

    @Test
    void tagErrorStaysMarkedEvenWithoutTooltip() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();

        panel.getItemTag().setText("%DB1.DBB0[.0.9]:BYTE");

        assertNotEquals(edgeOf(panel), panel.getItemTag().getBorder(),
                "el borde rojo es el aviso que queda sin pantalla");
    }

    private static Border edgeOf(StepGroupAreaPanel panel) {
        return panel.getGroupName().getBorder();
    }

    @Test
    void tablesShowTheStateValues() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        JTable grupos = panel.getGroupTable();
        assertEquals(1, grupos.getRowCount());
        assertEquals(5, grupos.getColumnCount());
        assertEquals("Rápido", grupos.getValueAt(0, 0));
        assertEquals("variables rápidas", grupos.getValueAt(0, 1));
        assertEquals("true", grupos.getValueAt(0, 3));

        JTable areas = panel.getItemTable();
        assertEquals(1, areas.getRowCount());
        assertEquals(8, areas.getColumnCount());
        assertEquals("Area", areas.getValueAt(0, 0));
        assertEquals("%DB22.DBB4[10..16]:REAL", areas.getValueAt(0, 2));
        assertEquals("Rápido", areas.getValueAt(0, 5),
                "el área muestra el nombre del grupo al que pertenece");
    }

    @Test
    void namesAreTruncatedAtTwenty() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        panel.getGroupName().setText("g".repeat(40));
        assertEquals(20, panel.getGroupName().getText().length(),
                "el nombre del grupo admite más de 20 caracteres");
        panel.getItemName().setText("a".repeat(40));
        assertEquals(20, panel.getItemName().getText().length(),
                "el nombre del área admite más de 20 caracteres");
    }

    @Test
    void descriptionsAreTruncatedAtSixty() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        panel.getGroupDescription().setText("g".repeat(100));
        assertEquals(60, panel.getGroupDescription().getText().length(),
                "la descripción del grupo admite más de 60 caracteres");
        panel.getItemDescription().setText("a".repeat(100));
        assertEquals(60, panel.getItemDescription().getText().length(),
                "la descripción del área admite más de 60 caracteres");
    }

    @Test
    void justoAlTopeSeEscribeEntero() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        panel.getGroupName().setText("g".repeat(20));
        assertEquals(20, panel.getGroupName().getText().length());
        panel.getItemDescription().setText("d".repeat(60));
        assertEquals(60, panel.getItemDescription().getText().length());
    }

    @Test
    void tagIsNotTruncatedBecauseItIsAnAddress() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        String tag = "%DB22.DBB4[10..16]:REAL";
        panel.getItemTag().setText(tag);
        assertEquals(tag, panel.getItemTag().getText());
    }

    @Test
    void plc4xSyntaxTagsAreAccepted() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        for (String tag : new String[]{
                "%DB1.DBX0.0:BOOL", "%DB10.DBW20:INT", "%I0.0:BOOL", "%MD100:DINT",
                "%DB1.DBB0[0..9]:BYTE", "%DB5:10:INT", "%DB1.DBB0:STRING(80)", "%DB1.DBB0:STRING"}) {
            assertNull(panel.tagProblem(tag, null),
                    "«" + tag + "» es un tag válido de PLC4X y no debe rechazarse");
        }
    }

    @Test
    void bitMarkersAreAccepted() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        for (String tag : new String[]{"%Q0.1:BOOL", "%M100.0:BOOL", "%IB0.0"}) {
            assertNull(panel.tagProblem(tag, null), "«" + tag + "» no debe rechazarse");
        }
    }

    @Test
    void reversedRangeIsRejected() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();

        String problema = panel.tagProblem("%DB21.DBB0[1848..0]:BYTE", null);

        assertNotNull(problema, "[1848..0] va al revés y hay que rechazarlo");
        assertTrue(problema.contains("al revés"), "el mensaje debe explicarlo: " + problema);
        assertFalse(problema.contains("-1"),
                "el mensaje no debe falar de -1, que es el centinela de 'sin rango'");
    }

    @Test
    void dosAreasSinRangoSeAceptan() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();

        assertNull(panel.tagProblem("%DB1.DBB0:BYTE", null));
        assertNull(panel.tagProblem("%DB1.DBL4:REAL", null));
    }

    @Test
    void overlapMessageDoesNotMentionTheSentinel() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();
        assertNull(panel.tagProblem("%DB1.DBB0[0..3]:BYTE", null));

        String problema = panel.tagProblem("%DB1.DBB0[2..5]:BYTE", null);

        if (problema != null) {
            assertFalse(problema.contains("-1"),
                    "un mensaje para el usuario no debe enseñar el centinela: " + problema);
        }
    }

    @Test
    void emptyTagIsRejectedWithExamples() {
        StepGroupAreaPanel panel = panelWithOneGroupAndOneArea();

        String problema = panel.tagProblem("", null);

        assertNotNull(problema, "un tag vacío no se puede guardar");
        assertTrue(problema.contains("%DB231"), "debe ofrecer un ejemplo: " + problema);
    }
}
