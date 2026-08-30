package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.DataType;
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
    private boolean reportsMode = false;
    private Window owner;

    private BiConsumer<String, Map<String, Object>> onSaveAction;

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

        JTextField txtName = new JTextField();
        JComboBox<String> comboType = new JComboBox<>(DataType.displayNames());
        JComboBox<String> comboEnumeration = new JComboBox<>();
        JTextField txtEngineeringUnit = new JTextField();
        JTextField txtMax = new JTextField();
        JTextField txtMin = new JTextField();
        JTextField txtDefault = new JTextField();
        JComboBox<String> comboDefault = new JComboBox<>();

        JPanel defaultField = new JPanel(new CardLayout());
        defaultField.add(txtDefault, "TEXT");
        defaultField.add(comboDefault, "COMBO");

        for (S88Enumeration enumeration : enumerations) {
            if (enumeration.getName() != null) {
                comboEnumeration.addItem(enumeration.getName());
            }
        }

        Runnable refreshDefaultCombo = () -> {
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
        };

        Runnable toggleTypeFields = () -> {
            boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
            comboEnumeration.setEnabled(isEnumeration);
            txtEngineeringUnit.setEnabled(!isEnumeration);
            if (reportsMode) {
                return;
            }
            txtMax.setEnabled(!isEnumeration);
            txtMin.setEnabled(!isEnumeration);
            CardLayout cl = (CardLayout) defaultField.getLayout();
            cl.show(defaultField, isEnumeration ? "COMBO" : "TEXT");
            if (isEnumeration) {
                refreshDefaultCombo.run();
            }
        };

        comboType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                toggleTypeFields.run();
            }
        });

        comboEnumeration.addItemListener(e -> {
            if (!reportsMode && e.getStateChange() == ItemEvent.SELECTED) {
                refreshDefaultCombo.run();
            }
        });

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.weightx = 1.0;

        int row = 0;
        addFormField(formPanel, gbc, row++, "Name", txtName);
        addFormField(formPanel, gbc, row++, "Type", comboType);
        addFormField(formPanel, gbc, row++, "Enumeration", comboEnumeration);
        addFormField(formPanel, gbc, row++, "Engineering Unit", txtEngineeringUnit);
        if (!reportsMode) {
            addFormField(formPanel, gbc, row++, "Max", txtMax);
            addFormField(formPanel, gbc, row++, "Min", txtMin);
            addFormField(formPanel, gbc, row++, "Default", defaultField);
        }

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");
        Dimension btnSize = new Dimension(80, 26);
        btnOk.setMaximumSize(btnSize);
        btnCancel.setMaximumSize(btnSize);
        buttonPanel.add(btnOk);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        buttonPanel.add(btnCancel);

        JPanel topSection = new JPanel(new BorderLayout(15, 0));
        topSection.add(formPanel, BorderLayout.CENTER);
        topSection.add(buttonPanel, BorderLayout.EAST);

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
                txtEngineeringUnit.setText(Objects.toString(initialProps.get("Eng_Units/Enum"), ""));
                if(!reportsMode){
                    txtMax.setText(Objects.toString(initialProps.get("Max"), ""));
                    txtMin.setText(Objects.toString(initialProps.get("Min"), ""));
                    txtDefault.setText(Objects.toString(initialProps.get("Default"), ""));
                }
            }
        }

        toggleTypeFields.run();

        if (!reportsMode && isEditMode && initialProps != null
                && DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""))) {
            String storedDefault = Objects.toString(initialProps.get("Default"), "");
            if (!storedDefault.isEmpty()) {
                if (indexOfItem(comboDefault, storedDefault) < 0) {
                    comboDefault.addItem(storedDefault);
                }
                comboDefault.setSelectedItem(storedDefault);
            }
        }

        btnCancel.addActionListener(e -> dialog.dispose());

        btnOk.addActionListener(e -> {
            if (onSaveAction != null) {
                String name = txtName.getText().trim();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Name cannot be empty.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                boolean isEnumeration = DataType.isEnumeration(Objects.toString(comboType.getSelectedItem(), ""));
                Map<String, Object> parameterBag = isEditMode && initialProps != null
                        ? initialProps : new LinkedHashMap<>();
                parameterBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));
                if (isEnumeration) {
                    parameterBag.put("Eng_Units/Enum", Objects.toString(comboEnumeration.getSelectedItem(), ""));
                    if(!reportsMode){
                        parameterBag.put("Default", Objects.toString(comboDefault.getSelectedItem(), ""));
                        parameterBag.put("Max", "");
                        parameterBag.put("Min", "");
                    }
                } else {
                    parameterBag.put("Eng_Units/Enum", txtEngineeringUnit.getText());
                    if(!reportsMode){
                        parameterBag.put("Default", txtDefault.getText());
                        parameterBag.put("Max", txtMax.getText());
                        parameterBag.put("Min", txtMin.getText());
                    }
                }
                onSaveAction.accept(name, parameterBag);
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
