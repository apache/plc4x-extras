package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.core.CreateClassUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class UnitTemplateDialog extends JDialog implements TemplateDialog {

    private boolean accepted = false;
    private JTextField nameField;
    private final CreateClassUseCase createClassUseCase = new CreateClassUseCase();


    public UnitTemplateDialog(S88Element parent, Plc4xPlantModel model) {
        setTitle("Create Unit Template");
        setModal(true);
        setSize(400, 150);
        setLocationRelativeTo(null);

        nameField = new JTextField();

        JButton btnOk = new JButton("OK");

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> { dispose(); });

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Template Name:"), BorderLayout.NORTH);
        panel.add(nameField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(btnOk);
        buttons.add(btnCancel);
        panel.add(buttons, BorderLayout.SOUTH);

        setContentPane(panel);


        btnOk.addActionListener(e -> {
            accepted = true;
            try {
                createClassUseCase.execute(model.getModel(), parent, nameField.getText());
                model.save();
                dispose();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }

        });


    }

    @Override
    public boolean showDialog() {
        setVisible(true);
        return accepted;
    }

    @Override
    public String getTemplateName() {
        return nameField.getText();
    }
}