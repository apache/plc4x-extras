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

/**
 * Los metodos empiezan en minuscula (T4.8).
 *
 * <p>Un metodo que empieza por mayuscula parece un constructor a primera vista y se lee como
 * si creara algo. Antes de este refactor habia seis: {@code Duplicate},
 * {@code OpenNativeBob}, {@code OverwriteMemento}, {@code XmlEscaping},
 * {@code CompilePanel} y {@code reversechildstack}, este ultimo al reves, todo en minuscula.
 *
 * <p>Los metodos que impone la plataforma quedan exentos: sus nombres los decide Swing o
 * NetBeans, no este proyecto. Se listan de forma explicita para que anadir uno nuevo obligue
 * a decidir si es realmente nuestro.
 *
 * <p>El reconocimiento de "esto es un metodo" hizo falta tres intentos. La primera version
 * no encontraba ni un metodo en el codigo real y el test pasaba en falso; la segunda marcaba
 * cientos de falsos positivos, porque tomaba por nombre el tipo de una llamada a constructor.
 * La regla que funciona exige un modificador, un tipo simple y que el texto anterior al
 * parentesis termine en el nombre, lo que descarta a la vez los constructores y las
 * instanciaciones.
 */
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

        // Los cuatro modulos del gate tienen 92 ficheros de produccion. El minimo queda
        // muy por debajo para no romper al anadir o quitar ficheros, pero es suficiente
        // para que un recorrido que no encuentre nada falle en vez de pasar en falso.
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
                        // lookingAt y no matches: el patron acaba en el parentesis de
                        // apertura y la declaracion sigue con los parametros. Con matches
                        // solo casarian las declaraciones sin nada detras.
                        if (!m.lookingAt()) {
                            continue;
                        }
                        String nombre = m.group(1);
                        String cabeza = limpia.substring(0, limpia.indexOf('(')).trim();
                        // en "private final JTextField txt = new JTextField(" lo que
                        // precede al parentesis es el tipo, no un nombre de metodo
                        if (!cabeza.endsWith(nombre)) {
                            continue;
                        }
                        // una declaracion de record anidado: record GroupConfig(...)
                        if (cabeza.contains("record ") || cabeza.contains("enum ")
                                || cabeza.contains("interface ")) {
                            continue;
                        }
                        // un constructor no lleva tipo de retorno: el patron ya lo descarta
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

    /**
     * Los cuatro modulos del gate.
     *
     * <p>{@code basedir} apunta al modulo que ejecuta el test, asi que hay que subir un
     * nivel hasta el agregador. Sin esto el recorrido no encuentra nada y el test pasa en
     * falso: fue justo lo que pasó en el primer intento.
     */
    private static List<Path> modulosDelGate() {
        Path base = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        Path agregador = Files.isDirectory(base.resolve("commsHmi")) ? base : base.getParent();
        return List.of(agregador.resolve("commsHmi"), agregador.resolve("ConfigPhoebus"),
                agregador.resolve("HMIPanelSubprojectCategory"),
                agregador.resolve("HMIPanelSubproject"));
    }
}