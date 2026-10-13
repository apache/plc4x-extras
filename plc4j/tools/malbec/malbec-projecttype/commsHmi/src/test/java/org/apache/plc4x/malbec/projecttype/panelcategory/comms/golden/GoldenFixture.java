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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.golden;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

public final class GoldenFixture {

    private GoldenFixture() {
    }

    public static final String DEV_PLC = "dev-plc-0001";
    public static final String DEV_VFD = "dev-vfd-0002";

    public static CommunicationsConfig config() {
        CommunicationsConfig config = new CommunicationsConfig();

        config.addDevice(new DeviceConfigData(deviceProps(
                DEV_PLC, "PLC_Principal", "Siemens", "S7-1500", "S7",
                "PLC principal de la linea de embolsado",
                "S88.1", "s88-dev-plc-0001", "ipa=10.0.0.1,port=102")));

        config.upsertComms(DEV_PLC, new CommConfigData(
                "PLC_Principal",
                List.of(
                        new CommConfigData.GroupConfig("g-plc-1", "Escaneo Rapido",
                                "Grupo de alta frecuencia", "500", true, "b3f1a0c2d4e5"),
                        new CommConfigData.GroupConfig("g-plc-2", "Lento",
                                "Grupo de baja frecuencia", "2000", false, "c4a2b1d3e5f6")),
                List.of(
                        new CommConfigData.ItemConfig("it-plc-1", "Presion Zona 1",
                                "Presion de la zona de llenado", "%DB1.DBB0:INT", true,
                                "d5e6c2b4f6a7", "g-plc-1"),
                        new CommConfigData.ItemConfig("it-plc-2", "Nivel Tanque &.Ab",
                                "Nivel del tanque de abasto", "%DB1.DBB2:REAL", true,
                                "e6f7d3c5a7b8", "g-plc-1")),
                List.of(
                        pv("pv-plc-1", "Presion Zona 1", "INT", "it-plc-1", "500", true, false,
                                "0", "10", "Presion (bar)", "%.2f", "bar", "0", "10", "0.1",
                                "f7a8e4d6b8c9"),
                        pv("pv-plc-2", "Presion Zona 1 - Setpoint", "INT", "it-plc-1", "500", false, true,
                                "0", "10", "Consigna (bar)", "%.2f", "bar", "0", "10", "0.1",
                                "a8b9f5e7c9d0"),
                        pv("pv-plc-3", "Nivel Tanque &.Ab", "REAL", "it-plc-2", "500", true, false,
                                "0", "100", "Nivel (%)", "%.1f", "%", "0", "100", "0.5",
                                "b9c0a6f8d0e1"))));

        config.addDevice(new DeviceConfigData(deviceProps(
                DEV_VFD, "VFD Bomba A <<1>>", "ABB", "ACS580", "Modbus",
                "Variador de la bomba de trasvase \"A\"",
                "S88.2", "s88-dev-vfd-0002", "ip=192.168.1.20,unitId=1")));

        config.upsertComms(DEV_VFD, new CommConfigData(
                "VFD Bomba A <<1>>",
                List.of(
                        new CommConfigData.GroupConfig("g-vfd-1", "Estrategia",
                                "Grupo de control del variador", "1000", true, "c1d2b7a9e3f4")),
                List.of(
                        new CommConfigData.ItemConfig("it-vfd-1", "Velocidad <Ref>",
                                "Velocidad de referencia", "%DB2.DBB0:INT", true,
                                "d3e4c8b0f4a5", "g-vfd-1"),
                        new CommConfigData.ItemConfig("it-vfd-2", "Corriente",
                                "Corriente del motor", "%DB2.DBB2:REAL", true,
                                "e4f5d9c1a5b6", "g-vfd-1")),
                List.of(
                        pv("pv-vfd-1", "Velocidad <Ref>", "INT", "it-vfd-1", "1000", true, false,
                                "0", "1500", "Velocidad (rpm)", "%.0f", "rpm", "0", "1500", "10",
                                "f5a6e0d2b6c7"),
                        pv("pv-vfd-2", "Velocidad - Escape", "INT", "it-vfd-1", "1000", false, false,
                                "0", "1500", "Escape", "%.0f", "rpm", "0", "1500", "10",
                                "a6b7f1e3c7d8"),
                        pv("pv-vfd-3", "Corriente", "REAL", "it-vfd-2", "1000", true, true,
                                "0", "50", "Corriente (A)", "%.2f", "A", "", "50", "0.1",
                                "b7c8a2f4d8e9"))));

        return config;
    }

    private static Properties deviceProps(String uuid, String deviceName, String brand, String model,
            String protocol, String description, String s88Node, String s88Uuid, String specificParameters) {
        Properties p = new Properties();
        p.put("brand", brand);
        p.put("model", model);
        p.put("protocol", protocol);
        p.put("deviceName", deviceName);
        p.put("deviceKey", "key-" + uuid);
        p.put("description", description);
        p.put("uuid", uuid);
        p.put("enable", true);
        p.put("s88Node", s88Node);
        p.put("s88Uuid", s88Uuid);
        p.put("specificParameters", specificParameters);
        return p;
    }

    private static CommConfigData.PvConfig pv(String uuid, String name, String type, String itemUuid,
            String scanTime, boolean scanEnable, boolean writeEnable, String displayLimitLow,
            String displayLimitHigh, String displayDescription, String displayFormat, String displayUnits,
            String controlLimitLow, String controlLimitHigh, String controlMinStep, String md5) {
        return new CommConfigData.PvConfig(uuid, name, type, itemUuid, "0", "", scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep, md5, "");
    }

    public static final class BufferingStorage implements CommConfigStorage {

        private String text;

        public BufferingStorage() {
            this("");
        }

        public BufferingStorage(String initial) {
            this.text = initial;
        }

        public String text() {
            return text;
        }

        @Override
        public String open() {
            return text;
        }

        @Override
        public OutputStream create() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() {
                    text = new String(toByteArray(), StandardCharsets.UTF_8);
                }
            };
        }
    }
}
