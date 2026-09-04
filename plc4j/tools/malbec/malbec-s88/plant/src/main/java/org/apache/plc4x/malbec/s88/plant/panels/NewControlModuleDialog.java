package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.ControlModules;
import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.core.CreateControlModuleUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;

import javax.swing.*;
import java.awt.*;

public class NewControlModuleDialog extends JDialog {

    private final JList<ControlModules> controlslist;
    private final DefaultListModel<ControlModules> listmodel;
    private final Plc4xPlantModel model;
    private final S88Element parent;

    public NewControlModuleDialog(Plc4xPlantModel model, S88Element parent) {
        this.model = model;
        this.parent = parent;
        this.listmodel = new DefaultListModel<>();
        this.controlslist = new JList<>(listmodel);

        setTitle("Select Control Module");
        setModal(true);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        for (ControlModules cm : ControlModules.values()) {
            this.listmodel.addElement(cm);
        }

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField txtName = new JTextField("", 20);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        top.add(new JLabel("Control Module Name:"));
        top.add(txtName);

        controlslist.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ControlModules) {
                    setText(((ControlModules) value).getName());
                }
                return this;
            }
        });
        controlslist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        controlslist.setSelectedIndex(0);

        JPanel center = new JPanel(new BorderLayout(0, 5));
        center.add(new JScrollPane(controlslist), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());
        btnOk.addActionListener(e -> createModule(txtName));
        actions.add(btnOk);
        actions.add(btnCancel);

        contentPane.add(top, BorderLayout.NORTH);
        contentPane.add(center, BorderLayout.CENTER);
        contentPane.add(actions, BorderLayout.SOUTH);

        setContentPane(contentPane);
        pack();
        setLocationRelativeTo(null);
    }

    private void createModule(JTextField txtName) {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Control Module Name cannot be empty!.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ControlModules selectedModule = controlslist.getSelectedValue();
        S88ControlModule cm = (selectedModule != null) ? selectedModule.create() : null;

        if (cm == null) {
            JOptionPane.showMessageDialog(this, "Please select a Control Module from the catalog.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            S88Element currentParent = model.getModel().findById(parent.getId()).orElse(parent);
            CreateControlModuleUseCase.execute(model.getModel(), currentParent, name, cm);
            model.save();
            dispose();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}