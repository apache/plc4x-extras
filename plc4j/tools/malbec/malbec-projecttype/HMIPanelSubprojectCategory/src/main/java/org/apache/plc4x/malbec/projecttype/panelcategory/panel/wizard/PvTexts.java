/*
 * Licensed to the Apache Software Foundation (ASF) under one
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

import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.MemoryTag;

/**
 * Los textos largos del formulario de variable de proceso.
 *
 * <p>Van fuera del panel por el mismo motivo que {@code DeviceNodeLabels}: que se puedan
 * leer y probar sin montar Swing, y que un cambio de redaccion no se lleve por delante
 * una clase de 400 lineas.
 *
 * <p>Estos textos explican por que no se puede seguir, que es justo lo que un mensaje
 * de error corto no suele hacer.
 *
 * <p>T5.1 los llevara a un {@code Bundle.properties}. Hasta entonces siguen aqui, en
 * Castellano y con el mismo texto de siempre.
 */
final class PvTexts {

    private PvTexts() {
    }

    /**
     * Por que no se puede calcular donde va cada variable.
     *
     * <p>Sin saber cuantos bytes ocupa el area no hay offset que repartir, asi que el
     * panel no puede ni avisar de un numero inventado. Este texto dice que falta y como
     * arreglarlo.
     */
    static String motivoDeOffsetDesconocido(CommConfigData.ItemConfig area) {
        MemoryTag tag = MemoryTag.parse(area.getTag());
        if (tag == null || !tag.tieneDireccion()) {
            return "El tag del área '" + area.getName() + "' es '" + area.getTag()
                    + "', y eso no es una dirección válida.\n\n"
                    + "Sin dirección no se sabe qué bytes ocupa el área, así que no se"
                    + " puede calcular dónde va cada variable.\n\n"
                    + "Corrija el tag en el paso anterior, del tipo"
                    + " %DB22.DBB4[0..9]:REAL.";
        }
        return "No se sabe el tamaño del área '" + area.getName() + "': su tag es '"
                + area.getTag() + "' y no dice cuántos bytes ocupa.\n\n"
                + "Añada un rango, del tipo %DB21.DBB0[0..9], o un tipo tras los dos"
                + " puntos, del tipo :INT.";
    }

    /** No cabe una variable mas del tipo pedido. */
    static String noCabeEnElArea(CommConfigData.ItemConfig area, DataType type,
            int bytesArea, int bytesLibres) {
        return "No cabe una variable más de tipo '" + type.label() + "' en el área '"
                + area.getName() + "'.\n\n"
                + "El área abarca " + bytesArea
                + " bytes y cada " + type.label() + " ocupa "
                + PvRenderers.pesoDe(type) + ".\n"
                + (bytesLibres > 0
                        ? "Quedan " + bytesLibres + " bytes sueltos, pero no llegan"
                        + " para uno entero, así que se considera llena.\n"
                        : "")
                + "Amplíe el rango del tag del área si necesita más sitio.";
    }

    /** El area esta llena segun el calculo de huecos, aunque sobre algo de sitio. */
    static String areaLlena(CommConfigData.ItemConfig area, DataType type, int bytesArea) {
        return "El área '" + area.getName() + "' ya está llena.\n\n"
                + "Abarca " + bytesArea
                + " bytes y admite " + type.label() + " de " + PvRenderers.pesoDe(type)
                + ", así que no cabe una variable más.\n"
                + "Amplíe el rango del tag del área si necesita más sitio.";
    }

    /**
     * El texto que explica el tipo que tiene el area y cuanto queda.
     *
     * <p>Va en HTML porque necesita negritas; el resto de textos del panel van en texto
     * plano y se quedan como estan.
     */
    static String bloqueoDeTipo(CommConfigData.ItemConfig area, DataType elegido,
            int slotsLibres, int bytesLibres, int bytesArea) {
        StringBuilder texto = new StringBuilder("<html>El área <b>")
                .append(area.getName()).append("</b> ");
        if (elegido == null) {
            texto.append("aún no tiene tipo: elígelo arriba. Todas sus variables")
                    .append(" usarán ese mismo tipo.");
        } else {
            texto.append("usa <b>").append(elegido.label()).append("</b> (")
                    .append(PvRenderers.pesoDe(elegido)).append(" por variable). Todas sus")
                    .append(" variables serán de ese tipo.");
        }
        if (bytesLibres < 0) {
            texto.append("<br>Del tag no se sabe el tamaño, así que no se lleva la cuenta.");
        } else if (slotsLibres <= 0) {
            texto.append("<br><b>No cabe una variable más: se ha llegado al límite.</b>")
                    .append(" El área abarca ")
                    .append(bytesArea)
                    .append(" bytes y están todos ocupados.");
        } else {
            texto.append("<br>Quedan <b>").append(bytesLibres).append(" bytes</b> libres, ")
                    .append("que es lo mismo que <b>").append(slotsLibres)
                    .append(" ").append(elegido == null ? "variables" : elegido.label())
                    .append("</b> de ").append(PvRenderers.pesoDe(elegido)).append(".");
        }
        return texto.append("</html>").toString();
    }

    /** Los valores corregidos al abrir la variable, antes de poder guardar. */
    static String ajustesAlCargar(java.util.List<String> ajustes) {
        return "Estos valores se han corregido al abrir la variable, porque no"
                + " encajaban en el formulario:\n\n"
                + String.join("\n", ajustes)
                + "\n\nSi se guarda, se escriben los valores corregidos.";
    }

    /** Los campos que hay que rellenar antes de poder guardar. */
    static String camposIncompletos(java.util.List<String> vacios) {
        return "Complete estos campos antes de guardar la variable:\n\n"
                + String.join("\n", vacios);
    }

    /** Un tipo que el area no admite, explicado en vez de solo rejectado. */
    static String tipoNoAdmitido() {
        return "Seleccione un tipo de dato que el área admita. Los que salen en "
                + "gris no tienen tamaño definido y no se pueden usar.";
    }
}