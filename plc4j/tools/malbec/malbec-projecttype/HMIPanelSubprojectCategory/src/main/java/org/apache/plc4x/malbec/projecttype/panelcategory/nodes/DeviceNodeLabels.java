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

/**
 * Los textos que muestra el nodo de un dispositivo.
 *
 * <p>Estaban dentro de {@code HMICommNode} como metodos privados, junto con el codigo Swing
 * que abre dialogos. Al estar ahi no se podian comprobar sin levantar la ventana principal de
 * NetBeans, y son justo la parte que tiene reglas: el texto de confirmacion de borrado decide
 * en plural o en singular segun cuantos grupos, areas y variables tenga el dispositivo.
 *
 * <p>Esta clase no importa nada de Swing: son funciones sobre texto. Los dialogos siguen
 * estaria en su sitio y solo piden aqui el texto que deben mostrar.
 */
final class DeviceNodeLabels {

    /** Texto unico para cuando el dispositivo no tiene nombre. */
    static final String SIN_NOMBRE = "(sin nombre)";

    private DeviceNodeLabels() {
    }

    /** Nombre del dispositivo, o un texto utilizable si viniera vacio. */
    static String nombreDe(DeviceConfigData dispositivo) {
        if (dispositivo == null) {
            return SIN_NOMBRE;
        }
        String nombre = dispositivo.getDeviceName();
        return nombre == null || nombre.isBlank() ? SIN_NOMBRE : nombre;
    }

    /** Tooltip del nodo: lo que identifica al dispositivo de un vistazo. */
    static String tooltipDe(DeviceConfigData dispositivo) {
        return dispositivo.getBrand() + " / " + dispositivo.getModel()
                + " / " + dispositivo.getProtocol();
    }

    /** Valor legible de una propiedad, o cadena vacia si no hay dispositivo. */
    static String textoDe(DeviceConfigData dispositivo, Function<DeviceConfigData, String> getter) {
        String valor = dispositivo == null ? null : getter.apply(dispositivo);
        return valor == null ? "" : valor;
    }

    /** Valor booleano de una propiedad; sin dispositivo se informa como desactivado. */
    static boolean booleanoDe(DeviceConfigData dispositivo,
            Function<DeviceConfigData, Boolean> getter) {
        return dispositivo != null && Boolean.TRUE.equals(getter.apply(dispositivo));
    }

    /**
     * Texto de confirmacion antes de eliminar un dispositivo.
     *
     * <p>Lista lo que se va a perder, y solo si hay algo que perder: un dispositivo sin
     * grupos, areas ni variables no necesita un recuento de ceros.
     *
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

    /** Aviso cuando se pide modificar un dispositivo que ya no esta en la configuracion. */
    static String mensajeDispositivoInexistente(String nombre) {
        return "El dispositivo '" + nombre + "' ya no est\u00e1 en la configuraci\u00f3n.\n"
                + "Puede que se haya borrado desde otra vista.";
    }

    /** Aviso cuando el guardado queda a medias: se borro del XML pero no de boot.db. */
    static String mensajeGuardadoParcial(String nombre) {
        return "El dispositivo '" + nombre + "' se quit\u00f3 de comunicacion.xml pero no se pudo\n"
                + "actualizar boot.db. Revis\u00e1 la consola.";
    }

    /** Confirmacion de borrado correcto. */
    static String mensajeBorradoCorrecto(String nombre) {
        return "Se elimin\u00f3 el dispositivo '" + nombre + "' correctamente.";
    }

    /** Aviso cuando no habia nada que borrar. */
    static String mensajeNadaQueBorrar(String nombre) {
        return "No se encontr\u00f3 el dispositivo '" + nombre + "'.";
    }
}