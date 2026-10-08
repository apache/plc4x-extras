/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.lector62x.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageService;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLecto620ShippingPackageServletConfirmDataImpl extends HttpServlet {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageServletConfirmDataImpl.class);
    private final MerlotLecto620ShippingPackageService service;
    private ObjectMapper mapper;

    public MerlotLecto620ShippingPackageServletConfirmDataImpl(MerlotLecto620ShippingPackageService service) {
        this.service = service;
        this.mapper = new ObjectMapper();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOGGER.info("Recibiendo mensaje de confirmación del servidor tras enviarle los datos");
        try {
            // 1. Leer el body de la petición
            String body = req.getReader().lines().collect(java.util.stream.Collectors.joining(System.lineSeparator()));

            /* 2. Deserializar con Jackson (o el ObjectMapper que uses). Lanza excepcion si al leer las propiedades encuentra
            nombres que no coiniciden o menos o mas propiedades
             */
            MerlotLector620ShippingPackageResponse jsonReadServer = mapper.readValue(body, MerlotLector620ShippingPackageResponse.class);

            // 3. Llamar al servicio
            this.service.confirmDataReceiptFromExternalServer(jsonReadServer);

            // 4. Responder OK
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } catch (Exception e) {
            LOGGER.error("Datos invalidos {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } finally {
            LOGGER.info("Finaliza la solicitud con exito");
        }

    }

}
