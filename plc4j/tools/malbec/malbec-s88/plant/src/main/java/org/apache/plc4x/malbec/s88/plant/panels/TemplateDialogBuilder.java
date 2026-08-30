package org.apache.plc4x.malbec.s88.plant.panels;

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TemplateDialogBuilder {
    private final JDialog dialog;
    private final JPanel formPanel;
    private JTextField txtTemplateName;
    private int currentRow = 0;
    private final Boolean showButtons;

    private Runnable onOkAction;

    public TemplateDialogBuilder(String title) {
        this(title, true, null);
    }

    public TemplateDialogBuilder(String title, Window owner) {
        this(title, true, owner);
    }

    public TemplateDialogBuilder(String title, boolean showButtons) {
        this(title, showButtons, null);
    }

    public TemplateDialogBuilder(String title, boolean showButtons, Window owner) {
        dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        formPanel = new JPanel(new GridBagLayout());
        this.showButtons = showButtons;
    }

    public TemplateDialogBuilder withNameField() {
        txtTemplateName = new JTextField(20);
        addRow("Template Name:", txtTemplateName);
        return this;
    }

    public TemplateDialogBuilder withReadOnlyNameField(String value) {
        JTextField field = new JTextField(value != null ? value : "");
        field.setEditable(false);
        addRow("Template Name:", field);
        return this;
    }

    public TemplateDialogBuilder addRow(String labelText, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = currentRow;
        gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(component, gbc);
        currentRow++;
        return this;
    }

    public TemplateDialogBuilder addComponentRow(JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridwidth = 2; gbc.gridx = 0; gbc.gridy = currentRow;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(component, gbc);
        currentRow++;
        return this;
    }

    public TemplateDialogBuilder onAccept(Runnable action) {
        this.onOkAction = action;
        return this;
    }

    public String getTemplateName() {
        return txtTemplateName != null ? txtTemplateName.getText() : "";
    }

    public JDialog getDialog() {
        return dialog;
    }

    public JDialog build() {
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(15, 15, 15, 15));

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);
        contentPane.add(scrollPane, BorderLayout.CENTER);
        if(Boolean.TRUE.equals(showButtons)) {
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            JButton btnOk = new JButton("OK");
            JButton btnCancel = new JButton("Cancel");
            btnOk.setPreferredSize(new Dimension(80, 26));
            btnCancel.setPreferredSize(new Dimension(80, 26));

            btnCancel.addActionListener(e -> dialog.dispose());

            btnOk.addActionListener(e -> {
                try {
                    if (onOkAction != null) {
                        onOkAction.run();
                    }
                    dialog.dispose();
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            });

            buttonPanel.add(btnOk);
            buttonPanel.add(btnCancel);
            contentPane.add(buttonPanel, BorderLayout.SOUTH);
        }
        dialog.setContentPane(contentPane);

        if(Boolean.TRUE.equals(showButtons)) {
            dialog.getRootPane().setDefaultButton((JButton) null);
        }
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        return dialog;
    }
}