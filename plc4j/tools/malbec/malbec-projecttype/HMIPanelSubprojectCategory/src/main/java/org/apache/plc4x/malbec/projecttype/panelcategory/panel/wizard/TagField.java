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
import java.awt.IllegalComponentStateException;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.TagDiagnostico;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.TagIssue;

final class TagField extends JTextField {

    private static final long serialVersionUID = 1L;

    private static final PopupFactory FACTORIA_AVISOS = new PopupFactory();

    private final Border bordeOriginal;
    private boolean mouseOverTag;
    private Popup popupTag;

    TagField(int columnas) {
        super(columnas);
        bordeOriginal = getBorder();
        ((AbstractDocument) getDocument()).setDocumentFilter(new UppercaseFilter());
        setDocument(new LimitedDocument(40));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                mouseOverTag = true;
                warnTag();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseOverTag = false;
                hideTagWarning();
            }
        });
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                warnTag();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                warnTag();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                warnTag();
            }
        });
    }

    private void warnTag() {
        hideTagWarning();
        List<TagIssue> errors = TagDiagnostico.errors(getText());
        List<TagIssue> warnings = TagDiagnostico.warnings(getText());
        List<TagIssue> todo = new ArrayList<>(errors);
        todo.addAll(warnings);
        setBorder(errors.isEmpty()
                ? bordeOriginal
                : BorderFactory.createLineBorder(new Color(180, 0, 0), 2));

        if (!todo.isEmpty() && mouseOverTag && isShowing()) {
            showTagWarning(todo);
        }
    }

    private static String messageFor(TagIssue issue) {
        List<String> a = issue.args();
        return switch (issue.codigo()) {
            case TagIssue.CORCHETE_SIN_CERRAR -> "Falta cerrar el corchete: hay un '[' sin su ']'.";
            case TagIssue.CORCHETE_SOBRANTE -> "Hay un ']' sin el '[' que lo abre.";
            case TagIssue.RANGO_INVALIDO -> "El rango «[" + a.get(0) + "]» no es válido."
                    + " Un rango se escribe con dos puntos, del tipo [0..9], y un solo"
                    + " número es un índice, del tipo [10].";
            case TagIssue.CARACTERES_RAROS -> "El tag tiene caracteres que no pueden ser parte"
                    + " de una dirección: «" + a.get(0) + "». Se guardará tal cual, pero el PLC"
                    + " puede que no lo entienda.";
            case TagIssue.NO_ES_DIRECCION -> "«" + a.get(0) + "» no es una dirección válida."
                    + " El área se puede guardar, pero se tratará como un nombre: no se le"
                    + " calculará el tamaño y no se podrán comprobar solapes con las"
                    + " demás áreas. Si es una dirección, revise la notación:"
                    + " Ej: %DB22.DBB4[0..9]:BYTE.";
            case TagIssue.SIN_TIPOS -> "Con el tipo '" + a.get(0) + "' el área no va a ofrecer"
                    + " ningún tipo de dato, así que no se le podrán añadir variables."
                    + " Los tipos del programa son: " + a.get(1) + ".";
            case TagIssue.SIN_TAMANO -> "No se sabe el tamaño del área, así que no se le podrá"
                    + " añadir ninguna variable. Añada un rango al tag, del tipo [0..9],"
                    + " o un tipo tras los dos puntos, del tipo :INT.";
            case TagIssue.NUMERO_DEMASIADO_GRANDE -> "El número «" + a.get(0) + "» es"
                    + " demasiado grande: no cabe en un entero. Seguramente sobre un dígito.";
            default -> issue.codigo();
        };
    }

    private void showTagWarning(List<TagIssue> issues) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        Point enPantalla = getLocationOnScreen();
        int anchoCampo = getWidth();
        int altoCampo = getHeight();
        int width = Math.max(240, Math.min(320, anchoCampo - 20));

        List<String> parrafos = new ArrayList<>(issues.size());
        for (TagIssue issue : issues) {
            parrafos.add(messageFor(issue));
        }
        JLabel etiqueta = new JLabel("<html><body style='width:" + (width - 16) + "px'>"
                + String.join("<br><br>", parrafos) + "</body></html>");
        etiqueta.setFont(getFont());

        JPanel caja = new JPanel(new BorderLayout());
        caja.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 0, 0)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        caja.setBackground(new Color(255, 250, 235));
        caja.add(etiqueta, BorderLayout.CENTER);

        Rectangle pantalla = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int alto = caja.getPreferredSize().height;
        int y = enPantalla.y + altoCampo + 2;
        if (y + alto + 8 > pantalla.y + pantalla.height) {
            y = enPantalla.y - alto;
        }
        popupTag = FACTORIA_AVISOS.getPopup(this, caja, enPantalla.x, y);
        popupTag.show();
    }

    private void hideTagWarning() {
        if (popupTag != null) {
            popupTag.hide();
            popupTag = null;
        }
    }

    private static class UppercaseFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr) {
            try {
                super.insertString(fb, offset, string.toUpperCase(), attr);
            } catch (javax.swing.text.BadLocationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs) {
            try {
                super.replace(fb, offset, length, text.toUpperCase(), attrs);
            } catch (javax.swing.text.BadLocationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class LimitedDocument extends PlainDocument {
        private final int maxLength;

        LimitedDocument(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(int offset, String str, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            if (str == null) return;
            if (getLength() + str.length() <= maxLength) {
                super.insertString(offset, str, attr);
            }
        }

        @Override
        public void replace(int offset, int length, String str, javax.swing.text.AttributeSet attrs)
                throws javax.swing.text.BadLocationException {
            if (str == null) return;
            int currentLength = getLength();
            if (currentLength - length + str.length() <= maxLength) {
                super.replace(offset, length, str, attrs);
            }
        }
    }
}
