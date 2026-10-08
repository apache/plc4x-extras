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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;

/**
 * La tabla de variables configuradas.
 *
 * <p>Trae el modelo, el {@code JTable}, los anchos y la seleccion. No sabe nada del
 * formulario ni de como se guarda: solo muestra una lista de {@code PvConfig} y avisa
 * de que el usuario ha cambiado de fila.
 *
 * <p>Los anchos tienen memoria: si el usuario ensancha una columna, se respeta al
 * rellenar la tabla, porque si no cada vez que se guarda y se recarga la tabla vuelve a
 * su tamano automatico y el usuario pierde el ajuste que hizo a proposito.
 */
final class PvTablePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Las veinte columnas del registro de variable de proceso. */
    private static final String[] COLUMNAS = {
        "Variable", "Área", "PvId", "Tipo", "Offset", "Descriptor", "ScanTime",
        "Scan", "Write", "Lím. bajo", "Lím. alto", "Descripción", "Formato", "Unidades",
        "Ctrl. bajo", "Ctrl. alto", "Ctrl. MinStep", "Ruta S88", "UUID", "Md5"
    };

    private final NonEditableTableModel modelo = new NonEditableTableModel(COLUMNAS);
    private final JTable tabla = new JTable(modelo) {
        private static final long serialVersionUID = 1L;

        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            if (row >= 0 && column >= 0) {
                Object value = getValueAt(row, column);
                return value == null ? null : value.toString();
            }
            return super.getToolTipText(event);
        }
    };

    private final int[] anchosUsuario = new int[COLUMNAS.length];
    private final boolean[] anchoEditado = new boolean[COLUMNAS.length];
    private boolean ajustandoAnchos;
    private boolean suppressSelection;
    private Runnable alCambiarSeleccion = () -> { };

    PvTablePanel() {
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.setFillsViewportHeight(false);
        tabla.setIntercellSpacing(new Dimension(6, 2));
        tabla.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(tabla);
        WizardUi.limitTableSpace(scroll, 700, 460);

        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createTitledBorder("Variables configuradas"));
        add(scroll, BorderLayout.CENTER);

        instalarAnchosAjustables();
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressSelection) {
                return;
            }
            // En invokeLater porque al rellenar la tabla Swing puede llamar a esto en
            // mitad de un cambio de modelo, y desde ahi se toca el formulario.
            SwingUtilities.invokeLater(alCambiarSeleccion);
        });
    }

    JTable tabla() {
        return tabla;
    }

    /** A quien hay que avisar cuando el usuario elige otra fila. */
    void setAlCambiarSeleccion(Runnable r) {
        this.alCambiarSeleccion = r;
    }

    int filaSeleccionada() {
        return tabla.getSelectedRow();
    }

    /**
     * Vacia la seleccion sin que eso dispare el aviso de cambio.
     *
     * <p>Hace falta porque al rellenar la tabla se pierde la seleccion, y si eso
     * contara como "el usuario ha cambiado de fila" el panel abriria el formulario de
     * edicion cada vez que se guarda.
     */
    void limpiarSeleccion() {
        suppressSelection = true;
        try {
            tabla.clearSelection();
        } finally {
            suppressSelection = false;
        }
    }

    /** Vuelca las variables en la tabla y reajusta los anchos. */
    void refrescar(List<CommConfigData.PvConfig> pvs,
            java.util.function.Function<String, String> nombreDelArea) {
        modelo.setRowCount(0);
        for (CommConfigData.PvConfig pv : pvs) {
            modelo.addRow(new Object[]{
                pv.getName(),
                nombreDelArea.apply(pv.getAreaUuid()),
                pv.getAreaUuid(),
                pv.getType(),
                pv.getOffset(),
                pv.getDescriptor(),
                pv.getScanTime(),
                pv.isScanEnable() ? "TRUE" : "FALSE",
                pv.isWriteEnable() ? "TRUE" : "FALSE",
                pv.getDisplayLimitLow(),
                pv.getDisplayLimitHigh(),
                pv.getDisplayDescription(),
                pv.getDisplayFormat(),
                pv.getDisplayUnits(),
                pv.getControlLimitLow(),
                pv.getControlLimitHigh(),
                pv.getControlMinStep(),
                pv.getS88Path(),
                pv.getUuid(),
                pv.getMd5()
            });
        }
        ajustarAnchos();
        limpiarSeleccion();
    }

    private void instalarAnchosAjustables() {
        for (int i = 0; i < tabla.getColumnModel().getColumnCount(); i++) {
            final int indice = i;
            tabla.getColumnModel().getColumn(i).addPropertyChangeListener(e -> {
                if (ajustandoAnchos || !TableColumn.COLUMN_WIDTH_PROPERTY.equals(e.getPropertyName())) {
                    return;
                }
                TableColumn columna = tabla.getColumnModel().getColumn(indice);
                anchosUsuario[indice] = columna.getWidth();
                anchoEditado[indice] = true;
            });
        }
    }

    private void ajustarAnchos() {
        ajustandoAnchos = true;
        try {
            WizardUi.sizeColumnsToContent(tabla, 90, 320);
            for (int i = 0; i < COLUMNAS.length && i < tabla.getColumnModel().getColumnCount(); i++) {
                if (anchoEditado[i]) {
                    TableColumn columna = tabla.getColumnModel().getColumn(i);
                    columna.setPreferredWidth(anchosUsuario[i]);
                    columna.setWidth(anchosUsuario[i]);
                }
            }
        } finally {
            ajustandoAnchos = false;
        }
    }
}