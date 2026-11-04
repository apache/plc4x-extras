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

final class PvCamposObligatorios {

    private PvCamposObligatorios() {
    }

    /**
     * @param descriptor        descriptor del PV
     * @param scanTimeElegido   valor del desplegable, null si no hay ninguno
     * @param descripcionDisplay texto de la descripcion en pantalla
     * @param formatoDisplay    texto del formato
     * @param unidadesDisplay   texto de las unidades
     */
    static List<String> missingFields(String descriptorText, Object scanTimeElegido,
            String displayDescriptionText, String displayFormatText, String displayUnitsText) {
        List<String> vacios = new ArrayList<>();
        if (isBlank(descriptorText)) {
            vacios.add("Descriptor");
        }
        if (scanTimeElegido == null) {
            vacios.add("ScanTime");
        }
        if (isBlank(displayDescriptionText)) {
            vacios.add("Display descripción");
        }
        if (isBlank(displayFormatText)) {
            vacios.add("Display formato");
        }
        if (isBlank(displayUnitsText)) {
            vacios.add("Display unidades");
        }
        return vacios;
    }

    static List<String> missingFields(JTextField descriptorText, Object scanTimeElegido,
            JTextField displayDescriptionText, JTextField displayFormatText, JTextField displayUnitsText) {
        return missingFields(descriptorText.getText(), scanTimeElegido,
                displayDescriptionText.getText(), displayFormatText.getText(), displayUnitsText.getText());
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}