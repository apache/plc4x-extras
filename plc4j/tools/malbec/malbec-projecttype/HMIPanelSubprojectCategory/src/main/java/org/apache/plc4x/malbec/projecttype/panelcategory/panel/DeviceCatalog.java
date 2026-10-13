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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceModel;

final class DeviceCatalog {

    private DeviceCatalog() {
    }

    private static final Map<String, List<DeviceModel>> MODELOS = Map.of(
            "Siemens", List.of(
                    new DeviceModel("Siemens", "S7-300", "S7"),
                    new DeviceModel("Siemens", "S7-400", "S7"),
                    new DeviceModel("Siemens", "S7-1200", "S7"),
                    new DeviceModel("Siemens", "S7-1500", "S7")),
            "Allen Bradley", List.of(
                    new DeviceModel("Allen Bradley", "ControlLogix", "EtherNet/IP"),
                    new DeviceModel("Allen Bradley", "CompactLogix", "EtherNet/IP")),
            "Modbus", List.of(
                    new DeviceModel("Modbus", "Modbus TCP", "modbus-tcp"),
                    new DeviceModel("Modbus", "Modbus RTU", "modbus-rtu"),
                    new DeviceModel("Modbus", "Modbus ASCII", "modbus-ascii")));

    static List<String> marcas() {
        return List.of("Siemens", "Allen Bradley", "Modbus");
    }

    static List<DeviceModel> de(String marca) {
        return MODELOS.getOrDefault(marca, List.of());
    }

    static boolean existeMarca(String marca) {
        return MODELOS.containsKey(marca);
    }
}