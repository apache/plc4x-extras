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

    private static boolean algunoErrores(String tag) {
        return !TagDiagnostico.errores(tag).isEmpty();
    }

    private static boolean algunoAvisos(String tag) {
        return !TagDiagnostico.avisos(tag).isEmpty();
    }

    @Test
    void losTagsBienEscritosNoSeAvisan() {
        assertFalse(algunoErrores("%DB1.DBB0[0..9]:BYTE"), "no debe avisar de un tag válido");
        assertFalse(algunoErrores("%DB231.DBB0[0..1848]:BOOL"));
        assertFalse(algunoErrores("%DB1.DBX0.0:BOOL"));
        assertFalse(algunoErrores("%DB5:10:INT"));
        assertFalse(algunoErrores("%DB1.DBB0:BYTE[1849]"));
        assertFalse(algunoErrores("%M0.0:BOOL"));
        assertFalse(algunoErrores("%MD100:DINT"));
        assertFalse(algunoErrores("40001"));
        assertFalse(algunoAvisos("%DB1.DBB0[0..9]:BYTE"),
                "un tag con rango y tipo no deja avisos");
    }

    @Test
    void unRangoQueNoLoEsSeAvisa() {
        assertTrue(algunoErrores("%DB1.DBB0[.0.9]:BYTE"));
        assertTrue(algunoErrores("%DB1.DBB0[0,9]:BYTE"), "la coma no separa nada");
        assertFalse(algunoErrores("%DB1.DBB0[0..9]:BYTE"), "pero el rango bueno no avisa");
        assertFalse(algunoErrores("%DB1.DBB0[10]:BYTE"), "ni un índice suelto");
    }

    @Test
    void losCorchetesDescompensadosSeAvisan() {
        assertTrue(algunoErrores("%DB1.DBB0[0..9:BYTE"), "falta cerrar el corchete");
        assertTrue(algunoErrores("%DB1.DBB00..9]:BYTE"), "falta abrirlo");
    }

    @Test
    void loQueNoEsUnaDireccionSeAvisa() {
        List<TagIssue> errores = TagDiagnostico.errores("-lkñl{l0'o");
        assertFalse(errores.isEmpty(), "un tag sin forma de dirección debe avisar");
        assertTrue(errores.stream().anyMatch(i -> TagIssue.NO_ES_DIRECCION.equals(i.codigo())),
                "el aviso tiene que decir por qué: " + errores);
        assertTrue(errores.stream().anyMatch(i -> i.args().contains("-lkñl{l0'o")),
                "el tag concreto va en los argumentos: " + errores);
    }

    @Test
    void losCaracteresRarosSeAvisan() {
        List<TagIssue> errores = TagDiagnostico.errores("%DB1.DBB0[0..9]:ÑBYTE");
        assertFalse(errores.isEmpty(), "la ñ no puede ser parte de una dirección");
        assertTrue(errores.stream().anyMatch(i -> TagIssue.CARACTERES_RAROS.equals(i.codigo())),
                "debe llegar el aviso de caracteres raros: " + errores);
    }

    @Test
    void unTagSimbolicoNoSeMarcaComoError() {
        assertTrue(algunoErrores("Control_Panel.Start_Button"));
        assertTrue(TagDiagnostico.avisos("Control_Panel.Start_Button").isEmpty(),
                "un símbolo no necesita además un aviso de tamaño");
    }

    @Test
    void unTipoDesconocidoSeAvisa() {
        assertTrue(algunoErrores("%DB1.DBB0[0..9]:RAW_BYTE_ARRAY_ALIAS"),
                "un tipo que no existe no debe dejar el área sin tipos");
        assertFalse(algunoErrores("%DB1.DBB0[0..9]:RAW_BYTE_ARRAY"),
                "pero el alias de BYTE sí se reconoce");
    }

    @Test
    void unaDireccionSinTamanoSeAvisaSinMarcarComoError() {
        List<TagIssue> errores = TagDiagnostico.errores("%DB1:0");
        assertTrue(errores.isEmpty(), "una dirección válida no se marca: " + errores);
        List<TagIssue> avisos = TagDiagnostico.avisos("%DB1:0");
        assertTrue(avisos.stream().anyMatch(i -> TagIssue.SIN_TAMANO.equals(i.codigo())),
                "debería avisar de que no se sabe el tamaño: " + avisos);
        assertTrue(avisos.stream().allMatch(
                        i -> TagIssue.Gravedad.AVISO.equals(i.gravedad())),
                "lo que no impide usar el tag es un aviso, no un error");
    }

    @Test
    void unBloqueSinDireccionSeAvisa() {
        List<TagIssue> vacio = TagDiagnostico.errores("%DB1[]");
        assertTrue(vacio.stream().anyMatch(i -> TagIssue.NO_ES_DIRECCION.equals(i.codigo())),
                "un bloque sin dirección debe avisar: " + vacio);

        List<TagIssue> rango = TagDiagnostico.errores("%DB1[0..9]");
        assertTrue(rango.stream().anyMatch(i -> TagIssue.NO_ES_DIRECCION.equals(i.codigo())),
                "el rango no sustituye a la dirección: " + rango);
    }

    @Test
    void unTagVacioNoDiceNada() {
        assertEquals(0, TagDiagnostico.errores("").size());
        assertEquals(0, TagDiagnostico.errores(null).size());
        assertEquals(0, TagDiagnostico.errores("   ").size());
        assertEquals(0, TagDiagnostico.avisos("").size());
    }

    @Test
    void elDiagnosticoNoDevuelvePresentacion() {
        // T3.8: la capa de modelo no debe emitir nada de HTML. El texto lo compone
        // quien lo pinta.
        for (String tag : new String[]{"%DB1[]", "%DB1.DBB0[0..9]:ÑBYTE", "-lkñl{l0'o",
                "%DB1.DBB0[.0.9]:BYTE", "%DB1:0", "%DB1.DBB0[0..9]:RAW_BYTE_ARRAY_ALIAS"}) {
            for (TagIssue issue : TagDiagnostico.errores(tag)) {
                assertFalse(issue.codigo().contains("<"),
                        "el código no debe traer HTML: " + issue);
                assertFalse(issue.args().stream().anyMatch(a -> a.contains("<br")),
                        "los argumentos no deben traer HTML: " + issue);
            }
            for (TagIssue issue : TagDiagnostico.avisos(tag)) {
                assertFalse(issue.codigo().contains("<"), "el código no debe traer HTML");
            }
        }
    }

    @Test
    void unNumeroDemasiadoGrandeSeAvisa() {
        for (String tag : new String[]{"%DB1.DBB0[99999999999]:BYTE",
                "%DB99999999999.DBB1", "%DB1.DBB0[0..99999999999]:BYTE",
                "%DB1.DBB0:STRING(99999999999)"}) {
            List<String> codigos = TagDiagnostico.errores(tag).stream()
                    .map(TagIssue::codigo).toList();
            assertTrue(codigos.contains(TagIssue.NUMERO_DEMASIADO_GRANDE),
                    "«" + tag + "» tiene un número que no cabe en un entero");
        }
    }

    @Test
    void losNumerosNormalesNoSeAvisan() {
        for (String tag : new String[]{"%DB231.DBB0[0..1848]:BYTE", "%DB1.DBB0:STRING(80)",
                "%I0.0:BOOL", "%DB9999.DBW20:INT"}) {
            List<String> codigos = TagDiagnostico.errores(tag).stream()
                    .map(TagIssue::codigo).toList();
            assertFalse(codigos.contains(TagIssue.NUMERO_DEMASIADO_GRANDE),
                    "«" + tag + "» no tiene ningún número demasiado grande");
        }
    }
}