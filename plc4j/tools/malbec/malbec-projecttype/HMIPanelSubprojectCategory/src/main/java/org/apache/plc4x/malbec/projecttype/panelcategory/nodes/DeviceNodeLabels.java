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

    static String nombreDe(DeviceConfigData dispositivo) {
        if (dispositivo == null) {
            return SIN_NOMBRE;
        }
        String nombre = dispositivo.getDeviceName();
        return nombre == null || nombre.isBlank() ? SIN_NOMBRE : nombre;
    }

    static String tooltipDe(DeviceConfigData dispositivo) {
        return dispositivo.getBrand() + " / " + dispositivo.getModel()
                + " / " + dispositivo.getProtocol();
    }

    static String textoDe(DeviceConfigData dispositivo, Function<DeviceConfigData, String> getter) {
        String valor = dispositivo == null ? null : getter.apply(dispositivo);
        return valor == null ? "" : valor;
    }

    static boolean booleanoDe(DeviceConfigData dispositivo,
            Function<DeviceConfigData, Boolean> getter) {
        return dispositivo != null && Boolean.TRUE.equals(getter.apply(dispositivo));
    }

    /**
     * @param nombre    nombre del dispositivo, ya resuelto por {@link #nombreDe}
     * @param comunicacion su comunicacion, o null si no tiene
     */
    static String mensajeDeBorrado(String nombre, CommConfigData comunicacion) {
        StringBuilder texto = new StringBuilder("Va a eliminar el dispositivo '")
                .append(nombre).append("'.")
                .append("\n\nAl borrarlo se pierden todas sus configuraciones.");
        if (comunicacion != null) {
            int grupos = comunicacion.getGroups().size();
            int areas = comunicacion.getItems().size();
            int variables = comunicacion.getPvs().size();
            if (grupos > 0 || areas > 0 || variables > 0) {
                texto.append("\n\n  \u2022 ").append(grupos)
                        .append(grupos == 1 ? " grupo" : " grupos")
                        .append("\n  \u2022 ").append(areas)
                        .append(areas == 1 ? " \u00e1rea" : " \u00e1reas")
                        .append("\n  \u2022 ").append(variables)
                        .append(variables == 1 ? " variable" : " variables");
            }
        }
        return texto.append("\n\nTambi\u00e9n se quitar\u00e1 de comunicacion.xml y de boot.db.")
                .append("\n\n\u00bfDesea continuar?").toString();
    }

    static String mensajeDispositivoInexistente(String nombre) {
        return "El dispositivo '" + nombre + "' ya no est\u00e1 en la configuraci\u00f3n.\n"
                + "Puede que se haya borrado desde otra vista.";
    }
    
    static String mensajeGuardadoParcial(String nombre) {
        return "El dispositivo '" + nombre + "' se quit\u00f3 de comunicacion.xml pero no se pudo\n"
                + "actualizar boot.db. Revis\u00e1 la consola.";
    }

    static String mensajeBorradoCorrecto(String nombre) {
        return "Se elimin\u00f3 el dispositivo '" + nombre + "' correctamente.";
    }

    static String mensajeNadaQueBorrar(String nombre) {
        return "No se encontr\u00f3 el dispositivo '" + nombre + "'.";
    }
}