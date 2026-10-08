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

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextField;

/**
 * Los campos que hay que rellenar antes de poder guardar una variable.
 *
 * <p>Estaba dentro de {@code StepPvPanel}, mezclado con el {@code JOptionPane} que la
 * complaint. Aqui solo se mira que campos estan vacios, sin Swing ni ventanas: se puede
 * probar y, mas adelante, cambiar el orden o el texto de la lista sin tocar el panel.
 *
 * <p>El ScanTime se cuenta aparte porque no es un campo de texto sino un desplegable,
 * y porque vacio ahi significa "no se ha elegido", no "se ha borrado".
 */
final class PvCamposObligatorios {

    private PvCamposObligatorios() {
    }

    /**
     * Los nombres de los campos que faltan, en el orden en que se avisa.
     *
     * @param descriptor        descriptor del PV
     * @param scanTimeElegido   valor del desplegable, null si no hay ninguno
     * @param descripcionDisplay texto de la descripcion en pantalla
     * @param formatoDisplay    texto del formato
     * @param unidadesDisplay   texto de las unidades
     */
    static List<String> queFaltan(String descriptor, Object scanTimeElegido,
            String descripcionDisplay, String formatoDisplay, String unidadesDisplay) {
        List<String> vacios = new ArrayList<>();
        if (esVacio(descriptor)) {
            vacios.add("Descriptor");
        }
        if (scanTimeElegido == null) {
            vacios.add("ScanTime");
        }
        if (esVacio(descripcionDisplay)) {
            vacios.add("Display descripción");
        }
        if (esVacio(formatoDisplay)) {
            vacios.add("Display formato");
        }
        if (esVacio(unidadesDisplay)) {
            vacios.add("Display unidades");
        }
        return vacios;
    }

    static List<String> queFaltan(JTextField descriptor, Object scanTimeElegido,
            JTextField descripcionDisplay, JTextField formatoDisplay, JTextField unidadesDisplay) {
        return queFaltan(descriptor.getText(), scanTimeElegido,
                descripcionDisplay.getText(), formatoDisplay.getText(), unidadesDisplay.getText());
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }
}