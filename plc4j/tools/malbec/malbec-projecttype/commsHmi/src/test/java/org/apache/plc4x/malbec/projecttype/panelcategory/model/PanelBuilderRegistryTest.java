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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PanelBuilderRegistryTest {

    private final PanelBuilderRegistry registro = PanelBuilderRegistry.withDefaults();

    @Test
    void eachKnownBrandGivesItsBuilder() {
        assertInstanceOf(SiemensPanelBuilder.class, registro.forBrand("Siemens"));
        assertInstanceOf(AllenBradleyPanelBuilder.class, registro.forBrand("Allen Bradley"));
        assertInstanceOf(ModbusPanelBuilder.class, registro.forBrand("Modbus"));
    }

@Test
    void unknownBrandFallsBackToTheDefaultBuilder() {
        assertInstanceOf(SiemensPanelBuilder.class, registro.forBrand("Omron"));
        assertInstanceOf(SiemensPanelBuilder.class, registro.forBrand(""));
        assertInstanceOf(SiemensPanelBuilder.class, registro.forBrand(null));
        assertFalse(registro.registers("Omron"));
    }

    @Test
    void emptyBrandDoesNotComplainBecauseItIsNormalOnOldDevices() {
        var warnings = new java.util.concurrent.atomic.AtomicInteger();
        var logger = java.util.logging.Logger.getLogger(PanelBuilderRegistry.class.getName());
        var anteriores = logger.getHandlers();
        var nivel = logger.getLevel();
        try {
            for (var h : anteriores) {
                logger.removeHandler(h);
            }
            logger.setUseParentHandlers(false);
            logger.setLevel(java.util.logging.Level.ALL);
            logger.addHandler(new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord r) {
                    if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                        warnings.incrementAndGet();
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });

            registro.forBrand("");
            registro.forBrand(null);

            assertEquals(0, warnings.get(),
                    "sin marca no hay nada que avisar: el equipo es de los antiguos");
        } finally {
            logger.setLevel(nivel);
            logger.setUseParentHandlers(true);
            for (var h : logger.getHandlers()) {
                logger.removeHandler(h);
            }
            for (var h : anteriores) {
                logger.addHandler(h);
            }
        }
    }

    @Test
    void registeredBrandDoesNotWarn() {
        var warnings = new java.util.concurrent.atomic.AtomicInteger();
        var logger = java.util.logging.Logger.getLogger(PanelBuilderRegistry.class.getName());
        var anteriores = logger.getHandlers();
        var nivel = logger.getLevel();
        try {
            for (var h : anteriores) {
                logger.removeHandler(h);
            }
            logger.setUseParentHandlers(false);
            logger.setLevel(java.util.logging.Level.ALL);
            logger.addHandler(new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord r) {
                    if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                        warnings.incrementAndGet();
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });

            registro.forBrand("Siemens");
            registro.forBrand("modbus");

            assertEquals(0, warnings.get(), "una marca registrada es el caso normal");
        } finally {
            logger.setLevel(nivel);
            logger.setUseParentHandlers(true);
            for (var h : logger.getHandlers()) {
                logger.removeHandler(h);
            }
            for (var h : anteriores) {
                logger.addHandler(h);
            }
        }
    }

    @Test
    void brandDoesNotDependOnHowItIsWritten() {
        assertInstanceOf(ModbusPanelBuilder.class, registro.forBrand("modbus"));
        assertInstanceOf(ModbusPanelBuilder.class, registro.forBrand("MODBUS"));
        assertInstanceOf(ModbusPanelBuilder.class, registro.forBrand("MoDbUs"));
        assertInstanceOf(ModbusPanelBuilder.class, registro.forBrand("  Modbus  "),
                "los espacios sobrantes no deberian decidir el builder");
        assertTrue(registro.registers("MODBUS"));
    }

    @Test
    void eachLookupReturnsANewInstance() {
        DeviceDynamicPanelBuilder primero = registro.forBrand("Modbus");
        DeviceDynamicPanelBuilder segundo = registro.forBrand("Modbus");

        assertNotSame(primero, segundo,
                "T3.5: si se compartiera la instancia, un dispositivo heredaria los "
                        + "parametros de conexion del anterior");
    }

    @Test
    void returnedBuilderIsUsable() {
        DeviceDynamicPanelBuilder builder = registro.forBrand("Siemens");

        builder.initForModel(new DeviceModel("Siemens", "S7-1500", "S7"));
        builder.setHost("10.0.0.1");
        builder.setPort("102");

        assertEquals("s7", builder.getProtocol());
        assertTrue(builder.getSpecificParametersAsString().contains("10.0.0.1"),
                "el builder recibido por el panel tiene que conservar el host: "
                        + builder.getSpecificParametersAsString());
    }

    @Test
    void registryDeclaresTheBrandsItKnows() {
        assertEquals(3, registro.brands().size());
        assertTrue(registro.brands().contains("siemens"));
        assertTrue(registro.brands().contains("allen bradley"));
        assertTrue(registro.brands().contains("modbus"));
    }

    @Test
    void brandMapCannotBeModifiedFromOutside() {
        try {
            registro.brands().add("omron");
            org.junit.jupiter.api.Assertions.fail("marcas() deberia devolver una vista de solo lectura");
        } catch (UnsupportedOperationException esperada) {
        }
    }

    @Test
    void ownRegistryAllowsAddingAProtocolWithoutChangingThePanel() {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> byBrand = new LinkedHashMap<>();
        byBrand.put("Omron", () -> new FakePanelBuilder("fins"));
        PanelBuilderRegistry propio =
                PanelBuilderRegistry.from(byBrand, SiemensPanelBuilder::new);

        assertInstanceOf(FakePanelBuilder.class, propio.forBrand("Omron"));
        assertEquals("fins", propio.forBrand("Omron").getProtocol());
        assertInstanceOf(SiemensPanelBuilder.class, propio.forBrand("Siemens"));
    }

    @Test
    void enUnRegistroPropioLasClavesSeNormalizan() {
        Map<String, Supplier<DeviceDynamicPanelBuilder>> byBrand = new LinkedHashMap<>();
        byBrand.put("  OMRON ", () -> new FakePanelBuilder("fins"));
        PanelBuilderRegistry propio = PanelBuilderRegistry.from(byBrand, SiemensPanelBuilder::new);

        assertInstanceOf(FakePanelBuilder.class, propio.forBrand("omron"));
        assertEquals(1, propio.brands().size(), "la clave debe quedar normalizada, no duplicada");
    }

    private static final class FakePanelBuilder implements DeviceDynamicPanelBuilder {

        private final String protocol;
        private final LinkedHashMap<String, String> parametros = new LinkedHashMap<>();
        private String host = "";
        private String port = "";
        private String transport = "";

        FakePanelBuilder(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public void initForModel(DeviceModel model) {
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public void setHost(String host) {
            this.host = host;
        }

        @Override
        public void setTransport(String transport) {
            this.transport = transport;
        }

        @Override
        public void setPort(String port) {
            this.port = port;
        }

        @Override
        public String getDefaultTransport() {
            return "tcp";
        }

        @Override
        public String[] getTransportOptions() {
            return new String[]{"tcp"};
        }

        @Override
        public LinkedHashMap<String, String> getParameterDefaults() {
            return new LinkedHashMap<>();
        }

        @Override
        public void addParameter(String key, String value) {
            parametros.put(key, value);
        }

        @Override
        public void removeLastParameter() {
            String ultima = null;
            for (String clave : parametros.keySet()) {
                ultima = clave;
            }
            if (ultima != null) {
                parametros.remove(ultima);
            }
        }

        @Override
        public void clearParameters() {
            parametros.clear();
        }

        @Override
        public LinkedHashMap<String, String> getParameters() {
            return parametros;
        }

        @Override
        public String getSpecificParametersAsString() {
            return host + port + transport;
        }

        String getHost() {
            return host;
        }

        String getPort() {
            return port;
        }
    }
}