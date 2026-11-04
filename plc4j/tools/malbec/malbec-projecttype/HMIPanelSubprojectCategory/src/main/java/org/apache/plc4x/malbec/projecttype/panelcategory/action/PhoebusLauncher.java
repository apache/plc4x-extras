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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PhoebusLauncher {

    private static final Logger LOGGER = Logger.getLogger(PhoebusLauncher.class.getName());

    static final String MEMENTO = "memento";
    static final String RESPALDO = "memento.malbec.bak";

    private static final String PLANTILLA =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<memento default_application=\"Display Editor\" last_opened_file=\"\""
            + " show_menu=\"false\" show_statusbar=\"false\" show_tabs=\"false\""
            + " show_toolbar=\"false\">\n"
            + "    <DockStage_MAIN height=\"1000.0\" maximized=\"true\" width=\"1296.0\""
            + " x=\"-8.0\" y=\"-8.0\">\n"
            + "        <pane selected=\"0\">\n"
            + "            <DockItem_%ID% application=\"display_editor\" user_name=\"%USER%\""
            + " input_uri=\"%URI%\"/>\n"
            + "        </pane>\n"
            + "    </DockStage_MAIN>\n"
            + "</memento>\n";

    private PhoebusLauncher() {
    }

    static Path rutaMemento() {
        return rutaMemento(Paths.get(System.getProperty("user.home", ".")));
    }

    static Path rutaMemento(Path home) {
        return home.resolve(".phoebus").resolve(MEMENTO);
    }

    static Path backupPath(Path home) {
        return home.resolve(".phoebus").resolve(RESPALDO);
    }

    static ProcessBuilder command(String rutaPhoebus) {
        ProcessBuilder pb = new ProcessBuilder(rutaPhoebus, "-nosplash", "-edit", "");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb;
    }

    static String contentFor(Path bob, String usuario) {
        return PLANTILLA
                .replace("%ID%", "DockItem_"
                        + UUID.randomUUID().toString().replace("-", "_"))
                .replace("%USER%", escapar(usuario))
                .replace("%URI%", escapar(bob.toUri().toString()));
    }

    static void writeMemento(Path memento, Path respaldo, String content) throws IOException {
        Files.createDirectories(memento.getParent());
        if (Files.exists(memento)) {
            if (!Files.exists(respaldo)) {
                Files.copy(memento, respaldo);
            }
        } else {
            Files.deleteIfExists(respaldo);
        }
        Files.write(memento, content.getBytes(StandardCharsets.UTF_8));
    }

    static void restoreIfStillOurs(Path memento, Path respaldo, String escrito) {
        try {
            if (!Files.exists(respaldo)) {
                return;
            }
            String actual = Files.readString(memento, StandardCharsets.UTF_8);
            if (!actual.equals(escrito)) {
                return;
            }
            Files.copy(respaldo, memento, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(respaldo);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "No se pudo restaurar el memento de Phoebus", ex);
        }
    }

    static void waitAndRestore(Process proceso, Path memento, Path respaldo, String escrito) {
        Thread hilo = new Thread(() -> {
            try {
                proceso.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            restoreIfStillOurs(memento, respaldo, escrito);
        }, "malbec-restaura-memento");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static String escapar(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}