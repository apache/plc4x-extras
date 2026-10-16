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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.TagIssue.Gravedad;

public final class TagDiagnostico {

    private static final Pattern content = Pattern.compile("\\[([^\\]]*)\\]");
    private static final Pattern CONTENIDO_VALIDO =
            Pattern.compile("\\s*\\d+\\s*(\\.\\.\\s*\\d+\\s*)?");
    private static final Pattern CARACTERES_RAROS =
            Pattern.compile("[^A-Za-z0-9_.:%\\[\\]()\\-\\s]");
    private static final Pattern NUMEROS = Pattern.compile("\\d+");

    private TagDiagnostico() {
    }

    public static List<TagIssue> errors(String tag) {
        List<TagIssue> errors = new ArrayList<>();
        if (tag == null) {
            return errors;
        }
        String t = tag.trim();
        if (t.isEmpty()) {
            return errors;
        }
        revisarCorchetes(t, errors);
        revisarCaracteres(t, errors);
        reviewNumbers(t, errors);

        MemoryTag m = MemoryTag.parse(t);
        if (m != null && !m.hasAddress()) {
            errors.add(new TagIssue(TagIssue.NO_ES_DIRECCION, Gravedad.ERROR, t));
        }
        if (m != null && m.family() != null
                && DataType.candidates(m.family(), m.codeBits()).isEmpty()) {
            errors.add(new TagIssue(TagIssue.SIN_TIPOS, Gravedad.ERROR,
                    m.family(), DataType.names()));
        }
        return errors;
    }

    public static List<TagIssue> warnings(String tag) {
        List<TagIssue> warnings = new ArrayList<>();
        if (tag == null) {
            return warnings;
        }
        String t = tag.trim();
        if (t.isEmpty()) {
            return warnings;
        }
        MemoryTag m = MemoryTag.parse(t);
        if (m != null && m.hasAddress() && m.byteCapacity() == 0) {
            warnings.add(new TagIssue(TagIssue.SIN_TAMANO, Gravedad.AVISO));
        }
        return warnings;
    }

    private static void reviewNumbers(String t, List<TagIssue> errors) {
        Matcher m = NUMEROS.matcher(t);
        while (m.find()) {
            String number = m.group();
            if (number.length() < 10) {
                continue;
            }
            try {
                Integer.parseInt(number);
            } catch (NumberFormatException ex) {
                errors.add(new TagIssue(TagIssue.NUMERO_DEMASIADO_GRANDE, Gravedad.ERROR, number));
            }
        }
    }

    private static void revisarCorchetes(String t, List<TagIssue> errors) {
        int abiertos = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '[') {
                abiertos++;
            } else if (c == ']') {
                abiertos--;
            }
        }
        if (abiertos > 0) {
            errors.add(new TagIssue(TagIssue.CORCHETE_SIN_CERRAR, Gravedad.ERROR));
        } else if (abiertos < 0) {
            errors.add(new TagIssue(TagIssue.CORCHETE_SOBRANTE, Gravedad.ERROR));
        }
        Matcher m = content.matcher(t);
        while (m.find()) {
            String dentro = m.group(1);
            if (dentro.isBlank() || CONTENIDO_VALIDO.matcher(dentro).matches()) {
                continue;
            }
            errors.add(new TagIssue(TagIssue.RANGO_INVALIDO, Gravedad.ERROR, dentro.trim()));
        }
    }

    private static void revisarCaracteres(String t, List<TagIssue> errors) {
        Matcher m = CARACTERES_RAROS.matcher(t);
        StringBuilder raros = new StringBuilder();
        while (m.find()) {
            raros.append(m.group());
        }
        if (raros.length() > 0) {
            errors.add(new TagIssue(TagIssue.CARACTERES_RAROS, Gravedad.ERROR, raros.toString()));
        }
    }
}
