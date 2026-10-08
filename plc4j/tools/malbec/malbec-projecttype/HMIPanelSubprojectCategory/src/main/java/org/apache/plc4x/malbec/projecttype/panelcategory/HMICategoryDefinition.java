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

/**
 * Las categorias de proyecto y los ficheros que contiene cada una.
 *
 * <p>Antes cada {@code NodeFactory} repetia su propia regla: la carpeta que le corresponde,
 * la extension que muestra y el fichero que esconde. Con nueve factories identicas en esa
 * parte, la regla vivia en nueve sitios y cambiar un nombre de fichero obligaba a editar nueve
 * clases de la plataforma. Aqui queda escrita una vez, y las factories la consultan.
 *
 * <p>Los metodos {@link #coincideConCarpeta} y {@link #acepta} son logica pura: reciben
 * cadenas y un booleano, no un {@code FileObject}. Eso permite probarlos sin el runtime de
 * NetBeans, que es justo lo que faltaba para poder tocar estas clases con confianza.
 *
 * <p>Las categorias sin extension propia ({@link #IMAGE} y {@link #COMUNICATION}) no
 * enumeran ficheros de esa clase: las muestran factories propias.
 */
public enum HMICategoryDefinition {

    IMAGE("image", "Image", null, null,
            CategoryIcons.DEFAULT),
    COMUNICATION("communication", "Comunicacion", null, null,
            CategoryIcons.COMMUNICATION),
    NOTICE_MANAGEMENT("warning-management", "Gestion de Avisos", "wmg", "base.wmg",
            CategoryIcons.DEFAULT),
    RECIPE("recipe", "Recetas", "rcp", "base.rcp",
            CategoryIcons.DEFAULT),
    HISTORIAL("history", "Historial", "record", "record.record",
            CategoryIcons.DEFAULT),
    SCRIPTS("scripts", "Scripts", "scp", "base.scp",
            CategoryIcons.DEFAULT),
    REPORT("report", "Informes", "rpt", "base.rpt",
            CategoryIcons.DEFAULT),
    TEXT_GRAPHIC("text-and-list-charts", "Texto y Lista de graficos", "tlc", "base.tlc",
            CategoryIcons.DEFAULT),
    ADMIN_USER("runtime-user-management", "Administracion de Usuarios runtime", "rtum", "base.rtum",
            CategoryIcons.DEFAULT),
    CONFIG_PANEL("operator-panel-config", "Configuracion de panel de operador", "oppc", "base.oppc",
            CategoryIcons.DEFAULT);

    private final String id;
    private final String displayName;
    private final String fileExtension;
    private final String hiddenFile;
    private final String iconPath;

    HMICategoryDefinition(String id, String displayName, String fileExtension, String hiddenFile,
            String iconPath) {
        this.id = id;
        this.displayName = displayName;
        this.fileExtension = fileExtension;
        this.hiddenFile = hiddenFile;
        this.iconPath = iconPath;
    }

    /** Identificador estable, para marcadores y para no depender del texto visible. */
    public String getId() {
        return id;
    }

    /** Nombre de la carpeta, que es tambien lo que se muestra. */
    public String getDisplayName() {
        return displayName;
    }

    /** Extension de los ficheros que esta categoria muestra, o null si no aplica. */
    public String getFileExtension() {
        return fileExtension;
    }

    /** Fichero de esa extension que no se muestra, o null si no aplica. */
    public String getHiddenFile() {
        return hiddenFile;
    }

    public String getIconPath() {
        return iconPath;
    }

    /** Si esta categoria corresponde a la carpeta con ese nombre. */
    public boolean coincideConCarpeta(String nombreCarpeta) {
        return nombreCarpeta != null && displayName.equalsIgnoreCase(nombreCarpeta.trim());
    }

    /**
     * Si un fichero de esta categoria debe aparecer en el arbol.
     *
     * <p>Reproduce la regla que repetian las factories: no es una carpeta, su extension es
     * la de la categoria y no es el fichero oculto. Se comparan sin distinguir mayusculas,
     * como se hacia antes.
     *
     * @param nombreExt nombre del fichero con su extension, tal como lo da el filesystem
     * @param extension extension del fichero
     * @param esCarpeta si el fichero es en realidad un directorio
     */
    public boolean acepta(String nombreExt, String extension, boolean esCarpeta) {
        if (esCarpeta || fileExtension == null || extension == null) {
            return false;
        }
        if (!fileExtension.equalsIgnoreCase(extension)) {
            return false;
        }
        return hiddenFile == null || !hiddenFile.equalsIgnoreCase(nombreExt);
    }
}