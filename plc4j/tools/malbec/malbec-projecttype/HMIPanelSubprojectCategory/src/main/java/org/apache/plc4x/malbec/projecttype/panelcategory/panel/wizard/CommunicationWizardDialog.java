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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardController;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommunicationWizardState;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;

public class CommunicationWizardDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String STEP_DEVICE = "device";
    private static final String STEP_AREA = "area";
    private static final String STEP_PV = "pv";
    
    private final HMICommunicationModel model;
    private final CommunicationWizardState state = new CommunicationWizardState();
    private final CommunicationWizardController controller;
    private final StepDevicePanel devicePanel;
    private final StepGroupAreaPanel areaPanel;
    private final StepPvPanel pvPanel;

    private final JButton btnBack = new JButton("< Atrás");
    private final JButton btnNext = new JButton("Siguiente >");
    private final JButton btnSave = new JButton("Guardar");
    private final JButton btnSaveAndClose = new JButton("Guardar y cerrar");
    private final JButton btnCancel = new JButton("Cancelar");

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private String currentStep = STEP_DEVICE;

    public CommunicationWizardDialog(Window owner, Project project, String deviceUuid) {
        super(owner, "Nueva Comunicación", ModalityType.APPLICATION_MODAL);
        this.model = project != null ? project.getLookup().lookup(HMICommunicationModel.class) : null;
        this.controller = new CommunicationWizardController(model, state);
        this.devicePanel = new StepDevicePanel(controller);
        this.areaPanel = new StepGroupAreaPanel(controller);
        this.pvPanel = new StepPvPanel(controller, new PlantModelReader(
                project != null ? FileUtil.toFile(project.getProjectDirectory()).toPath() : null));

        buildUi();
        wireSteps();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                intentarCerrar();
            }
        });
        getRootPane().registerKeyboardAction(e -> intentarCerrar(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        loadDevices(deviceUuid);
        showStep(STEP_DEVICE);

        pack();
        Dimension pref = getPreferredSize();
        Rectangle screen = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getBounds()
                : new Rectangle(0, 0, 1024, 768);
        int maxW = Math.min(1000, Math.max(760, screen.width - 120));
        int maxH = Math.min(740, Math.max(600, screen.height - 140));
        setSize(Math.min(pref.width, maxW), Math.min(pref.height, maxH));
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        cardPanel.add(devicePanel, STEP_DEVICE);
        cardPanel.add(areaPanel, STEP_AREA);
        cardPanel.add(pvPanel, STEP_PV);

        btnBack.addActionListener(e -> {
            if (STEP_PV.equals(currentStep)) {
                showStep(STEP_AREA);
            } else {
                showStep(STEP_DEVICE);
            }
        });
        btnNext.addActionListener(e -> {
            if (STEP_AREA.equals(currentStep)) {
                showStep(STEP_PV);
                return;
            }
            if (state.getSelectedDevice() == null) {
                JOptionPane.showMessageDialog(this,
                        "Seleccione un dispositivo para continuar.",
                        "Sin dispositivo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            showStep(STEP_AREA);
        });
        btnSave.addActionListener(e -> guardar(false));
        btnSaveAndClose.addActionListener(e -> guardar(true));
        btnCancel.addActionListener(e -> intentarCerrar());

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(cardPanel, BorderLayout.CENTER);
        content.add(WizardUi.nav(btnBack, btnNext, btnSave,
                btnSaveAndClose, btnCancel), BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void wireSteps() {
        devicePanel.setOnDeviceChanged(this::onDeviceChanged);
        areaPanel.setOnItemsChanged(this::updateNavButtons);
        pvPanel.setSaveHooks(this::confirmNoMissingGroups, mensaje -> guardarYavisar(mensaje));
    }

    private void onDeviceChanged() {
        areaPanel.onDeviceChanged();
        pvPanel.refreshData();
        updateNavButtons();
    }

    private void loadDevices(String deviceUuid) {
        if (deviceUuid == null || deviceUuid.isEmpty()) {
            devicePanel.refresh();
        } else {
            devicePanel.selectDeviceByUuid(deviceUuid);
        }
    }

    private void showStep(String step) {
        currentStep = step;
        cardLayout.show(cardPanel, step);
        if (STEP_AREA.equals(step)) {
            areaPanel.refresh();
        } else if (STEP_PV.equals(step)) {
            pvPanel.refreshData();
        }
        updateNavButtons();
    }

    private void updateNavButtons() {
        boolean device = STEP_DEVICE.equals(currentStep);
        boolean pv = STEP_PV.equals(currentStep);
        btnBack.setEnabled(!device);
        btnNext.setText(device ? "Siguiente >" : "Continuar a variable >");
        btnNext.setEnabled(device || !state.getItems().isEmpty());
        btnSave.setEnabled(!device && !pv);
        btnSaveAndClose.setEnabled(pv);
        btnCancel.setEnabled(true);
    }

    private void guardar(boolean cerrar) {
        if (state.getSelectedDevice() == null || model == null) {
            return;
        }
        if (!confirmNoMissingGroups()) {
            return;
        }
        SaveResult resultado = guardarYavisar(cerrar ? "Comunicación guardada." : "Grupos y áreas guardados.");
        if (cerrar && resultado.isComplete()) {
            dispose();
        }
    }

    private boolean confirmNoMissingGroups() {
        List<String> nombres = controller.areasSinGrupoNames();
        if (nombres.isEmpty()) {
            return true;
        }
        JOptionPane.showMessageDialog(this,
                "Las siguientes áreas de memoria no tienen grupo de escaneo:\n\n"
                        + String.join(", ", nombres)
                        + "\n\nEl grupo de escaneo es obligatorio, por lo que no se "
                        + "guarda ni el XML ni la base de datos.\n"
                        + "Vuelva a crear esas áreas eligiendo su grupo.",
                "Áreas sin grupo", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    private SaveResult guardarYavisar(String what) {
        SaveResult resultado = controller.saveAll();
        if (resultado.isComplete()) {
            JOptionPane.showMessageDialog(this, what + "\nGuardado en comunicacion.xml y en boot.db.",
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } else if (!resultado.xmlSaved()) {
            JOptionPane.showMessageDialog(this, what
                            + "\nNo se guardó nada: " + resultado.message(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, what
                            + "\nGuardado en comunicacion.xml, pero NO se pudo actualizar boot.db."
                            + "\nRevise la salida de la aplicación para el detalle.",
                    "Guardado parcial", JOptionPane.WARNING_MESSAGE);
        }
        return resultado;
    }

    private void intentarCerrar() {
        if (state.isCambiosSinGuardar()) {
            int opcion = JOptionPane.showConfirmDialog(this,
                    "Hay cambios sin guardar en la configuración de comunicación.\n"
                            + "Si sale ahora, esos cambios se pierden.\n\n"
                            + "¿Desea guardarlos antes de salir?",
                    "Cambios sin guardar", JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (opcion == JOptionPane.CANCEL_OPTION || opcion == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (opcion == JOptionPane.YES_OPTION) {
                // Aqui no se puede cerrar sea cual sea el resultado: el usuario ha
                // pedido guardar, y si se cierra con el guardado a medias se va sin
                // saberlo. Con H-01 tampoco: ahi no se guardo nada, y cerrar haria
                // perder los cambios creyendo que estan a salvo.
                if (!confirmNoMissingGroups()
                        || !guardarYavisar("Comunicación guardada.").isComplete()) {
                    return;
                }
            }
        }
        dispose();
    }

    /** Exposto sólo para las pruebas: el paso visible del asistente. */
    String getCurrentStep() {
        return currentStep;
    }

    /** Dispositivo sobre el que se está configurando. */
    DeviceConfigData getSelectedDevice() {
        return state.getSelectedDevice();
    }

    /** Sólo para pruebas: comprueba que el grupo quedó asociado al área. */
    String groupNameOf(String itemUuid) {
        CommConfigData.ItemConfig item = state.itemByUuid(itemUuid);
        return item == null ? "" : state.groupNameOf(item.getUuid());
    }
}
