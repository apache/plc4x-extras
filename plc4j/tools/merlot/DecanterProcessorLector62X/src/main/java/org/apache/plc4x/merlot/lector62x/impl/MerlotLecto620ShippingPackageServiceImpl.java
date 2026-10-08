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
package org.apache.plc4x.merlot.lector62x.impl;

import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageRepository;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageService;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLecto620ShippingPackageServiceImpl implements MerlotLecto620ShippingPackageService {

    private final static Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageServiceImpl.class);
    private final MerlotLecto620ShippingPackageRepository repository;

    public MerlotLecto620ShippingPackageServiceImpl(MerlotLecto620ShippingPackageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean confirmDataReceiptFromExternalServer(MerlotLector620ShippingPackageResponse jsonReadServer) throws Exception {

        if (jsonReadServer == null) {
            throw new Exception("Los campos del json no pueden estar vacios");
        }
        
        if (jsonReadServer.getResultado()) {
            return true;
        } else {
            return false;
        }

    }

    @Override
    public boolean updateGuideStatusInDataRecord(String codGuide) throws Exception {

        if (this.repository.checkShippingGuide(codGuide)) {
            return (this.repository.confirmWithShippingGuide(codGuide));
        } else {
            //TODO: Excepcion personalizada
            throw new Exception("No existe el registro con ese codigo de guia en la base de datos");
        }
    }

}
