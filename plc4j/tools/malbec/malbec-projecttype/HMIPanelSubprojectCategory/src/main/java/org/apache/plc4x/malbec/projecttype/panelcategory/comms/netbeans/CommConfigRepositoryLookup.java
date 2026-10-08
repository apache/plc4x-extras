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
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepositoryProvider;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.openide.util.Lookup;

/**
 * Localiza el {@link CommConfigRepositoryProvider} registrado en el {@code Lookup} de NetBeans.
 *
 * <p>Sustituye a {@code CommConfigServices}, que era una clase anotada como servicio de si
 * misma a la que nadie buscaba: sus consumidores la instanciaban con {@code new}, y lo unico
 * que hacia por dentro era esta misma busqueda. Era una indireccion que no ocultaba nada.
 *
 * <p>El Lookup si se conserva, y aqui se busca de verdad. Es el punto de extension para un
 * segundo formato de repositorio, y quitarlo obligaria a
 * {@link HMICommunicationModel} a construir directamente
 * {@code PlcOpenCommConfigRepositoryImpl}, que es un acoplamiento peor que el actual.
 *
 * <p>La busqueda ocurre aqui y no en {@code commsHmi} porque {@code Lookup} es una clase de
 * NetBeans y {@code commsHmi} se compila sin ella.
 */
public final class CommConfigRepositoryLookup {

    private CommConfigRepositoryLookup() {
    }

    /**
     * Repositorio del primer proveedor registrado.
     *
     * @throws IllegalStateException si no hay ningun proveedor en el {@code Lookup}
     */
    public static CommConfigRepository createRepository(CommConfigStorage storage) {
        for (CommConfigRepositoryProvider provider
                : Lookup.getDefault().lookupAll(CommConfigRepositoryProvider.class)) {
            return provider.createRepository(storage);
        }
        throw new IllegalStateException(
                "No hay ningun CommConfigRepositoryProvider registrado en el Lookup");
    }
}