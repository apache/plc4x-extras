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
import java.util.Set;

public class SiemensPanelBuilder implements DeviceDynamicPanelBuilder {

    private static final LinkedHashMap<String, String> DRIVER_OPTIONS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> COTP_OPTIONS = new LinkedHashMap<>();

    static {
        DRIVER_OPTIONS.put("pdu-size", "1024");
        DRIVER_OPTIONS.put("max-amq-caller", "8");
        DRIVER_OPTIONS.put("max-amq-callee", "8");
        DRIVER_OPTIONS.put("controller-type", "ANY");
        DRIVER_OPTIONS.put("read-timeout-ms", "10000");
        DRIVER_OPTIONS.put("ha-heartbeat-interval-ms", "4000");
        DRIVER_OPTIONS.put("ha-failover-timeout-ms", "2000");

        COTP_OPTIONS.put("cotp.local-rack", "1");
        COTP_OPTIONS.put("cotp.local-slot", "1");
        COTP_OPTIONS.put("cotp.local-device-group", "OTHERS");
        COTP_OPTIONS.put("cotp.remote-rack", "0");
        COTP_OPTIONS.put("cotp.remote-slot", "0");
        COTP_OPTIONS.put("cotp.remote-device-group", "PG_OR_PC");
        COTP_OPTIONS.put("cotp.local-tsap", "0");
        COTP_OPTIONS.put("cotp.remote-tsap", "0");
        COTP_OPTIONS.put("cotp.tpdu-size", "8192");
        COTP_OPTIONS.put("cotp.handshake-timeout-ms", "5000");
        COTP_OPTIONS.put("cotp.protocol-class", "0");
        COTP_OPTIONS.put("cotp.connect-timeout-ms", "5000");
        COTP_OPTIONS.put("cotp.read-timeout-ms", "0");
        COTP_OPTIONS.put("cotp.write-timeout-ms", "0");
        COTP_OPTIONS.put("cotp.no-delay", "true");
        COTP_OPTIONS.put("cotp.keep-alive", "false");
        COTP_OPTIONS.put("cotp.send-buffer-size", "81920");
        COTP_OPTIONS.put("cotp.receive-buffer-size", "81920");
        COTP_OPTIONS.put("cotp.local-address", "");
        COTP_OPTIONS.put("cotp.local-port", "0");
    }

    private String host = "";
    private String transport = "";
    private String port = "";
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

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
        return transport;
    }

    @Override
    public String[] getTransportOptions() {
        return new String[]{"", "cotp"};
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
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public LinkedHashMap<String, String> getParameterDefaults() {
        return getParameterDefaults(transport);
    }

    @Override
    public LinkedHashMap<String, String> getParameterDefaults(String transport) {
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>(DRIVER_OPTIONS);
        defaults.putAll(COTP_OPTIONS);
        return defaults;
    }

    @Override
    public String getHostLabel() {
        return "Host/IP:";
    }

    @Override
    public boolean isPortApplicable() {
        return true;
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
            for (String key : params.keySet()) {
                lastKey = key;
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