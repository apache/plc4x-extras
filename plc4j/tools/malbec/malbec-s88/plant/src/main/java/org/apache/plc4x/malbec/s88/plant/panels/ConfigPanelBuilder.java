package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ConfigPanelBuilder {
    private final JPanel mainPanel;
    private final JPanel centerPanel;
    private final JPanel bottomPanel;
    private final S88Element element;

    public ConfigPanelBuilder(S88Element element) {
        this.element = element;
        this.mainPanel = new JPanel(new BorderLayout(5, 10));
        this.mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        this.centerPanel = new JPanel(new BorderLayout());
        this.bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
    }

    public ConfigPanelBuilder withInfoPanel() {
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Info", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);

        JTextField name = new JTextField(element.getId());
        name.setEditable(false);

        JTextField template = new JTextField(element.getElementClass().getName());
        template.setEditable(false);

        JTextField parent = new JTextField(element.getParent() != null ? element.getParent().getId() : "");
        parent.setEditable(false);

        int row = 0;
        addFormField(optionsPanel, gbc, row++, "Name:", name);
        addFormField(optionsPanel, gbc, row++, "Template:", template);
        addFormField(optionsPanel, gbc, row++, "Parent:", parent);

        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        return this;
    }

    public ConfigPanelBuilder withInfoCMPanel() {
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Info", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);

        JTextField name = new JTextField(element.getId());
        name.setEditable(false);

        JTextField parent = new JTextField(element.getParent() != null ? element.getParent().getId() : "");
        parent.setEditable(false);

        JTextField type = new JTextField(element.getTypeName());
        type.setEditable(false);

        int row = 0;
        addFormField(optionsPanel, gbc, row++, "Name:", name);
        addFormField(optionsPanel, gbc, row++, "Parent:", parent);
        addFormField(optionsPanel, gbc, row, "Type:", type);

        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        return this;
    }

    public ConfigPanelBuilder withCenterComponent(String title, JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        if (title != null && !title.isEmpty()) {
            wrapper.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                    title, TitledBorder.LEFT, TitledBorder.TOP));
        }
        wrapper.add(component, BorderLayout.CENTER);
        centerPanel.add(wrapper, BorderLayout.CENTER);
        return this;
    }

    public ConfigPanelBuilder addBottomButton(JButton button) {
        bottomPanel.add(button);
        return this;
    }

    public JPanel build() {
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        if (bottomPanel.getComponentCount() > 0) {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(mainPanel, BorderLayout.CENTER);
            wrapper.add(bottomPanel, BorderLayout.SOUTH);
            wrapper.setBorder(new EmptyBorder(5, 5, 5, 5));
            return wrapper;
        }

        JPanel finalPanel = new JPanel(new BorderLayout());
        finalPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        finalPanel.add(mainPanel, BorderLayout.CENTER);
        return finalPanel;
    }
    
    private void addFormField(JPanel parentPanel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        parentPanel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        parentPanel.add(field, gbc);
    }
}