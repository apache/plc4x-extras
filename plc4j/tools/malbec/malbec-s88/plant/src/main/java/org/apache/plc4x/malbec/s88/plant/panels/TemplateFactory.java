package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.core.CreateClassUseCase;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class TemplateFactory {

    public static JDialog createDialog(S88Element parent, Plc4xPlantModel model) {
        JDialog dialog = switch (parent.getLevel()) {
            case AREA -> createSimpleTemplateDialog(parent, model);
            case PROCESSCELL -> createUnitTemplateDialog(parent, model);
            case UNIT -> createEMTemplateDialog(parent, model);
            case EQUIPMENTMODULE -> createSimpleTemplateDialog(parent, model);
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + parent.getLevel().name());
        };

        if(dialog != null){
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }

        return dialog;
    }

    public static JDialog createSimpleTemplateDialog(S88Element parent, Plc4xPlantModel model) {
        TemplateDialogBuilder builder = new TemplateDialogBuilder("Create " + parent.getLevel().getChildLevel() + " Template");

        Runnable okLogic = () -> {
            CreateClassUseCase.execute(model.getModel(), parent, builder.getTemplateName(), null);
            try {
                model.save();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        return builder
                .withNameField()
                .onAccept(okLogic)
                .build();
    }

    public static JDialog createUnitTemplateDialog(S88Element parent, Plc4xPlantModel model) {
        String[] columns = {"Name", "Engineering_Units", "Type"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0);
        JTable table = createStandardTable(tableModel);

        JPanel attributePanel = createAttributePanelWithButtons(table, tableModel, "Unit attributes");

        TemplateDialogBuilder builder = new TemplateDialogBuilder("Create " + parent.getLevel().getChildLevel() + " Template");

        Runnable okLogic = () -> {
            Map<String, Object> propertyMap = buildPropertiesFromTable(tableModel);
            CreateClassUseCase.execute(model.getModel(), parent, builder.getTemplateName(), propertyMap);
            try {
                model.save();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        return builder
                .withNameField()
                .addComponentRow(attributePanel)
                .onAccept(okLogic)
                .build();
    }

    public static JDialog createEMTemplateDialog(S88Element parent, Plc4xPlantModel model) {
        DefaultTableModel paramsTableModel = new DefaultTableModel(new String[]{"Name", "Engineering_Units", "Type", "Max", "Min", "Default"}, 0);
        DefaultTableModel reportsTableModel = new DefaultTableModel(new String[]{"Name", "Engineering_Units", "Type"}, 0);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Parameters", createTabPanel(paramsTableModel));
        tabbedPane.addTab("Reports", createTabPanel(reportsTableModel));

        TemplateDialogBuilder builder = new TemplateDialogBuilder("Create " + parent.getLevel().getChildLevel() + " Template");

        Runnable okLogic = () -> {
            Map<String, Object> propertyBag = new LinkedHashMap<>();
            propertyBag.put("Parameters", buildPropertiesFromTable(paramsTableModel));
            propertyBag.put("Reports", buildPropertiesFromTable(reportsTableModel));

            CreateClassUseCase.execute(model.getModel(), parent, builder.getTemplateName(), propertyBag);
            try {
                model.save();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        return builder
                .withNameField()
                .addComponentRow(tabbedPane)
                .onAccept(okLogic)
                .build();
    }


    private static JPanel createAttributePanelWithButtons(JTable table, DefaultTableModel tableModel, String title) {
        JPanel attributePanel = new JPanel(new BorderLayout());
        attributePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                title, TitledBorder.LEFT, TitledBorder.TOP));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton addButton = new JButton("Add attribute");
        JButton removeButton = new JButton("Remove attribute");

        addButton.addActionListener(e -> tableModel.addRow(new Object[]{"", "", "INTEGER"}));
        removeButton.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                tableModel.removeRow(selectedRow);
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(buttonPanel, BorderLayout.NORTH);
        contentPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        attributePanel.add(contentPanel, BorderLayout.CENTER);
        return attributePanel;
    }

    private static JPanel createTabPanel(DefaultTableModel tableModel) {
        JPanel panel = new JPanel(new BorderLayout());
        JTable table = createStandardTable(tableModel);

        JPanel paramsButtons = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton addP = new JButton("Add");
        JButton removeP = new JButton("Remove");

        addP.addActionListener(e -> {
            Object[] emptyRow = new Object[tableModel.getColumnCount()];
            emptyRow[0] = "";
            emptyRow[1] = "";
            emptyRow[2] = "INTEGER";
            for(int i = 3; i < emptyRow.length; i++) emptyRow[i] = "";
            tableModel.addRow(emptyRow);
        });

        removeP.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                tableModel.removeRow(selectedRow);
            }
        });

        paramsButtons.add(addP);
        paramsButtons.add(removeP);
        panel.add(paramsButtons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    private static JTable createStandardTable(DefaultTableModel tableModel){
        JTable table = new JTable(tableModel);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"INTEGER", "REAL", "ENUMERATION"});
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(typeCombo));
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setFillsViewportHeight(true);

        return table;
    }


    private static Map<String, Object> buildPropertiesFromTable(DefaultTableModel tableModel) {
        Map<String, Object> propertyMap = new LinkedHashMap<>();
        int rowCount = tableModel.getRowCount();
        int colCount = tableModel.getColumnCount();

        for (int i = 0; i < rowCount; i++) {
            Object nameObj = tableModel.getValueAt(i, 0);
            String name = (nameObj != null) ? nameObj.toString().trim() : "";

            if (name.isEmpty()) {
                continue;
            }

            Map<String, Object> property = new LinkedHashMap<>();

            for (int j = 1; j < colCount; j++) {
                String columnName = tableModel.getColumnName(j);
                Object cellValueObj = tableModel.getValueAt(i, j);
                String cellValue = (cellValueObj != null) ? cellValueObj.toString() : "";

                if (columnName.equals("Type") && cellValue.isEmpty()) {
                    cellValue = "INTEGER";
                }

                property.put(columnName, cellValue);
            }

            propertyMap.put(name, property);
        }
        return propertyMap;
    }

    public static void showTemplate(S88ElementClass ec) {
        JDialog dialog = switch (ec.getTargetLevel()) {
            case UNIT -> showUnitTemplate(ec);
            case EQUIPMENTMODULE -> showEMTemplate(ec);
            default -> null;
        };

        if (dialog != null) {
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }
    }

    public static JDialog showUnitTemplate(S88ElementClass ec) {
        String[] columns = {"Name", "Engineering_Units", "Type"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);

        populateTable(tableModel, ec.getProperties());

        JPanel attributePanel = createReadOnlyAttributePanel(tableModel, "Unit attributes");

        TemplateDialogBuilder builder = new TemplateDialogBuilder(ec.getName() != null ? ec.getName() : "Template")
                .withReadOnlyNameField(ec.getName())
                .addComponentRow(attributePanel)
                .onAccept(() -> { });
        return builder.build();
    }

    public static JDialog showEMTemplate(S88ElementClass ec) {
        DefaultTableModel paramsTableModel = createReadOnlyTableModel(new String[]{"Name", "Engineering_Units", "Type", "Max", "Min", "Default"});
        DefaultTableModel reportsTableModel = createReadOnlyTableModel(new String[]{"Name", "Engineering_Units", "Type"});

        Object params = ec.getProperty("Parameters");
        if (params instanceof Map<?, ?>) {
            populateTable(paramsTableModel, (Map<String, Object>) params);
        }
        Object reports = ec.getProperty("Reports");
        if (reports instanceof Map<?, ?>) {
            populateTable(reportsTableModel, (Map<String, Object>) reports);
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Parameters", createReadOnlyTabPanel(paramsTableModel));
        tabbedPane.addTab("Reports", createReadOnlyTabPanel(reportsTableModel));

        TemplateDialogBuilder builder = new TemplateDialogBuilder(ec.getName() != null ? ec.getName() : "Template")
                .withReadOnlyNameField(ec.getName())
                .addComponentRow(tabbedPane)
                .onAccept(() -> { });
        return builder.build();
    }

    private static void populateTable(DefaultTableModel tableModel, Map<String, Object> propertiesMap) {
        int colCount = tableModel.getColumnCount();
        for (Map.Entry<String, Object> entry : propertiesMap.entrySet()) {
            Object[] row = new Object[colCount];
            row[0] = entry.getKey();
            Object val = entry.getValue();
            Map<String, Object> values = val instanceof Map<?, ?> ? (Map<String, Object>) val : null;
            for (int i = 1; i < colCount; i++) {
                String columnName = tableModel.getColumnName(i);
                Object v = values != null ? values.get(columnName) : null;
                row[i] = v != null ? v : "";
            }
            tableModel.addRow(row);
        }
    }

    private static JPanel createReadOnlyAttributePanel(DefaultTableModel tableModel, String title) {
        JPanel attributePanel = new JPanel(new BorderLayout());
        attributePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                title, TitledBorder.LEFT, TitledBorder.TOP));
        attributePanel.add(new JScrollPane(createStandardTable(tableModel)), BorderLayout.CENTER);
        return attributePanel;
    }

    private static JPanel createReadOnlyTabPanel(DefaultTableModel tableModel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(createStandardTable(tableModel)), BorderLayout.CENTER);
        return panel;
    }

    private static DefaultTableModel createReadOnlyTableModel(String[] columns) {
        return new DefaultTableModel(null, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

}