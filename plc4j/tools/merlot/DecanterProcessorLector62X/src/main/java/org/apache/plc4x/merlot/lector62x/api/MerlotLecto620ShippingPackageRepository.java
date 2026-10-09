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

import java.util.List;

public interface MerlotLecto620ShippingPackageRepository {

   /** true si existe al menos un registro con ese código de guía. */
    boolean checkShippingGuide(String codGuide) throws Exception;

    /** Marca guiaconfirmada='true'. Devuelve true si actualizó alguna fila. */
    boolean confirmWithShippingGuide(String codGuide) throws Exception;

    /** Guías con guiaconfirmada='false' (máximo 100 por ciclo). */
    List<MerlotLector620ShippingPackageDTO> findUnconfirmed() throws Exception;
}
