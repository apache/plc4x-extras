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

import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PlantVariable;

/**
 * Los tres pintores de los desplegables del formulario de variable de proceso.
 *
 * <p>Eran tres clases anonimas de unas veinte lineas cada una dentro de
 * {@code StepPvPanel}, y metian el fichero por encima de mil lineas sin que aportaran
 * nada al resto del panel. Aqui viven, y lo unico que hacen es pintar una fila.
 *
 * <p>No saben nada de que se esta configurando ni de por que un tipo esta en gris: solo
 * reciben lo que necesitan para decidir. La decision de si un tipo cabe la toma el
 * controlador, que es quien conoce el area.
 */
final class PvRenderers {

    private PvRenderers() {
    }

    /** Como se ve un tipo de dato, segun lo que ocupa en el area. */
    static String pesoDe(DataType type) {
        return type.bitAddressed() ? "1 bit" : type.byteSize() + " bytes";
    }

    /**
     * Pinta los tipos marcando en gris los que el area no admite.
     *
     * <p>Se queda en gris en vez de desaparecer: asi el usuario ve que el tipo existe y
     * por que no puede elegirlo. Ocultarlos haria que el desplegable pareciera corto sin
     * explicacion.
     *
     * @param areaUuid area seleccionada ahora mismo, no un uuid fijo: el area cambia
     *                 mientras el desplegable esta abierto y el pintor se llama en cada
     *                 fila que se dibuja
     * @param admite   decide si el area acepta ese tipo
     */
    static DefaultListCellRenderer deTipos(
            java.util.function.Supplier<String> areaUuid,
            java.util.function.BiPredicate<String, DataType> admite) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataType t) {
                    boolean elegible = admite.test(areaUuid.get(), t);
                    label.setEnabled(elegible);
                    label.setText(t.label() + "  ("
                            + (elegible ? pesoDe(t) : "no disponible") + ")");
                } else {
                    label.setText("—");
                }
                return label;
            }
        };
    }

    /** Pinta un area como "nombre (tag)", que es como la reconoce el usuario. */
    static DefaultListCellRenderer deAreas() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setText(value instanceof CommConfigData.ItemConfig i
                        ? i.getName() + "  (" + i.getTag() + ")"
                        : " ");
                return label;
            }
        };
    }

    /**
     * Pinta una variable de planta, marcando las que ya estan en uso.
     *
     * <p>Una variable ocupada se ve, pero no se deja elegir: es informacion, no una
     * opcion. El texto del tooltip explica por que esta en gris, porque el color solo
     * no dice nada.
     */
    static DefaultListCellRenderer deVariables() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof PlantVariable v) {
                    String text = v.getPath() == null || v.getPath().isEmpty()
                            ? v.getName() + "  (" + v.getType() + ")"
                            : v.getPath() + "/" + v.getName();
                    if (v.isUsed()) {
                        label.setEnabled(false);
                        label.setToolTipText("Variable ya asignada a otra configuración");
                        text += "  — en uso";
                    }
                    label.setText(text);
                } else {
                    label.setText("-- Seleccione --");
                }
                return label;
            }
        };
    }
}