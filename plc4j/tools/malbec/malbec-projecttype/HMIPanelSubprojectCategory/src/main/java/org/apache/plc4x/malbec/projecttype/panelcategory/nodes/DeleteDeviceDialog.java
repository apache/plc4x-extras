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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

import java.awt.Frame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

final class DeleteDeviceDialog {

    private DeleteDeviceDialog() {
    }

    static void eliminar(String uuid, String nombre, HMICommunicationModel model, Frame ventana) {
        if (model == null) {
            return;
        }

        Object[] opciones = {"Aceptar", "Cancelar"};
        int elegida = JOptionPane.showOptionDialog(ventana,
                DeviceNodeLabels.mensajeDeBorrado(nombre, model.getComms(uuid)),
                "Eliminar dispositivo", JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, opciones, opciones[0]);
        if (elegida != 0) {
            return;
        }

        if (!model.removeDevice(uuid)) {
            JOptionPane.showMessageDialog(ventana,
                    DeviceNodeLabels.mensajeNadaQueBorrar(nombre),
                    "Nada que eliminar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RequestProcessor.getDefault().post(() -> {
            SaveResult resultado = model.save();
            SwingUtilities.invokeLater(() -> mostrar(ventana, nombre, resultado));
        });
    }

    private static void mostrar(Frame ventana, String nombre, SaveResult resultado) {
        if (!resultado.xmlSaved()) {
            JOptionPane.showMessageDialog(ventana,
                    "No se pudo eliminar '" + nombre + "'.\n\n" + resultado.message(),
                    "Error al eliminar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (resultado.isPartial()) {
            JOptionPane.showMessageDialog(ventana,
                    DeviceNodeLabels.mensajeGuardadoParcial(nombre),
                    "Guardado parcial", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(ventana,
                DeviceNodeLabels.mensajeBorradoCorrecto(nombre),
                "Eliminación exitosa", JOptionPane.INFORMATION_MESSAGE);
    }


    static Frame ventanaPrincipal() {
        return WindowManager.getDefault().getMainWindow();
    }
}
