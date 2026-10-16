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

import java.util.function.Function;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

final class DeviceNodeLabels {

    static final String SIN_NOMBRE = "(sin nombre)";

    private DeviceNodeLabels() {
    }

    static String nameOf(DeviceConfigData device) {
        if (device == null) {
            return SIN_NOMBRE;
        }
        String name = device.getDeviceName();
        return name == null || name.isBlank() ? SIN_NOMBRE : name;
    }

    static String tooltipOf(DeviceConfigData device) {
        return device.getBrand() + " / " + device.getModel()
                + " / " + device.getProtocol();
    }

    static String textOf(DeviceConfigData device, Function<DeviceConfigData, String> getter) {
        String valor = device == null ? null : getter.apply(device);
        return valor == null ? "" : valor;
    }

    static boolean booleanOf(DeviceConfigData device,
            Function<DeviceConfigData, Boolean> getter) {
        return device != null && Boolean.TRUE.equals(getter.apply(device));
    }

    /**
     * @param nombre    nombre del dispositivo, ya resuelto por {@link #nombreDe}
     * @param comunicacion su comunicacion, o null si no tiene
     */
    static String deletionMessage(String name, CommConfigData communication) {
        StringBuilder text = new StringBuilder("Va a eliminar el dispositivo '")
                .append(name).append("'.")
                .append("\n\nAl borrarlo se pierden todas sus configuraciones.");
        if (communication != null) {
            int grupos = communication.getGroups().size();
            int areas = communication.getItems().size();
            int variables = communication.getPvs().size();
            if (grupos > 0 || areas > 0 || variables > 0) {
                text.append("\n\n  \u2022 ").append(grupos)
                        .append(grupos == 1 ? " grupo" : " grupos")
                        .append("\n  \u2022 ").append(areas)
                        .append(areas == 1 ? " \u00e1rea" : " \u00e1reas")
                        .append("\n  \u2022 ").append(variables)
                        .append(variables == 1 ? " variable" : " variables");
            }
        }
        return text.append("\n\nTambi\u00e9n se quitar\u00e1 de comunicacion.xml y de boot.db.")
                .append("\n\n\u00bfDesea continuar?").toString();
    }

    static String unknownDeviceMessage(String name) {
        return "El dispositivo '" + name + "' ya no est\u00e1 en la configuraci\u00f3n.\n"
                + "Puede que se haya borrado desde otra vista.";
    }
    
    static String partialSaveMessage(String name) {
        return "El dispositivo '" + name + "' se quit\u00f3 de comunicacion.xml pero no se pudo\n"
                + "actualizar boot.db. Revis\u00e1 la consola.";
    }

    static String correctDeletionMessage(String name) {
        return "Se elimin\u00f3 el dispositivo '" + name + "' correctamente.";
    }

    static String nothingToDeleteMessage(String name) {
        return "No se encontr\u00f3 el dispositivo '" + name + "'.";
    }
}