package org.apache.plc4x.malbec.s88.plant.panels;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class EnumerationDialogBuilder {

    private final String title;
    private boolean isEditMode = false;
    private String initialName = "";
    private Map<String, Integer> initialValues = new LinkedHashMap<>();

    private BiConsumer<String, Map<String, Integer>> onSaveAction;

    public EnumerationDialogBuilder(String title) {
        this.title = title;
    }

    public EnumerationDialogBuilder withInitialData(String name, Map<String, Integer> values) {
        this.isEditMode = true;
        this.initialName = name;
        if (values != null) {
            this.initialValues = values;
        }
        return this;
    }

    public EnumerationDialogBuilder onSave(BiConsumer<String, Map<String, Integer>> action) {
        this.onSaveAction = action;
        return this;
    }

    public void show() {
        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField txtName = new JTextField(initialName, 20);

        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Label", "Index"}, 0);
        for (var entry : initialValues.entrySet()) {
            tableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
        JTable table = new JTable(tableModel);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        top.add(new JLabel("Enumeration Set Name:"));
        top.add(txtName);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton btnAdd = new JButton("Add");
        JButton btnRemove = new JButton("Remove");
        btnAdd.addActionListener(e -> tableModel.addRow(new Object[]{"", 0}));
        btnRemove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
            }
        });
        btnPanel.add(btnAdd);
        btnPanel.add(btnRemove);

        JPanel center = new JPanel(new BorderLayout(0, 5));
        center.add(btnPanel, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dialog.dispose());
        btnOk.addActionListener(e -> {
            String name = txtName.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Enumeration set name cannot be empty.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Map<String, Integer> values = new LinkedHashMap<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String label = String.valueOf(tableModel.getValueAt(i, 0)).trim();
                if (label.isEmpty()) {
                    continue;
                }
                Object idx = tableModel.getValueAt(i, 1);
                int index;
                if (idx == null) {
                    index = 0;
                } else if (idx instanceof Number n) {
                    index = n.intValue();
                } else {
                    try {
                        index = Integer.parseInt(String.valueOf(idx).trim());
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(dialog,
                                "Index for '" + label + "' must be a number.",
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                values.put(label, index);
            }
            if (onSaveAction != null) {
                onSaveAction.accept(name, values);
            }
            dialog.dispose();
        });
        actions.add(btnOk);
        actions.add(btnCancel);

        contentPane.add(top, BorderLayout.NORTH);
        contentPane.add(center, BorderLayout.CENTER);
        contentPane.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(contentPane);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }
}
