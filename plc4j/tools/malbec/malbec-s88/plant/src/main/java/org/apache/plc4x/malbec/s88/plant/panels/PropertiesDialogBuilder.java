package org.apache.plc4x.malbec.s88.plant.panels;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PropertiesDialogBuilder {
    private final JDialog dialog;
    private final JTabbedPane tabbedPane;


    private Runnable onOkAction;
    private Runnable onApplyAction;

    public PropertiesDialogBuilder(String title) {
        dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setModal(true);
        dialog.setSize(600, 420);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        tabbedPane = new JTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        dialog.setContentPane(mainPanel);
    }

    public TabBuilder beginTab(String tabTitle) {
        return new TabBuilder(this, tabTitle);
    }


    public PropertiesDialogBuilder addEmptyTab(String tabTitle) {
        tabbedPane.addTab(tabTitle, new JPanel());
        return this;
    }

    public PropertiesDialogBuilder onOk(Runnable action) {
        this.onOkAction = action;
        return this;
    }

    public PropertiesDialogBuilder onApply(Runnable action) {
        this.onApplyAction = action;
        return this;
    }

    public JDialog build() {

        JPanel mainPanel = (JPanel) dialog.getContentPane();
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
        return dialog;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");
        JButton btnApply = new JButton("Apply");
        JButton btnHelp = new JButton("Help");


        Dimension btnSize = new Dimension(85, 26);
        btnOk.setPreferredSize(btnSize);
        btnCancel.setPreferredSize(btnSize);
        btnApply.setPreferredSize(btnSize);
        btnHelp.setPreferredSize(btnSize);

        btnCancel.addActionListener(e -> dialog.dispose());

        btnOk.addActionListener(e -> {
            if (onOkAction != null) onOkAction.run();
            dialog.dispose();
        });

        btnApply.addActionListener(e -> {
            if (onApplyAction != null) onApplyAction.run();
        });

        bottomPanel.add(btnOk);
        bottomPanel.add(btnCancel);
        bottomPanel.add(btnApply);
        bottomPanel.add(btnHelp);

        return bottomPanel;
    }

    public static class TabBuilder {
        private final PropertiesDialogBuilder parentBuilder;
        private final JPanel gridPanel;
        private final GridBagConstraints gbc;
        private int currentRow = 0;
        private final String title;

        public TabBuilder(PropertiesDialogBuilder parentBuilder, String title) {
            this.parentBuilder = parentBuilder;
            this.title = title;

            gridPanel = new JPanel(new GridBagLayout());
            gridPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 10, 8, 10);
        }

        public TabBuilder addPropertyRow(String labelText, JComponent field) {
            gbc.gridy = currentRow;

            gbc.gridx = 0; gbc.weightx = 0.0;
            gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1;
            gridPanel.add(new JLabel(labelText), gbc); // Añadir Label[cite: 9]

            gbc.gridx = 1; gbc.weightx = 1.0;
            gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.gridwidth = 2;
            gridPanel.add(field, gbc); // Añadir Field[cite: 9]

            currentRow++;
            return this;
        }

        public TabBuilder addIconRow(String labelText, JComponent field, JButton iconButton) {
            gbc.gridy = currentRow;

            gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE;
            gridPanel.add(new JLabel(labelText), gbc);

            gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
            gridPanel.add(field, gbc);

            gbc.gridx = 2; gbc.gridheight = 2; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.CENTER;
            iconButton.setPreferredSize(new Dimension(65, 65));
            iconButton.setBackground(Color.WHITE);
            gridPanel.add(iconButton, gbc);

            gbc.gridheight = 1;
            currentRow++;
            return this;
        }

        public TabBuilder addCustomComponent(JComponent component) {
            gbc.gridy = currentRow;
            gbc.gridx = 0;
            gbc.gridwidth = 3;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            gridPanel.add(component, gbc);

            gbc.gridwidth = 1;
            gbc.weighty = 0.0;
            currentRow++;
            return this;
        }


        public PropertiesDialogBuilder endTab() {
            JPanel outerPanel = new JPanel(new BorderLayout());
            outerPanel.add(gridPanel, BorderLayout.NORTH);
            parentBuilder.tabbedPane.addTab(title, outerPanel);
            return parentBuilder;
        }
    }
}