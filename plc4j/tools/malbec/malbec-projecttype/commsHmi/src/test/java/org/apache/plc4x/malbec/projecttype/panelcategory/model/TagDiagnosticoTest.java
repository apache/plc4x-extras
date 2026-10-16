/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagDiagnosticoTest {

    private static boolean anyErrors(String tag) {
        return !TagDiagnostico.errors(tag).isEmpty();
    }

    private static boolean anyWarnings(String tag) {
        return !TagDiagnostico.warnings(tag).isEmpty();
    }

    @Test
    void wellWrittenTagsAreNotWarned() {
        assertFalse(anyErrors("%DB1.DBB0[0..9]:BYTE"), "no debe avisar de un tag válido");
        assertFalse(anyErrors("%DB231.DBB0[0..1848]:BOOL"));
        assertFalse(anyErrors("%DB1.DBX0.0:BOOL"));
        assertFalse(anyErrors("%DB5:10:INT"));
        assertFalse(anyErrors("%DB1.DBB0:BYTE[1849]"));
        assertFalse(anyErrors("%M0.0:BOOL"));
        assertFalse(anyErrors("%MD100:DINT"));
        assertFalse(anyErrors("40001"));
        assertFalse(anyWarnings("%DB1.DBB0[0..9]:BYTE"),
                "un tag con rango y tipo no deja avisos");
    }

    @Test
    void rangeThatIsNotARangeIsWarned() {
        assertTrue(anyErrors("%DB1.DBB0[.0.9]:BYTE"));
        assertTrue(anyErrors("%DB1.DBB0[0,9]:BYTE"), "la coma no separa nada");
        assertFalse(anyErrors("%DB1.DBB0[0..9]:BYTE"), "pero el rango bueno no avisa");
        assertFalse(anyErrors("%DB1.DBB0[10]:BYTE"), "ni un índice suelto");
    }

    @Test
    void unbalancedBracketsAreWarned() {
        assertTrue(anyErrors("%DB1.DBB0[0..9:BYTE"), "falta cerrar el corchete");
        assertTrue(anyErrors("%DB1.DBB00..9]:BYTE"), "falta abrirlo");
    }

    @Test
    void loQueNoEsUnaDireccionSeAvisa() {
        List<TagIssue> errors = TagDiagnostico.errors("-lkñl{l0'o");
        assertFalse(errors.isEmpty(), "un tag sin forma de dirección debe avisar");
        assertTrue(errors.stream().anyMatch(i -> TagIssue.NO_ES_DIRECCION.equals(i.codigo())),
                "el aviso tiene que decir por qué: " + errors);
        assertTrue(errors.stream().anyMatch(i -> i.args().contains("-lkñl{l0'o")),
                "el tag concreto va en los argumentos: " + errors);
    }

    @Test
    void weirdCharactersAreWarned() {
        List<TagIssue> errors = TagDiagnostico.errors("%DB1.DBB0[0..9]:ÑBYTE");
        assertFalse(errors.isEmpty(), "la ñ no puede ser parte de una dirección");
        assertTrue(errors.stream().anyMatch(i -> TagIssue.CARACTERES_RAROS.equals(i.codigo())),
                "debe llegar el aviso de caracteres raros: " + errors);
    }

    @Test
    void symbolicTagIsNotMarkedAsError() {
        assertTrue(anyErrors("Control_Panel.Start_Button"));
        assertTrue(TagDiagnostico.warnings("Control_Panel.Start_Button").isEmpty(),
                "un símbolo no necesita además un aviso de tamaño");
    }

    @Test
    void unknownTypeIsWarned() {
        assertTrue(anyErrors("%DB1.DBB0[0..9]:RAW_BYTE_ARRAY_ALIAS"),
                "un tipo que no existe no debe dejar el área sin tipos");
        assertFalse(anyErrors("%DB1.DBB0[0..9]:RAW_BYTE_ARRAY"),
                "pero el alias de BYTE sí se reconoce");
    }

    @Test
    void addressWithoutSizeIsWarnedWithoutBeingAnError() {
        List<TagIssue> errors = TagDiagnostico.errors("%DB1:0");
        assertTrue(errors.isEmpty(), "una dirección válida no se marca: " + errors);
        List<TagIssue> warnings = TagDiagnostico.warnings("%DB1:0");
        assertTrue(warnings.stream().anyMatch(i -> TagIssue.SIN_TAMANO.equals(i.codigo())),
                "debería avisar de que no se sabe el tamaño: " + warnings);
        assertTrue(warnings.stream().allMatch(
                        i -> TagIssue.Gravedad.AVISO.equals(i.gravedad())),
                "lo que no impide usar el tag es un aviso, no un error");
    }

    @Test
void blockWithoutAddressIsWarned() {
        List<TagIssue> vacio = TagDiagnostico.errors("%DBZ[]");
        assertTrue(vacio.stream().anyMatch(i -> TagIssue.NO_ES_DIRECCION.equals(i.codigo())),
                "un bloque con cA3digo desconocido debe avisar: " + vacio);

        List<TagIssue> rango = TagDiagnostico.errors("%DBZ[0..9]");
        assertTrue(rango.stream().anyMatch(i -> TagIssue.NO_ES_DIRECCION.equals(i.codigo())),
                "un cA3digo desconocido con rango debe avisar: " + rango);
    }

    @Test
    void emptyTagSaysNothing() {
        assertEquals(0, TagDiagnostico.errors("").size());
        assertEquals(0, TagDiagnostico.errors(null).size());
        assertEquals(0, TagDiagnostico.errors("   ").size());
        assertEquals(0, TagDiagnostico.warnings("").size());
    }

    @Test
    void diagnosticDoesNotReturnPresentation() {
        // T3.8: la capa de modelo no debe emitir nada de HTML. El texto lo compone
        // quien lo pinta.
        for (String tag : new String[]{"%DB1[]", "%DB1.DBB0[0..9]:ÑBYTE", "-lkñl{l0'o",
                "%DB1.DBB0[.0.9]:BYTE", "%DB1:0", "%DB1.DBB0[0..9]:RAW_BYTE_ARRAY_ALIAS"}) {
            for (TagIssue issue : TagDiagnostico.errors(tag)) {
                assertFalse(issue.codigo().contains("<"),
                        "el código no debe traer HTML: " + issue);
                assertFalse(issue.args().stream().anyMatch(a -> a.contains("<br")),
                        "los argumentos no deben traer HTML: " + issue);
            }
            for (TagIssue issue : TagDiagnostico.warnings(tag)) {
                assertFalse(issue.codigo().contains("<"), "el código no debe traer HTML");
            }
        }
    }

    @Test
    void tooLargeNumberIsWarned() {
        for (String tag : new String[]{"%DB1.DBB0[99999999999]:BYTE",
                "%DB99999999999.DBB1", "%DB1.DBB0[0..99999999999]:BYTE",
                "%DB1.DBB0:STRING(99999999999)"}) {
            List<String> codigos = TagDiagnostico.errors(tag).stream()
                    .map(TagIssue::codigo).toList();
            assertTrue(codigos.contains(TagIssue.NUMERO_DEMASIADO_GRANDE),
                    "«" + tag + "» tiene un número que no cabe en un entero");
        }
    }

    @Test
    void normalNumbersAreNotWarned() {
        for (String tag : new String[]{"%DB231.DBB0[0..1848]:BYTE", "%DB1.DBB0:STRING(80)",
                "%I0.0:BOOL", "%DB9999.DBW20:INT"}) {
            List<String> codigos = TagDiagnostico.errors(tag).stream()
                    .map(TagIssue::codigo).toList();
            assertFalse(codigos.contains(TagIssue.NUMERO_DEMASIADO_GRANDE),
                    "«" + tag + "» no tiene ningún número demasiado grande");
        }
    }
}