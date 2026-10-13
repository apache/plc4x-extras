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
package org.apache.plc4x.malbec.projecttype.panelcategory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConvencionNombresTest {

    /** Modificadores, tipo simple y nombre. El tipo no admite punto ni parentesis. */
    private static final Pattern DECLARACION = Pattern.compile(
            "^(?:public|protected|private|static|final|abstract|synchronized|native|default|\\s)+"
                    + "[A-Za-z_]\\w*\\s+([A-Za-z_]\\w*)\\s*\\(");

    /** Metodos cuya nombre impone la plataforma o una interfaz ya normalizada. */
    private static final List<String> EXENTOS = List.of(
            // Swing / AWT
            "actionPerformed", "mouseEntered", "mouseExited", "mousePressed", "mouseReleased",
            "mouseDragged", "mouseMoved", "mouseWheelMoved", "focusGained", "focusLost",
            "insertUpdate", "removeUpdate", "changedUpdate", "insertString", "remove",
            "componentOpened", "componentClosed", "componentShowing", "componentHidden",
            "componentActivated", "componentDeactivated", "componentResized", "getPreferredSize",
            "setToolTipText", "getToolTipText", "getText", "setText", "isEditable",
            "addActionListener", "removeActionListener", "setEnabled", "isEnabled",
            "getSelectedIndex", "setSelectedIndex", "getSelectedItem", "setSelectedItem",
            "getItemCount", "addItem", "removeAllItems", "setEditable", "getItemAt",
            "doClick", "getClientProperty", "putClientProperty", "getTextFields",
            // NetBeans: Node, NodeList, FileChangeListener, Project, DataObject, Lookup
            "getActions", "createNodes", "keys", "node", "addChangeListener",
            "removeChangeListener", "addNotify", "removeNotify", "fileDataCreated",
            "fileDeleted", "fileRenamed", "fileFolderCreated", "fileChanged",
            "fileAttributeChanged", "stateChanged", "getName", "getDisplayName", "getIcon",
            "getOpenedIcon", "getProjectDirectory", "getProject", "getLookup", "getChildren",
            "getCookie", "getType", "createEditor", "createContextAwareInstance",
            "createLogicalView", "findPath", "isProject", "getVisualRepresentation",
            "getToolbarRepresentation", "getValue", "setValue", "getHelpCtx",
            "getComponent", "getSubprojects", "saveProject", "addPropertyChangeListener",
            "removePropertyChangeListener", "canCloseElement", "getUndoRedo", "getWidth",
            "setMultiViewCallback", "associateLookup", "getIconBase", "getActions",
            // Project / ProjectFactory / ProjectState
            "getProjectDirectory", "getLookup", "saveProject", "isProject",
            // ProjectStorage, CommConfigStorage, CommConfigRepository
            "getDevices", "findByUuid", "getComms", "upsertDevice", "upsertComms",
            "removeDevice", "save", "load", "open", "create", "close", "directoryName",
            "has", "config", "bootDbFolder", "setOnChanged", "setOnDeleted",
            "watchForChanges", "pauseNotifications", "resumeNotifications",
            "createRepository", "has", "directoryName",
            // Object
            "toString", "equals", "hashCode");

    @Test
    void ningunMetodoEmpiezaPorMayuscula() throws IOException {
        Recorrido recorrido = recorrer();

        assertTrue(recorrido.ficheros() > 50,
                "el recorrido solo ha visto " + recorrido.ficheros() + " ficheros; si el "
                        + "directorio base no es el del agregador, el test pasa sin comprobar nada");
        assertTrue(recorrido.metodos() > 150,
                "solo se han visto " + recorrido.metodos() + " metodos; el recorrido no esta "
                        + "llegando al codigo");

        assertTrue(recorrido.infracciones().isEmpty(),
                "T4.8: estos metodos deberian empezar en minuscula:\n  "
                        + String.join("\n  ", recorrido.infracciones()));
    }

    private record Recorrido(List<String> infracciones, int ficheros, int metodos) {
    }

    private static Recorrido recorrer() throws IOException {
        List<String> infracciones = new ArrayList<>();
        int ficheros = 0;
        int metodos = 0;
        for (Path modulo : modulosDelGate()) {
            Path raiz = modulo.resolve("src").resolve("main").resolve("java");
            if (!Files.isDirectory(raiz)) {
                continue;
            }
            try (Stream<Path> archivos = Files.walk(raiz)) {
                for (Path p : archivos.filter(x -> x.toString().endsWith(".java")).toList()) {
                    ficheros++;
                    List<String> lineas = Files.readAllLines(p);
                    String clase = p.getFileName().toString().replace(".java", "");
                    for (int i = 0; i < lineas.size(); i++) {
                        String limpia = lineas.get(i).trim();
                        if (limpia.startsWith("//") || limpia.startsWith("*")
                                || limpia.startsWith("/*")) {
                            continue;
                        }
                        Matcher m = DECLARACION.matcher(limpia);

                        if (!m.lookingAt()) {
                            continue;
                        }
                        String nombre = m.group(1);
                        String cabeza = limpia.substring(0, limpia.indexOf('(')).trim();

                        if (!cabeza.endsWith(nombre)) {
                            continue;
                        }
                        
                        if (cabeza.contains("record ") || cabeza.contains("enum ")
                                || cabeza.contains("interface ")) {
                            continue;
                        }
                        
                        if (nombre.equals(clase) || EXENTOS.contains(nombre)) {
                            continue;
                        }
                        metodos++;
                        if (Character.isUpperCase(nombre.charAt(0))) {
                            infracciones.add(p.getFileName() + ":" + (i + 1) + "  " + nombre);
                        }
                    }
                }
            }
        }
        return new Recorrido(infracciones, ficheros, metodos);
    }

    private static List<Path> modulosDelGate() {
        Path base = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        Path agregador = Files.isDirectory(base.resolve("commsHmi")) ? base : base.getParent();
        return List.of(agregador.resolve("commsHmi"), agregador.resolve("ConfigPhoebus"),
                agregador.resolve("HMIPanelSubprojectCategory"),
                agregador.resolve("HMIPanelSubproject"));
    }
}