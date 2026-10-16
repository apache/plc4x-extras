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

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getHiddenFile() {
        return hiddenFile;
    }

    public String getIconPath() {
        return iconPath;
    }

    public boolean matchesFolder(String nombreCarpeta) {
        return nombreCarpeta != null && displayName.equalsIgnoreCase(nombreCarpeta.trim());
    }

    public String detectionKey() {
        return this == COMUNICATION ? ProjectIds.COMMUNICATION_CONFIG_FILE : hiddenFile;
    }

    public static java.util.Optional<HMICategoryDefinition> byFolderName(
            String nombreCarpeta) {
        for (HMICategoryDefinition category : values()) {
            if (category.matchesFolder(nombreCarpeta)) {
                return java.util.Optional.of(category);
            }
        }
        return java.util.Optional.empty();
    }

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