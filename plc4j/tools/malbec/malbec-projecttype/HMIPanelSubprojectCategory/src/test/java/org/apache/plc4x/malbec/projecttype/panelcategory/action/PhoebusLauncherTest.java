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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoebusLauncherTest {

    @Test
    void laSalidaDelProcesoSeDescarta(@TempDir Path home) {
        ProcessBuilder pb = PhoebusLauncher.comando("phoebus.sh");

        assertEquals(ProcessBuilder.Redirect.DISCARD, pb.redirectOutput(),
                "si nadie lee la salida, el proceso se queda bloqueado al llenarse el pipe");
        assertTrue(pb.redirectErrorStream());
    }

    @Test
    void elComandoMantieneLosArgumentosQueYaFuncionaban(@TempDir Path home) {
        List<String> comando = PhoebusLauncher.comando("phoebus.sh").command();

        assertEquals("phoebus.sh", comando.get(0));
        assertTrue(comando.contains("-nosplash"));
        assertTrue(comando.contains("-edit"));
    }

    @Test
    void elMementoDelUsuarioSeGuardaAntesDeTocarlo(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);
        Files.createDirectories(memento.getParent());
        Files.writeString(memento, "contenido del usuario");

        PhoebusLauncher.escribirMemento(memento, respaldo, "contenido de malbec");

        assertEquals("contenido de malbec", Files.readString(memento));
        assertEquals("contenido del usuario", Files.readString(respaldo),
                "el memento del usuario tiene que quedar a salvo antes de sobrescribirlo");
    }

    @Test
    void alTerminarPhoebusSeRestauraElMementoDelUsuario(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);
        Files.createDirectories(memento.getParent());
        Files.writeString(memento, "contenido del usuario");

        String escrito = PhoebusLauncher.contenidoPara(home.resolve("CELDA1.bob"), "user");
        PhoebusLauncher.escribirMemento(memento, respaldo, escrito);
        PhoebusLauncher.restaurarSiSigueSuyo(memento, respaldo, escrito);

        assertEquals("contenido del usuario", Files.readString(memento));
        assertFalse(Files.exists(respaldo), "ya restaurado, el respaldo sobra");
    }

    @Test
    void siPhoebusHaCambiadoElMementoNoSeToca(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);
        Files.createDirectories(memento.getParent());
        Files.writeString(memento, "contenido del usuario");

        String escrito = PhoebusLauncher.contenidoPara(home.resolve("CELDA1.bob"), "user");
        PhoebusLauncher.escribirMemento(memento, respaldo, escrito);
        Files.writeString(memento, "lo que haya escrito Phoebus al cerrar su sesion");

        PhoebusLauncher.restaurarSiSigueSuyo(memento, respaldo, escrito);

        assertEquals("lo que haya escrito Phoebus al cerrar su sesion",
                Files.readString(memento),
                "no hay que pisar lo que Phoebus haya guardado al salir");
    }

    @Test
    void sinMementoPrevioNoSeDejaRespaldo(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);
        Files.createDirectories(respaldo.getParent());
        Files.writeString(respaldo, "respaldo viejo de una sesion anterior");

        PhoebusLauncher.escribirMemento(memento, respaldo, "contenido de malbec");

        assertFalse(Files.exists(respaldo),
                "sin memento previo no hay nada que restaurar, y un respaldo viejo confundiria");
        assertEquals("contenido de malbec", Files.readString(memento));
    }

    @Test
    void elMementoApuntaAlBobConElUriEscapado(@TempDir Path home) throws Exception {
        Path bob = home.resolve("CELDA & 1.bob");

        String contenido = PhoebusLauncher.contenidoPara(bob, "user");

        assertTrue(contenido.contains("CELDA%20&amp;%201.bob"),
                "el ampersand del nombre debe ir escapado en el XML: " + contenido);
        assertFalse(contenido.contains("%ID%"));
        assertFalse(contenido.contains("%URI%"));
    }

    @Test
    void elMementoVaUtf8(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);

        PhoebusLauncher.escribirMemento(memento, respaldo, "descripción con ñ");

        assertEquals("descripción con ñ",
                new String(Files.readAllBytes(memento), StandardCharsets.UTF_8));
    }

    @Test
    void laAccionNoEscribeElMementoPorSuCuenta() throws Exception {
        java.nio.file.Path fuente = java.nio.file.Path.of("src", "main", "java",
                "org", "apache", "plc4x", "malbec", "projecttype", "panelcategory",
                "action", "HMICategoryOpenPhoebusAction.java");
        String codigo = Files.readString(fuente, StandardCharsets.UTF_8);

        assertFalse(codigo.contains("Files.write"),
                "escribir el memento es cosa de PhoebusLauncher, con copia de seguridad");
        assertFalse(codigo.contains("new ProcessBuilder("),
                "el comando lo arma PhoebusLauncher, que descarta la salida");
        assertTrue(codigo.contains("PhoebusLauncher.escribirMemento"));
        assertTrue(codigo.contains("PhoebusLauncher.esperarYRestaurar"),
                "sin restaurar despues, el memento del usuario se queda pisado");
    }

    @Test
    void abrirUnSegundoBobNoDestruyeElMementoOriginal(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);
        Files.createDirectories(memento.getParent());
        Files.writeString(memento, "memento original del usuario");

        PhoebusLauncher.escribirMemento(memento, respaldo, "contenido del primer bob");
        assertEquals("memento original del usuario", Files.readString(respaldo));

        PhoebusLauncher.escribirMemento(memento, respaldo, "contenido del segundo bob");

        assertEquals("memento original del usuario", Files.readString(respaldo),
                "el segundo lanzamiento no puede pisar el respaldo con nuestro propio memento");

        PhoebusLauncher.restaurarSiSigueSuyo(memento, respaldo, "contenido del segundo bob");
        assertEquals("memento original del usuario", Files.readString(memento),
                "al cerrar, vuelve el memento que tenia el usuario");
    }

    @Test
    void trasRestaurarSePuedeVolverABackupar(@TempDir Path home) throws Exception {
        Path memento = PhoebusLauncher.rutaMemento(home);
        Path respaldo = PhoebusLauncher.rutaRespaldo(home);
        Files.createDirectories(memento.getParent());
        Files.writeString(memento, "original");

        PhoebusLauncher.escribirMemento(memento, respaldo, "nuestro");
        PhoebusLauncher.restaurarSiSigueSuyo(memento, respaldo, "nuestro");
        assertFalse(Files.exists(respaldo));

        Files.writeString(memento, "otro original");
        PhoebusLauncher.escribirMemento(memento, respaldo, "nuestro otra vez");

        assertEquals("otro original", Files.readString(respaldo),
                "el respaldo tiene que volver a estar disponible para la siguiente sesion");
    }
}