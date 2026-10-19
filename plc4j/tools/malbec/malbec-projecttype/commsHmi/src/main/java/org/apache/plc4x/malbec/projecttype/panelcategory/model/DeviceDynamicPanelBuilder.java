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
import java.util.Map;
import java.util.Set;

public interface DeviceDynamicPanelBuilder {

    void initForModel(DeviceModel model);

    String getProtocol();

    void setHost(String host);

    void setTransport(String transport);

    void setPort(String port);

    String getDefaultTransport();

    String[] getTransportOptions();

    LinkedHashMap<String, String> getParameterDefaults();
    
    ValidationResult validateParameter(String key, String value);

    default LinkedHashMap<String, String> getParameterDefaults(String transport) {
        return getParameterDefaults();
    }

    default String getHostLabel() {
        return "Host/IP:";
    }

    default boolean isPortApplicable() {
        return true;
    }

    default void retainParameters(Set<String> validKeys) {
    }

    void addParameter(String key, String value);

    void removeLastParameter();

    void clearParameters();

    LinkedHashMap<String, String> getParameters();

    String getSpecificParametersAsString();

    static String assembleUrl(String protocol, String transport, String host, String port,
            LinkedHashMap<String, String> params) {
        return assembleUrl(protocol, transport, host, port, params, true);
    }

    static String assembleUrl(String protocol, String transport, String host, String port,
            LinkedHashMap<String, String> params, boolean includePort) {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol == null ? "" : protocol);
        if (transport != null && !transport.trim().isEmpty()) {
            sb.append(":").append(transport);
        }
        sb.append("://").append(hostInBrackets(host == null ? "" : host));
        if (includePort && port != null && !port.trim().isEmpty()) {
            sb.append(":").append(port.trim());
        }
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        if (params != null) {
            ordered.putAll(params);
        }
        if (!ordered.isEmpty()) {
            sb.append("?");
            int i = 0;
            for (Map.Entry<String, String> e : ordered.entrySet()) {
                if (i++ > 0) {
                    sb.append("&");
                }
                // Los valores se codifican para que un '&' o un '=' dentro de ellos no
                // rompa el formato. Un valor sin caracteres reservados se escribe igual
                // que antes, asi que las URLs existentes no cambian.
                sb.append(UrlDisassembler.codificar(e.getKey()))
                        .append("=")
                        .append(UrlDisassembler.codificar(e.getValue() == null ? "" : e.getValue()));
            }
        }
        return sb.toString();
    }

    private static String hostInBrackets(String host) {
        String limpio = host.trim();
        if (limpio.startsWith("[") && limpio.endsWith("]")) {
            return limpio;
        }
        if (!limpio.contains(":")) {
            return limpio;
        }
        return "[" + limpio + "]";
    }
}
