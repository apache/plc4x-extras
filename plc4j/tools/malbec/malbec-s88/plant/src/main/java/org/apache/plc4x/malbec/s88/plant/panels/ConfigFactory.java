/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.core.AddStructEntryUseCase;
import org.apache.plc4x.malbec.s88.core.UpdateStructEntryUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
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
            default -> new JPanel();
        };
    }

    private static JPanel buildUnitPanel(Plc4xPlantModel model, S88Element element) {
        String[] columns = {"Name", "Type", "Eng_Units/Enum", "Reference", "StaticValue"};
        DefaultTableModel tableModel = createReadOnlyTableModel(columns);
        JTable table = createStandardConfigTable(tableModel);

        updateUnitTableData(element, tableModel);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2){
                    int row = table.rowAtPoint(e.getPoint());
                    if (row < 0) {
                        return;
                    }
                    String name = String.valueOf(table.getValueAt(row, 0));
                    Map<String, Object> prop = element.getStructuredProperty(name);

                    new AttributeDialogBuilder("Edit Attribute")
                            .withEnumerations(enumerations(model))
                            .withEditable(false)
                            .withInitialData(name, prop)
                            .onSave((updatedName, updatedProps) -> {
                                try {
                                    S88Element current = currentElement(model, element);
                                    UpdateStructEntryUseCase.execute(model.getModel(), current, null, updatedName, updatedProps);
                                    model.save();
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    showError(ex);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            })
                            .onUpdate(() -> updateUnitTableData(currentElement(model, element), tableModel))
                            .show();
                }
            }
        });

        JButton btnAdd = new JButton("Add unit attribute");
        btnAdd.addActionListener(e -> new AttributeDialogBuilder("Create Unit Attribute")
                .withEnumerations(enumerations(model))
                .withEditable(true)
                .onSave((updatedName, updatedProps) -> {
                    try {
                        S88Element current = currentElement(model, element);
                        AddStructEntryUseCase.execute(model.getModel(), current, null, updatedName, updatedProps);
                        model.save();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        showError(ex);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                })
                .onUpdate(() -> updateUnitTableData(currentElement(model, element), tableModel))
                .show());

        return new ConfigPanelBuilder(element)
                .withInfoPanel()
                .withCenterComponent("Unit attributes", new JScrollPane(table))
                .addBottomButton(btnAdd)
                .build();
    }

    private static List<S88Enumeration> enumerations(Plc4xPlantModel model) {
        if (model != null && model.getModel() != null) {
            return model.getModel().getEnumerations();
        }
        return Collections.emptyList();
    }


    private static S88Element currentElement(Plc4xPlantModel model, S88Element element) {
        if (model != null && element != null && element.getId() != null) {
            S88Element current = model.getElementByID(element.getId());
            if (current != null) {
                return current;
            }
        }
        return element;
    }

    private static void showError(Exception ex) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
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
                    if (row < 0) {
                        return;
                    }
                    String name = String.valueOf(paramsTable.getValueAt(row, 0));
                    Map<String, Object> params = element.getStructuredProperty("Parameters");
                    Map<String, Object> bag = params != null ? (Map<String, Object>) params.get(name) : null;

                    new ParameterDialogBuilder("Edit Parameter")
                            .withEnumerations(enumerations(model))
                            .withInitialData(name, bag)
                            .withEditableFields(false)
                            .onSave((updatedName, updatedProps) -> {
                                S88Element current = currentElement(model, element);
                                try {
                                    UpdateStructEntryUseCase.execute(model.getModel(), current, "Parameters", updatedName, updatedProps);
                                    model.save();
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    showError(ex);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                                updateEMTableData(currentElement(model, element), paramsTableModel, "Parameters");
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
                    if (row < 0) {
                        return;
                    }
                    String name = String.valueOf(reportsTable.getValueAt(row, 0));
                    Map<String, Object> reports = element.getStructuredProperty("Reports");
                    Map<String, Object> bag = reports != null ? (Map<String, Object>) reports.get(name) : null;

                    new ParameterDialogBuilder("Edit Report")
                            .withEnumerations(enumerations(model))
                            .reportsMode()
                            .withInitialData(name, bag)
                            .withEditableFields(false)
                            .onSave((updatedName, updatedProps) -> {
                                S88Element current = currentElement(model, element);
                                try {
                                    UpdateStructEntryUseCase.execute(model.getModel(), current, "Reports", updatedName, updatedProps);
                                    model.save();
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    showError(ex);
                                } catch (Exception ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                                updateEMTableData(currentElement(model, element), reportsTableModel, "Reports");
                            })
                            .show();
                }
            }
        });

        JButton btnAddParameter = new JButton("Add parameter");
        btnAddParameter.addActionListener(e -> new ParameterDialogBuilder("Add Parameter")
                .withEnumerations(enumerations(model))
                .onSave((updatedName, updatedProps) -> {
                    S88Element current = currentElement(model, element);
                    try {
                        AddStructEntryUseCase.execute(model.getModel(), current, "Parameters", updatedName, updatedProps);
                        model.save();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        showError(ex);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    updateEMTableData(currentElement(model, element), paramsTableModel, "Parameters");
                })
                .show());


        JButton btnAddReport = new JButton("Add Report");
        btnAddReport.addActionListener(e -> new ParameterDialogBuilder("Add Report")
                .withEnumerations(enumerations(model))
                .reportsMode()
                .onSave((updatedName, updatedProps) -> {
                    S88Element current = currentElement(model, element);
                    try {
                        AddStructEntryUseCase.execute(model.getModel(), current, "Reports", updatedName, updatedProps);
                        model.save();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        showError(ex);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    updateEMTableData(currentElement(model, element), reportsTableModel, "Reports");
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
