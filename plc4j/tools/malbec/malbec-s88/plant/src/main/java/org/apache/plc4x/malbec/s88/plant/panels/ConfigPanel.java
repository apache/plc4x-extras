package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Should this panel be different for each level?
 */

public class ConfigPanel extends JPanel {

    private Plc4xPlantModel model;
    private S88Element parent;

    public ConfigPanel(Plc4xPlantModel model, S88Element parent) {
        this.model = model;
        this.parent = parent;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));


        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Principal", createPrincipalTab());
        tabbedPane.addTab("More", new JPanel()); // placeholder
        tabbedPane.addTab("Other", new JPanel());

        add(tabbedPane, BorderLayout.CENTER);


    }

    private JPanel createPrincipalTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();


        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 10);
        panel.add(createLeftPanel(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(createRightPanel(), gbc);

        return panel;
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));


        JPanel topPart = new JPanel(new BorderLayout(0, 5));
        JLabel lblTitle = new JLabel("<html><b> " + parent.getId() + " configuration</b></html>");
        JButton btnAddRemove = new JButton("Add / Remove");
        topPart.add(lblTitle, BorderLayout.NORTH);
        topPart.add(btnAddRemove, BorderLayout.SOUTH);
        panel.add(topPart, BorderLayout.NORTH);

        String[] presets = {
                "Attribute 1",
                "Attribute 2",
                "Attribute 3",
                "Attribute 4",
        };
        JList<String> list = new JList<>(presets);
        JScrollPane scrollPane = new JScrollPane(list);
        panel.add(scrollPane, BorderLayout.CENTER);


        JPanel bottomButtons = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        bottomButtons.add(new JButton("Show settings"), gbc);

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.insets = new Insets(2, 0, 2, 2);
        bottomButtons.add(new JButton("Restore"), gbc);

        gbc.gridx = 1; gbc.insets = new Insets(2, 2, 2, 0);
        bottomButtons.add(new JButton("Option"), gbc);

        // Todo: put any picture or more props
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(Box.createVerticalStrut(100), BorderLayout.CENTER); // Espacio reservado donde iría la imagen
        bottomContainer.add(bottomButtons, BorderLayout.SOUTH);

        panel.add(bottomContainer, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 5, 4, 5);

        int row = 0;


        addFormRow(panel, gbc, row++, "Prop 1", new JComboBox<>(new String[]{"Value 1", "Value 2"}));


        JPanel orientPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JRadioButton rbVertical = new JRadioButton("Value 1", true);
        JRadioButton rbHorizontal = new JRadioButton("Value 2");
        ButtonGroup bgOrient = new ButtonGroup();
        bgOrient.add(rbVertical); bgOrient.add(rbHorizontal);
        orientPanel.add(rbVertical);
        orientPanel.add(Box.createHorizontalStrut(15));
        orientPanel.add(rbHorizontal);
        addFormRow(panel, gbc, row++, "Prop 2", orientPanel);


        addFormRow(panel, gbc, row++, "Prop 3", new JComboBox<>(new String[]{"Value"}));


        addFormRow(panel, gbc, row++, "Prop 4", new JComboBox<>(new String[]{"Value 1", "Value 2", "Value 3"}));

        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JRadioButton rbColor = new JRadioButton("Some value", true);
        JRadioButton rbGray = new JRadioButton("Another value");
        ButtonGroup bgColor = new ButtonGroup();
        bgColor.add(rbColor); bgColor.add(rbGray);
        colorPanel.add(rbColor);
        colorPanel.add(Box.createHorizontalStrut(15));
        colorPanel.add(rbGray);
        addFormRow(panel, gbc, row++, "Prop 5", colorPanel);


        gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;


        addFormRow(panel, gbc, row++, "Another prop", new JComboBox<>(new String[]{"Disabled", "Enabled"}));


        JPanel btnAjustesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnAjustesPanel.add(new JButton("Settings..."));
        addFormRow(panel, gbc, row++, "", btnAjustesPanel);


        JPanel multiPanel = new JPanel(new BorderLayout(5, 0));
        multiPanel.add(new JComboBox<>(new String[]{"Disabled", "Value"}), BorderLayout.CENTER);
        multiPanel.add(new JButton("Configuration..."), BorderLayout.EAST);
        addFormRow(panel, gbc, row++, "Prop", multiPanel);


        gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;


        JPanel copiasPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        copiasPanel.add(new JSpinner(new SpinnerNumberModel(1, 1, 999, 1)));
        copiasPanel.add(Box.createHorizontalStrut(20));

        JPanel checksPanel = new JPanel(new GridLayout(2, 1));
        checksPanel.add(new JCheckBox("Setting 1", true));
        checksPanel.add(new JCheckBox("Setting 2", true));
        copiasPanel.add(checksPanel);

        addFormRow(panel, gbc, row++, "Numbers", copiasPanel);


        addFormRow(panel, gbc, row++, "Mode", new JComboBox<>(new String[]{"Disabled", "Enabled"}));


        JPanel finalChecksPanel = new JPanel(new GridLayout(2, 1));
        finalChecksPanel.add(new JCheckBox("Property"));
        finalChecksPanel.add(new JCheckBox("Last property"));
        addFormRow(panel, gbc, row++, "", finalChecksPanel);


        gbc.gridy = row; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;

        // Label
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        panel.add(new JLabel(labelText), gbc);

        // Field
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
    }
}