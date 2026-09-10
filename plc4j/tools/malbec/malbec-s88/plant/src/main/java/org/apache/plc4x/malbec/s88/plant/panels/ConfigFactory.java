package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88ControlModule;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.core.UpdatePropertyUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.util.Exceptions;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigFactory {
    private ConfigFactory() {
        /* This utility class should not be instantiated */
    }


    public static JPanel createConfigPanel(Plc4xPlantModel model, S88Element element) {
        return switch(element.getLevel()){
            case PROCESSCELL -> new JPanel();
            case UNIT -> buildUnitPanel(model, element);
            case EQUIPMENTMODULE -> buildEMPanel(model, element);
            case CONTROLMODULE -> buildCMPanel(model, element);
            default -> new JPanel();
        };
    }

    private static JPanel buildUnitPanel(Plc4xPlantModel model, S88Element element) {
        String[] columns = {"Name", "Type", "Eng_Units/Enum", "Reference"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);
        JTable table = createStandardConfigTable(tableModel);

        updateUnitTableData(element, tableModel);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = table.rowAtPoint(e.getPoint());
                    String name = String.valueOf(table.getValueAt(row, 0));
                    Map<String, Object> prop = element.getStructuredProperty(name);

                    new AttributeDialogBuilder("Edit Attribute")
                            .withEnumerations(enumerationNames(model))
                            .withControlModules(controlModules(model))
                            .withEditable(false)
                            .withInitialData(name, prop)
                            .onSave((updatedName, updatedProps) -> {
                                try {
                                    UpdatePropertyUseCase.execute(model.getModel(), element, updatedName, updatedProps);
                                    model.save();
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            })
                            .onUpdate(() -> updateUnitTableData(element, tableModel))
                            .show();
                }
            }
        });

        JButton btnAdd = new JButton("Add unit attribute");
        btnAdd.addActionListener(e -> new AttributeDialogBuilder("Create Unit Attribute")
                .withEnumerations(enumerationNames(model))
                .withControlModules(controlModules(model))
                .withEditable(false)
                .onSave((name, props) -> {
                    try {
                        UpdatePropertyUseCase.execute(model.getModel(), element, name, props);
                        model.save();
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                })
                .onUpdate(() -> updateUnitTableData(element, tableModel))
                .show());

        return new ConfigPanelBuilder(element)
                .withInfoPanel()
                .withCenterComponent("Unit attributes", new JScrollPane(table))
                .addBottomButton(btnAdd)
                .build();
    }

    private static List<String> enumerationNames(Plc4xPlantModel model) {
        List<String> names = new ArrayList<>();
        if (model != null && model.getModel() != null) {
            for (S88Enumeration enumeration : model.getModel().getEnumerations()) {
                names.add(enumeration.getName());
            }
        }
        return names;
    }

    private static List<S88Enumeration> enumerations(Plc4xPlantModel model) {
        if (model != null && model.getModel() != null) {
            return model.getModel().getEnumerations();
        }
        return Collections.emptyList();
    }

    private static List<S88ControlModule> controlModules(Plc4xPlantModel model) {
        if (model != null && model.getModel() != null) {
            return model.getModel().findControlModules();
        }
        return Collections.emptyList();
    }

    private static JPanel buildEMPanel(Plc4xPlantModel model, S88Element element) {
        String[] paramColumns = {"Name", "Eng_Units/Enum", "Type", "Max", "Min", "Default", "Reference"};
        String[] reportColumns = {"Name", "Eng_Units/Enum", "Type", "Reference"};

        DefaultTableModel paramsTableModel = createReadOnlyTableModel(paramColumns);
        DefaultTableModel reportsTableModel = createReadOnlyTableModel(reportColumns);

        JTable paramsTable = createStandardConfigTable(paramsTableModel);
        JTable reportsTable = createStandardConfigTable(reportsTableModel);

        updateEMTableData(element, paramsTableModel, "Parameters");
        updateEMTableData(element, reportsTableModel, "Reports");

        paramsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = paramsTable.rowAtPoint(e.getPoint());
                    String name = String.valueOf(paramsTable.getValueAt(row, 0));
                    Map<String, Object> params = element.getStructuredProperty("Parameters");
                    Map<String, Object> bag = params != null ? (Map<String, Object>) params.get(name) : null;

                    new ParameterDialogBuilder("Edit Parameter")
                            .withEnumerations(enumerations(model))
                            .withControlModules(controlModules(model))
                            .withInitialData(name, bag)
                            .onSave((updatedName, updatedProps) -> {
                                Map<String, Object> updated = element.getStructuredProperty("Parameters");
                                if (updated == null) {
                                    updated = new LinkedHashMap<>();
                                }
                                updated.put(updatedName, updatedProps);
                                try {
                                    UpdatePropertyUseCase.execute(model.getModel(), element, "Parameters", updated);
                                    model.save();
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                                updateEMTableData(element, paramsTableModel, "Parameters");
                            })
                            .show();
                }
            }
        });

        reportsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = reportsTable.rowAtPoint(e.getPoint());
                    String name = String.valueOf(reportsTable.getValueAt(row, 0));
                    Map<String, Object> reports = element.getStructuredProperty("Reports");
                    Map<String, Object> bag = reports != null ? (Map<String, Object>) reports.get(name) : null;

                    new ParameterDialogBuilder("Edit Report")
                            .withEnumerations(enumerations(model))
                            .withControlModules(controlModules(model))
                            .reportsMode()
                            .withInitialData(name, bag)
                            .onSave((updatedName, updatedProps) -> {
                                Map<String, Object> updated = element.getStructuredProperty("Reports");
                                if (updated == null) {
                                    updated = new LinkedHashMap<>();
                                }
                                updated.put(updatedName, updatedProps);
                                try {
                                    UpdatePropertyUseCase.execute(model.getModel(), element, "Reports", updated);
                                    model.save();
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                                updateEMTableData(element, reportsTableModel, "Reports");
                            })
                            .show();
                }
            }
        });

        JButton btnAddParameter = new JButton("Add parameter");
        btnAddParameter.addActionListener(e -> new ParameterDialogBuilder("Add Parameter")
                .withEnumerations(enumerations(model))
                .withControlModules(controlModules(model))
                .reportsMode()
                .onSave((updatedName, updatedProps) -> {
                    Map<String, Object> updated = element.getStructuredProperty("Parameters");
                    if (updated == null) {
                        updated = new LinkedHashMap<>();
                    }
                    updated.put(updatedName, updatedProps);
                    try {
                        UpdatePropertyUseCase.execute(model.getModel(), element, "Parameters", updated);
                        model.save();
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    updateEMTableData(element, paramsTableModel, "Parameters");
                })
                .show());


        JButton btnAddReport = new JButton("Add Report");
        btnAddReport.addActionListener(e -> new ParameterDialogBuilder("Add Report")
                .withEnumerations(enumerations(model))
                .withControlModules(controlModules(model))
                .reportsMode()
                .onSave((updatedName, updatedProps) -> {
                    Map<String, Object> updated = element.getStructuredProperty("Reports");
                    if (updated == null) {
                        updated = new LinkedHashMap<>();
                    }
                    updated.put(updatedName, updatedProps);
                    try {
                        UpdatePropertyUseCase.execute(model.getModel(), element, "Reports", updated);
                        model.save();
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    updateEMTableData(element, reportsTableModel, "Reports");
                })
                .show());


        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Parameters", new JScrollPane(paramsTable));
        tabbedPane.addTab("Reports", new JScrollPane(reportsTable));


        return new ConfigPanelBuilder(element)
                .withInfoPanel()
                .withCenterComponent(null, tabbedPane)
                .addBottomButton(btnAddParameter)
                .addBottomButton(btnAddReport)
                .build();
    }

    public static JPanel buildCMPanel(Plc4xPlantModel model, S88Element element){
        String[] columns = {"Name", "Type", "Value"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);
        JTable table = createStandardConfigTable(tableModel);

        updateCMTableData(element, tableModel, columns.length);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    String name = String.valueOf(table.getValueAt(row, 0));
                    Object currentValue = element.getProperties().get(name);

                    new ValueEditorDialogBuilder("Edit value", name, currentValue)
                            .onSave((updatedName, updatedProps) -> {
                                Object newValue = updatedProps.get("Value");
                                try {
                                    UpdatePropertyUseCase.execute(model.getModel(), element, updatedName, newValue);
                                    model.save();
                                    updateCMTableData(element, tableModel, columns.length);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }

                            })
                            .show();
                }
            }
        });

        return new ConfigPanelBuilder(element)
                .withInfoCMPanel()
                .withCenterComponent("Properties", new JScrollPane(table))
                .build();
    }


    private static DefaultTableModel createReadOnlyTableModel(String[] columns) {
        return new DefaultTableModel(null, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable createStandardConfigTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setFillsViewportHeight(true);
        return table;
    }



    private static void updateUnitTableData(S88Element element, DefaultTableModel tableModel) {
        tableModel.setRowCount(0);
        int columnCount = tableModel.getColumnCount();
        iterate(element, tableModel, columnCount, null );
    }

    private static void updateEMTableData(S88Element element, DefaultTableModel tableModel, String propertyName) {
        tableModel.setRowCount(0);
        int columnCount = tableModel.getColumnCount();

        Map<String, Object> params = element.getStructuredProperty(propertyName);
        if (params == null) params = Collections.emptyMap();

        iterate(element, tableModel, columnCount, params);
    }

    private static void updateCMTableData(S88Element element, DefaultTableModel tableModel, int columnCount) {
        tableModel.setRowCount(0);
        for(var entry : element.getProperties().entrySet()) {
            Object[] row = new Object[columnCount];

            Object name = entry.getKey();
            Object value = entry.getValue();
            Object type = value.getClass().getSimpleName();

            row[0] = name;
            row[1] = type;
            row[2] = value;

            tableModel.addRow(row);

        }
    }

    private static void iterate(S88Element element, DefaultTableModel tableModel, int columnCount, Map<String, Object> property) {
        for (var entry : element.getStructuredProperties(property).entrySet()) {
            String name = entry.getKey();
            Map<String, Object> propertyValues = entry.getValue();

            Object[] rowData = new Object[columnCount];
            if (columnCount > 0) {
                rowData[0] = name;
            }
            for (int i = 1; i < columnCount; i++) {
                String columnName = tableModel.getColumnName(i);

                Object value;
                if ("Reference".equals(columnName)) {
                    Object cm = propertyValues.get("ControlModule");
                    Object variable = propertyValues.get("Variable");
                    value = (cm != null && !String.valueOf(cm).isEmpty())
                            ? cm + "." + variable
                            : propertyValues.get("ItemName");
                } else {
                    value = propertyValues.get(columnName);
                }
                rowData[i] = value != null ? value : "";
            }
            tableModel.addRow(rowData);
        }
    }
}