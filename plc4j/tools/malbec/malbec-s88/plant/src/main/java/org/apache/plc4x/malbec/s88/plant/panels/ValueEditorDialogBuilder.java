package org.apache.plc4x.malbec.s88.plant.panels;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class ValueEditorDialogBuilder extends AttributeDialogBuilder {

    private final String propertyName;
    private final Object currentValue;
    private JComponent valueField;
    private Function<String, Object> parser;

    public ValueEditorDialogBuilder(String title, String propertyName, Object currentValue) {
        super(title);
        this.propertyName = propertyName;
        this.currentValue = currentValue;
    }

    @Override
    protected boolean includeDataSourceSection() {
        return false;
    }

    @Override
    protected void createWidgets() {
        txtName = new JTextField();
        txtName.setEditable(false);
        comboType = new JComboBox<>();
        comboType.setEnabled(false);
        comboType.addItem((currentValue != null ? currentValue.getClass() : String.class).getSimpleName());
        txtName.setText(propertyName);
        valueField = buildValueField();
    }

    @Override
    protected void populateLists() {
    }

    @Override
    protected void applyInitialData() {
    }

    @Override
    protected void wireToggleListeners() {
    }

    @Override
    protected void toggleTypeFields() {
    }

    @Override
    protected void toggleDataSourceFields() {
    }

    @Override
    protected JPanel buildFormPanel(GridBagConstraints gbc) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;
        addFormField(formPanel, gbc, row++, "Name", txtName);
        addFormField(formPanel, gbc, row++, "Type", comboType);
        addFormField(formPanel, gbc, row++, "Value", valueField);
        return formPanel;
    }

    @Override
    protected Map<String, Object> buildAttributeBag() {
        Map<String, Object> bag = new LinkedHashMap<>();
        if (currentValue instanceof Boolean) {
            bag.put("Value", Boolean.valueOf(String.valueOf(((JComboBox<String>) valueField).getSelectedItem())));
            return bag;
        }
        String text = ((JTextField) valueField).getText().trim();
        bag.put("Value", text.isEmpty() ? null : parser.apply(text));
        return bag;
    }

    private JComponent buildValueField() {
        if (currentValue instanceof Boolean) {
            JComboBox<String> comboBool = new JComboBox<>(new String[]{"true", "false"});
            comboBool.setSelectedItem(String.valueOf(currentValue));
            return comboBool;
        }
        JTextField field;
        if (currentValue instanceof Integer || currentValue instanceof Long) {
            field = buildNumericField(false);
            parser = text -> currentValue instanceof Integer
                    ? Integer.parseInt(text) : Long.parseLong(text);
        } else if (currentValue instanceof Double || currentValue instanceof Float) {
            field = buildNumericField(true);
            parser = text -> currentValue instanceof Double
                    ? Double.parseDouble(text) : Float.parseFloat(text);
        } else {
            field = new JTextField();
            parser = text -> text;
        }
        field.setText(currentValue != null ? String.valueOf(currentValue) : "");
        return field;
    }

    private JTextField buildNumericField(boolean allowDecimal) {
        JTextField field = new JTextField();
        PlainDocument doc = new PlainDocument();
        doc.setDocumentFilter(numericFilter(allowDecimal));
        field.setDocument(doc);
        return field;
    }

    private DocumentFilter numericFilter(boolean allowDecimal) {
        return new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offs, String text, AttributeSet a)
                    throws BadLocationException {
                if (matches(fb, offs, 0, text)) {
                    super.insertString(fb, offs, text, a);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offs, int len, String text, AttributeSet a)
                    throws BadLocationException {
                if (matches(fb, offs, len, text)) {
                    super.replace(fb, offs, len, text, a);
                }
            }

            private boolean matches(FilterBypass fb, int offs, int len, String text) {
                try {
                    String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                    String proposed = new StringBuilder(current).replace(offs, offs + len, text).toString();
                    return allowDecimal ? proposed.matches("-?\\d*\\.?\\d*") : proposed.matches("-?\\d*");
                } catch (BadLocationException ex) {
                    return false;
                }
            }
        };
    }
}