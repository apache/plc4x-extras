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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SiemensPanelBuilder implements DeviceDynamicPanelBuilder {

    private String host = "";
    private String transport = "cotp"; 
    private String port = "";
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

    // COMPLETE STATIC CATALOG OF S7 PARAMETERS (PLC4X)
    private static final Map<String, DeviceParameterDef> SIEMENS_STATIC_PARAMS = new LinkedHashMap<>();

    static {
        // 1. CONFIG OPTIONS (S7 Protocol Level)
        // pdu-size (INT, default 1024)
        SIEMENS_STATIC_PARAMS.put("pdu-size", new DeviceParameterDef(
            "pdu-size", "1024", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) > 0 && 
                    Integer.parseInt(val) <= 1024,
            "Debe ser un número entero mayor a 0 y menor que 1025."
        ));

        // max-amq-caller (INT, default 8)
        SIEMENS_STATIC_PARAMS.put("max-amq-caller", new DeviceParameterDef(
            "max-amq-caller", "8", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) > 0 &&
                    Integer.parseInt(val) <= 8,
            "Debe ser un número entero mayor a 0 y menor a 9."
        ));

        // max-amq-callee (INT, default 8)
        SIEMENS_STATIC_PARAMS.put("max-amq-callee", new DeviceParameterDef(
            "max-amq-callee", "8", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) > 0 &&
                    Integer.parseInt(val) <= 8,
            "Debe ser un número entero mayor a 0 y menor a 9."
        ));

        // controller-type (STRING, default ANY)
        SIEMENS_STATIC_PARAMS.put("controller-type", new DeviceParameterDef(
            "controller-type", "ANY", false,
            val -> !val.trim().isEmpty(), "No puede estar vacío."
        ));

        // read-timeout-ms (INT, default 10000)
        SIEMENS_STATIC_PARAMS.put("read-timeout-ms", new DeviceParameterDef(
            "read-timeout-ms", "10000", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 10000,
            "Debe ser un número entero mayor o igual a 0 y menor o "
                    + "igual a 10000 (milisegundos)."
        ));

        // ha-heartbeat-interval-ms (INT, default 4000)
        SIEMENS_STATIC_PARAMS.put("ha-heartbeat-interval-ms", new DeviceParameterDef(
            "ha-heartbeat-interval-ms", "4000", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 4000,
            "Debe ser un número entero mayor o igual a 0 y menor o "
                    + "igual a 4000 (milisegundos)."
        ));

        // ha-failover-timeout-ms (INT, default 2000)
        SIEMENS_STATIC_PARAMS.put("ha-failover-timeout-ms", new DeviceParameterDef(
            "ha-failover-timeout-ms", "2000", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 2000,
            "Debe ser un número entero mayor o igual a 0 y menor o "
                    + "igual a 2000 (milisegundos)."
        ));

        // 2. TRANSPORT CONFIG OPTIONS (COTP Level)

        // cotp.local-rack (INT, default 1)
        SIEMENS_STATIC_PARAMS.put("cotp.local-rack", new DeviceParameterDef(
            "cotp.local-rack", "1", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un número entero mayor o igual a 0."
        ));

        // cotp.local-slot (INT, default 1)
        SIEMENS_STATIC_PARAMS.put("cotp.local-slot", new DeviceParameterDef(
            "cotp.local-slot", "1", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un número entero mayor o igual a 0."
        ));

        // cotp.local-device-group (STRING, default OTHERS)
        SIEMENS_STATIC_PARAMS.put("cotp.local-device-group", new DeviceParameterDef(
            "cotp.local-device-group", "OTHERS", false,
            val -> !val.trim().isEmpty(),
            "No puede estar vacío."
        ));

        // cotp.remote-rack (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.remote-rack", new DeviceParameterDef(
            "cotp.remote-rack", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un número entero mayor o igual a 0."
        ));

        // cotp.remote-slot (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.remote-slot", new DeviceParameterDef(
            "cotp.remote-slot", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un número entero mayor o igual a 0."
        ));

        // cotp.remote-device-group (STRING, default PG_OR_PC)
        SIEMENS_STATIC_PARAMS.put("cotp.remote-device-group", new DeviceParameterDef(
            "cotp.remote-device-group", "PG_OR_PC", false,
            val -> !val.trim().isEmpty(),
            "No puede estar vacío."
        ));

        // cotp.local-tsap (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.local-tsap", new DeviceParameterDef(
            "cotp.local-tsap", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un entero mayor o igual a 0."
        ));

        // cotp.remote-tsap (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.remote-tsap", new DeviceParameterDef(
            "cotp.remote-tsap", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un entero mayor o igual a 0."
        ));

        // cotp.tpdu-size (INT, default 8192, válidos: 128, 256, 512, 1024, 2048, 4096, 8192)
        SIEMENS_STATIC_PARAMS.put("cotp.tpdu-size", new DeviceParameterDef(
            "cotp.tpdu-size", "8192", false,
            val -> val.matches("128|256|512|1024|2048|4096|8192"),
            "Debe ser uno de los siguientes valores: 128, 256, 512, 1024, 2048, 4096, 8192."
        ));

        // cotp.handshake-timeout-ms (INT, default 5000)
        SIEMENS_STATIC_PARAMS.put("cotp.handshake-timeout-ms", new DeviceParameterDef(
            "cotp.handshake-timeout-ms", "5000", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 5000,
            "Debe ser un entero mayor o igual a 0 y menor o "
                    + "igual a 5000 (milisegundos)."
        ));

        // cotp.protocol-class (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.protocol-class", new DeviceParameterDef(
            "cotp.protocol-class", "0", false,
            val -> val.matches("\\d+"),
            "Debe ser un entero numérico válido."
        ));

        // cotp.connect-timeout-ms (INT, default 5000)
        SIEMENS_STATIC_PARAMS.put("cotp.connect-timeout-ms", new DeviceParameterDef(
            "cotp.connect-timeout-ms", "5000", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 5000,
            "Debe ser un entero mayor o igual a 0 y menor o "
                    + "igual a 5000 (milisegundos)."
        ));

        // cotp.read-timeout-ms (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.read-timeout-ms", new DeviceParameterDef(
            "cotp.read-timeout-ms", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un entero mayor o igual a 0 (milisegundos)."
        ));

        // cotp.write-timeout-ms (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.write-timeout-ms", new DeviceParameterDef(
            "cotp.write-timeout-ms", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0,
            "Debe ser un entero mayor o igual a 0 (milisegundos)."
        ));

        // cotp.no-delay (BOOLEAN, default true)
        SIEMENS_STATIC_PARAMS.put("cotp.no-delay", new DeviceParameterDef(
            "cotp.no-delay", "true", false,
            val -> val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false"),
            "Debe ser un valor booleano ('true' o 'false')."
        ));

        // cotp.keep-alive (BOOLEAN, default false)
        SIEMENS_STATIC_PARAMS.put("cotp.keep-alive", new DeviceParameterDef(
            "cotp.keep-alive", "false", false,
            val -> val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false"),
            "Debe ser un valor booleano ('true' o 'false')."
        ));

        // cotp.send-buffer-size (INT, default 81920)
        SIEMENS_STATIC_PARAMS.put("cotp.send-buffer-size", new DeviceParameterDef(
            "cotp.send-buffer-size", "81920", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 81920,
            "Debe ser un entero mayor o igual a 0 y menor o igual a 81920,"
                    + " 0 utiliza el valor predeterminado del sistema."
        ));

        // cotp.receive-buffer-size (INT, default 81920)
        SIEMENS_STATIC_PARAMS.put("cotp.receive-buffer-size", new DeviceParameterDef(
            "cotp.receive-buffer-size", "81920", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 &&
                    Integer.parseInt(val) <= 81920,
            "Debe ser un entero mayor o igual a 0 y menor o igual a 81920,"
                    + " 0 utiliza el valor predeterminado del sistema."
        ));

        // cotp.local-address (STRING, opcional)
        SIEMENS_STATIC_PARAMS.put("cotp.local-address", new DeviceParameterDef(
            "cotp.local-address", "", false,
            val -> true, // No requiere comprobación estricta de formato si es opcional
            ""
        ));

        // cotp.local-port (INT, default 0)
        SIEMENS_STATIC_PARAMS.put("cotp.local-port", new DeviceParameterDef(
            "cotp.local-port", "0", false,
            val -> val.matches("\\d+") && Integer.parseInt(val) >= 0 && Integer.parseInt(val) <= 65535,
            "Debe ser un número de puerto entero válido (0-65535)."
        ));
    }

