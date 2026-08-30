package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.commons.compress.utils.OsgiUtils;
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
import java.util.List;
import java.util.Map;

public class ConfigFactory {

    public static JPanel createConfigPanel(Plc4xPlantModel model, S88Element element) {
        return switch(element.getLevel()){
            case PROCESSCELL -> new JPanel();
            case UNIT -> buildUnitPanel(model, element);
            case EQUIPMENTMODULE -> buildEMPanel(element);
            case CONTROLMODULE -> new JPanel();
            default -> new JPanel();
        };
    }

    private static JPanel buildUnitPanel(Plc4xPlantModel model, S88Element element) {
        String[] columns = {"Name", "Type", "Eng_Units/Enum", "ItemName"};
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
        btnAdd.addActionListener(e -> {
            new AttributeDialogBuilder("Create Unit Attribute")
                    .withEnumerations(enumerationNames(model))
                    .onSave((name, props) -> {
                        try {
                            UpdatePropertyUseCase.execute(model.getModel(), element, name, props);
                            model.save();
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    })
                    .onUpdate(() -> updateUnitTableData(element, tableModel))
                    .show();
        });

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

    private static JPanel buildEMPanel(S88Element element) {
        String[] paramColumns = {"Name", "Eng_Units/Enum", "Type", "Max", "Min", "Default"};
        String[] reportColumns = {"Name", "Eng_Units/Enum", "Type"};

        DefaultTableModel paramsTableModel = createReadOnlyTableModel(paramColumns);
        DefaultTableModel reportsTableModel = createReadOnlyTableModel(reportColumns);

        JTable paramsTable = createStandardConfigTable(paramsTableModel);
        JTable reportsTable = createStandardConfigTable(reportsTableModel);

        updateEMTableData(element, paramsTableModel, "Parameters");
        updateEMTableData(element, reportsTableModel, "Reports");

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Parameters", new JScrollPane(paramsTable));
        tabbedPane.addTab("Reports", new JScrollPane(reportsTable));


        return new ConfigPanelBuilder(element)
                .withInfoPanel()
                .withCenterComponent(null, tabbedPane)
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

                Object value = propertyValues.get(columnName);
                rowData[i] = value != null ? value : "";
            }
            tableModel.addRow(rowData);
        }
    }
}