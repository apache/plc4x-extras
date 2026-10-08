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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.util.Properties;
import javax.swing.table.TableColumnModel;
import javax.swing.text.Document;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.junit.jupiter.api.Test;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardState;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.NoopCommunicationStore;

class StepPvPanelTest {

    private static final CommConfigData.PvConfig PV = new CommConfigData.PvConfig(
            "uuid-pv", "Level", "float", "uuid-area", "0", "descripcion",
            "1000", true, false,
            "1", "5", "fwefwe", "123", "3123", "2", "6", "1",
            "md5-pv", "Area/Level");

    private static DeviceConfigData dispositivo() {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", "PLC de control");
        p.put("deviceKey", "key-control");
        p.put("description", "PLC de control");
        p.put("uuid", "uuid-control");
        p.put("enable", "true");
        // El nodo S88 es obligatorio: sin el, refrescarVariables sale antes de preguntar
        // por la variable en edicion, que es justo el recorrido que rompia.
        p.put("s88Node", "s88-control");
        p.put("s88Uuid", "s88-control");
        p.put("specificParameters", "ipa=10.0.0.1,port=102");
        return new DeviceConfigData(p);
    }

    private StepPvPanel panelConUnaVariable() {
        CommunicationWizardState state = new CommunicationWizardState();
        state.getPvs().add(PV);
        StepPvPanel panel = new StepPvPanel(
                new CommunicationWizardController(new NoopCommunicationStore(), state),
                new PlantModelReader(null));
        panel.refresh();
        return panel;
    }

    /**
     * Panel con variable Y dispositivo seleccionado, que es el estado en el que se
     * abre el asistente.
     *
     * <p>Es la combinacion que hacia que el dialogo no apareciera en un proyecto con
     * variables ya configuradas: al elegir el dispositivo se recarga este panel, y al
     * recargar se pregunta por la variable en edicion antes de que haya ninguna. Sin
     * dispositivo seleccionado el refresco salia antes de llegar ahi, asi que el caso
     * no se daba en los otros proyectos, que no tienen variables.
     */
    private StepPvPanel panelConVariableYDispositivo() {
        CommunicationWizardState state = new CommunicationWizardState();
        state.getPvs().add(PV);
        state.setSelectedDevice(dispositivo());
        StepPvPanel panel = new StepPvPanel(
                new CommunicationWizardController(new NoopCommunicationStore(), state),
                new PlantModelReader(null));
        panel.refresh();
        return panel;
    }

    /**
     * Refrescar con una variable guardada y sin fila seleccionada no debe reventar.
     *
     * <p>Este es el recorrido que abria el asistente y que rompia con
     * {@code NullPointerException}. Se fija aqui, y no solo en el estado, porque el
     * fallo se vio en la aplicacion y no en un test.
     */
    @Test
    void refrescarConDispositivoYVariableSinFilaElegidaNoRevienta() {
        assertDoesNotThrow(() -> panelConVariableYDispositivo(),
                "elegir el dispositivo recarga el panel antes de que haya nada en edicion");
    }

    /** Y el refresco tiene que dejar la variable en la tabla, no solo no reventar. */
    @Test
    void laVariableSigueEnLaTablaDespuesDeElegirDispositivo() {
        StepPvPanel panel = panelConVariableYDispositivo();

        assertEquals(1, panel.getPvTable().getRowCount(),
                "la variable configurada tiene que seguir visible");
    }

    private StepPvPanel panelVacio() {
        return new StepPvPanel(
                new CommunicationWizardController(new NoopCommunicationStore(), new CommunicationWizardState()),
                new PlantModelReader(null));
    }

    private static CommConfigData.PvConfig pvCon(String scanTime, String limiteBajo,
            String limiteAlto, String minStep) {
        return new CommConfigData.PvConfig(
                "uuid-pv", "Level", "float", "uuid-area", "0", "descripcion",
                scanTime, true, false,
                limiteBajo, limiteAlto, "fwefwe", "123", "3123", "2", "6", minStep,
                "md5-pv", "Area/Level");
    }

