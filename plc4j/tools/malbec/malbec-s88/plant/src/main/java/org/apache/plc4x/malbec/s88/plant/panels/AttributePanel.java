package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public class AttributePanel extends JPanel {

    private Map<String, String> attributeBag = new LinkedHashMap<>();;

    private JTextField txtName;
    private JComboBox<String> comboAttribute;
    private JComboBox<String> comboType;
    private JComboBox<String> comboEnumeration;
    private JTextField txtEngineeringUnit;


    private JButton btnOk;
    private JButton btnCancel;

    private JRadioButton radStatic;
    private JRadioButton radDynamic;
    private JTextField txtStaticValue;

    private JComboBox<String> comboDataServer;
    private JTextField txtAccessPath;
    private JTextField txtItemName;
    private JTextField txtWriteAccessPath;
    private JTextField txtWriteItemName;

    private S88Element unit;
    private Plc4xPlantModel model;

    public AttributePanel(S88Element unit, Plc4xPlantModel model) {
        initUI();
        setupListeners();
        setDefaultStates();
        this.unit = unit;
        this.model = model;

    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));


        JPanel topSection = buildTopSection();
        JPanel dataSourceSection = buildDataSourceSection();

        add(topSection, BorderLayout.NORTH);
        add(dataSourceSection, BorderLayout.CENTER);
    }


    private JPanel buildTopSection() {
        JPanel panel = new JPanel(new BorderLayout(15, 0));


        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.weightx = 1.0;

        int row = 0;
        txtName = new JTextField();
        comboAttribute = new JComboBox<>(new String[]{"..."});
        comboType = new JComboBox<>(new String[]{"INTEGER", "REAL", "STRING", "BOOLEAN", "ENUMERATION"});
        comboEnumeration = new JComboBox<>();
        txtEngineeringUnit = new JTextField();

        addFormField(formPanel, gbc, row++, "Name", txtName);
        addFormField(formPanel, gbc, row++, "Attribute", comboAttribute);
        addFormField(formPanel, gbc, row++, "Type", comboType);
        addFormField(formPanel, gbc, row++, "Enumeration", comboEnumeration);
        addFormField(formPanel, gbc, row++, "Engineering Unit", txtEngineeringUnit);


        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

        btnOk = new JButton("OK");

        btnOk.addActionListener(e ->{
            buildAttribute();

//            for(Map.Entry<String, String> entry : attributeBag.entrySet()) {
//               UpdatePropertyUseCase.execute(model.getModel(), unit, entry.getKey(), entry.getValue());
//            }

        });

        btnCancel = new JButton("Cancel");


        Dimension btnSize = new Dimension(80, 26);
        btnOk.setMaximumSize(btnSize);
        btnCancel.setMaximumSize(btnSize);

        buttonPanel.add(btnOk);
        buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        buttonPanel.add(btnCancel);


        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private void buildAttribute() {
        attributeBag.put("Name", txtName.getText());
        attributeBag.put("Attribute", Objects.toString(comboAttribute.getSelectedItem(), ""));
        attributeBag.put("Type", Objects.toString(comboType.getSelectedItem(), ""));
        attributeBag.put("Enumeration", Objects.toString(comboEnumeration.getSelectedItem(), ""));
        attributeBag.put("Engineering_Unit", txtEngineeringUnit.getText());
        attributeBag.put("ItemName", txtItemName.getText());
        attributeBag.put("WriteAccessPath", txtWriteAccessPath.getText());
        attributeBag.put("WriteItemName", txtWriteItemName.getText());

        if (radStatic.isSelected()) attributeBag.put("StaticValue", txtStaticValue.getText());
    }


    private JPanel buildDataSourceSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Data Source", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 5, 3, 5);

        int row = 0;


        ButtonGroup bgSource = new ButtonGroup();
        radStatic = new JRadioButton("Static");
        radDynamic = new JRadioButton("Dynamic", true); // Seleccionado por defecto
        bgSource.add(radStatic);
        bgSource.add(radDynamic);


        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2; gbc.weightx = 0.0;
        panel.add(radStatic, gbc);

        txtStaticValue = new JTextField();
        gbc.gridwidth = 1;
        addIndentedFormField(panel, gbc, row++, "Value", txtStaticValue);


        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2; gbc.weightx = 0.0;
        panel.add(radDynamic, gbc);

        comboDataServer = new JComboBox<>(new String[]{"LOCAL_DB"});
        txtAccessPath = new JTextField();
        txtItemName = new JTextField();
        txtWriteAccessPath = new JTextField();
        txtWriteItemName = new JTextField();

        gbc.gridwidth = 1;
        addIndentedFormField(panel, gbc, row++, "Data Adquisition", comboDataServer);
        addIndentedFormField(panel, gbc, row++, "Access Path", txtAccessPath);
        addIndentedFormField(panel, gbc, row++, "Item Name", txtItemName);
        addIndentedFormField(panel, gbc, row++, "Write Access Path", txtWriteAccessPath);
        addIndentedFormField(panel, gbc, row++, "Write Item Name", txtWriteItemName);


        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }


    private void addFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;


        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.EAST;
        parent.add(new JLabel(labelText), gbc);

        // Campo a la derecha
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
    }

    private void addIndentedFormField(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;


        JLabel lbl = new JLabel(labelText);
        lbl.setBorder(new EmptyBorder(0, 30, 0, 0));

        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.EAST;
        parent.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        parent.add(field, gbc);
    }


    private void setupListeners() {
        ItemListener sourceListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                boolean isDynamic = radDynamic.isSelected();
                toggleDataSourceFields(isDynamic);
            }
        };

        radStatic.addItemListener(sourceListener);
        radDynamic.addItemListener(sourceListener);
    }


    private void setDefaultStates() {
        comboEnumeration.setEnabled(false);
        txtWriteAccessPath.setEditable(false);
        txtWriteItemName.setEditable(false);

        toggleDataSourceFields(true);
    }

    private void toggleDataSourceFields(boolean isDynamic) {
        txtStaticValue.setEnabled(!isDynamic);

        comboDataServer.setEnabled(isDynamic);
        txtAccessPath.setEnabled(isDynamic);
        txtItemName.setEnabled(isDynamic);
        txtWriteAccessPath.setEnabled(isDynamic);
        txtWriteItemName.setEnabled(isDynamic);
    }


    public JButton getBtnOk() { return btnOk; }
    public JButton getBtnCancel() { return btnCancel; }

}