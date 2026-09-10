package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.EngineeringUnits;
import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ParameterDialogBuilder {

    private final String title;
    private boolean isEditMode = false;
    private String initialName = "";
    private Map<String, Object> initialProps = null;
    private List<S88Enumeration> enumerations = new ArrayList<>();
    private List<S88ControlModule> controlModules = new ArrayList<>();
    private boolean reportsMode = false;
    private Window owner;

    private BiConsumer<String, Map<String, Object>> onSaveAction;

    protected JTextField txtName;
    protected JComboBox<String> comboType;
    protected JComboBox<String> comboEnumeration;
    protected JComboBox<EngineeringUnits> comboEngineeringUnits;
    protected JTextField txtMax;
    protected JTextField txtMin;
    protected JTextField txtDefault;
    protected JComboBox<String> comboDefault;
    protected JPanel defaultField;
    protected JRadioButton radStatic;
    protected JRadioButton radReferenced;
    protected JComboBox<S88ControlModule> comboControlModule;
    protected JComboBox<String> comboVariable;
    protected JTextField txtPreviewValue;
    protected JButton btnOk;
    protected JButton btnCancel;

    public ParameterDialogBuilder(String title) {
        this.title = title;
    }

    public ParameterDialogBuilder reportsMode() {
        this.reportsMode = true;
        return this;
    }

    public ParameterDialogBuilder withOwner(Window owner) {
        this.owner = owner;
        return this;
    }

    public ParameterDialogBuilder withEnumerations(List<S88Enumeration> enumerations) {
        this.enumerations = enumerations != null ? new ArrayList<>(enumerations) : new ArrayList<>();
        return this;
    }

    public ParameterDialogBuilder withControlModules(List<S88ControlModule> controlModules) {
        this.controlModules = controlModules != null ? new ArrayList<>(controlModules) : new ArrayList<>();
        return this;
    }

    public ParameterDialogBuilder withInitialData(String name, Map<String, Object> props) {
        this.isEditMode = true;
        this.initialName = name;
        this.initialProps = props;
        return this;
    }

    public ParameterDialogBuilder onSave(BiConsumer<String, Map<String, Object>> action) {
        this.onSaveAction = action;
        return this;
    }

    public void show() {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        createWidgets();

        JPanel topSection = new JPanel(new BorderLayout(15, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.weightx = 1.0;
        topSection.add(buildFormPanel(gbc), BorderLayout.CENTER);
        topSection.add(buildButtonPanel(), BorderLayout.EAST);

        wireToggleListeners();
        applyInitialData();
        toggleTypeFields();
        toggleSourceFields();
        applyStoredDefault();

        btnCancel.addActionListener(e -> dialog.dispose());

        btnOk.addActionListener(e -> {
            if (onSaveAction != null) {
                if (!validateName(dialog)) {
                    return;
                }
                Map<String, Object> parameterBag = buildParameterBag();
                onSaveAction.accept(txtName.getText().trim(), parameterBag);
            }
            dialog.dispose();
        });

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPane.add(topSection, BorderLayout.NORTH);

        dialog.setContentPane(contentPane);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    protected void createWidgets() {
        txtName = new JTextField();
        comboType = new JComboBox<>(DataType.displayNames());
        comboEnumeration = new JComboBox<>();
        comboEngineeringUnits = new JComboBox<>();
        txtMax = new JTextField();
        txtMin = new JTextField();
        txtDefault = new JTextField();
        comboDefault = new JComboBox<>();

        defaultField = new JPanel(new CardLayout());
        defaultField.add(txtDefault, "TEXT");
        defaultField.add(comboDefault, "COMBO");

        for (S88Enumeration enumeration : enumerations) {
            if (enumeration.getName() != null) {
                comboEnumeration.addItem(enumeration.getName());
            }
        }

        for (EngineeringUnits e : EngineeringUnits.values()) {
            comboEngineeringUnits.addItem(e);
        }

        comboEngineeringUnits.setRenderer(new DefaultListCellRenderer() {
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

        radStatic = new JRadioButton("Static");
        radReferenced = new JRadioButton("Referenced", true);
        ButtonGroup sourceGroup = new ButtonGroup();
        sourceGroup.add(radStatic);
        sourceGroup.add(radReferenced);

        comboControlModule = new JComboBox<>();
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

        comboVariable = new JComboBox<>();
        txtPreviewValue = new JTextField();
        txtPreviewValue.setEditable(false);
    }

    protected boolean showRangeFields() {
        return !reportsMode;
    }

    protected boolean hasControlModules() {
        return !controlModules.isEmpty();
    }

    protected JPanel buildFormPanel(GridBagConstraints gbc) {
        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;
        addFormField(formPanel, gbc, row++, "Name", txtName);
        addFormField(formPanel, gbc, row++, "Type", comboType);
        addFormField(formPanel, gbc, row++, "Enumeration", comboEnumeration);
        addFormField(formPanel, gbc, row++, "Engineering Unit", comboEngineeringUnits);
        if (hasControlModules()) {
            if (showRangeFields()) {
                JPanel sourcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                sourcePanel.add(radStatic);
                sourcePanel.add(Box.createHorizontalStrut(10));
                sourcePanel.add(radReferenced);
                addFormField(formPanel, gbc, row++, "Source", sourcePanel);
            }
            addFormField(formPanel, gbc, row++, "Control Module", comboControlModule);
            addFormField(formPanel, gbc, row++, "Variable", comboVariable);
            addFormField(formPanel, gbc, row++, "Current Value", txtPreviewValue);
        }
        if (showRangeFields()) {
            addFormField(formPanel, gbc, row++, "Max", txtMax);
            addFormField(formPanel, gbc, row++, "Min", txtMin);
            addFormField(formPanel, gbc, row++, "Default", defaultField);
        }
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

    protected void refreshDefaultCombo() {
        String previous = Objects.toString(comboDefault.getSelectedItem(), "");
        comboDefault.removeAllItems();
        String enumName = Objects.toString(comboEnumeration.getSelectedItem(), "");
        for (S88Enumeration enumeration : enumerations) {
            if (enumName.equals(enumeration.getName())) {
                for (String label : enumeration.getValues().keySet()) {
                    comboDefault.addItem(label);
                }
                break;
            }
        }
        if (!previous.isEmpty()) {
            comboDefault.setSelectedItem(previous);
        }
    }

    protected void toggleTypeFields() {
        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        comboEnumeration.setEnabled(isEnumeration);
        comboEngineeringUnits.setEnabled(!isEnumeration);
        if (!showRangeFields()) {
            return;
        }
        txtMax.setEnabled(!isEnumeration);
        txtMin.setEnabled(!isEnumeration);
        CardLayout cl = (CardLayout) defaultField.getLayout();
        cl.show(defaultField, isEnumeration ? "COMBO" : "TEXT");
        if (isEnumeration) {
            refreshDefaultCombo();
        }
    }

    protected void toggleSourceFields() {
        if (!hasControlModules()) {
            return;
        }
        boolean referenced = !showRangeFields() || radReferenced.isSelected();
        comboControlModule.setEnabled(referenced);
        comboVariable.setEnabled(referenced);
        txtPreviewValue.setEnabled(referenced);
    }

    protected void wireToggleListeners() {
        comboType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleTypeFields();
            }
        });

        comboEnumeration.addItemListener(e -> {
            if (showRangeFields() && e.getStateChange() == ItemEvent.SELECTED) {
                refreshDefaultCombo();
            }
        });

        radStatic.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleSourceFields();
            }
        });
        radReferenced.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleSourceFields();
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

    protected void applyInitialData() {
        if (isEditMode && initialProps != null) {
            txtName.setText(initialName);
            txtName.setEditable(false);
            if (initialProps.get("Type") != null) {
                comboType.setSelectedItem(String.valueOf(initialProps.get("Type")).trim().toUpperCase());
            }
            boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
            if (isEnumeration) {
                comboEnumeration.setSelectedItem(Objects.toString(initialProps.get("Eng_Units/Enum"), ""));
            } else {
                comboEngineeringUnits.setSelectedItem(EngineeringUnits.fromName(Objects.toString(initialProps.get("Eng_Units/Enum"), "")));
                if (showRangeFields()) {
                    txtMax.setText(Objects.toString(initialProps.get("Max"), ""));
                    txtMin.setText(Objects.toString(initialProps.get("Min"), ""));
                    txtDefault.setText(Objects.toString(initialProps.get("Default"), ""));
                }
            }

            if (hasControlModules()) {
                String cmId = Objects.toString(initialProps.get("ControlModule"), "");
                if (!cmId.isEmpty()) {
                    radReferenced.setSelected(true);
                    selectControlModule(cmId, Objects.toString(initialProps.get("Variable"), ""));
                } else if (showRangeFields()) {
                    radStatic.setSelected(true);
                }
            }
        }
    }

    protected void applyStoredDefault() {
        if (showRangeFields() && isEditMode && initialProps != null
                && DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""))) {
            String storedDefault = Objects.toString(initialProps.get("Default"), "");
            if (!storedDefault.isEmpty()) {
                if (indexOfItem(comboDefault, storedDefault) < 0) {
                    comboDefault.addItem(storedDefault);
                }
                comboDefault.setSelectedItem(storedDefault);
            }
        }
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

    protected boolean validateName(JDialog dialog) {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "Name cannot be empty.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    protected Map<String, Object> buildParameterBag() {
        boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
        Map<String, Object> parameterBag = isEditMode && initialProps != null
                ? initialProps : new LinkedHashMap<>();
        parameterBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));
        if (isEnumeration) {
            parameterBag.put("Eng_Units/Enum", Objects.toString(comboEnumeration.getSelectedItem(), ""));
            if (showRangeFields()) {
                parameterBag.put("Default", Objects.toString(comboDefault.getSelectedItem(), ""));
                parameterBag.put("Max", "");
                parameterBag.put("Min", "");
            }
        } else {
            parameterBag.put("Eng_Units/Enum", comboEngineeringUnits.getSelectedItem() != null
                    ? ((EngineeringUnits) comboEngineeringUnits.getSelectedItem()).getName()
                    : "");
            if (showRangeFields()) {
                parameterBag.put("Default", txtDefault.getText());
                parameterBag.put("Max", txtMax.getText());
                parameterBag.put("Min", txtMin.getText());
            }
        }

        if (hasControlModules()) {
            boolean referenced = !showRangeFields() || radReferenced.isSelected();
            if (referenced && comboControlModule.getSelectedItem() != null) {
                parameterBag.put("ControlModule", ((S88ControlModule) comboControlModule.getSelectedItem()).getId());
                parameterBag.put("Variable", Objects.toString(comboVariable.getSelectedItem(), ""));
            } else {
                parameterBag.remove("ControlModule");
                parameterBag.remove("Variable");
            }
        }
        return parameterBag;
    }

    private void addFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        parent.add(new JLabel(labelText), gbc);
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