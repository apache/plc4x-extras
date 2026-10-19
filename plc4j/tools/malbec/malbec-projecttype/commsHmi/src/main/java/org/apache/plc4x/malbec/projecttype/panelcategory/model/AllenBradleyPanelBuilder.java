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

public class AllenBradleyPanelBuilder implements DeviceDynamicPanelBuilder {

    private String host = "";
    private String transport = "tcp";
    private String port = "";
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

    @Override
    public void initForModel(DeviceModel model) {
    }

    @Override
    public String getProtocol() {
        return "eip";
    }

    @Override
    public String getDefaultTransport() {
        return transport;
    }

    @Override
    public String[] getTransportOptions() {
        return new String[]{"tcp"};
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
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
        defaults.put("path", "");
        defaults.put("slot", "0");
        return defaults;
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
        return DeviceDynamicPanelBuilder.assembleUrl(getProtocol(), transport, host, port, params);
    }

    @Override
    public ValidationResult validateParameter(String key, String value) {
        return ValidationResult.valid();
    }
}