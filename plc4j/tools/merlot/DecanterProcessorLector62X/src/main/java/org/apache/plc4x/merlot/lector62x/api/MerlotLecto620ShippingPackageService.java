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
package org.apache.plc4x.merlot.lector62x.api;

import org.osgi.service.event.EventHandler;

public interface MerlotLecto620ShippingPackageService{

  
    /**
     *
     * @param jsonReadServer Respuesta del servidor (POST)
     *
     * Caso 1: Servidor recibió los datos satisfactoriamente, respuesta=
     * {"resultado":true, "mensaje": "Guía registrada"} Código HTTP: 201 Caso 2:
     * Servidor recibió los datos pero no son correctos, respuesta=
     * {"resultado":false, "mensaje": "Datos inválidos"} Código HTTP: 400
     */
    boolean confirmDataReceiptFromExternalServer(boolean jsonReadServer)throws Exception;

    /**(POST)
     *
     * @param codGuide Número de guia recibidio por el servidor, es enviado a
     * merlot para modificar el estado del registro en la base de datos (ya fue
     * enviado y procesado por el servidor). Recibe el numero de guia, lo busca
     * en su base de datos, y válida que realmente exista.
     *
     * @return Si está presente, devuelve true, si no, false.
     */
    boolean updateGuideStatusInDataRecord(String codGuide) throws Exception;

}
