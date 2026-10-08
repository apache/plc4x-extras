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
package org.apache.plc4x.malbec.projecttype.panel.plant;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.plc4x.malbec.projecttype.panel.plant.ImportResult.Exito;
import org.apache.plc4x.malbec.projecttype.panel.plant.ImportResult.Fallo;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.AreaNode;
import org.apache.plc4x.malbec.projecttype.panelcategory.ProjectIds;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.plant.services.S88ProjectServices;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public final class PlantImporter {

    private static final Logger LOGGER = Logger.getLogger(PlantImporter.class.getName());

    private PlantImporter() {
    }

    private static final String INVALIDOS = "/\\:*?\"<>|";

    public static ImportResult importPlant(File archivo, FileObject panel) {
        S88Repository repositorio;
        try {
            repositorio = S88ProjectServices.createRepository(
                    extensionDe(archivo), new Storage(archivo));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "No hay repositorio para " + archivo.getName(), ex);
            return new Fallo(archivo, "el formato " + extensionDe(archivo) + " no está soportado.");
        }

        S88PlantModel modelo;
        try {
            modelo = repositorio.loadPlant();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "No se pudo interpretar " + archivo.getName(), ex);
            return new Fallo(archivo, describe(ex));
        }

        S88Element root = modelo == null ? null : modelo.getRoot();
        String areaId = root == null || root.getId() == null ? "" : root.getId().trim();
        if (areaId.isEmpty()) {
            return new Fallo(archivo, "el archivo no define un área de planta.");
        }

        File panelFile = FileUtil.toFile(panel);
        if (panelFile != null) {
            FileUtil.refreshFor(panelFile);
        }

        FileObject imageFo = panel.getFileObject("image");
        String problema = problemaDelDestino(imageFo);
        if (problema != null) {
            LOGGER.log(Level.WARNING, "No se importó {0}: {1}",
                    new Object[]{archivo.getName(), problema});
            return new Fallo(archivo, problema);
        }

        int fallidos = 0;

        String nombreInvalido = primerNombreInvalido(root);
        if (nombreInvalido != null) {
            LOGGER.log(Level.WARNING, "No se importó {0}: {1}",
                    new Object[]{archivo.getName(), nombreInvalido});
            return new Fallo(archivo, nombreInvalido);
        }

        List<FileObject> creados = new ArrayList<>();
        if (root.getChildren() != null) {
            for (S88Element child : root.getChildren()) {
                fallidos += reverseChildStack(child, imageFo, creados);
            }
        }
        if (fallidos > 0) {
            deshacer(creados);
            return new Fallo(archivo, fallidos + " nodo(s) del modelo no se pudieron crear;"
                    + " la importación está incompleta.");
        }

        try {
            File dir = FileUtil.toFile(panel);
            if (dir == null) {
                deshacer(creados);
                return new Fallo(archivo, "no se pudo escribir " + PlantModelXml.FILE_NAME
                        + " en el proyecto.");
            }
            AreaNode snapshot = PlantSnapshot.collectElement(root);
            File target = new File(dir, PlantModelXml.FILE_NAME);
            PlantModelXml.export(snapshot, target);
            FileUtil.refreshFor(target);
            LOGGER.log(Level.INFO, "Snapshot del modelo de planta en: {0}",
                    target.getAbsolutePath());
        } catch (IOException ex) {
            deshacer(creados);
            LOGGER.log(Level.WARNING, "No se pudo escribir " + PlantModelXml.FILE_NAME, ex);
            return new Fallo(archivo, describe(ex));
        }

        if (panelFile != null) {
            FileUtil.refreshAll();
            ProjectManager.getDefault().clearNonProjectCache();
        }
        return new Exito(archivo, areaId);
    }

    public static int reverseChildStack(S88Element root, FileObject baseDir) {
        return reverseChildStack(root, baseDir, new ArrayList<>());
    }

    public static int reverseChildStack(S88Element root, FileObject baseDir,
            List<FileObject> creados) {
        if (root == null || baseDir == null) {
            return 0;
        }

        Deque<S88Element> stack = new ArrayDeque<>();
        Map<S88Element, FileObject> dirMap = new HashMap<>();
        int fallidos = 0;

        stack.push(root);
        dirMap.put(root, baseDir);

        while (!stack.isEmpty()) {
            S88Element node = stack.pop();
            FileObject parentDir = dirMap.remove(node);

            if (parentDir == null) {
                continue;
            }

            try {

                String problema = problemaDeNombre(node.getId());
                if (problema != null) {
                    fallidos++;
                    LOGGER.log(Level.WARNING, "Nodo rechazado: {0}", problema);
                    continue;
                }
                FileObject currentDir = createFolder(node, parentDir, creados);
                createCfgFile(currentDir, creados);

                LOGGER.fine(() -> "Propiedades en " + node.getId() + ": " + node.getProperties());
                LOGGER.fine(() -> "check: " + node.isCheck());
                if (node.isCheck()) {
                    LOGGER.fine(() -> "Creando BOB para: " + node.getId());
                    createBobFile(node, currentDir, creados);
                }

                List<S88Element> children = node.getChildren();
                if (children != null) {
                    for (int i = children.size() - 1; i >= 0; i--) {
                        S88Element child = children.get(i);
                        dirMap.put(child, currentDir);
                        stack.push(child);
                    }
                }
            } catch (IOException ex) {
                fallidos++;
                LOGGER.log(Level.WARNING, "No se pudo crear el nodo '" + node.getId() + "'", ex);
            }
        }
        return fallidos;
    }

    public static String problemaDeNombre(String id) {
        if (id == null || id.isBlank()) {
            return "un nodo del modelo no tiene identificador";
        }
        String nombre = id.trim();
        if (nombre.isEmpty()) {
            return "un nodo del modelo tiene un identificador vacío";
        }

        if (nombre.contains("..")) {
            return "el identificador '" + nombre + "' contiene '..' y podría crear carpetas"
                    + " fuera del panel";
        }
        for (int i = 0; i < INVALIDOS.length(); i++) {
            char c = INVALIDOS.charAt(i);
            if (nombre.indexOf(c) >= 0) {
                return "el identificador '" + nombre + "' contiene el carácter '" + c
                        + "', que no se puede usar en el nombre de una carpeta";
            }
        }
        if (Character.isISOControl(nombre.charAt(0))
                || Character.isISOControl(nombre.charAt(nombre.length() - 1))) {
            return "el identificador '" + nombre + "' empieza o acaba con un carácter de control";
        }
        return null;
    }

    public static String primerNombreInvalido(S88Element root) {
        Deque<S88Element> pendientes = new ArrayDeque<>();
        if (root != null) {
            pendientes.push(root);
        }
        while (!pendientes.isEmpty()) {
            S88Element node = pendientes.pop();
            String problema = problemaDeNombre(node.getId());
            if (problema != null) {
                return problema;
            }
            List<S88Element> children = node.getChildren();
            if (children != null) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    S88Element child = children.get(i);
                    if (child != null) {
                        pendientes.push(child);
                    }
                }
            }
        }
        return null;
    }

    public static String problemaDelDestino(FileObject imageFo) {
        if (imageFo == null) {
            return "el panel no tiene la carpeta 'image', que es donde se crea la estructura;"
                    + " no se ha creado nada.";
        }
        if (!imageFo.isFolder()) {
            return "'image' existe pero no es una carpeta; no se ha creado nada.";
        }
        return null;
    }

    static String extensionDe(File archivo) {
        String nombre = archivo.getName();
        int punto = nombre.lastIndexOf('.');
        return punto < 0 || punto == nombre.length() - 1
                ? "" : nombre.substring(punto + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String describe(Throwable ex) {
        String mensaje = ex.getMessage();
        return mensaje == null || mensaje.isBlank() ? ex.getClass().getSimpleName() : mensaje;
    }

    private static FileObject createFolder(S88Element node, FileObject parentDir,
            List<FileObject> creados) throws IOException {
        FileObject folder = parentDir.getFileObject(node.getId());
        if (folder != null) {
            return folder;
        }
        FileObject nuevo = parentDir.createFolder(node.getId());
        creados.add(nuevo);
        return nuevo;
    }

    private static void createCfgFile(FileObject folder, List<FileObject> creados) throws IOException {
        if (folder.getFileObject(ProjectIds.CATEGORY_MARKER_FILE) == null) {
            creados.add(folder.createData(ProjectIds.CATEGORY_MARKER_FILE));
        }
    }

    private static void createBobFile(S88Element node, FileObject folder,
            List<FileObject> creados) throws IOException {
        if (folder.getFileObject(node.getId() + ".bob") == null) {
            creados.add(folder.createData(node.getId(), "bob"));
        }
    }

    /**
     * Borra lo que se creo, del ultimo al primero.
     *
     * <p>Al reves porque borrar una carpeta borra recursivamente lo que tenga dentro, y
     * si se borrara antes el padre, los hijos ya no estarian y su borrado fallaria.
     *
     * <p>Solo se deshace lo que esta importacion ha creado. Lo que ya estaba en el panel
     * se respeta: puede ser de una importacion anterior.
     */
    static void deshacer(List<FileObject> creados) {
        for (int i = creados.size() - 1; i >= 0; i--) {
            FileObject fo = creados.get(i);
            try {
                if (fo.isValid()) {
                    fo.delete();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "No se pudo deshacer " + fo.getName(), ex);
            }
        }
        creados.clear();
    }

    private record Storage(File file) implements S88Storage {

        @Override
        public InputStream openInput() throws IOException, FileNotFoundException {
            return new FileInputStream(file);
        }

        @Override
        public OutputStream openOutput() throws IOException {
            return new FileOutputStream(file);
        }
    }
}