//    public SiemensPanelBuilder() {
//    }

    @Override
    public void initForModel(DeviceModel model) {
        params.put("controller-type", model.getModel().replace("-", "_"));
    }

    @Override
    public String getProtocol() {
        return "s7";
    }

    @Override
    public String getDefaultTransport() {
        return "cotp";
    }

    @Override
    public String[] getTransportOptions() {
        return new String[]{"cotp"};
    }

    @Override
    public void setHost(String host) { 
        this.host = host; 
    }

    @Override
    public void setPort(String port) { 
        this.port = port; 
    }

    @Override
    public void setTransport(String transport) { 
        this.transport = transport; 
    }

    @Override
    public LinkedHashMap<String, String> getParameterDefaults() {
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
        SIEMENS_STATIC_PARAMS.forEach((k, v) -> defaults.put(k, v.getDefaultValue()));
        return defaults;
    }

    @Override
    public ValidationResult validateParameter(String key, String value) {
        DeviceParameterDef def = SIEMENS_STATIC_PARAMS.get(key);
        if (def != null) {
            return def.validate(value);
        }
        return ValidationResult.valid();
    }

    @Override
    public void retainParameters(Set<String> validKeys) {
        params.keySet().retainAll(validKeys == null ? List.of() : validKeys);
    }

    @Override
    public void addParameter(String key, String value) {
        params.put(key, value);
    }

    @Override
    public void removeLastParameter() {
        if (!params.isEmpty()) {
            String lastKey = null;
            for (String k : params.keySet()) {
                lastKey = k;
            }
            params.remove(lastKey);
        }
    }

    @Override
    public void clearParameters() {
        params.clear();
    }

    @Override
    public LinkedHashMap<String, String> getParameters() {
        return new LinkedHashMap<>(params);
    }

    @Override
    public String getSpecificParametersAsString() {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>(params);
        String controllerType = ordered.remove("controller-type");
        if (controllerType != null) {
            ordered.put("controller-type", controllerType);
        }
        return DeviceDynamicPanelBuilder.assembleUrl(getProtocol(), transport, host, port, ordered);
    }
}