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

/**
 * Que builder corresponde a cada marca de dispositivo.
 *
 * <p>Antes de T3.5 el panel de alta de dispositivo tenia un {@code if/else} que elegia
 * builder comparando el nombre de la marca, asi que anadir un protocolo obligaba a tocar
 * ese panel. Aqui la correspondencia vive en un sitio y el panel solo pregunta.
 *
 * <p>Se elige por <em>marca</em> y no por protocolo porque Modbau cambia de protocolo
 * segun la variante del dispositivo ({@code ModbusPanelBuilder#getProtocol()} devuelve el
 * de la variante), asi que el protocolo no identifica al builder.
 *
 * <p>Una marca desconocida devuelve el builder por defecto. Es el comportamiento que
 * tenia el {@code if/else}, donde la rama final era Siemens: se conserva a proposito para
 * no cambiar lo que ocurre con una marca nueva o mal escrita.
 *
 * <p>Las claves se normalizan a minusculas, de modo que la comparacion no depende de como
 * se haya escrito la marca en el modelo.
 */
public final class PanelBuilderRegistry {

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

    /** Registro con los builders que trae el producto. */
    public static PanelBuilderRegistry porDefecto() {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> porMarca = new LinkedHashMap<>();
        porMarca.put(MARCA_SIEMENS, SiemensPanelBuilder::new);
        porMarca.put(MARCA_ALLEN_BRADLEY, AllenBradleyPanelBuilder::new);
        porMarca.put(MARCA_MODBUS, ModbusPanelBuilder::new);
        return new PanelBuilderRegistry(porMarca, SiemensPanelBuilder::new);
    }

    /**
     * Registro propio, para pruebas o para anadir protocolos sin tocar el registro de
     * producto.
     *
     * @param porMarca   marca normalizada a builder; null no se admite
     * @param porDefecto builder usado cuando la marca no esta en el mapa
     */
    public static PanelBuilderRegistry de(Map<String, Supplier<DeviceDynamicPanelBuilder>> porMarca,
            Supplier<DeviceDynamicPanelBuilder> porDefecto) {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> copia = new LinkedHashMap<>();
        porMarca.forEach((marca, factory) -> copia.put(normalizar(marca), factory));
        return new PanelBuilderRegistry(copia, porDefecto);
    }

    /**
     * Builder de la marca indicada, o el de por defecto si no la conoce.
     *
     * <p>Cada llamada devuelve una instancia nueva: los builders guardan los parametros de
     * conexion del dispositivo que se esta editando, y compartir uno entre dispositivos
     * haria que uno heredase los parametros del anterior.
     */
    public DeviceDynamicPanelBuilder para(String marca) {
        Supplier<DeviceDynamicPanelBuilder> factory = porMarca.get(normalizar(marca));
        return (factory != null ? factory : porDefecto).get();
    }

    /** Builder de por defecto, el mismo que devuelve {@code para} ante una marca desconocida. */
    public DeviceDynamicPanelBuilder builderPorDefecto() {
        return porDefecto.get();
    }

    /** Marcas que el registro conoce, en el orden en que se declararon. */
    public Set<String> marcas() {
        return porMarca.keySet();
    }

    /** Si el registro sabe construir el panel de esa marca. */
    public boolean registra(String marca) {
        return porMarca.containsKey(normalizar(marca));
    }

    private static String normalizar(String marca) {
        return marca == null ? "" : marca.trim().toLowerCase(Locale.ROOT);
    }
}