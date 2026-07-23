package org.apache.plc4x.malbec.s88.plant.panels;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public abstract class PropertiesDialog extends JDialog {

    protected JTabbedPane tabbedPane;

    public PropertiesDialog(String title) {
        setTitle(title);
        setModal(true);
        setSize(600, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        tabbedPane = new JTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        add(mainPanel);
    }


    protected class FormTab {
        private JPanel gridPanel;
        private GridBagConstraints gbc;
        private int currentRow = 0;

        public FormTab(String tabTitle) {
            gridPanel = new JPanel(new GridBagLayout());
            gridPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 10, 8, 10);


            JPanel outerPanel = new JPanel(new BorderLayout());
            outerPanel.add(gridPanel, BorderLayout.NORTH);


            tabbedPane.addTab(tabTitle, outerPanel);
        }

        public void addPropertyRow(String labelText, JComponent field) {
            gbc.gridy = currentRow;

            gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1;
            gridPanel.add(new JLabel(labelText), gbc);

            gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.gridwidth = 2;
            gridPanel.add(field, gbc);

            currentRow++;
        }

        public void addIconRow(String labelText, JComponent field, JButton iconButton) {
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
        }
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton btnOk = new JButton("OK");

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnApply = new JButton("Apply");

        JButton btnHelp = new JButton("Help");

        Dimension btnSize = new Dimension(85, 26);
        btnOk.setPreferredSize(btnSize);
        btnCancel.setPreferredSize(btnSize);
        btnApply.setPreferredSize(btnSize);
        btnHelp.setPreferredSize(btnSize);

        bottomPanel.add(btnOk);
        bottomPanel.add(btnCancel);
        bottomPanel.add(btnApply);
        bottomPanel.add(btnHelp);

        return bottomPanel;
    }
}