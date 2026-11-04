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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

public final class WizardUi {

    public static final int MAX_NOMBRE = 20;
    public static final int MAX_DESCRIPCION = 60;
    private static final String CLIENTE_REPARTIDO = "wizardUi.repartoAncho";

    private WizardUi() {
    }

    public static JTextField limitedText(int columnas, int max) {
        JTextField campo = new JTextField(columnas);
        campo.setDocument(new DocumentoLimitado(max));
        return campo;
    }

    private static final class DocumentoLimitado extends PlainDocument {

        private final int max;

        DocumentoLimitado(int max) {
            this.max = max;
        }

        @Override
        public void insertString(int offs, String str, AttributeSet a)
                throws BadLocationException {
            if (str == null) {
                return;
            }
            int caben = max - getLength();
            if (caben <= 0) {
                return;
            }
            super.insertString(offs, str.length() > caben ? str.substring(0, caben) : str, a);
        }
    }

    public static void limitTableSpace(JScrollPane scroll, int width, int height) {
        scroll.setPreferredSize(new Dimension(width, height));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    }

    public static void configColumns(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
    }

    public static void distributeVisibleWidth(JTable table) {
        applyVisibleWidth(table);
        Container padre = table.getParent();
        if (!(padre instanceof JViewport viewport)) {
            return;
        }
        if (Boolean.TRUE.equals(table.getClientProperty(CLIENTE_REPARTIDO))) {
            return;
        }
        table.putClientProperty(CLIENTE_REPARTIDO, Boolean.TRUE);
        viewport.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                distributeVisibleWidth(table);
            }
        });
    }

    private static void applyVisibleWidth(JTable table) {
        Container padre = table.getParent();
        if (!(padre instanceof JViewport viewport)) {
            return;
        }
        int disponible = viewport.getExtentSize().width;
        if (disponible <= 0) {
            return;
        }
        TableColumnModel columnas = table.getColumnModel();
        int n = columnas.getColumnCount();
        if (n == 0) {
            return;
        }
        int[] anchos = new int[n];
        for (int i = 0; i < n; i++) {
            anchos[i] = columnas.getColumn(i).getWidth();
        }
        int[] repartidos = distributeProportionally(anchos, disponible);
        if (repartidos == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            columnas.getColumn(i).setPreferredWidth(repartidos[i]);
            columnas.getColumn(i).setWidth(repartidos[i]);
        }
    }

    static int[] distributeProportionally(int[] anchos, int disponible) {
        if (anchos == null || anchos.length == 0 || disponible <= 0) {
            return null;
        }
        int total = 0;
        for (int width : anchos) {
            total += width;
        }
        if (total <= 0 || total >= disponible) {
            return null;
        }
        int sobra = disponible - total;
        int[] resultado = new int[anchos.length];
        int repartido = 0;
        for (int i = 0; i < anchos.length; i++) {
            int extra = sobra * anchos[i] / total;
            repartido += extra;
            resultado[i] = anchos[i] + extra;
        }
        int masAncha = 0;
        for (int i = 1; i < resultado.length; i++) {
            if (resultado[i] > resultado[masAncha]) {
                masAncha = i;
            }
        }
        resultado[masAncha] += sobra - repartido;
        return resultado;
    }

    public static void sizeColumnsToContent(JTable table, int minWidth, int maxWidth) {
        FontMetrics fm = table.getFontMetrics(table.getTableHeader().getFont());
        TableColumnModel cm = table.getColumnModel();
        TableCellRenderer cellRenderer = table.getDefaultRenderer(Object.class);
        for (int i = 0; i < cm.getColumnCount(); i++) {
            int width = fm.stringWidth(table.getModel().getColumnName(i)) + 28;
            for (int r = 0; r < table.getRowCount() && width < maxWidth; r++) {
                Object value = table.getModel().getValueAt(r, i);
                if (value == null) {
                    continue;
                }
                Component comp = cellRenderer.getTableCellRendererComponent(
                        table, value, false, false, r, i);
                width = Math.max(width, comp.getPreferredSize().width + 24);
            }
            TableColumn col = cm.getColumn(i);
            col.setPreferredWidth(Math.max(minWidth, Math.min(maxWidth, width)));
            col.setMinWidth(minWidth);
        }
    }

    public static void fixComboWidth(JComboBox<?> combo, int width) {
        Dimension pref = combo.getPreferredSize();
        combo.setPreferredSize(new Dimension(width, pref.height));
        combo.setMinimumSize(new Dimension(Math.min(120, width), pref.height));
    }

    public static JPanel nav(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (JButton b : buttons) {
            panel.add(b);
        }
        return panel;
    }

    public static JPanel buttonRow(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JButton b : buttons) {
            panel.add(b);
        }
        return panel;
    }

    public static void addLabel(JPanel target, GridBagConstraints g, int y, String text) {
        g.gridx = 0;
        g.gridy = y;
        g.gridwidth = 1;
        g.weightx = 0.0;
        target.add(new JLabel(text), g);
    }

    public static JLabel stepTitle(String text) {
        JLabel title = new JLabel(text);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        return title;
    }

    public static JLabel formTitle(String text) {
        JLabel title = new JLabel(text);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        return title;
    }

    public static GridBagConstraints formConstraints() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 8, 2, 8);
        g.anchor = GridBagConstraints.WEST;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }

    public static GridBagConstraints formConstraintsTight() {
        GridBagConstraints g = formConstraints();
        g.insets = new Insets(2, 8, 2, 8);
        return g;
    }

    public static JPanel formGrid() {
        return new JPanel(new GridBagLayout());
    }
}
