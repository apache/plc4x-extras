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

    private final Map<String, Supplier<DeviceDynamicPanelBuilder>> byBrand;
    private final Supplier<DeviceDynamicPanelBuilder> defaultSupplier;

    private PanelBuilderRegistry(Map<String, Supplier<DeviceDynamicPanelBuilder>> byBrand,
            Supplier<DeviceDynamicPanelBuilder> defaultSupplier) {
        this.byBrand = Collections.unmodifiableMap(byBrand);
        this.defaultSupplier = defaultSupplier;
    }

    public static PanelBuilderRegistry withDefaults() {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> byBrand = new LinkedHashMap<>();
        byBrand.put(MARCA_SIEMENS, SiemensPanelBuilder::new);
        byBrand.put(MARCA_ALLEN_BRADLEY, AllenBradleyPanelBuilder::new);
        byBrand.put(MARCA_MODBUS, ModbusPanelBuilder::new);
        return new PanelBuilderRegistry(byBrand, SiemensPanelBuilder::new);
    }

    public static PanelBuilderRegistry from(Map<String, Supplier<DeviceDynamicPanelBuilder>> byBrand,
            Supplier<DeviceDynamicPanelBuilder> defaultSupplier) {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> copia = new LinkedHashMap<>();
        byBrand.forEach((brand, factory) -> copia.put(normalizar(brand), factory));
        return new PanelBuilderRegistry(copia, defaultSupplier);
    }

public DeviceDynamicPanelBuilder forBrand(String brand) {
        String clave = normalizar(brand);
        Supplier<DeviceDynamicPanelBuilder> factory = byBrand.get(clave);
        if (factory != null) {
            return factory.get();
        }
        if (!clave.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "La marca ''{0}'' no tiene panel de conexion propio; se usa el de"
                            + " {1} por defecto. Las URLs saldran como si fuera de ese"
                            + " protocolo.",
                    new Object[]{brand, defaultBuilder().getProtocol()});
        }
        return defaultSupplier.get();
    }

    public DeviceDynamicPanelBuilder defaultBuilder() {
        return defaultSupplier.get();
    }

    public Set<String> brands() {
        return byBrand.keySet();
    }

    public boolean registers(String brand) {
        return byBrand.containsKey(normalizar(brand));
    }

    private static String normalizar(String brand) {
        return brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
    }
}
