package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.EngineeringUnits;
import org.apache.plc4x.malbec.s88.api.S88ControlModule;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
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
    private List<String> enumerations = new ArrayList<>();
    private List<S88ControlModule> controlModules = new ArrayList<>();
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
    protected JTextField txtStaticValue;
    protected JComboBox<S88ControlModule> comboControlModule;
    protected JComboBox<String> comboVariable;
    protected JTextField txtPreviewValue;
    protected JButton btnOk;
    protected JButton btnCancel;

    public AttributeDialogBuilder(String title) {
        this.title = title;
    }

    public AttributeDialogBuilder withEnumerations(List<String> enumerations) {
        this.enumerations = enumerations != null ? new ArrayList<>(enumerations) : new ArrayList<>();
        return this;
    }

    public AttributeDialogBuilder withControlModules(List<S88ControlModule> controlModules) {
        this.controlModules = controlModules != null ? new ArrayList<>(controlModules) : new ArrayList<>();
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
        txtStaticValue = new JTextField();
        comboControlModule = new JComboBox<>();
        comboVariable = new JComboBox<>();
        txtPreviewValue = new JTextField();
        txtPreviewValue.setEditable(false);

        for (S88ControlModule cm : controlModules) {
            comboControlModule.addItem(cm);
        }
        comboControlModule.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof S88ControlModule cm) {
                    setText(cm.getId() + " (" + cm.getTypeName() + ")");
                }
                return this;
            }
        });
    }

    protected void populateLists() {
        for (String enumName : enumerations) {
            comboEnumeration.addItem(enumName);
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
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Value", txtStaticValue);

        gbcDs.gridx = 0; gbcDs.gridy = dsRow++; gbcDs.gridwidth = 2; gbcDs.weightx = 0.0;
        dataSourceSection.add(radDynamic, gbcDs);
        gbcDs.gridwidth = 1;
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Control Module", comboControlModule);
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Variable", comboVariable);
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Current Value", txtPreviewValue);

        gbcDs.gridx = 0; gbcDs.gridy = dsRow; gbcDs.gridwidth = 2; gbcDs.weighty = 1.0;
        dataSourceSection.add(Box.createVerticalGlue(), gbcDs);

        return dataSourceSection;
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
            }

            if (initialProps.get("StaticValue") != null) {
                radStatic.setSelected(true);
                txtStaticValue.setText(initialProps.get("StaticValue").toString());
            } else {
                radDynamic.setSelected(true);
                selectControlModule(Objects.toString(initialProps.get("ControlModule"), ""),
                        Objects.toString(initialProps.get("Variable"), ""));
            }
        } else {
            radDynamic.setSelected(true);
        }
    }

    protected void toggleTypeFields() {
        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        if (editable) {
            comboEnumeration.setEnabled(isEnumeration);
            comboEngineeringUnit.setEnabled(!isEnumeration);
        }
    }

    protected void toggleDataSourceFields() {
        boolean isReferenced = radDynamic.isSelected();
        txtStaticValue.setEnabled(!isReferenced);
        comboControlModule.setEnabled(isReferenced);
        comboVariable.setEnabled(isReferenced);
        txtPreviewValue.setEnabled(isReferenced);
    }

    protected void wireToggleListeners() {
        comboType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleTypeFields();
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

        comboControlModule.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                refreshVariableCombo();
            }
        });
        comboVariable.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                refreshPreviewValue();
            }
        });
    }

    protected void refreshVariableCombo() {
        S88ControlModule cm = (S88ControlModule) comboControlModule.getSelectedItem();
        String previous = Objects.toString(comboVariable.getSelectedItem(), "");
        comboVariable.removeAllItems();
        if (cm != null) {
            for (String name : cm.getPropertyNames()) {
                comboVariable.addItem(name);
            }
        }
        if (!previous.isEmpty()) {
            comboVariable.setSelectedItem(previous);
        }
        refreshPreviewValue();
    }

    protected void refreshPreviewValue() {
        String text = "";
        S88ControlModule cm = (S88ControlModule) comboControlModule.getSelectedItem();
        if (cm != null) {
            Object value = cm.getProperty(Objects.toString(comboVariable.getSelectedItem(), ""));
            text = value != null ? String.valueOf(value) : "";
        }
        txtPreviewValue.setText(text);
    }

    protected void selectControlModule(String id, String variable) {
        for (int i = 0; i < comboControlModule.getItemCount(); i++) {
            S88ControlModule cm = comboControlModule.getItemAt(i);
            if (Objects.equals(id, cm.getId())) {
                comboControlModule.setSelectedIndex(i);
                break;
            }
        }
        refreshVariableCombo();
        if (!variable.isEmpty()) {
            if (indexOfItem(comboVariable, variable) < 0) {
                comboVariable.addItem(variable);
            }
            comboVariable.setSelectedItem(variable);
        }
    }

    protected Map<String, Object> buildAttributeBag() {
        Map<String, Object> attributeBag = isEditMode ? initialProps : new LinkedHashMap<>();
        attributeBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));
        attributeBag.remove("Enumeration");

        if (DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""))) {
            attributeBag.put("Eng_Units/Enum", Objects.toString(comboEnumeration.getSelectedItem(), ""));
        } else {
            attributeBag.put("Eng_Units/Enum", comboEngineeringUnit.getSelectedItem() != null
                    ? ((EngineeringUnits) comboEngineeringUnit.getSelectedItem()).getName()
                    : "");
        }

        if (radStatic.isSelected()) {
            attributeBag.put("StaticValue", txtStaticValue.getText());
            attributeBag.remove("ControlModule");
            attributeBag.remove("Variable");
            attributeBag.remove("ItemName");
        } else if (comboControlModule.getSelectedItem() != null) {
            attributeBag.put("ControlModule", ((S88ControlModule) comboControlModule.getSelectedItem()).getId());
            attributeBag.put("Variable", Objects.toString(comboVariable.getSelectedItem(), ""));
            attributeBag.remove("StaticValue");
            attributeBag.remove("ItemName");
        }

        return attributeBag;
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
}