    private static int valorDe(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    @Test
    void laTablaExponeLosVeinteCamposDelPvRecord() {
        JTable tabla = panelConUnaVariable().getPvTable();
        assertEquals(20, tabla.getColumnCount(), "faltan columnas en la tabla");
        assertEquals(1, tabla.getRowCount(), "la variable no llegó a la tabla");
    }

    @Test
    void laTablaMuestraLosValoresDeLimitesDescripcionFormatoYUnidades() {
        JTable tabla = panelConUnaVariable().getPvTable();
        assertEquals("FALSE", tabla.getValueAt(0, 8), "writeEnable");
        assertEquals("1", tabla.getValueAt(0, 9), "displayLimitLow");
        assertEquals("5", tabla.getValueAt(0, 10), "displayLimitHigh");
        assertEquals("fwefwe", tabla.getValueAt(0, 11), "displayDescription");
        assertEquals("123", tabla.getValueAt(0, 12), "displayFormat");
        assertEquals("3123", tabla.getValueAt(0, 13), "displayUnits");
        assertEquals("2", tabla.getValueAt(0, 14), "controlLimitLow");
        assertEquals("6", tabla.getValueAt(0, 15), "controlLimitHigh");
        assertEquals("1", tabla.getValueAt(0, 16), "controlMinStep");
    }

    @Test
    void laTablaNoDeclaraUnAnchoInferiorAlDeSusColumnas() {
        JTable tabla = panelConUnaVariable().getPvTable();
        TableColumnModel columnas = tabla.getColumnModel();
        int anchoColumnas = 0;
        for (int i = 0; i < columnas.getColumnCount(); i++) {
            anchoColumnas += columnas.getColumn(i).getWidth();
        }
        int anchoDeclarado = tabla.getPreferredSize().width;
        assertTrue(anchoDeclarado >= anchoColumnas,
                "la tabla se declara de " + anchoDeclarado + " px pero sus columnas suman "
                        + anchoColumnas
                        + " px: las últimas quedan recortadas y, al no haber barra de scroll,"
                        + " tampoco se pueden redimensionar");
    }

    @Test
    void todasLasColumnasSePuedenRedimensionar() {
        JTable tabla = panelConUnaVariable().getPvTable();
        TableColumnModel columnas = tabla.getColumnModel();
        for (int i = 0; i < columnas.getColumnCount(); i++) {
            assertTrue(columnas.getColumn(i).getResizable(),
                    "la columna " + tabla.getColumnName(i) + " no se puede redimensionar");
        }
    }

    @Test
    void lasColumnasNoSePuedenReodenar() {
        JTable tabla = panelConUnaVariable().getPvTable();
        assertFalse(tabla.getTableHeader().getReorderingAllowed(),
                "las columnas se pueden mover de sitio");
    }

    @Test
    void lasCeldasNoSeEditanDirectamente() {
        JTable tabla = panelConUnaVariable().getPvTable();
        assertFalse(tabla.getModel().isCellEditable(0, 0));
        assertFalse(tabla.getModel().isCellEditable(0, 11));
    }

    @Test
    void elScanTimeOfreceLasMismasOfertasQueElGrupo() {
        JComboBox<String> combo = panelVacio().getScanTimeCombo();
        assertEquals(10, combo.getItemCount(), "de 100 a 1000 de cien en cien son diez");
        assertEquals("100", combo.getItemAt(0));
        assertEquals("1000", combo.getItemAt(9));
        assertFalse(combo.isEditable(), "las ofertas son cerradas, no se escribe una de otra");
    }

    @Test
    void elScanTimeEmpiezaSinSeleccionPorqueEsObligatorio() {
        assertNull(panelVacio().getScanTimeCombo().getSelectedItem(),
                "con algo seleccionado de salida no se sabría qué falta completar");
    }

    @Test
    void unScanTimeGuardadoSeSeleccionaSiEstaEnLasOfertas() {
        StepPvPanel panel = panelVacio();
        panel.cargarEnFormulario(pvCon("300", "1", "5", "1"));
        assertEquals("300", panel.getScanTimeCombo().getSelectedItem());
    }

    @Test
    void unScanTimeFueraDeLasOfertasSeQuedaSinSeleccionar() {
        StepPvPanel panel = panelVacio();
        panel.cargarEnFormulario(pvCon("2500", "1", "5", "1"));
        assertNull(panel.getScanTimeCombo().getSelectedItem(),
                "se inventó un ScanTime que no estaba en las ofertas");
        assertFalse(panel.getAjustesAlCargar().isEmpty(),
                "y no se avisa de que el valor guardado no tiene sitio en el desplegable");
    }

    @Test
    void losCamposNumericosTienenElRangoPedido() {
        StepPvPanel panel = panelVacio();
        for (JSpinner spinner : List.of(panel.getDisplayLimitLow(), panel.getDisplayLimitHigh(),
                panel.getControlLimitLow(), panel.getControlLimitHigh(), panel.getControlMinStep())) {
            SpinnerNumberModel modelo = (SpinnerNumberModel) spinner.getModel();
            assertEquals(1, modelo.getMinimum(), "el mínimo es 1");
            assertEquals(65535, modelo.getMaximum(), "el máximo es 65535");
            assertEquals(1, modelo.getStepSize(), "sube de uno en uno");
        }
    }

    @Test
    void losCamposNumericosSePuedenEscribirYNoSoloPulsarLasFlechas() {
        StepPvPanel panel = panelVacio();
        JSpinner spinner = panel.getDisplayLimitHigh();
        assertTrue(spinner.getEditor() instanceof JSpinner.NumberEditor,
                "el editor por defecto no deja escribir el número");
        spinner.setValue(4000);
        assertEquals(4000, valorDe(spinner));
    }

    @Test
    void loGuardadoSeCargaTalCualCuandoEncaja() {
        StepPvPanel panel = panelVacio();
        panel.cargarEnFormulario(pvCon("100", "10", "500", "5"));
        assertEquals(10, valorDe(panel.getDisplayLimitLow()));
        assertEquals(500, valorDe(panel.getDisplayLimitHigh()));
        assertEquals(5, valorDe(panel.getControlMinStep()));
        assertTrue(panel.getAjustesAlCargar().isEmpty(),
                "no hay nada que corregir: " + panel.getAjustesAlCargar());
    }

    @Test
    void unDecimalSeQuedaConSuParteEntera() {
        StepPvPanel panel = panelVacio();
        panel.cargarEnFormulario(pvCon("100", "12.5", "-273.15", "0.9"));
        assertEquals(12, valorDe(panel.getDisplayLimitLow()), "trunca, no redondea");
        assertEquals(1, valorDe(panel.getDisplayLimitHigh()), "un negativo no cabe en el rango");
        assertEquals(1, valorDe(panel.getControlMinStep()),
                "0.9 truncado es 0, que tampoco llega al mínimo");
        assertEquals(2, panel.getAjustesAlCargar().size(),
                "los dos descartes se avisan: " + panel.getAjustesAlCargar());
    }

    @Test
    void unValorFueraDeRangoSeRecortaAlExtremo() {
        StepPvPanel panel = panelVacio();
        panel.cargarEnFormulario(pvCon("100", "70000", "0", "1"));
        assertEquals(65535, valorDe(panel.getDisplayLimitLow()), "por encima del máximo");
        assertEquals(1, valorDe(panel.getDisplayLimitHigh()), "por debajo del mínimo");
        assertFalse(panel.getAjustesAlCargar().isEmpty(),
                "un recorte tiene que avisarse antes de guardar, o el usuario no"
                        + " se entera de que el valor guardado cambia");
    }

    @Test
    void unValorQueNoEsNumeroSeSustituyePorElMinimo() {
        StepPvPanel panel = panelVacio();
        panel.cargarEnFormulario(pvCon("100", "abc", "", null));
        assertEquals(1, valorDe(panel.getDisplayLimitLow()));
        assertEquals(1, valorDe(panel.getDisplayLimitHigh()), "un vacío tampoco es un número");
        assertEquals(1, valorDe(panel.getControlMinStep()), "y un nulo tampoco");
        assertEquals(3, panel.getAjustesAlCargar().size(),
                "los tres se avisan por separado: " + panel.getAjustesAlCargar());
    }

@Test
    void losCamposNumericosEmpiecenEnElMinimo() {
        StepPvPanel panel = panelVacio();
        assertEquals(1, valorDe(panel.getDisplayLimitLow()));
        assertEquals(1, valorDe(panel.getDisplayLimitHigh()));
        assertEquals(1, valorDe(panel.getControlLimitLow()));
        assertEquals(1, valorDe(panel.getControlLimitHigh()));
        assertEquals(1, valorDe(panel.getControlMinStep()));
    }

    @Test
    void losCamposNumericosNoAdmitenLetras() throws Exception {
        JSpinner spinner = panelVacio().getDisplayLimitHigh();
        JSpinner.NumberEditor editor = (JSpinner.NumberEditor) spinner.getEditor();
        JTextField campo = editor.getTextField();
        campo.setText("12");
        Document doc = campo.getDocument();
        doc.insertString(doc.getLength(), "a", null);
        doc.insertString(doc.getLength(), "-", null);
        doc.insertString(doc.getLength(), ",", null);
        assertEquals("12", campo.getText(),
                "el editor dejó pasar algo que no es un dígito");
    }

    @Test
    void escribirMasDeSesentaEnUnaDescripcionSeRecorta() {
        StepPvPanel panel = panelVacio();
        panel.getDisplayDescription().setText("d".repeat(80));
        assertEquals(60, panel.getDisplayDescription().getText().length(),
                "la descripción del PV admite más de 60 caracteres");
        panel.getDescriptor().setText("x".repeat(80));
        assertEquals(60, panel.getDescriptor().getText().length(),
                "el descriptor admite más de 60 caracteres");
    }

    @Test
    void elFormatoYLasUnidadesNoSeCortan() {
        StepPvPanel panel = panelVacio();
        panel.getDisplayFormat().setText("f".repeat(80));
        assertEquals(80, panel.getDisplayFormat().getText().length());
        panel.getDisplayUnits().setText("u".repeat(80));
        assertEquals(80, panel.getDisplayUnits().getText().length());
    }
}
