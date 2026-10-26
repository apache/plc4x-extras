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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationStore;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;

public final class CreateDeviceDialog extends JDialog {

    private final transient Path projectDir;
    private final transient CommunicationStore store;
    private final transient CreateDevicePanel panel;
    private final boolean editando;
    private final JButton btnOk = new JButton("Guardar");
    private final JButton btnCancel = new JButton("Cancelar");

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
                ? "Nuevo dispositivo de comunicación"
                : "Modificar dispositivo", ModalityType.APPLICATION_MODAL);
        this.projectDir = directoryOf(project);
        this.store = storeOf(project);
        this.panel = new CreateDevicePanel(projectDir, device);
        this.editando = device != null;

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botones.add(btnCancel);
        botones.add(btnOk);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(panel, BorderLayout.CENTER); 
        content.add(botones, BorderLayout.SOUTH);
        setContentPane(content);
        content.add(botones, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(btnOk);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        btnOk.addActionListener(e -> save());
        btnCancel.addActionListener(e -> dispose());

        setResizable(false);
        setSize(550, 620);
//        pack();
//        Rectangle pantalla = getGraphicsConfiguration() != null
//                ? getGraphicsConfiguration().getBounds()
//                : new Rectangle(0, 0, 400, 700);
//        Dimension pref = getPreferredSize();
//        setSize(Math.min(pref.width, pantalla.width - 80),
//                Math.min(pref.height, pantalla.height - 80));
        setLocationRelativeTo(owner);
    }

    private void save() {
        DeviceConfigData data = panel.getDeviceConfigData();
        if (data == null) {
            return;
        }
        if (store == null) {
            JOptionPane.showMessageDialog(this,
                    "No se encontró el modelo de comunicación del proyecto.",
                    "Error", JOptionPane.ERROR_MESSAGE);
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

    private void show(DeviceConfigData data, SaveResult resultado) {
        setCursor(Cursor.getDefaultCursor());
        btnOk.setEnabled(true);
        btnCancel.setEnabled(true);

        if (!resultado.xmlSaved()) {
            // Antes este mismo aviso decia "se guardó en comunicacion.xml" incluso
            // cuando no se habia escrito nada, porque el booleano no lo distinguia.
            JOptionPane.showMessageDialog(this,
                    "No se pudo guardar el dispositivo:\n\n" + resultado.message(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (resultado.isPartial()) {
            JOptionPane.showMessageDialog(this,
                    "El dispositivo se guardó en comunicacion.xml pero no se pudo\n"
                    + "actualizar la base de datos. Revisá la consola.",
                    "Guardado parcial", JOptionPane.WARNING_MESSAGE);
            return;
        }
        saveWarning(data);
        dispose();
    }

    private void saveWarning(DeviceConfigData data) {
        String name = data.getDeviceName();
        if (name == null || name.isBlank()) {
            name = "(sin nombre)";
        }
        JOptionPane.showMessageDialog(this,
                "El dispositivo '" + name + "' fue "
                + (editando ? "modificado" : "creado") + " correctamente.",
                editando ? "Modificación exitosa" : "Dispositivo creado",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
