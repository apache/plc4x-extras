package org.apache.plc4x.malbec.s88.plant.actions;

import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.EnumerationDialogBuilder;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@ActionID(category = "Project", id = "org.apache.plc4x.malbec.s88.plant.actions.ManageEnumerationsAction")
@ActionRegistration(displayName = "#CTL_ManageEnumerationsAction", lazy = false)
@ActionReference(path = "Projects/org-plc4x-plant-project/Actions", position = 160)
@NbBundle.Messages({
        "CTL_ManageEnumerationsAction=Enumerations",
})
public class ManageEnumerationsAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public ManageEnumerationsAction() {
        this(Lookup.EMPTY);
    }

    private ManageEnumerationsAction(Lookup context) {
        super(Bundle.CTL_ManageEnumerationsAction());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ManageEnumerationsAction(actionContext);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        if (project == null) {
            return;
        }
        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null || plantModel.getModel() == null) {
            return;
        }
        new EnumerationManager(plantModel).show();
    }

    private static class EnumerationManager {
        private final Plc4xPlantModel plantModel;
        private final DefaultListModel<S88Enumeration> listModel = new DefaultListModel<>();

        EnumerationManager(Plc4xPlantModel plantModel) {
            this.plantModel = plantModel;
            for (S88Enumeration enumeration : plantModel.getModel().getEnumerations()) {
                listModel.addElement(enumeration);
            }
        }

        void show() {
            JDialog dialog = new JDialog();
            dialog.setTitle("Global Enumerations");
            dialog.setModal(true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            JList<S88Enumeration> list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer((JList<? extends S88Enumeration> l, S88Enumeration value,
                                  int index, boolean isSelected, boolean cellHasFocus) -> {
                JLabel label = new JLabel(value.getName());
                label.setOpaque(true);
                label.setBackground(isSelected ? l.getSelectionBackground() : l.getBackground());
                label.setForeground(isSelected ? l.getSelectionForeground() : l.getForeground());
                return label;
            });

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
            JButton btnNew = new JButton("New...");
            JButton btnEdit = new JButton("Edit...");
            JButton btnDelete = new JButton("Delete");
            JButton btnClose = new JButton("Close");

            btnNew.addActionListener(ev -> {
                new EnumerationDialogBuilder("New Enumeration Set")
                        .onSave((name, values) -> {
                            if (plantModel.getModel().findEnumeration(name) != null) {
                                JOptionPane.showMessageDialog(dialog,
                                        "An enumeration set named '" + name + "' already exists.",
                                        "Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            S88Enumeration enumeration = new S88Enumeration(name);
                            enumeration.getValues().putAll(values);
                            plantModel.getModel().registerEnumeration(enumeration);
                            save(plantModel);
                            listModel.addElement(enumeration);
                        })
                        .show();
            });

            btnEdit.addActionListener(ev -> {
                S88Enumeration selected = list.getSelectedValue();
                if (selected == null) {
                    JOptionPane.showMessageDialog(dialog, "Select an enumeration set to edit.",
                            "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                String name = selected.getName();
                S88Enumeration fresh = plantModel.getModel().findEnumeration(name);
                Map<String, Integer> current = fresh != null
                        ? new LinkedHashMap<>(fresh.getValues())
                        : new LinkedHashMap<>();
                new EnumerationDialogBuilder("Edit Enumeration Set: " + name)
                        .withInitialData(name, current)
                        .onSave((n, values) -> {
                            if (!n.equals(name) && plantModel.getModel().findEnumeration(n) != null) {
                                JOptionPane.showMessageDialog(dialog,
                                        "An enumeration set named '" + n + "' already exists.",
                                        "Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            plantModel.getModel().unregisterEnumeration(name);
                            S88Enumeration updated = new S88Enumeration(n);
                            updated.getValues().putAll(values);
                            plantModel.getModel().registerEnumeration(updated);
                            save(plantModel);
                            listModel.setElementAt(new S88Enumeration(n), listModel.indexOf(selected));
                        })
                        .show();
            });

            btnDelete.addActionListener(ev -> {
                S88Enumeration selected = list.getSelectedValue();
                if (selected == null) {
                    JOptionPane.showMessageDialog(dialog, "Select an enumeration set to delete.",
                            "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                plantModel.getModel().unregisterEnumeration(selected.getName());
                save(plantModel);
                listModel.removeElement(selected);
            });

            btnClose.addActionListener(ev -> dialog.dispose());

            btnPanel.add(btnNew);
            btnPanel.add(btnEdit);
            btnPanel.add(btnDelete);
            btnPanel.add(btnClose);

            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(320, 280));

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            content.add(scroll, BorderLayout.CENTER);
            content.add(btnPanel, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }

        private static void save(Plc4xPlantModel plantModel) {
            try {
                plantModel.save();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Failed to save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
