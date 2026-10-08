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
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.openide.windows.WindowManager;

/**
 * Confirmacion y ejecucion del borrado de un dispositivo.
 *
 * <p>Antes vivia dentro del nodo, mezclado con las acciones y la hoja de propiedades. Aqui
 * queda separado: quien llama solo dice que UUID borrar.
 *
 * <p>El flujo tiene cuatro salidas, y todas estan en el mismo sitio para que no se pierda
 * ninguna: cancelar, el dispositivo ya no esta, el guardado fallo, y el guardado quedo a
 * medias. Esa ultima es la que mas importa: el dispositivo sale de {@code comunicacion.xml}
 * pero no de {@code boot.db}, y el usuario tiene que enterarse por ventana.
 *
 * <p>El texto vive en {@link DeviceNodeLabels}, sin Swing, y esta probado. Esta clase solo
 * muestra lo que devuelve y decide cuando.
 */
final class DeleteDeviceDialog {

    private DeleteDeviceDialog() {
    }

    /**
     * Pide confirmacion y, si se acepta, borra el dispositivo y guarda.
     *
     * @return true si el dispositivo se borro y se guardo
     */
    static boolean eliminar(String uuid, String nombre, HMICommunicationModel model, Frame ventana) {
        if (model == null) {
            return false;
        }

        Object[] opciones = {"Aceptar", "Cancelar"};
        int elegida = JOptionPane.showOptionDialog(ventana,
                DeviceNodeLabels.mensajeDeBorrado(nombre, model.getComms(uuid)),
                "Eliminar dispositivo", JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, opciones, opciones[0]);
        if (elegida != 0) {
            return false;
        }

        if (!model.removeDevice(uuid)) {
            JOptionPane.showMessageDialog(ventana,
                    DeviceNodeLabels.mensajeNadaQueBorrar(nombre),
                    "Nada que eliminar", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // save() ya no lanza por un fallo de escritura: devuelve lo que paso, y aqui
        // cada caso tiene su aviso porque son tres situaciones distintas para el usuario.
        SaveResult resultado = model.save();

        if (!resultado.xmlSaved()) {
            // No se escribio nada: el dispositivo sigue en el fichero, asi que el aviso
            // no puede insinuar lo contrario.
            JOptionPane.showMessageDialog(ventana,
                    "No se pudo eliminar '" + nombre + "'.\n\n" + resultado.message(),
                    "Error al eliminar", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (resultado.isPartial()) {
            JOptionPane.showMessageDialog(ventana,
                    DeviceNodeLabels.mensajeGuardadoParcial(nombre),
                    "Guardado parcial", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        JOptionPane.showMessageDialog(ventana,
                DeviceNodeLabels.mensajeBorradoCorrecto(nombre),
                "Eliminaci\u00f3n exitosa", JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    /** Ventana principal de NetBeans, sobre la que se muestran los avisos. */
    static Frame ventanaPrincipal() {
        return WindowManager.getDefault().getMainWindow();
    }
}
