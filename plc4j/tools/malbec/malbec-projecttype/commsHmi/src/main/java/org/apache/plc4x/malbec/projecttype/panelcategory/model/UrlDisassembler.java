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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;


public final class UrlDisassembler {

    private UrlDisassembler() {
    }

    public record Partes(String protocol, String transport, String host, String port,
                 LinkedHashMap<String, String> params) {
    }

    public static Partes disassemble(String url) {
        if (url == null) {
            return null;
        }
        String limpio = url.trim();
        int separador = limpio.indexOf("://");
        if (separador < 0) {
            return null;
        }

        String izquierda = limpio.substring(0, separador);
        String resto = limpio.substring(separador + 3);

        int colonTransporte = izquierda.indexOf(':');
        String protocol = colonTransporte < 0 ? izquierda : izquierda.substring(0, colonTransporte);
        String transport = colonTransporte < 0 ? "" : izquierda.substring(colonTransporte + 1);

        int interrogacion = resto.indexOf('?');
        String hostPort = interrogacion < 0 ? resto : resto.substring(0, interrogacion);
        String query = interrogacion < 0 ? "" : resto.substring(interrogacion + 1);

        String host;
        String port;
        if (hostPort.startsWith("[")) {
            // Direccion IPv6 entre corchetes. Sin esto, 'lastIndexOf(:)' partia por el
            // medio de la direccion y '::1' se leia como host '[' y puerto ':1]'.
            int cierre = hostPort.indexOf(']');
            if (cierre > 0) {
                host = hostPort.substring(1, cierre);
                String detras = hostPort.substring(cierre + 1);
                port = detras.startsWith(":") ? detras.substring(1) : "";
            } else {
                // Corchete sin cerrar: se toma todo como host, mejor que cortar la direccion.
                host = hostPort;
                port = "";
            }
        } else {
            host = hostPort;
            port = "";
            int colonPuerto = hostPort.lastIndexOf(':');
            if (colonPuerto >= 0) {
                host = hostPort.substring(0, colonPuerto);
                port = hostPort.substring(colonPuerto + 1);
            }
        }

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        for (String par : query.split("&")) {
            if (par.isEmpty()) {
                continue;
            }
            int igual = par.indexOf('=');
            String clave = igual < 0 ? par : par.substring(0, igual);
            String valor = igual < 0 ? "" : par.substring(igual + 1);
            params.put(decodificar(clave), decodificar(valor));
        }

        return new Partes(protocol, transport, host, port, params);
    }

    static String decodificar(String texto) {
        try {
            return URLDecoder.decode(texto, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException | IllegalArgumentException ex) {
            return texto;
        }
    }

    static String codificar(String texto) {
        try {
            return URLEncoder.encode(texto, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            return texto;
        }
    }
}