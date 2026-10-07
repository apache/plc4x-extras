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
package org.apache.plc4x.merlot.lector62x.controller;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageRestController;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageService;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/guias")
public class MerlotLecto620ShippingPackageRestControllerImpl implements MerlotLecto620ShippingPackageRestController {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageRestController.class);
    private final MerlotLecto620ShippingPackageService service;

    public MerlotLecto620ShippingPackageRestControllerImpl(MerlotLecto620ShippingPackageService service) {
        this.service = service;
    }

    @Override
    @Path("/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    public void confirmDataReceiptFromExternalServer(MerlotLector620ShippingPackageResponse jsonReadServer) {
        try {
            this.service.confirmDataReceiptFromExternalServer(jsonReadServer.getResultado());
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }

    @Override
    @Path("/codguia")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response updateGuideStatusInDataRecord(GuideRequest guideRequest) {

        try {
            if (this.service.updateGuideStatusInDataRecord(guideRequest.getCodguia())) {
                return Response.status(Response.Status.CREATED)
                        .entity(new MerlotLector620ShippingPackageResponse(true, "Guia confirmada"))
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            } else {
                LOGGER.info("An error occurred while updating the record in the table with the guide code. {}", guideRequest.getCodguia());
            }

        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new MerlotLector620ShippingPackageResponse(false, "Datos inválidos"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        return null;
    }

    public class GuideRequest {

        private String codguia;

        public GuideRequest() {
        }

        public String getCodguia() {
            return codguia;
        }

        public void setCodguia(String codguia) {
            this.codguia = codguia;
        }
    }

}
