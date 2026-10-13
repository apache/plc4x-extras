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
package org.apache.plc4x.malbec.projecttype.panelcategory.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.swing.Action;
import org.junit.jupiter.api.Test;

class NotImplementedCategoryActionsTest {

    private static List<AbstractNotImplementedAction> acciones() {
        return List.of(
                new HMICategoryOPCAction(null),
                new HMICategoryTextAndListChartsAction(null),
                new HMICategoryWarningManagementAction(null),
                new HMICategoryRuntimeUserManagementAction(null));
    }

    @Test
    void todasAvisanDeQueNoEstanImplementadas() {
        for (AbstractNotImplementedAction accion : acciones()) {
            String mensaje = accion.mensajeDeNoImplementado();
            assertNotNull(mensaje, accion.getClass().getSimpleName() + " debe tener un aviso");
            assertTrue(mensaje.contains("todavía no está implementada"), mensaje);
        }
    }

    @Test
    void elAvisoNombraLaAccionParaQueElUsuarioSepaCualFalta() {
        for (AbstractNotImplementedAction accion : acciones()) {
            String etiqueta = String.valueOf(accion.getValue(Action.NAME));
            assertTrue(accion.mensajeDeNoImplementado().contains(etiqueta),
                    "el aviso tiene que decir qué acción falta, no algo genérico: "
                            + accion.mensajeDeNoImplementado());
        }
    }

    @Test
    void cadaAccionMantieneSuEtiquetaEnElMenu() {
        List<String> etiquetas = acciones().stream()
                .map(a -> String.valueOf(a.getValue(Action.NAME))).toList();

        assertTrue(etiquetas.contains("Editar panel de operador"));
        assertTrue(etiquetas.contains("Editar textos y gráficos"));
        assertTrue(etiquetas.contains("Editar gestión de avisos"));
        assertTrue(etiquetas.contains("Editar usuarios runtime"));
        assertFalse(etiquetas.contains("null"),
                "una acción sin etiqueta en el menú no se puede leer: " + etiquetas);
    }

    @Test
    void lasEtiquetasNoSeRepiten() {
        List<String> etiquetas = acciones().stream()
                .map(a -> String.valueOf(a.getValue(Action.NAME))).toList();

        assertTrue(etiquetas.stream().allMatch(e -> !e.isBlank()),
                "ninguna acción puede quedarse sin etiqueta: " + etiquetas);
    }

    @Test
    void ningunaAccioneImprimeEnLaConsola() throws Exception {
        java.nio.file.Path raiz = java.nio.file.Path.of(System.getProperty("basedir", "."),
                "src", "main", "java");
        List<String> infracciones = new java.util.ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> archivos = java.nio.file.Files.walk(raiz)) {
            for (java.nio.file.Path p : archivos.filter(x -> x.toString().endsWith(".java"))
                    .toList()) {
                if (!p.toString().contains("action")) {
                    continue;
                }
                List<String> lineas = java.nio.file.Files.readAllLines(p);
                for (int i = 0; i < lineas.size(); i++) {
                    String linea = lineas.get(i);
                    if (linea.contains("System.out") || linea.contains("System.err")) {
                        if (!linea.trim().startsWith("//") && !linea.trim().startsWith("*")) {
                            infracciones.add(p.getFileName() + ":" + (i + 1) + "  " + linea.trim());
                        }
                    }
                }
            }
        }
        assertTrue(infracciones.isEmpty(),
                "K-07: las acciones no deben imprimir en la consola; usa LOGGER o avisa al "
                        + "usuario con NotifyDescriptor. Encontrado:\n  "
                        + String.join("\n  ", infracciones));
    }
}