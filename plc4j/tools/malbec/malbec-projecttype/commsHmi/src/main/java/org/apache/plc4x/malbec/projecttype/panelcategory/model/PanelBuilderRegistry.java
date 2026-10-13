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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PanelBuilderRegistry {
    private static final Logger LOGGER = Logger.getLogger(PanelBuilderRegistry.class.getName());

    private static final String MARCA_SIEMENS = "siemens";
    private static final String MARCA_ALLEN_BRADLEY = "allen bradley";
    private static final String MARCA_MODBUS = "modbus";

    private final Map<String, Supplier<DeviceDynamicPanelBuilder>> porMarca;
    private final Supplier<DeviceDynamicPanelBuilder> porDefecto;

    private PanelBuilderRegistry(Map<String, Supplier<DeviceDynamicPanelBuilder>> porMarca,
            Supplier<DeviceDynamicPanelBuilder> porDefecto) {
        this.porMarca = Collections.unmodifiableMap(porMarca);
        this.porDefecto = porDefecto;
    }

    public static PanelBuilderRegistry porDefecto() {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> porMarca = new LinkedHashMap<>();
        porMarca.put(MARCA_SIEMENS, SiemensPanelBuilder::new);
        porMarca.put(MARCA_ALLEN_BRADLEY, AllenBradleyPanelBuilder::new);
        porMarca.put(MARCA_MODBUS, ModbusPanelBuilder::new);
        return new PanelBuilderRegistry(porMarca, SiemensPanelBuilder::new);
    }

    public static PanelBuilderRegistry de(Map<String, Supplier<DeviceDynamicPanelBuilder>> porMarca,
            Supplier<DeviceDynamicPanelBuilder> porDefecto) {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> copia = new LinkedHashMap<>();
        porMarca.forEach((marca, factory) -> copia.put(normalizar(marca), factory));
        return new PanelBuilderRegistry(copia, porDefecto);
    }

public DeviceDynamicPanelBuilder para(String marca) {
        String clave = normalizar(marca);
        Supplier<DeviceDynamicPanelBuilder> factory = porMarca.get(clave);
        if (factory != null) {
            return factory.get();
        }
        if (!clave.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "La marca ''{0}'' no tiene panel de conexion propio; se usa el de"
                            + " {1} por defecto. Las URLs saldran como si fuera de ese"
                            + " protocolo.",
                    new Object[]{marca, builderPorDefecto().getProtocol()});
        }
        return porDefecto.get();
    }

    public DeviceDynamicPanelBuilder builderPorDefecto() {
        return porDefecto.get();
    }

    public Set<String> marcas() {
        return porMarca.keySet();
    }

    public boolean registra(String marca) {
        return porMarca.containsKey(normalizar(marca));
    }

    private static String normalizar(String marca) {
        return marca == null ? "" : marca.trim().toLowerCase(Locale.ROOT);
    }
}
