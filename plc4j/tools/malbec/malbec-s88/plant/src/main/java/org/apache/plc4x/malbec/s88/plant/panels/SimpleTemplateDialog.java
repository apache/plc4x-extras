package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.core.CreateClassUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;


public class SimpleTemplateDialog extends JDialog {

    private Plc4xPlantModel model;
    private S88Element parent;
    private JPanel formPanel;
    private int currentRow = 0;

    private CreateClassUseCase createClassUseCase = new CreateClassUseCase();


    private JTextField txtTemplateName;
    private boolean isOkPressed = false;


    public SimpleTemplateDialog(S88Element parent, Plc4xPlantModel model) {

        super(null, "Create " + parent.getLevel().getChildLevel() + " Template", Dialog.ModalityType.APPLICATION_MODAL);
        this.parent = parent;
        this.model = model;
        initUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(15, 15, 15, 15));


        formPanel = new JPanel(new GridBagLayout());

        txtTemplateName = new JTextField(20);
        addRow("Template Name:", txtTemplateName);


        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);
        contentPane.add(scrollPane, BorderLayout.CENTER);


        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");

        Dimension btnSize = new Dimension(80, 26);
        btnOk.setPreferredSize(btnSize);
        btnCancel.setPreferredSize(btnSize);


        btnOk.addActionListener(e -> {
            isOkPressed = true;
            try {
                createClassUseCase.execute(model.getModel(), parent, txtTemplateName.getText());
                model.save();
                dispose();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            dispose();
        });

        btnCancel.addActionListener(e -> {
            isOkPressed = false;
            dispose();
        });

        buttonPanel.add(btnOk);
        buttonPanel.add(btnCancel);

        contentPane.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(contentPane);


        getRootPane().setDefaultButton(btnOk);
    }


    public void addRow(String labelText, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);


        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel(labelText), gbc);


        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(component, gbc);

        currentRow++;
    }


    public void addExpandingRow(String labelText, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);


        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        formPanel.add(new JLabel(labelText), gbc);


        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(component, gbc);

        currentRow++;
    }


    public boolean isOkPressed() {
        return isOkPressed;
    }

    public String getTemplateName() {
        return txtTemplateName.getText();
    }


    public JTextField getTxtTemplateName() {
        return txtTemplateName;
    }
}