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
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationStore;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.UrlDisassembler;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;

public final class CreateDeviceDialog extends JDialog {

    private final transient Path projectDir;
    private final transient CommunicationStore store;
    private final transient CreateDevicePanel panel;
    private final boolean editing;
    private final JButton btnOk = new JButton("Save");
    private final JButton btnCancel = new JButton("Cancel");
    private final JButton btntestConexion = new JButton("Test Conexion");

    public static CreateDeviceDialog forCreate(Window owner, Project project) {
        return new CreateDeviceDialog(owner, project, null);
    }

    public static CreateDeviceDialog forEdit(Window owner, Project project, DeviceConfigData device) {
        return new CreateDeviceDialog(owner, project, device);
    }

    private static Path directoryOf(Project project) {
        return project != null ? FileUtil.toFile(project.getProjectDirectory()).toPath() : null;
    }

    private static CommunicationStore storeOf(Project project) {
        return project != null ? project.getLookup().lookup(HMICommunicationModel.class) : null;
    }

    private CreateDeviceDialog(Window owner, Project project, DeviceConfigData device) {
        super(owner, device == null
                ? "New device"
                : "Edit Device", ModalityType.APPLICATION_MODAL);
        this.projectDir = directoryOf(project);
        this.store = storeOf(project);
        this.panel = new CreateDevicePanel(projectDir, device);
        this.editing = device != null;

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(btntestConexion);
        buttons.add(btnCancel);
        buttons.add(btnOk);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(btnOk);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        btntestConexion.addActionListener(e -> test());
        btnOk.addActionListener(e -> save());
        btnCancel.addActionListener(e -> dispose());

        setResizable(false);
        setSize(550, 620);
        setLocationRelativeTo(owner);
    }

    private void save() {
        DeviceConfigData data = panel.getDeviceConfigData();
        if (data == null) {
            return;
        }
        if (store == null) {
            JOptionPane.showMessageDialog(this,
                    "The project's communication model was not found.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String conflict = duplicateDeviceMessage(data, store.getDevices());
        if (conflict != null) {
            JOptionPane.showMessageDialog(this, conflict,
                    "Duplicate device", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnOk.setEnabled(false);
        btnCancel.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        RequestProcessor.getDefault().post(() -> {
            store.upsertDevice(data);
            SaveResult resultado = store.save();
            SwingUtilities.invokeLater(() -> show(data, resultado));
        });
    }

    static String duplicateDeviceMessage(DeviceConfigData candidate,
            List<DeviceConfigData> existing) {
        Set<String> conflicts = new LinkedHashSet<>();
        String candidateName = normalized(candidate.getDeviceName());
        Set<String> candidateHosts = hostsOf(candidate);
        for (DeviceConfigData device : existing) {
            if (device == null || sameUuid(candidate.getUuid(), device.getUuid())) {
                continue;
            }
            if (!candidateName.isEmpty()
                    && candidateName.equalsIgnoreCase(normalized(device.getDeviceName()))) {
                conflicts.add("The name '" + candidateName
                        + "' is already used by another device.");
            }
            for (String host : hostsOf(device)) {
                if (candidateHosts.contains(host)) {
                    conflicts.add("The IP '" + host + "' is already used by device '"
                            + displayName(device) + "'.");
                }
            }
        }
        return conflicts.isEmpty() ? null : String.join("\n", conflicts);
    }

    private static Set<String> hostsOf(DeviceConfigData device) {
        UrlDisassembler.Partes partes =
                UrlDisassembler.disassemble(device.getSpecificParameters());
        if (partes == null || partes.host() == null || partes.host().isBlank()) {
            return Set.of();
        }
        String rawHost = partes.host().trim();
        if (rawHost.startsWith("/")) {
            Set<String> hosts = new LinkedHashSet<>();
            addHost(hosts, rawHost);
            return hosts;
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (String host : rawHost.split("/")) {
            addHost(hosts, stripPort(host.trim(), partes.port()));
        }
        return hosts;
    }

    private static String stripPort(String host, String port) {
        if (port == null || port.isBlank()) {
            return host;
        }
        String suffix = ":" + port;
        return host.endsWith(suffix)
                ? host.substring(0, host.length() - suffix.length())
                : host;
    }

    private static void addHost(Set<String> hosts, String host) {
        String value = normalized(host);
        if (!value.isEmpty()) {
            hosts.add(value.toLowerCase(Locale.ROOT));
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameUuid(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }

    private static String displayName(DeviceConfigData device) {
        String name = normalized(device.getDeviceName());
        return name.isEmpty() ? "(unnamed)" : name;
    }

    private void show(DeviceConfigData data, SaveResult resultado) {
        setCursor(Cursor.getDefaultCursor());
        btnOk.setEnabled(true);
        btnCancel.setEnabled(true);

        if (!resultado.xmlSaved()) {
            JOptionPane.showMessageDialog(this,
                    "The device could not be saved:\n\n" + resultado.message(),
                    "Error saving", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (resultado.isPartial()) {
            JOptionPane.showMessageDialog(this,
                    "The device was saved in communicacion.xml, but the\n"
                    + "database could not be updated. Check the console.",
                    "Partial Save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        saveWarning(data);
        dispose();
    }

    private void saveWarning(DeviceConfigData data) {
        String name = data.getDeviceName();
        if (name == null || name.isBlank()) {
            name = "(unnamed)";
        }
        JOptionPane.showMessageDialog(this,
                "The device '" + name + "' was "
                + (editing ? "modified" : "created") + " correctly.",
                editing ? "Successful modification" : "Device created",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void test() {
        String url = panel.urlActual();
        if (url == null || url.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a connection URL before testing.",
                    "Missing URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog waitDialog = createWaitDialog();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        btnOk.setEnabled(false);
        btnCancel.setEnabled(false);
        btntestConexion.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        RequestProcessor.getDefault().post(() -> {
            try (PlcConnection connection = PlcDriverManager.getDefault()
                    .getConnectionFactory()
                    .getConnection(url)) {
            } catch (Exception | LinkageError | ServiceConfigurationError e) {
                failure.set(e);
            } finally {
                SwingUtilities.invokeLater(waitDialog::dispose);
            }
        });

        waitDialog.setVisible(true);

        setCursor(Cursor.getDefaultCursor());
        btnOk.setEnabled(true);
        btnCancel.setEnabled(true);
        btntestConexion.setEnabled(true);

        Throwable error = failure.get();
        if (error == null) {
            JOptionPane.showMessageDialog(this,
                    "Successful connection",
                    "Successful", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Unable to establish a connection.\n\n" + messageOf(error),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JDialog createWaitDialog() {
        JDialog dialog = new JDialog(this, "Testing connection", ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JLabel message = new JLabel("Testing connection, please wait...");
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        content.add(message, BorderLayout.NORTH);
        content.add(progress, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

}
