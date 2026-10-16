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

final class PvRenderers {

    private PvRenderers() {
    }

    /** Como se ve un tipo de dato, segun lo que ocupa en el area. */
    static String weightOf(DataType type) {
        return type.bitAddressed() ? "1 bit" : type.byteSize() + " bytes";
    }

    static DefaultListCellRenderer typeRenderer(
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
                            + (elegible ? weightOf(t) : "no disponible") + ")");
                } else {
                    label.setText("—");
                }
                return label;
            }
        };
    }

    static DefaultListCellRenderer areaRenderer() {
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

    static DefaultListCellRenderer variableRenderer() {
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