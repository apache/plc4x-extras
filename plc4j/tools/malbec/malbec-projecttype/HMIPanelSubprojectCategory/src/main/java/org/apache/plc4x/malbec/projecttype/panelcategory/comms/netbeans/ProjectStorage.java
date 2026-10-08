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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans;

import java.io.File;
import java.io.IOException;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;

/**
 * Acceso al almacenamiento de un proyecto, aislado de NetBeans.
 *
 * <p>Existe por el mismo motivo que {@link CommConfigLoader}: sin esta costura,
 * {@code HMICommunicationModel} no se puede construir en un test. Necesita un
 * {@code Project}, y en el build de pruebas no hay ningun {@code FileSystem}
 * registrado, de modo que {@code FileUtil.toFileObject(File)} devuelve {@code null}
 * para cualquier directorio real. Levantar el arnes completo de NetBeans para
 * comprobar la guarda de {@code save()} no compensa.
 *
 * <p>Con esta interfaz, la unica implementacion que depende de NetBeans es
 * {@link NetBeansProjectStorage}. Las pruebas usan una implementacion en memoria.
 *
 * <p>Ningun metodo debe propagar el error de una lectura fallida por el camino
 * feliz: el modelo decide que hacer con el, y esa decision es justamente lo que hay
 * que poder verificar (H-01).
 */
interface ProjectStorage {

    /** Nombre del directorio del proyecto, para decidir si es un proyecto de comunicaciones. */
    String directoryName();

    /** Si el proyecto contiene el fichero indicado. */
    boolean has(String fileName);

    /**
     * Almacenamiento de {@code comunicacion.xml}.
     *
     * @return el almacenamiento, o {@code null} si el fichero todavia no existe
     */
    CommConfigStorage config();

    /**
     * Crea {@code comunicacion.xml} si falta y devuelve su almacenamiento.
     *
     * @throws IOException si el fichero no se puede crear
     */
    CommConfigStorage createConfig() throws IOException;

    /** Carpeta donde vive {@code boot.db}, o {@code null} si no se puede resolver. */
    File bootDbFolder();

    /**
     * Registra el callback que se invoca cuando cambia el archivo de configuracion
     * por fuera, por ejemplo desde el editor o desde otra herramienta.
     */
    void setOnChanged(Runnable onChanged);

    /**
     * Registra el callback que se invoca cuando se borra el archivo de
     * configuracion. No es lo mismo que un cambio: no hay nada que recargar, hay
     * que olvidar el modelo.
     */
    void setOnDeleted(Runnable onDeleted);

    /**
     * Engancha las notificaciones al archivo de configuracion actual. Se llama en
     * cada recarga porque el archivo puede haberse creado o sustituido.
     */
    void watchForChanges();

    /**
     * Silencia las notificaciones. Necesario al escribir desde dentro, porque el
     * evento de cambio provocaria una recarga sobre una escritura a medias.
     */
    void pauseNotifications();

    /** Reanuda las notificaciones tras {@link #pauseNotifications()}. */
    void resumeNotifications();
}
