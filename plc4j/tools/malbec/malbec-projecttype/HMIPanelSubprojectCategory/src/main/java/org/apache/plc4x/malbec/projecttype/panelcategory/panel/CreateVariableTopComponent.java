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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.netbeans.api.project.Project;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

@ConvertAsProperties(
        dtd = "-//org.apache.plc4x.malbec.projecttype.panelcategory.panel//VariableCreation//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "CreateVariableTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@Messages({
    "CTL_CreateVariableTopComponent=Variables",
    "HINT_CreateVariableTopComponent=Window for editing and creating variables"
})
public final class CreateVariableTopComponent extends TopComponent {

    private static final String HISTORY_TAB = "History";
    private final Set<String> deletedPvUuids = new LinkedHashSet<>();

    private final ChangeListener modelListener = event -> refreshConnectionOptions();

    private VariableTablePanel tablePanel;
    private JSpinner spnDuplicateCount;
    private JTabbedPane tabs;
    private Project project;
    private HMICommunicationModel model;
    private boolean listening;

    private final VariableDocumentService documentService = new VariableDocumentService();
    private NativeHistoryPanel historyPanel;

    public CreateVariableTopComponent() {
        initComponents();
        setName(Bundle.CTL_CreateVariableTopComponent());
        setToolTipText(Bundle.HINT_CreateVariableTopComponent());
    }

    public void initData(Project project) {
        detachModelListener();
        documentService.close();
        closeHistory();

        boolean projectChanged = this.project != project;
        if (projectChanged) {
            deletedPvUuids.clear();
        }

        this.project = project;
        this.model = project == null
                ? null
                : project.getLookup().lookup(HMICommunicationModel.class);
        documentService.open(
                project == null ? null : project.getProjectDirectory(),
                this::applyExternalChange);

        if (project == null) {
            rebuildHistoryTab();
            return;
        }

        attachModelListener();
        refreshConnectionOptions();
        if (projectChanged) {
            loadVariablesFromXml(project);
        }
        rebuildHistoryTab();
    }

    public void filterByDevice(String deviceUuid) {
        DeviceConfigData device = model == null ? null : model.findByUuid(deviceUuid);
        String deviceName = device == null ? null : device.getDeviceName();
        if (deviceName == null || deviceName.isBlank()) {
            clearFilter();
            return;
        }

        tablePanel.filterByDeviceName(deviceName);
    }

    public void clearFilter() {
        tablePanel.clearFilter();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        JPanel variablesPanel = new JPanel(new BorderLayout(10, 10));

        JLabel lblTitle = new JLabel("Variables", JLabel.LEFT);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel panelHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panelHeader.add(lblTitle);

        variablesPanel.add(panelHeader, BorderLayout.NORTH);

        tablePanel = new VariableTablePanel();
        variablesPanel.add(tablePanel, BorderLayout.CENTER);

        refreshConnectionOptions();
        tablePanel.addRow("Var_1", "%DB1.DBD0");

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton btnAddRow = new JButton("Add Row");
        JButton btnDeleteRow = new JButton("Delete Row");
        JButton btnDuplicate = new JButton("Duplicate");
        JButton btnGuardar = new JButton("Save");
        JButton btnCancelar = new JButton("Cancel");

        spnDuplicateCount = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        spnDuplicateCount.setPreferredSize(new Dimension(60, 24));
        JSpinner.NumberEditor duplicateEditor = new JSpinner.NumberEditor(spnDuplicateCount, "0");
        duplicateEditor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        spnDuplicateCount.setEditor(duplicateEditor);

        btnAddRow.addActionListener(e -> addVariableRow());
        btnDeleteRow.addActionListener(e -> deleteSelectedRow());
        btnDuplicate.addActionListener(e -> duplicateSelectedRow());
        btnGuardar.addActionListener(e -> saveVariables());
        btnCancelar.addActionListener(e -> close());

        panelButtons.add(btnAddRow);
        panelButtons.add(btnDeleteRow);
        panelButtons.add(new JLabel("Copies:"));
        panelButtons.add(spnDuplicateCount);
        panelButtons.add(btnDuplicate);
        panelButtons.add(btnGuardar);
        panelButtons.add(btnCancelar);
        variablesPanel.add(panelButtons, BorderLayout.SOUTH);

        tabs = new JTabbedPane();
        tabs.addTab("Variables", variablesPanel);
        tabs.addTab(HISTORY_TAB, historyPlaceholder());
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    public void componentOpened() {
        if (project == null) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Select a project before opening Variable Creation.",
                        "Create Variables",
                        JOptionPane.WARNING_MESSAGE);
                close();
            });
            return;
        }
        attachModelListener();
        refreshConnectionOptions();
        rebuildHistoryTab();
    }

    @Override
    public void componentClosed() {
        detachModelListener();
        documentService.close();
        closeHistory();
        deletedPvUuids.clear();
    }

    private void applyExternalChange() {
        if (project == null || model == null) {
            return;
        }

        VariableDocumentService.ExternalChange change = documentService.readExternalChange();
        if (change == null) {
            return;
        }

        if (change.blank()) {
            model.reload();
            loadVariablesFromXml(project);
            refreshConnectionOptions();
            rebuildHistoryTab();
            return;
        }

        try {
            var content = documentService.readContent(change.xml());
            model.reload();
            loadVariablesFromXml(project);
            refreshConnectionOptions();

            File folder = documentService.folder();
            if (folder != null) {
                var rebuild = documentService.rebuildBootDb(folder, content);
                if (!rebuild.success()) {
                    JOptionPane.showMessageDialog(this,
                            "The variables could not be saved to boot.db: "
                            + rebuild.reason(),
                            "Variables",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
            rebuildHistoryTab();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "The variables in comunicacion.xml could not be loaded: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    "Variables",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void rebuildHistoryTab() {
        if (tabs == null) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::rebuildHistoryTab);
            return;
        }

        closeHistory();
        Component historyPanel = createHistoryPanel();
        int index = tabs.indexOfTab(HISTORY_TAB);
        if (index >= 0) {
            tabs.setComponentAt(index, historyPanel);
        } else {
            tabs.addTab(HISTORY_TAB, historyPanel);
        }
    }

    private Component createHistoryPanel() {
        FileObject xmlFile = documentService.xmlFileObject();
        if (xmlFile == null) {
            return historyPlaceholder();
        }

        historyPanel = NativeHistoryPanel.create(xmlFile);
        if (historyPanel == null) {
            return historyUnavailablePlaceholder();
        }
        return historyPanel;
    }

    private void closeHistory() {
        if (historyPanel != null) {
            historyPanel.close();
            historyPanel = null;
        }
    }

    private static Component historyPlaceholder() {
        JLabel label = new JLabel(
                "Save the variables to create comunicacion.xml and enable local history.",
                SwingConstants.CENTER);
        label.setEnabled(false);
        return label;
    }

    private static Component historyUnavailablePlaceholder() {
        JLabel label = new JLabel(
                "Local History is not available in this application.",
                SwingConstants.CENTER);
        label.setEnabled(false);
        return label;
    }

    private void refreshConnectionOptions() {
        tablePanel.refreshDeviceOptions(this::deviceNames);
    }

    private List<String> deviceNames() {
        return model == null
                ? List.of()
                : model.getDevices().stream()
                        .map(DeviceConfigData::getDeviceName)
                        .filter(name -> name != null && !name.isBlank())
                        .toList();
    }

    private void loadVariablesFromXml(Project project) {
        File folder = FileUtil.toFile(project.getProjectDirectory());
        if (folder != null) {
            loadVariablesFromXml(folder);
        }
    }

    void loadVariablesFromXml(File folder) {
        tablePanel.setRows(List.of());

        File xmlFile = new File(folder, DynamicVariableXmlMapper.FILE_NAME);
        if (!xmlFile.isFile()) {
            addVariableRow();
            return;
        }

        try {
            List<CreateVariableController.Row> rows = CreateVariableController.loadRows(folder);
            if (rows.isEmpty()) {
                addVariableRow();
                return;
            }
            tablePanel.setRows(rows);
        } catch (Exception ex) {
            addVariableRow();
            JOptionPane.showMessageDialog(this,
                    "The variables in comunicacion.xml could not be loaded: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    "Variables",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    void addVariableRow() {
        tablePanel.addRow("", "");
    }

    JTable getTableVariables() {
        return tablePanel.table();
    }

    JSpinner getDuplicateCountSpinner() {
        return spnDuplicateCount;
    }

    private void duplicateSelectedRow() {
        if (!tablePanel.stopEditing()) {
            return;
        }

        int modelRow = tablePanel.selectedModelRow();
        if (modelRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Select a row to duplicate.",
                    "Duplicate Row",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int copies = ((Number) spnDuplicateCount.getValue()).intValue();
        try {
            duplicateRow(modelRow, copies);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Duplicate Row",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    void duplicateRow(int modelRow, int copies) {
        tablePanel.duplicateRow(modelRow, copies);
    }

    private void deleteSelectedRow() {
        if (!tablePanel.stopEditing()) {
            return;
        }

        int modelRow = tablePanel.selectedModelRow();
        if (modelRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Select a row to delete.",
                    "Delete Row",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        CreateVariableController.Row selected = tablePanel.row(modelRow);
        String name = selected.name();
        String tag = selected.itemTag();

        Object[] options = {"Accept", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Are you sure you want to delete this row?\n\n"
                + "Row: " + (modelRow + 1) + "\n"
                + "Name: " + name + "\n"
                + "Tag: " + tag,
                "Delete Row",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);

        if (choice != 0) {
            return;
        }

        removeRow(modelRow);
    }

    void removeRow(int modelRow) {
        String uuid = tablePanel.removeRow(modelRow).uuid();
        if (!uuid.isBlank()) {
            deletedPvUuids.add(uuid);
        }
    }

    private void attachModelListener() {
        if (model != null && !listening) {
            model.addChangeListener(modelListener);
            listening = true;
        }
    }

    private void detachModelListener() {
        if (model != null && listening) {
            model.removeChangeListener(modelListener);
            listening = false;
        }
    }

    private void saveVariables() {
        if (project == null || model == null) {
            JOptionPane.showMessageDialog(this,
                    "There is no communication model.",
                    "Save variables",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        File carpeta = documentService.folder();
        if (carpeta == null) {
            JOptionPane.showMessageDialog(this,
                    "The project folder could not be retrieved.",
                    "Save variables",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<CreateVariableController.Row> rowsToSave = tablePanel.rows().stream()
                .filter(row -> !(isBlank(row.name()) && isBlank(row.itemTag())))
                .toList();

        VariableDocumentService.SaveOutcome outcome = documentService.save(
                carpeta, model.getDevices(), rowsToSave, deletedPvUuids);
        CreateVariableController.Result result = outcome.result();

        if (result.success() || outcome.xmlChanged()) {
            deletedPvUuids.clear();
        }
        rebuildHistoryTab();

        if (!result.success()) {
            JOptionPane.showMessageDialog(this,
                    String.join("\n", result.errors()),
                    "Errors in the variables",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Variables saved successfully.",
                "Save variables",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

}
