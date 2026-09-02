package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.core.CreateClassUseCase;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class TemplateFactory {

    public static void createDialog(S88Element parent, Plc4xPlantModel model) {
        createDialog(parent, model, null);
    }

    public static void createDialog(S88Element parent, Plc4xPlantModel model, Window owner) {
        JDialog dialog = switch (parent.getLevel()) {
            case AREA -> createSimpleTemplateDialog(parent, model, owner);
            case PROCESSCELL -> createUnitTemplateDialog(parent, model, owner);
            case UNIT -> createEMTemplateDialog(parent, model, owner);
            case EQUIPMENTMODULE -> createSimpleTemplateDialog(parent, model, owner);
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + parent.getLevel().name());
        };

        if(dialog != null){
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }

    }

    public static JDialog createSimpleTemplateDialog(S88Element parent, Plc4xPlantModel model, Window owner) {
        TemplateDialogBuilder builder = new TemplateDialogBuilder("Create " + parent.getLevel().getChildLevel() + " Template", owner);

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

    public static JDialog createUnitTemplateDialog(S88Element parent, Plc4xPlantModel model, Window owner) {
        String[] columns = {"Name", "Eng_Units/Enum", "Type"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);

        List<S88Enumeration> enumerations = (model != null && model.getModel() != null)
                ? model.getModel().getEnumerations()
                : List.of();

        TemplateDialogBuilder builder = new TemplateDialogBuilder("Create " + parent.getLevel().getChildLevel() + " Template", owner);

        JPanel attributePanel = createAttributeTabPanel(tableModel, enumerations, builder::getDialog);

        Runnable okLogic = () -> {
            Map<String, Object> propertyMap = buildPropertiesFromTable(tableModel);
            assert model != null;
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

    public static JDialog createEMTemplateDialog(S88Element parent, Plc4xPlantModel model, Window owner) {
        DefaultTableModel paramsTableModel = createReadOnlyTableModel(new String[]{"Name", "Eng_Units/Enum", "Type", "Max", "Min", "Default"});
        DefaultTableModel reportsTableModel = createReadOnlyTableModel(new String[]{"Name", "Eng_Units/Enum", "Type"});

        List<S88Enumeration> enumerations = (model != null && model.getModel() != null)
                ? model.getModel().getEnumerations()
                : List.of();

        TemplateDialogBuilder builder = new TemplateDialogBuilder("Create " + parent.getLevel().getChildLevel() + " Template", owner);

        Supplier<Window> ownerSupplier = builder::getDialog;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Parameters", createEntryTabPanel(paramsTableModel, enumerations, false, "Add", "Parameter", ownerSupplier));
        tabbedPane.addTab("Reports", createEntryTabPanel(reportsTableModel, enumerations, true, "Add report", "Report", ownerSupplier));

        Runnable okLogic = () -> {
            Map<String, Object> propertyBag = new LinkedHashMap<>();
            propertyBag.put("Parameters", buildPropertiesFromTable(paramsTableModel));
            propertyBag.put("Reports", buildPropertiesFromTable(reportsTableModel));

            assert model != null;
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


    private static ParameterDialogBuilder parameterDialog(String title, List<S88Enumeration> enumerations, boolean reports, Window owner) {
        ParameterDialogBuilder builder = new ParameterDialogBuilder(title).withEnumerations(enumerations).withOwner(owner);
        return reports ? builder.reportsMode() : builder;
    }

    private static JPanel createEntryTabPanel(DefaultTableModel tableModel, List<S88Enumeration> enumerations,
                                              boolean reports, String addButtonLabel, String titleNoun,
                                              Supplier<Window> ownerSupplier) {
        JPanel panel = new JPanel(new BorderLayout());
        JTable table = createStandardTable(tableModel);

        JPanel paramsButtons = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton addP = new JButton(addButtonLabel);
        JButton removeP = new JButton("Remove");

        addP.addActionListener(e -> parameterDialog("Add " + titleNoun, enumerations, reports, ownerSupplier.get())
                .onSave((name, params) -> {
                    Object[] row = new Object[tableModel.getColumnCount()];
                    row[0] = name;
                    for (int j = 1; j < row.length; j++) {
                        row[j] = Objects.toString(params.get(tableModel.getColumnName(j)), "");
                    }
                    tableModel.addRow(row);
                })
                .show());

        removeP.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                tableModel.removeRow(selectedRow);
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() == 2) {
                    int row = table.rowAtPoint(me.getPoint());
                    if (row == -1) {
                        return;
                    }
                    Map<String, Object> rowData = readRowAsMap(tableModel, row);
                    String name = Objects.toString(rowData.remove("Name"), "");
                    parameterDialog("Edit " + titleNoun, enumerations, reports, ownerSupplier.get())
                            .withInitialData(name, rowData)
                            .onSave((updatedName, params) -> {
                                tableModel.setValueAt(updatedName, row, 0);
                                for (int j = 1; j < tableModel.getColumnCount(); j++) {
                                    tableModel.setValueAt(
                                            Objects.toString(params.get(tableModel.getColumnName(j)), ""),
                                            row, j);
                                }
                            })
                            .show();
                }
            }
        });

        paramsButtons.add(addP);
        paramsButtons.add(removeP);
        panel.add(paramsButtons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel createAttributeTabPanel(DefaultTableModel tableModel, List<S88Enumeration> enumerations,
                                                  Supplier<Window> ownerSupplier) {
        JPanel panel = createEntryTabPanel(tableModel, enumerations, true, "Add attribute", "Attribute", ownerSupplier);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Unit attributes", TitledBorder.LEFT, TitledBorder.TOP));
        return panel;
    }

    private static Map<String, Object> readRowAsMap(DefaultTableModel tableModel, int row) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int j = 0; j < tableModel.getColumnCount(); j++) {
            Object value = tableModel.getValueAt(row, j);
            data.put(tableModel.getColumnName(j), value != null ? value.toString() : "");
        }
        return data;
    }

    private static JTable createStandardTable(DefaultTableModel tableModel){
        JTable table = new JTable(tableModel);
        JComboBox<String> typeCombo = new JComboBox<>(DataType.displayNames());
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
                    cellValue = DataType.defaultForEmpty().name();
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
        String[] columns = {"Name", "Eng_Units/Enum", "Type"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);

        populateTable(tableModel, ec.getProperties());

        JPanel attributePanel = createReadOnlyAttributePanel(tableModel);

        TemplateDialogBuilder builder = new TemplateDialogBuilder(ec.getName() != null ? ec.getName() : "Template")
                .withReadOnlyNameField(ec.getName())
                .addComponentRow(attributePanel)
                .onAccept(() -> { });
        return builder.build();
    }

    public static JDialog showEMTemplate(S88ElementClass ec) {
        DefaultTableModel paramsTableModel = createReadOnlyTableModel(new String[]{"Name", "Eng_Units/Enum", "Type", "Max", "Min", "Default"});
        DefaultTableModel reportsTableModel = createReadOnlyTableModel(new String[]{"Name", "Eng_Units/Enum", "Type"});

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

    private static JPanel createReadOnlyAttributePanel(DefaultTableModel tableModel) {
        JPanel attributePanel = new JPanel(new BorderLayout());
        attributePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                "Unit attributes", TitledBorder.LEFT, TitledBorder.TOP));
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