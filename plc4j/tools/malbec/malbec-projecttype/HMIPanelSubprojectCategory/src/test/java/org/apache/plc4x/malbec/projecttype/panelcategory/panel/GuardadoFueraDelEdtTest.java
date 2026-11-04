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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardadoFueraDelEdtTest {

    private static String sourceOf(String modulo, String clase) throws Exception {
        return Files.readString(Path.of("..", modulo, "src", "main", "java",
                "org", "apache", "plc4x", "malbec", "projecttype", "panelcategory",
                "panel", clase + ".java"));
    }

    @Test
    void guardarNoTocaElDiscoEnElHiloDeSwing() throws Exception {
        String codigo = sourceOf("HMIPanelSubprojectCategory", "CreateDeviceDialog");

        int post = codigo.indexOf("RequestProcessor.getDefault().post(");
        int save = codigo.indexOf("store.save()");
        int volver = codigo.indexOf("SwingUtilities.invokeLater(");

        assertTrue(post >= 0, "el guardado tiene que salir del EDT");
        assertTrue(save >= 0 && post < save,
                "store.save() tiene que ejecutarse despues de salir del EDT");
        assertTrue(volver > save,
                "los dialogos se enseñan al volver al EDT, no desde el hilo de fondo");
    }

    @Test
    void losBotonesSeBloqueanMientrasSeGuarda() throws Exception {
        String codigo = sourceOf("HMIPanelSubprojectCategory", "CreateDeviceDialog");

        assertTrue(codigo.contains("btnOk.setEnabled(false)"),
                "sin bloquear, se pueden pulsar OK dos veces y guardar en paralelo");
        assertTrue(codigo.contains("btnOk.setEnabled(true)"),
                "los botones tienen que volver a estar disponibles despues");
    }

    @Test
    void losListenersSeAvisanSiempreEnElHiloDeSwing() throws Exception {
        java.nio.file.Path fuente = java.nio.file.Path.of("..", "HMIPanelSubprojectCategory",
                "src", "main", "java", "org", "apache", "plc4x", "malbec", "projecttype",
                "panelcategory", "comms", "netbeans", "HMICommunicationModel.java");
        String codigo = Files.readString(fuente);

        assertFalse(codigo.contains("cs.fireChange();")
                        && codigo.indexOf("cs.fireChange();") < codigo.indexOf("avisarCambio()"),
                "el aviso tiene que pasar por el reenvio");
        assertTrue(codigo.contains("SwingUtilities.invokeLater(cs::fireChange)"),
                "llamar desde el hilo del watchdog repinta el arbol desde ese hilo");
        assertTrue(codigo.contains("isEventDispatchThread()"),
                "si ya estamos en el EDT, reenviar seria un retardo tonto");
    }
}