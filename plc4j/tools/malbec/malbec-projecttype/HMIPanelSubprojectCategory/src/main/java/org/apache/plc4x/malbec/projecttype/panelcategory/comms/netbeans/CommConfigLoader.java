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

import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;

/**
 * Politica de carga de la configuracion de comunicaciones, aislada del entorno de
 * NetBeans (H-01).
 *
 * <p>Antes esta logica vivia dentro de {@code HMICommunicationModel.reload()} y no
 * era testeable: {@code reload()} necesita un {@code Project}, y en el build de
 * pruebas no hay ningun {@code FileSystem} registrado, de modo que
 * {@code FileUtil.toFileObject(File)} devuelve {@code null} para cualquier
 * directorio real. Levantar el arnes de NetBeans para cubrir unas pocas lineas
 * no compensa, asi que la politica se extrae aqui y se inyecta el repositorio.
 *
 * <p>Contrato: un fallo de lectura se propaga. No se devuelve nunca una
 * configuracion vacia en lugar de la que hay en el fichero. Antes el error se
 * tragaba con un {@code catch (Throwable)} y el usuario veia «cero dispositivos»
 * sin ninguna pista, con lo que un guardado posterior podia escribir encima un
 * modelo que nunca se habia leido.
 */
final class CommConfigLoader {

    /**
     * Resultado de una carga correcta.
     *
     * @param config         la configuracion leida, ya con los UUID reparados
     * @param uuidReparados  si hizo falta rellenar algun UUID ausente, para saber
     *                       si el fichero debe reescribirse
     */
    record Result(CommunicationsConfig config, boolean uuidReparados) {
    }

    private final CommConfigRepository repository;

    CommConfigLoader(CommConfigRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("hace falta un repositorio de configuración");
        }
        this.repository = repository;
    }

    /**
     * Lee la configuracion y repara los UUID ausentes.
     *
     * @throws Exception si la lectura falla. El error se propaga a proposito: quien
     *                   llama decide como avisar, y el modelo nunca queda con una
     *                   configuracion vacia por defecto.
     */
    Result load() throws Exception {
        CommunicationsConfig config = repository.load();
        if (config == null) {
            throw new IllegalStateException(
                    "el repositorio devolvió una configuración nula en lugar de una vacía");
        }
        boolean reparados = HMICommunicationModel.repararUuidsAusentes(config);
        return new Result(config, reparados);
    }
}
