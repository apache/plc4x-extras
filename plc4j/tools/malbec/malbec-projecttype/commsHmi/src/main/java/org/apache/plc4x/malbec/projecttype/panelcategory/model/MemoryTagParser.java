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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MemoryTagParser {

    private static final Pattern LONGITUD = Pattern.compile("\\((\\d+)\\)");
    private static final Pattern CORCHETE = Pattern.compile("\\[\\s*(\\d*)\\s*]");
    private static final Pattern RANGO = Pattern.compile("\\[\\s*(\\d+)\\s*\\.\\.\\s*(\\d+)\\s*]");

    private static final Pattern DB_CON_CODIGO
            = Pattern.compile("^([A-Za-z]+)(\\d+)\\.DB([A-Za-z]*)(\\d+)$");
    private static final Pattern DB_CORTO = Pattern.compile("^([A-Za-z]+)(\\d+):(\\d+)$");
    private static final Pattern CODIGO_Y_NUMERO
            = Pattern.compile("^((?:[MIQE]|[MIQE][BWDL]|C|T|D|DB|DBI|LD))(\\d+)$");
    private static final Pattern SOLO_NUMERO = Pattern.compile("^(\\d+)$");
    private static final Pattern type
            = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\s*\\(\\s*\\d+\\s*\\))?(\\s*\\[\\s*\\d*\\s*])?$");

    private MemoryTagParser() {
    }

    record Partes(String original, String block, String code, int baseByte, int bit,
            int firstByte, int lastByte, int amount, String family, boolean hasRange) {

    }

    private record ConTipo(String address, String type) {

    }

    private record Ubicacion(String block, String code, int number) {

    }

    private static int parseIntSafe(String grupo) {
        try {
            return Integer.parseInt(grupo);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    static Partes parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String resto = raw.trim();
        if (resto.startsWith("%")) {
            resto = resto.substring(1);
        }

        ConTipo conTipo = splitType(resto);
        String type = null;
        int amount = -1;
        if (conTipo.type() != null) {
            type = conTipo.type();
            Matcher mt = LONGITUD.matcher(type);
            if (mt.find()) {
                amount = parseIntSafe(mt.group(1));
            }
            Matcher mc = CORCHETE.matcher(type);
            if (mc.find() && !mc.group(1).isEmpty()) {
                amount = parseIntSafe(mc.group(1));
            }
            type = type.replaceFirst("\\s*\\(\\s*\\d+\\s*\\)", "")
                    .replaceFirst("\\s*\\[\\s*\\d*\\s*]", "")
                    .trim().toUpperCase();
        }

        String direccionTags = conTipo.address();
        int firstByte = -1;
        int lastByte = -1;
        boolean rangoExplicito = false;

        Matcher rango = RANGO.matcher(direccionTags);
        if (rango.find()) {
            firstByte = parseIntSafe(rango.group(1));
            lastByte = parseIntSafe(rango.group(2));
            direccionTags = rango.replaceFirst("");
            rangoExplicito = true;
        } else {
            Matcher indice = CORCHETE.matcher(direccionTags);
            if (indice.find()) {
                String valor = indice.group(1);
                firstByte = valor.isEmpty() ? -1 : parseIntSafe(valor);
                lastByte = firstByte;
                direccionTags = indice.replaceFirst("");
            }
        }

        String address = direccionTags.replaceAll("\\s+", "");
        int bit = -1;
        int posPunto = address.lastIndexOf('.');
        if (posPunto > 0 && posPunto < address.length() - 1) {
            String posibleBit = address.substring(posPunto + 1);
            if (posibleBit.matches("[0-7]")) {
                bit = parseIntSafe(posibleBit);
                address = address.substring(0, posPunto);
            }
        }

        Ubicacion ubicacion = locate(address);
        return new Partes(raw, ubicacion.block(), ubicacion.code(), ubicacion.number(), bit,
                firstByte, lastByte, amount, type, rangoExplicito);
    }

    private static Ubicacion locate(String address) {
        Matcher dbCodigo = DB_CON_CODIGO.matcher(address);
        if (dbCodigo.matches()) {
            String code = dbCodigo.group(3).isEmpty() ? "" : "DB" + dbCodigo.group(3).toUpperCase();
            return new Ubicacion(dbCodigo.group(1).toUpperCase() + dbCodigo.group(2),
                    code, parseIntSafe(dbCodigo.group(4)));
        }
        Matcher dbCorto = DB_CORTO.matcher(address);
        if (dbCorto.matches()) {
            return new Ubicacion(dbCorto.group(1).toUpperCase() + dbCorto.group(2),
                    "", parseIntSafe(dbCorto.group(3)));
        }
        Matcher codNum = CODIGO_Y_NUMERO.matcher(address);
        if (codNum.matches()) {
            return new Ubicacion("", codNum.group(1).toUpperCase(),
                    parseIntSafe(codNum.group(2)));
        }
        Matcher soloNum = SOLO_NUMERO.matcher(address);
        if (soloNum.matches()) {
            return new Ubicacion("", "", parseIntSafe(soloNum.group(1)));
        }
        return new Ubicacion(address, "", -1);
    }

    private static ConTipo splitType(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ':' && i + 1 < text.length()) {
                String tras = text.substring(i + 1).trim();
                if (type.matcher(tras).matches()) {
                    return new ConTipo(text.substring(0, i), tras);
                }
            }
        }
        return new ConTipo(text, null);
    }
}
