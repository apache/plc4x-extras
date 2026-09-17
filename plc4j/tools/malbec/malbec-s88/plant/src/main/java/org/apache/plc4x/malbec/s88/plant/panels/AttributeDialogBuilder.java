package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.EngineeringUnits;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class AttributeDialogBuilder {

    private final String title;
    private boolean isEditMode = false;
    private String initialName = "";
    private Map<String, Object> initialProps = null;
    private List<S88Enumeration> enumerations = new ArrayList<>();
    private Window owner;
    private boolean editable = false;

    private BiConsumer<String, Map<String, Object>> onSaveAction;
    private Runnable onUpdateCallback;

    protected JTextField txtName;
    protected JComboBox<String> comboType;
    protected JComboBox<String> comboEnumeration;
    protected JComboBox<EngineeringUnits> comboEngineeringUnit;
    protected JRadioButton radStatic;
    protected JRadioButton radDynamic;
    protected JPanel staticField;
    protected JTextField txtStaticValue;
    protected JComboBox<String> comboStaticValue;
    protected JTextField txtReference;
    protected boolean hasReference = true;
    protected JButton btnOk;
    protected JButton btnCancel;

    private final ValueDocumentFilter valueFilter = new ValueDocumentFilter();

    public AttributeDialogBuilder(String title) {
        this.title = title;
    }

    public AttributeDialogBuilder withEnumerations(List<S88Enumeration> enumerations) {
        this.enumerations = enumerations != null ? new ArrayList<>(enumerations) : new ArrayList<>();
        return this;
    }

    public AttributeDialogBuilder withInitialData(String name, Map<String, Object> props) {
        this.isEditMode = true;
        this.initialName = name;
        this.initialProps = props;
        return this;
    }

    public AttributeDialogBuilder onSave(BiConsumer<String, Map<String, Object>> action) {
        this.onSaveAction = action;
        return this;
    }

    public AttributeDialogBuilder onUpdate(Runnable callback) {
        this.onUpdateCallback = callback;
        return this;
    }

    public AttributeDialogBuilder withOwner(Window owner) {
        this.owner = owner;
        return this;
    }

    public AttributeDialogBuilder withEditable(boolean editable) {
        this.editable = editable;
        return this;
    }

    public void show() {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));

        createWidgets();
        populateLists();

        JPanel topSection = new JPanel(new BorderLayout(15, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.weightx = 1.0;
        topSection.add(buildFormPanel(gbc), BorderLayout.CENTER);
        topSection.add(buildButtonPanel(), BorderLayout.EAST);

        JPanel dataSourceSection = buildDataSourceSection();

        wireToggleListeners();
        applyInitialData();
        toggleDataSourceFields();
        toggleTypeFields();
        applyStoredStaticValue();

        btnCancel.addActionListener(e -> dialog.dispose());
        btnOk.addActionListener(e -> {
            if (onSaveAction != null) {
                Map<String, Object> attributeBag = buildAttributeBag();
                onSaveAction.accept(txtName.getText(), attributeBag);
            }
            dialog.dispose();
            if (onUpdateCallback != null) {
                onUpdateCallback.run();
            }
        });

        contentPane.add(topSection, BorderLayout.NORTH);
        if (dataSourceSection != null) {
            contentPane.add(dataSourceSection, BorderLayout.CENTER);
        }

        dialog.setContentPane(contentPane);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    protected void createWidgets() {
        txtName = new JTextField();
        comboType = new JComboBox<>(DataType.displayNames());
        comboType.setEnabled(editable);
        comboEnumeration = new JComboBox<>();
        comboEnumeration.setEnabled(editable);
        comboEngineeringUnit = new JComboBox<>();
        comboEngineeringUnit.setEnabled(editable);

        radStatic = new JRadioButton("Static");
        radDynamic = new JRadioButton("Referenced", true);
        staticField = new JPanel(new CardLayout());
        txtStaticValue = new JTextField();
        ((PlainDocument) txtStaticValue.getDocument()).setDocumentFilter(valueFilter);
        comboStaticValue = new JComboBox<>();

        staticField.add(txtStaticValue, "TEXT");
        staticField.add(comboStaticValue, "COMBO");

        txtReference = new JTextField();
        txtReference.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e){
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE) {
                    e.consume();
                }
            }
        });
    }

    protected void populateLists() {
        for (S88Enumeration enumeration : enumerations) {
            if (enumeration.getName() != null) {
                comboEnumeration.addItem(enumeration.getName());
            }
        }

        for (EngineeringUnits engUnit : EngineeringUnits.values()) {
            comboEngineeringUnit.addItem(engUnit);
        }

        comboEngineeringUnit.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EngineeringUnits) {
                    setText(((EngineeringUnits) value).name());
                }
                return this;
            }
        });
    }

    protected boolean includeDataSourceSection() {
        return true;
    }

    protected JPanel buildFormPanel(GridBagConstraints gbc) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;
        addFormField(formPanel, gbc, row++, "Name", txtName);
        addFormField(formPanel, gbc, row++, "Type", comboType);
        addFormField(formPanel, gbc, row++, "Enumeration", comboEnumeration);
        addFormField(formPanel, gbc, row++, "Engineering Unit", comboEngineeringUnit);
        return formPanel;
    }

    protected JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        btnOk = new JButton("OK");
        btnCancel = new JButton("Cancel");
        Dimension btnSize = new Dimension(80, 26);
        btnOk.setMaximumSize(btnSize);
        btnCancel.setMaximumSize(btnSize);
        buttonPanel.add(btnOk);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        buttonPanel.add(btnCancel);
        return buttonPanel;
    }

    protected JPanel buildDataSourceSection() {
        if (!includeDataSourceSection()) {
            return null;
        }

        JPanel dataSourceSection = new JPanel(new GridBagLayout());
        dataSourceSection.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Data Source", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbcDs = new GridBagConstraints();
        gbcDs.fill = GridBagConstraints.HORIZONTAL;
        gbcDs.insets = new Insets(3, 5, 3, 5);
        int dsRow = 0;

        ButtonGroup bgSource = new ButtonGroup();
        bgSource.add(radStatic);
        bgSource.add(radDynamic);

        gbcDs.gridx = 0; gbcDs.gridy = dsRow++; gbcDs.gridwidth = 2; gbcDs.weightx = 0.0;
        dataSourceSection.add(radStatic, gbcDs);
        gbcDs.gridwidth = 1;
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Value", staticField);

        gbcDs.gridx = 0; gbcDs.gridy = dsRow++; gbcDs.gridwidth = 2; gbcDs.weightx = 0.0;
        dataSourceSection.add(radDynamic, gbcDs);
        gbcDs.gridwidth = 1;
        if(hasReference) {
            addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Reference", txtReference);
        }

        gbcDs.gridx = 0; gbcDs.gridy = dsRow; gbcDs.gridwidth = 2; gbcDs.weighty = 1.0;
        dataSourceSection.add(Box.createVerticalGlue(), gbcDs);

        return dataSourceSection;
    }

    protected void refreshStaticValueCombo() {
        String previous = Objects.toString(comboStaticValue.getSelectedItem(), "");
        comboStaticValue.removeAllItems();
        String enumName = Objects.toString(comboEnumeration.getSelectedItem(), "");
        for (S88Enumeration enumeration : enumerations) {
            if (enumName.equals(enumeration.getName())) {
                for (String label : enumeration.getValues().keySet()) {
                    comboStaticValue.addItem(label);
                }
                break;
            }
        }
        if (!previous.isEmpty()) {
            comboStaticValue.setSelectedItem(previous);
        }
    }

    protected void applyInitialData() {
        if (isEditMode && initialProps != null) {
            txtName.setText(initialName);
            txtName.setEnabled(false);
            if (initialProps.get("Type") != null) {
                comboType.setSelectedItem(initialProps.get("Type").toString().trim().toUpperCase());
            }

            boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
            if (isEnumeration) {
                Object enumValue = initialProps.get("Enumeration");
                if (enumValue == null) {
                    enumValue = initialProps.get("Eng_Units/Enum");
                }
                comboEnumeration.setSelectedItem(enumValue != null ? String.valueOf(enumValue) : null);
            } else {
                comboEngineeringUnit.setSelectedItem(EngineeringUnits.fromName(Objects.toString(initialProps.get("Eng_Units/Enum"), "")));
                txtStaticValue.setText(Objects.toString(initialProps.get("StaticValue"), ""));
            }

            if(initialProps.get("Reference") != null) {
                txtReference.setText(initialProps.get("Reference").toString());
            }

            if (initialProps.get("StaticValue") != null) {
                radStatic.setSelected(true);
            } else {
                radDynamic.setSelected(true);
            }
        } else {
            radDynamic.setSelected(true);
        }
    }

    protected void applyStoredStaticValue() {
        if (isEditMode && initialProps != null
                && DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""))) {
            String stored = Objects.toString(initialProps.get("StaticValue"), "");
            if (!stored.isEmpty() && indexOfItem(comboStaticValue, stored) >= 0) {
                comboStaticValue.setSelectedItem(stored);
            }
        }
    }

    protected void toggleTypeFields() {
        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        if (editable) {
            comboEnumeration.setEnabled(isEnumeration);
            comboEngineeringUnit.setEnabled(!isEnumeration);
        }
        CardLayout cl = (CardLayout) staticField.getLayout();
        cl.show(staticField, isEnumeration ? "COMBO" : "TEXT");
        if (isEnumeration) {
            refreshStaticValueCombo();
        }
        applyValueInputFilter();
    }

    protected void toggleDataSourceFields() {
        boolean isReferenced = radDynamic.isSelected();
        txtStaticValue.setEnabled(!isReferenced);
        comboStaticValue.setEnabled(!isReferenced);
        txtReference.setEnabled(isReferenced);
    }

    protected void wireToggleListeners() {
        comboType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleTypeFields();
            }
        });

        comboEnumeration.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                refreshStaticValueCombo();
            }
        });

        radStatic.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleDataSourceFields();
            }
        });
        radDynamic.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleDataSourceFields();
            }
        });


    }

    protected void applyValueInputFilter() {
        valueFilter.setType(DataType.fromString(Objects.toString(comboType.getSelectedItem(), "")));
    }



    protected Map<String, Object> buildAttributeBag() {
        Map<String, Object> attributeBag = isEditMode && initialProps != null
                ? new LinkedHashMap<>(initialProps) : new LinkedHashMap<>();
        attributeBag.remove("ControlModule");
        attributeBag.remove("Variable");
        attributeBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));
        attributeBag.remove("Enumeration");

        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        if (isEnumeration) {
            attributeBag.put("Eng_Units/Enum", Objects.toString(comboEnumeration.getSelectedItem(), ""));
        } else {
            attributeBag.put("Eng_Units/Enum", comboEngineeringUnit.getSelectedItem() != null
                    ? ((EngineeringUnits) comboEngineeringUnit.getSelectedItem()).getName()
                    : "");
        }

        if (radStatic.isSelected()) {
            attributeBag.put("StaticValue", isEnumeration
                    ? Objects.toString(comboStaticValue.getSelectedItem(), "")
                    : txtStaticValue.getText());
            attributeBag.remove("Reference");
        } else {
            attributeBag.put("Reference", safeReference(txtReference.getText()));
            attributeBag.remove("StaticValue");
        }
        return attributeBag;
    }

    private int safeReference(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected void addFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        parent.add(new JLabel(labelText), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
    }

    private void addIndentedFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        JLabel lbl = new JLabel(labelText);
        lbl.setBorder(new EmptyBorder(0, 30, 0, 0));
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        parent.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
    }

    private static int indexOfItem(JComboBox<String> combo, String item) {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) combo.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            if (Objects.equals(item, model.getElementAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static class ValueDocumentFilter extends DocumentFilter {
        private DataType type = DataType.STRING;

        void setType(DataType type) {
            this.type = type;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            if (matches(fb, offset, 0, text)) {
                super.insertString(fb, offset, text, attrs);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (matches(fb, offset, length, text)) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        private boolean matches(FilterBypass fb, int offset, int length, String text) {
            if (type != DataType.INTEGER && type != DataType.REAL) {
                return true;
            }
            if (text == null) {
                return true;
            }
            try {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String proposed = new StringBuilder(current).replace(offset, offset + length, text).toString();
                return type == DataType.REAL
                        ? proposed.matches("-?\\d*\\.?\\d*")
                        : proposed.matches("-?\\d*");
            } catch (BadLocationException ex) {
                return false;
            }
        }
    }
}
