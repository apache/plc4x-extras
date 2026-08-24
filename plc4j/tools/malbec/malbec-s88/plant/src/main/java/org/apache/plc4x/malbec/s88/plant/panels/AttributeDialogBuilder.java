package org.apache.plc4x.malbec.s88.plant.panels;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class AttributeDialogBuilder {

    private final String title;
    private boolean isEditMode = false;
    private String initialName = "";
    private Map<String, Object> initialProps = null;

    private BiConsumer<String, Map<String, Object>> onSaveAction;
    private Runnable onUpdateCallback;

    public AttributeDialogBuilder(String title) {
        this.title = title;
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


    public void show() {
        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));


        JTextField txtName = new JTextField();
        JComboBox<String> comboType = new JComboBox<>(new String[]{"INTEGER", "REAL", "ENUMERATION"});
        JComboBox<String> comboEnumeration = new JComboBox<>();
        JTextField txtEngineeringUnit = new JTextField();

        JRadioButton radStatic = new JRadioButton("Static");
        JRadioButton radDynamic = new JRadioButton("Dynamic", true);
        JTextField txtStaticValue = new JTextField();
        JComboBox<String> comboDataServer = new JComboBox<>(new String[]{"LOCAL_DB"});
        JTextField txtAccessPath = new JTextField();
        JTextField txtItemName = new JTextField();
        JTextField txtWriteAccessPath = new JTextField();
        JTextField txtWriteItemName = new JTextField();


        JPanel topSection = new JPanel(new BorderLayout(15, 0));
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

        topSection.add(formPanel, BorderLayout.CENTER);
        topSection.add(buttonPanel, BorderLayout.EAST);


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
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Data Adquisition", comboDataServer);
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Access Path", txtAccessPath);
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Item Name", txtItemName);
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Write Access Path", txtWriteAccessPath);
        addIndentedFormField(dataSourceSection, gbcDs, dsRow++, "Write Item Name", txtWriteItemName);

        gbcDs.gridx = 0; gbcDs.gridy = dsRow; gbcDs.gridwidth = 2; gbcDs.weighty = 1.0;
        dataSourceSection.add(Box.createVerticalGlue(), gbcDs);


        comboEnumeration.setEnabled(false);
        txtWriteAccessPath.setEditable(false);
        txtWriteItemName.setEditable(false);


        Runnable toggleFields = () -> {
            boolean isDynamic = radDynamic.isSelected();
            txtStaticValue.setEnabled(!isDynamic);
            comboDataServer.setEnabled(isDynamic);
            txtAccessPath.setEnabled(isDynamic);
            txtItemName.setEnabled(isDynamic);
            txtWriteAccessPath.setEnabled(isDynamic);
            txtWriteItemName.setEnabled(isDynamic);
        };

        radStatic.addItemListener(e -> { if (e.getStateChange() == ItemEvent.SELECTED) toggleFields.run(); });
        radDynamic.addItemListener(e -> { if (e.getStateChange() == ItemEvent.SELECTED) toggleFields.run(); });


        if (isEditMode && initialProps != null) {
            txtName.setText(initialName);
            txtName.setEditable(false);
            if (initialProps.get("Type") != null) {
                comboType.setSelectedItem(initialProps.get("Type").toString().trim().toUpperCase());
            }
            if (initialProps.get("Engineering_Units") != null) {
                txtEngineeringUnit.setText(String.valueOf(initialProps.get("Engineering_Units")));
            }
            if (initialProps.get("ItemName") != null) {
                txtItemName.setText(String.valueOf(initialProps.get("ItemName")));
            }

            if (initialProps.get("StaticValue") != null) {
                radStatic.setSelected(true);
                txtStaticValue.setText(initialProps.get("StaticValue").toString());
            } else {
                radDynamic.setSelected(true);
            }
        } else {
            radDynamic.setSelected(true);
        }

        toggleFields.run();

        btnCancel.addActionListener(e -> dialog.dispose());

        btnOk.addActionListener(e -> {
            if (onSaveAction != null) {
                Map<String, Object> attributeBag = isEditMode ? initialProps : new LinkedHashMap<>();
                attributeBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));
                attributeBag.put("Engineering_Units", txtEngineeringUnit.getText());

                if (radStatic.isSelected()) {
                    attributeBag.put("StaticValue", txtStaticValue.getText());
                } else {
                    attributeBag.remove("StaticValue");
                    attributeBag.put("ItemName", txtItemName.getText());
                    // more...
                }

                onSaveAction.accept(txtName.getText(), attributeBag);
            }
            dialog.dispose();
            if (onUpdateCallback != null) onUpdateCallback.run();
        });

        contentPane.add(topSection, BorderLayout.NORTH);
        contentPane.add(dataSourceSection, BorderLayout.CENTER);

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

    private void addIndentedFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        JLabel lbl = new JLabel(labelText);
        lbl.setBorder(new EmptyBorder(0, 30, 0, 0));
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        parent.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
    }
}