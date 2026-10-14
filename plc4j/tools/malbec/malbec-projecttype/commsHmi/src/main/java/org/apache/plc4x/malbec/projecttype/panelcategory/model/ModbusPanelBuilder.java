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


public class ModbusPanelBuilder implements DeviceDynamicPanelBuilder {

    public enum ModbusVariant {
        TCP("modbus-tcp", "tcp", new String[]{"tcp", "tls", "tls-psk", "udp"}),
        RTU("modbus-rtu", "serial", new String[]{"serial", "tcp", "tls", "tls-psk", "udp"}),
        ASCII("modbus-ascii", "serial", new String[]{"serial", "tcp", "tls", "tls-psk", "udp"});

        private final String code;
        private final String defaultTransport;
        private final String[] transports;

        ModbusVariant(String code, String defaultTransport, String[] transports) {
            this.code = code;
            this.defaultTransport = defaultTransport;
            this.transports = transports;
        }

        public String getCode() {
            return code;
        }

        public String getDefaultTransport() {
            return defaultTransport;
        }

        public String[] getTransports() {
            return transports;
        }

        public static ModbusVariant fromCode(String code) {
            for (ModbusVariant variant : values()) {
                if (variant.code.equalsIgnoreCase(code == null ? "" : code)) {
                    return variant;
                }
            }
            return TCP;
        }
    }

    private static final LinkedHashMap<String, String> COMMON_OPTIONS = new LinkedHashMap<>();
    static {
        COMMON_OPTIONS.put("default-unit-identifier", "1");
        COMMON_OPTIONS.put("request-timeout-ms", "5000");
        COMMON_OPTIONS.put("ping-address", "4x00001:BOOL");
        COMMON_OPTIONS.put("default-payload-byte-order", "BIG_ENDIAN");
        COMMON_OPTIONS.put("max-coils-per-request", "2000");
        COMMON_OPTIONS.put("max-registers-per-request", "125");
    }

    private static final Map<String, LinkedHashMap<String, String>> TRANSPORT_OPTIONS = new LinkedHashMap<>();
    static {
        LinkedHashMap<String, String> serial = new LinkedHashMap<>();
        serial.put("serial.baud-rate", "9600");
        serial.put("serial.data-bits", "8");
        serial.put("serial.stop-bits", "1");
        serial.put("serial.parity", "none");
        serial.put("serial.flow-control", "none");
        serial.put("serial.read-timeout-ms", "1000");
        serial.put("serial.write-timeout-ms", "1000");
        serial.put("serial.dtr", "false");
        serial.put("serial.rts", "false");
        serial.put("serial.reuse-port", "false");
        serial.put("serial.interframe-delay", "0");
        TRANSPORT_OPTIONS.put("serial", serial);

        LinkedHashMap<String, String> tcp = new LinkedHashMap<>();
        tcp.put("tcp.connect-timeout-ms", "5000");
        tcp.put("tcp.read-timeout-ms", "0");
        tcp.put("tcp.write-timeout-ms", "0");
        tcp.put("tcp.no-delay", "true");
        tcp.put("tcp.keep-alive", "false");
        tcp.put("tcp.send-buffer-size", "81920");
        tcp.put("tcp.receive-buffer-size", "81920");
        tcp.put("tcp.local-address", "");
        tcp.put("tcp.local-port", "0");
        TRANSPORT_OPTIONS.put("tcp", tcp);
        
        LinkedHashMap<String, String> tls = new LinkedHashMap<>(tcp);
        tls.put("tls.verify", "true");
        tls.put("tls.ignore-common-name", "false");
        tls.put("tls.trust-store", "");
        tls.put("tls.trust-store-password", "");
        tls.put("tls.trust-store-type", "PKCS12");
        tls.put("tls.version", "");
        tls.put("tls.keystore", "");
        tls.put("tls.keystore-password", "");
        tls.put("tls.keystore-type", "");
        tls.put("tls.log-session-keys", "false");
        TRANSPORT_OPTIONS.put("tls", tls);

        LinkedHashMap<String, String> tlsPsk = new LinkedHashMap<>(tcp);
        tlsPsk.put("tls-psk.psk-identity", "");
        tlsPsk.put("tls-psk.psk-key", "");
        tlsPsk.put("tls-psk.log-session-keys", "false");
        TRANSPORT_OPTIONS.put("tls-psk", tlsPsk);

        LinkedHashMap<String, String> udp = new LinkedHashMap<>();
        udp.put("udp.local-address", "");
        udp.put("udp.local-port", "0");
        udp.put("udp.read-timeout-ms", "0");
        udp.put("udp.max-packet-size", "65507");
        udp.put("udp.send-buffer-size", "0");
        udp.put("udp.receive-buffer-size", "0");
        udp.put("udp.broadcast", "false");
        udp.put("udp.reuse-address", "false");
        udp.put("udp.share-socket", "false");
        udp.put("udp.multicast-ttl", "1");
        TRANSPORT_OPTIONS.put("udp", udp);
    }

    private String host = "";
    private String protocol = "modbus-tcp";
    private String transport = "tcp";
    private String port = "";
    private ModbusVariant variant = ModbusVariant.TCP;
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

    @Override
    public void initForModel(DeviceModel model) {
        String p = model != null ? model.getProtocol() : null;
        variant = ModbusVariant.fromCode(p);
        protocol = variant.getCode();
        transport = variant.getDefaultTransport();
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getDefaultTransport() {
        return transport;
    }

    @Override
    public String[] getTransportOptions() {
        return variant.getTransports();
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
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>(COMMON_OPTIONS);
        LinkedHashMap<String, String> options = TRANSPORT_OPTIONS.get(transport);
        if (options != null) {
            defaults.putAll(options);
        }
        return defaults;
    }

    @Override
    public String getHostLabel() {
        return "serial".equals(transport) ? "Puerto/Dispositivo:" : "Host/IP:";
    }

    @Override
    public boolean isPortApplicable() {
        return !"serial".equals(transport);
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
        return DeviceDynamicPanelBuilder.assembleUrl(
                getProtocol(), transport, host, port, ordered, isPortApplicable());
    }
}