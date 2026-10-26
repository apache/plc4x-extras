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
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.BootDbTagDynamicRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.netbeans.api.project.Project;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
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

    private static final String DEFAULT_DATA_TYPE = "int";
    private static final String DEFAULT_SCAN_TIME = "1000ms";
    private static final String HISTORY_TAB = "History";
    private final Set<String> deletedPvUuids = new LinkedHashSet<>();

    private static final int COL_NUMBER = 0;
    private static final int COL_NAME = 1;
    private static final int COL_DEVICE = 2;
    private static final int COL_TYPE = 3;
    private static final int COL_TAG = 4;
    private static final int COL_DESCRIPTOR = 5;
    private static final int COL_SCAN_TIME = 6;
    private static final int COL_SCAN_ENABLE = 7;
    private static final int COL_WRITE_ENABLE = 8;
    private static final int COL_DISPLAY_LIMIT_LOW = 9;
    private static final int COL_DISPLAY_LIMIT_HIGH = 10;
    private static final int COL_DISPLAY_DESCRIPTION = 11;
    private static final int COL_DISPLAY_FORMAT = 12;
    private static final int COL_DISPLAY_UNITS = 13;
    private static final int COL_CONTROL_LIMIT_LOW = 14;
    private static final int COL_CONTROL_LIMIT_HIGH = 15;
    private static final int COL_CONTROL_MIN_STEP = 16;
    private static final int COL_UUID = 17;

    private final ChangeListener modelListener = event -> refreshConnectionOptions();

    private JTable tableVariables;
    private DefaultTableModel tableModel;
    private JComboBox<String> cbConexion;
    private JTabbedPane tabs;
    private Project project;
    private HMICommunicationModel model;
    private boolean listening;

    private FileObject projectFolder;
    private FileChangeAdapter xmlListener;
    private NativeHistoryPanel historyPanel;
    private Timer reloadTimer;
    private final AtomicBoolean saving = new AtomicBoolean();
    private String lastKnownXml = "";

    public CreateVariableTopComponent() {
        initComponents();
        reloadTimer = new Timer(250, event -> applyExternalChange());
        reloadTimer.setRepeats(false);
        setName(Bundle.CTL_CreateVariableTopComponent());
        setToolTipText(Bundle.HINT_CreateVariableTopComponent());
    }

    public void initData(Project project) {
        detachModelListener();
        detachProjectListener();
        closeHistory();

        boolean projectChanged = this.project != project;
        if (projectChanged) {
            deletedPvUuids.clear();
        }

        this.project = project;
        this.model = project == null
                ? null
                : project.getLookup().lookup(HMICommunicationModel.class);
        this.projectFolder = project == null ? null : project.getProjectDirectory();

        if (project == null) {
            lastKnownXml = "";
            rebuildHistoryTab();
            return;
        }

        if (projectFolder != null) {
            attachProjectListener(projectFolder);
        }
        lastKnownXml = readXml(xmlFileObject());

        attachModelListener();
        refreshConnectionOptions();
        if (projectChanged) {
            loadVariablesFromXml(project);
        }
        rebuildHistoryTab();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        JPanel variablesPanel = new JPanel(new BorderLayout(10, 10));

        JLabel lblTitle = new JLabel("Variables", JLabel.LEFT);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel panelHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panelHeader.add(lblTitle);
        variablesPanel.add(panelHeader, BorderLayout.NORTH);

        String[] columnNames = {
            "Number",
            "Name", "Conexión", "Data type", "Tag", "Descriptor",
            "Scan Time", "ScanEnable", "WriteEnable", "DisplayLimitLow",
            "DisplayLimitHigh", "DisplayDescription", "DisplayFormat",
            "DisplayUnits", "ControlLimitLow", "ControlLimitHigh",
            "ControlMinStep", "Uuid"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != COL_UUID && column != COL_NUMBER;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == COL_NUMBER) {
                    return Integer.class;
                }
                if (columnIndex == COL_SCAN_ENABLE || columnIndex == COL_WRITE_ENABLE) {
                    return Boolean.class;
                }
                return String.class;
            }
        };

        tableVariables = new JTable(tableModel);
        tableVariables.setRowHeight(24);
        tableVariables.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tableVariables.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer centeredRenderer = new DefaultTableCellRenderer();
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tableVariables.setDefaultRenderer(String.class, centeredRenderer);
        tableVariables.setDefaultRenderer(Integer.class, centeredRenderer);
        tableVariables.setDefaultRenderer(Boolean.class, new CenteredBooleanRenderer());

        JTextField centeredTextField = new JTextField();
        centeredTextField.setHorizontalAlignment(SwingConstants.CENTER);
        tableVariables.setDefaultEditor(String.class, new DefaultCellEditor(centeredTextField));

        JCheckBox centeredCheckBox = new JCheckBox();
        centeredCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
        tableVariables.setDefaultEditor(Boolean.class, new DefaultCellEditor(centeredCheckBox));

        tableVariables.getColumnModel().getColumn(COL_NAME)
                .setCellEditor(new MaxLengthCellEditor(20));
        tableVariables.getColumnModel().getColumn(COL_TAG)
                .setCellEditor(new MaxLengthCellEditor(15));
        tableVariables.getColumnModel().getColumn(COL_DESCRIPTOR)
                .setCellEditor(new MaxLengthCellEditor(40));

        cbConexion = new JComboBox<>();
        centerComboBox(cbConexion);
        TableColumn colConexion = tableVariables.getColumnModel().getColumn(COL_DEVICE);
        colConexion.setCellEditor(new DefaultCellEditor(cbConexion));

        JComboBox<String> cbDataType = new JComboBox<>(new String[]{"boolean", "byte", "float", "int", "short"});
        centerComboBox(cbDataType);
        TableColumn colDataType = tableVariables.getColumnModel().getColumn(COL_TYPE);
        colDataType.setCellEditor(new DefaultCellEditor(cbDataType));

        JComboBox<String> cbScanTime = new JComboBox<>(new String[]{
            "100ms", "200ms", "300ms", "400ms", "500ms",
            "600ms", "700ms", "800ms", "900ms", "1000ms"
        });
        centerComboBox(cbScanTime);
        TableColumn colScanTime = tableVariables.getColumnModel().getColumn(COL_SCAN_TIME);
        colScanTime.setCellEditor(new DefaultCellEditor(cbScanTime));

        for (int column : new int[]{
            COL_DISPLAY_LIMIT_LOW, COL_DISPLAY_LIMIT_HIGH,
            COL_CONTROL_LIMIT_LOW, COL_CONTROL_LIMIT_HIGH, COL_CONTROL_MIN_STEP}) {
            tableVariables.getColumnModel().getColumn(column)
                    .setCellEditor(new SpinnerCellEditor(1, 65535));
        }

        TableColumn uuidColumn = tableVariables.getColumnModel().getColumn(COL_UUID);
        tableVariables.removeColumn(uuidColumn);

        configureColumnWidths();

        tableModel.addTableModelListener(e -> {
            if (e.getType() != TableModelEvent.UPDATE
                    || e.getFirstRow() < 0
                    || e.getColumn() < 0) {
                return;
            }
            int row = e.getFirstRow();
            int column = e.getColumn();
            onCellUpdated(row, column, tableModel.getValueAt(row, column));
        });

        refreshConnectionOptions();

        addRow("Var_1", "%DB1.DBD0");

        JScrollPane scrollPane = new JScrollPane(tableVariables);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        variablesPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton btnAddRow = new JButton("Add Row");
        JButton btnDeleteRow = new JButton("Delete Row");
        JButton btnGuardar = new JButton("Save");
        JButton btnCancelar = new JButton("Cancel");

        btnAddRow.addActionListener(e -> addVariableRow());
        btnDeleteRow.addActionListener(e -> deleteSelectedRow());
        btnGuardar.addActionListener(e -> saveVariables());
        btnCancelar.addActionListener(e -> close());

        panelButtons.add(btnAddRow);
        panelButtons.add(btnDeleteRow);
        panelButtons.add(btnGuardar);
        panelButtons.add(btnCancelar);
        variablesPanel.add(panelButtons, BorderLayout.SOUTH);

        tabs = new JTabbedPane();
        tabs.addTab("Variables", variablesPanel);
        tabs.addTab(HISTORY_TAB, historyPlaceholder());
        add(tabs, BorderLayout.CENTER);
    }

    private void configureColumnWidths() {
        int[] minimumWidths = {
            60, 120, 120, 90, 110, 200, 80, 80, 80,
            100, 110, 200, 90, 100, 100, 110, 100
        };
        for (int column = 0; column < minimumWidths.length; column++) {
            TableColumn tableColumn = tableVariables.getColumnModel().getColumn(column);
            tableColumn.setMinWidth(minimumWidths[column]);
            tableColumn.setPreferredWidth(minimumWidths[column]);
        }
    }

    private static void centerComboBox(JComboBox<String> comboBox) {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.CENTER);
        comboBox.setRenderer(renderer);
        if (comboBox.getEditor().getEditorComponent() instanceof JTextField textField) {
            textField.setHorizontalAlignment(SwingConstants.CENTER);
        }
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
        detachProjectListener();
        if (reloadTimer != null) {
            reloadTimer.stop();
        }
        closeHistory();
        deletedPvUuids.clear();
    }

    private void attachProjectListener(FileObject folder) {
        xmlListener = new FileChangeAdapter() {
            @Override
            public void fileChanged(FileEvent event) {
                scheduleReload(event.getFile());
            }

            @Override
            public void fileDataCreated(FileEvent event) {
                scheduleReload(event.getFile());
            }

            @Override
            public void fileDeleted(FileEvent event) {
                scheduleReload(event.getFile());
            }
        };
        folder.addFileChangeListener(xmlListener);
    }

    private void detachProjectListener() {
        if (projectFolder != null && xmlListener != null) {
            projectFolder.removeFileChangeListener(xmlListener);
        }
        xmlListener = null;
    }

    private void scheduleReload(FileObject file) {
        if (file == null
                || !DynamicVariableXmlMapper.FILE_NAME.equals(file.getNameExt())) {
            return;
        }
        if (reloadTimer != null) {
            reloadTimer.restart();
        }
    }

    private void applyExternalChange() {
        if (saving.get() || project == null || model == null || projectFolder == null) {
            return;
        }

        FileObject xmlFile = xmlFileObject();
        String xml = readXml(xmlFile);
        if (xml.equals(lastKnownXml)) {
            return;
        }
        lastKnownXml = xml;

        if (xml.isBlank()) {
            model.reload();
            loadVariablesFromXml(project);
            refreshConnectionOptions();
            rebuildHistoryTab();
            return;
        }

        try {
            var content = DynamicVariableXmlMapper.read(xml);
            model.reload();
            loadVariablesFromXml(project);
            refreshConnectionOptions();

            File folder = FileUtil.toFile(projectFolder);
            if (folder != null) {
                var rebuild = BootDbTagDynamicRepository.rebuildWithReason(
                        folder, content.devices(), content.pvs());
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

    private FileObject xmlFileObject() {
        return projectFolder == null
                ? null
                : projectFolder.getFileObject(DynamicVariableXmlMapper.FILE_NAME);
    }

    private static String readXml(FileObject file) {
        if (file == null || !file.isValid()) {
            return "";
        }
        try (InputStream in = file.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
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
        FileObject xmlFile = xmlFileObject();
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
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshConnectionOptions);
            return;
        }
        if (tableModel == null || cbConexion == null) {
            return;
        }

        List<String> names = model == null
                ? List.of()
                : model.getDevices().stream()
                        .map(DeviceConfigData::getDeviceName)
                        .filter(name -> name != null && !name.isBlank())
                        .toList();

        cbConexion.removeAllItems();
        for (String name : names) {
            cbConexion.addItem(name);
        }

        String fallback = names.isEmpty() ? "" : names.get(0);
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            Object current = tableModel.getValueAt(row, COL_DEVICE);
            if (current == null || !names.contains(current.toString())) {
                tableModel.setValueAt(fallback, row, COL_DEVICE);
            }
        }
    }

    private void loadVariablesFromXml(Project project) {
        File folder = FileUtil.toFile(project.getProjectDirectory());
        if (folder != null) {
            loadVariablesFromXml(folder);
        }
    }

    void loadVariablesFromXml(File folder) {
        tableModel.setRowCount(0);

        File xmlFile = new File(folder, DynamicVariableXmlMapper.FILE_NAME);
        if (!xmlFile.isFile()) {
            addVariableRow();
            return;
        }

        try {
            List<PvConfigData> pvs = DynamicVariableXmlMapper.read(folder).pvs();
            if (pvs.isEmpty()) {
                addVariableRow();
                return;
            }
            for (PvConfigData pv : pvs) {
                addPvRow(pv);
            }
            renumberRows();
        } catch (Exception ex) {
            addVariableRow();
            JOptionPane.showMessageDialog(this,
                    "The variables in comunicacion.xml could not be loaded: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    "Variables",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addPvRow(PvConfigData pv) {
        tableModel.addRow(new Object[]{
            tableModel.getRowCount() + 1,
            value(pv.getName()),
            value(pv.getDeviceName()),
            value(pv.getType()),
            value(pv.getItemTag()),
            value(pv.getDescriptorText()),
            value(pv.getScanTime()),
            pv.isScanEnable(),
            pv.isWriteEnable(),
            value(pv.getDisplayLimitLow()),
            value(pv.getDisplayLimitHigh()),
            value(pv.getDisplayDescription()),
            value(pv.getDisplayFormat()),
            value(pv.getDisplayUnits()),
            value(pv.getControlLimitLow()),
            value(pv.getControlLimitHigh()),
            value(pv.getControlMinStep()),});

        int row = tableModel.getRowCount() - 1;
        tableModel.setValueAt(value(pv.getUuid()), row, COL_UUID);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private String defaultConnectionName() {
        return cbConexion.getItemCount() > 0 ? cbConexion.getItemAt(0) : "";
    }

    private void addRow(String name, String tag) {
        tableModel.addRow(new Object[]{
            tableModel.getRowCount() + 1,
            name,
            defaultConnectionName(),
            DEFAULT_DATA_TYPE,
            tag,
            "",
            DEFAULT_SCAN_TIME,
            Boolean.TRUE,
            Boolean.FALSE,});

        int row = tableModel.getRowCount() - 1;
        tableModel.setValueAt(UUID.randomUUID().toString(), row, COL_UUID);
        renumberRows();
    }

    void addVariableRow() {
        addRow("", "");
    }

    JTable getTableVariables() {
        return tableVariables;
    }

    private void deleteSelectedRow() {
        if (tableVariables.isEditing()
                && !tableVariables.getCellEditor().stopCellEditing()) {
            return;
        }

        int viewRow = tableVariables.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Select a row to delete.",
                    "Delete Row",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tableVariables.convertRowIndexToModel(viewRow);
        String name = istext(modelRow, COL_NAME);
        String tag = istext(modelRow, COL_TAG);

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
        String uuid = istext(modelRow, COL_UUID);
        if (!uuid.isBlank()) {
            deletedPvUuids.add(uuid);
        }

        tableModel.removeRow(modelRow);
        renumberRows();
        tableVariables.setToolTipText(null);

        if (tableModel.getRowCount() > 0) {
            int nextModelRow = Math.min(modelRow, tableModel.getRowCount() - 1);
            int nextViewRow = tableVariables.convertRowIndexToView(nextModelRow);
            tableVariables.setRowSelectionInterval(nextViewRow, nextViewRow);
        }
    }

    private void renumberRows() {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            tableModel.setValueAt(row + 1, row, COL_NUMBER);
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

    private void onCellUpdated(int row, int col, Object newValue) {
        if (col == COL_TYPE || col == COL_TAG) {
            String error = CreateVariableController.validate(readRows(row));
            tableVariables.setToolTipText(
                    error == null ? null : "Row " + (row + 1) + ": " + error);
        }
    }

    private CreateVariableController.Row readRows(int row) {
        return new CreateVariableController.Row(
                row + 1,
                istext(row, COL_UUID),
                istext(row, COL_NAME),
                istext(row, COL_DEVICE),
                istext(row, COL_TYPE),
                istext(row, COL_TAG),
                istext(row, COL_DESCRIPTOR),
                istext(row, COL_SCAN_TIME),
                isboolean(row, COL_SCAN_ENABLE),
                isboolean(row, COL_WRITE_ENABLE),
                istext(row, COL_DISPLAY_LIMIT_LOW),
                istext(row, COL_DISPLAY_LIMIT_HIGH),
                istext(row, COL_DISPLAY_DESCRIPTION),
                istext(row, COL_DISPLAY_FORMAT),
                istext(row, COL_DISPLAY_UNITS),
                istext(row, COL_CONTROL_LIMIT_LOW),
                istext(row, COL_CONTROL_LIMIT_HIGH),
                istext(row, COL_CONTROL_MIN_STEP));
    }

    private List<CreateVariableController.Row> readRows() {
        List<CreateVariableController.Row> rows = new ArrayList<>();

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (istext(row, COL_NAME).isBlank() && istext(row, COL_TAG).isBlank()) {
                continue;
            }
            rows.add(readRows(row));
        }

        return rows;
    }

    private void saveVariables() {
        if (project == null || model == null || projectFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "There is no communication model.",
                    "Save variables",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        File carpeta = FileUtil.toFile(projectFolder);
        if (carpeta == null) {
            JOptionPane.showMessageDialog(this,
                    "The project folder could not be retrieved.",
                    "Save variables",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        saving.set(true);
        try {
            String previousXml = lastKnownXml;
            CreateVariableController.Result result
                    = CreateVariableController.saveVariables(carpeta.getAbsolutePath(),
                            model.getDevices(),
                            readRows(),
                            deletedPvUuids);

            FileObject xmlFile = xmlFileObject();
            if (xmlFile == null) {
                projectFolder.refresh();
                xmlFile = xmlFileObject();
            }
            String currentXml = previousXml;
            if (xmlFile != null) {
                xmlFile.refresh();
                currentXml = readXml(xmlFile);
            }
            lastKnownXml = currentXml;
            if (result.success() || !currentXml.equals(previousXml)) {
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
        } finally {
            saving.set(false);
        }
    }

    private String istext(int row, int col) {
        Object value = tableModel.getValueAt(row, col);
        return value == null ? "" : value.toString().trim();
    }

    private boolean isboolean(int row, int col) {
        Object value = tableModel.getValueAt(row, col);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

    private static final class MaxLengthCellEditor extends DefaultCellEditor {

        private MaxLengthCellEditor(int maxLength) {
            super(new JTextField());
            JTextField textField = (JTextField) getComponent();
            textField.setHorizontalAlignment(SwingConstants.CENTER);
            ((AbstractDocument) textField.getDocument())
                    .setDocumentFilter(maxLengthFilter(maxLength));
        }
    }

    private static DocumentFilter maxLengthFilter(int maxLength) {
        return new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text,
                    AttributeSet attrs) throws BadLocationException {
                String replacement = text == null ? "" : text;
                int available = maxLength - (fb.getDocument().getLength() - length);
                if (available <= 0) {
                    return;
                }
                if (replacement.length() > available) {
                    replacement = replacement.substring(0, available);
                }
                super.replace(fb, offset, length, replacement, attrs);
            }
        };
    }

    private static final class CenteredBooleanRenderer extends JCheckBox implements TableCellRenderer {

        private CenteredBooleanRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(Boolean.TRUE.equals(value));
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    private static final class SpinnerCellEditor extends AbstractCellEditor implements TableCellEditor {

        private final JSpinner spinner;

        private SpinnerCellEditor(int min, int max) {
            spinner = new JSpinner(new SpinnerNumberModel(min, min, max, 1));
            JSpinner.NumberEditor spinnerEditor = new JSpinner.NumberEditor(spinner, "0");
            spinnerEditor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
            spinner.setEditor(spinnerEditor);
        }

        @Override
        public Object getCellEditorValue() {
            return ((Number) spinner.getValue()).intValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            if (value instanceof Number number) {
                spinner.setValue(number.intValue());
            } else {
                spinner.setValue(1);
            }
            return spinner;
        }

        @Override
        public boolean stopCellEditing() {
            try {
                spinner.commitEdit();
            } catch (ParseException ex) {
                return false;
            }
            return super.stopCellEditing();
        }
    }
}
