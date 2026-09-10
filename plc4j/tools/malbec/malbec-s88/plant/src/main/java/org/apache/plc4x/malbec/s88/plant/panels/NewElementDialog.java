package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.core.CreateElementUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

public class NewElementDialog extends JDialog{

    private final JList<S88ElementClass> classList;
    private final DefaultListModel<S88ElementClass> classListModel;
    private final Plc4xPlantModel model;
    private final S88Element parent;
    private JTextField txtClass;
    private JTextField IDField;

    public NewElementDialog(Plc4xPlantModel model, S88Element parent, List<S88ElementClass> definedClasses) {

        this.classListModel = new DefaultListModel<>();
        for(S88ElementClass ec : definedClasses){
            this.classListModel.addElement(ec);
        }

        classList = new JList<>(classListModel);

        this.parent = parent;
        this.model = model;

        setTitle("Create New Element");
        setModal(true);
        setSize(1000, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));



        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        add(mainPanel);


        if (!definedClasses.isEmpty()) {
            classList.setSelectedIndex(0);
        }
    }

    private JPanel createTopPanel() {
        JPanel middlePanel = new JPanel(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));

        JPanel headerTemplatesPanel = new JPanel(new BorderLayout());
        headerTemplatesPanel.add(new JLabel("Templates"), BorderLayout.WEST);

        JButton btnNewTemplate = new JButton("New Template");

        if(parent.getLevel() == S88Level.EQUIPMENTMODULE) btnNewTemplate.setEnabled(false);

        btnNewTemplate.addActionListener(e -> {
            int sizeBefore = parent.getElementClasses().size();
            TemplateFactory.createDialog(parent, model, this);

                List<S88ElementClass> updated = parent.getElementClasses();
                if(updated.size() > sizeBefore){
                    classListModel.addElement(updated.getLast());
                }

        });
        headerTemplatesPanel.add(btnNewTemplate, BorderLayout.EAST);


        leftPanel.add(headerTemplatesPanel, BorderLayout.NORTH);

        classList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof S88ElementClass) {
                    setText(((S88ElementClass) value).getName());
                }
                return this;
            }
        });
        classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        classList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && classList.getSelectedValue() != null) {
                S88ElementClass ec = classList.getSelectedValue();
                txtClass.setText(ec.getName().toUpperCase());
            }
        });

        classList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                if (e.getClickCount() == 2) {
                    int index = classList.locationToIndex(e.getPoint());
                    if (index >= 0 && classList.getCellBounds(index, index).contains(e.getPoint())) {
                        S88ElementClass ec = classList.getModel().getElementAt(index);
                        TemplateFactory.showTemplate(ec);
                    }
                }
            }
        });

        JScrollPane listScrollPane = new JScrollPane(classList);
        listScrollPane.setPreferredSize(new Dimension(250, 0));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        middlePanel.add(leftPanel, BorderLayout.WEST);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("General", createDetailsTab());
        middlePanel.add(tabbedPane, BorderLayout.CENTER);

        return middlePanel;
    }

    private JScrollPane createDetailsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        IDField = createField("", true);
        addFormRow(panel, gbc, row++, "Element ID", IDField);
        addFormRow(panel, gbc, row++, "Level", createField(parent.getLevel().getChildLevel().toString(), false));


        txtClass = createField("", false);
        addFormRow(panel, gbc, row++, "Template", txtClass);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(labelText + " ", SwingConstants.RIGHT), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }

    private JTextField createField(String text, Boolean editable) {
        JTextField tf = new JTextField(text);
        tf.setEditable(editable);
        tf.setBackground(new Color(240, 240, 240));
        return tf;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton btnCreate = new JButton("Create");
        btnCreate.setPreferredSize(new Dimension(100, 26));


        btnCreate.addActionListener(this::actionPerformed);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setPreferredSize(new Dimension(100, 26));
        btnCancel.addActionListener(e -> dispose());

        bottomPanel.add(btnCreate);
        bottomPanel.add(btnCancel);

        return bottomPanel;
    }

    private void actionPerformed(ActionEvent e) {
        S88ElementClass selected = classList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a template for the new Element.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }


            try {
                S88Element currentParent = model.getModel().findById(parent.getId()).orElse(parent);
                CreateElementUseCase.execute(model.getModel(), currentParent, IDField.getText(), selected);
                model.save();
                dispose();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
    }
}
