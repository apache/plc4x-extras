package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class UnitPanel extends JPanel {

    private S88Element element;
    private Plc4xPlantModel model;
    private DefaultTableModel tableModel;
    private final String[] columns = {"Name", "Type", "Engineering_Unit", "ItemName"};

    public UnitPanel(Plc4xPlantModel model, S88Element element) {
        this.element = element;
        this.model = model;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));


        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Config", createSeekTab());
        tabbedPane.addTab("Browser", new JPanel()); // Placeholder

        add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));


        JButton btnAdd = new JButton("Add unit attribute");

        btnAdd.addActionListener(e -> {
            //it could show a dialog then update table
            JDialog dialog = new JDialog();
            dialog.setTitle("Unit attribute");
            dialog.setModal(true);
            AttributePanel panel = new AttributePanel(element, model);

            dialog.setLocationRelativeTo(null);

            dialog.setResizable(false);
            dialog.setContentPane(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
            updateTable();
        });

        bottomPanel.add(btnAdd);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createSeekTab() {
        JPanel panel = new JPanel(new BorderLayout(5, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));


        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Configuration", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 5, 4, 5);

        int row = 0;

        JPanel row0 = new JPanel(new BorderLayout(5,0));
        JTextField name = new JTextField(element.getId());
        name.setEditable(false);

        row0.add(new JLabel("Name: "), BorderLayout.WEST);
        row0.add(name, BorderLayout.CENTER);

        gbc.gridy = row++;
        optionsPanel.add(row0, gbc);


        JPanel row1 = new JPanel(new BorderLayout(5, 0));

        JTextField template = new JTextField(element.getElementClass().getName());
        template.setEditable(false);

        row1.add(new JLabel("Template: "), BorderLayout.WEST);
        row1.add(template, BorderLayout.CENTER);

        gbc.gridy = row++;
        optionsPanel.add(row1, gbc);


        JPanel row2 = new JPanel(new BorderLayout(5, 0));
        row2.add(new JLabel("Arbitration: "), BorderLayout.WEST);
        row2.add(new JComboBox<>(new String[]{"..."}), BorderLayout.CENTER);

        gbc.gridy = row++;
        optionsPanel.add(row2, gbc);


        JPanel row3 = new JPanel(new BorderLayout(5, 0));
        row3.add(new JLabel("Read/Write DB: "), BorderLayout.WEST);
        row3.add(new JComboBox<>(new String[]{"..."}), BorderLayout.CENTER);

        gbc.gridy = row++;
        optionsPanel.add(row3, gbc);

        // para agregar más opciones:
        // crear un JPanel row4, configurar y añadir row++


        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Unit attributes", TitledBorder.LEFT, TitledBorder.TOP));



        tableModel = new DefaultTableModel(null, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };


        JTable table = new JTable(tableModel);
        table.getTableHeader().setReorderingAllowed(false);
        table.setEnabled(false);
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setFillsViewportHeight(true);

        resultPanel.add(new JScrollPane(table), BorderLayout.CENTER);


        panel.add(optionsPanel, BorderLayout.NORTH);

        panel.add(resultPanel, BorderLayout.CENTER);

        return panel;
    }


    private void updateTable(){
        tableModel.setRowCount(0);


    }


}


