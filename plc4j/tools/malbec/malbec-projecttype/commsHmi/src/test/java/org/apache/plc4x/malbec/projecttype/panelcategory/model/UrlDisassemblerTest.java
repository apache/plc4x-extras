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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class UrlDisassemblerTest {

    @Test
    void separaProtocoloYTransporte() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("s7:cotp://192.168.0.10:102");
        assertEquals("s7", partes.protocol());
        assertEquals("cotp", partes.transport());
        assertEquals("192.168.0.10", partes.host());
        assertEquals("102", partes.port());
        assertTrue(partes.params().isEmpty());
    }

    @Test
    void sinTransporteNiPuerto() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("s7://192.168.0.10");
        assertEquals("s7", partes.protocol());
        assertEquals("", partes.transport());
        assertEquals("192.168.0.10", partes.host());
        assertEquals("", partes.port());
    }

    @Test
    void conservaElOrdenDeLosParametros() {
        UrlDisassembler.Partes partes =
                UrlDisassembler.disassemble("eip://10.0.0.5:44818?path=1&slot=0");
        assertEquals("10.0.0.5", partes.host());
        assertEquals("44818", partes.port());
        assertEquals("[path, slot]", partes.params().keySet().toString(),
                "el orden importa porque es el que assembleUrl escribe");
        assertEquals("1", partes.params().get("path"));
        assertEquals("0", partes.params().get("slot"));
    }

    @Test
    void leeUnPuertoSerieComoHost() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("modbus-rtu://COM3");
        assertEquals("modbus-rtu", partes.protocol());
        assertEquals("COM3", partes.host());
        assertEquals("", partes.port());
    }

    @Test
    void devuelveNullSiNoTieneElSeparadorDeProtocolo() {
        assertNull(UrlDisassembler.disassemble("192.168.0.10:102"));
        assertNull(UrlDisassembler.disassemble(""));
        assertNull(UrlDisassembler.disassemble(null));
    }

    @Test
    void elViajeDeIdaYVueltaConservaLaUrl() {
        assertViajeIdaYVuelta("s7:cotp://192.168.0.10:102?controller-type=S7_1500&pdu-size=512");
        assertViajeIdaYVuelta("s7://192.168.0.10");
        assertViajeIdaYVuelta("eip://10.0.0.5:44818?path=1&slot=0");
        assertViajeIdaYVuelta("modbus-tcp://10.0.0.9:502?unit-identifier=3");
        assertViajeIdaYVuelta("modbus-rtu://COM3");
        assertViajeIdaYVuelta("modbus-rtu:///dev/ttyUSB0");
    }

    private void assertViajeIdaYVuelta(String original) {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble(original);
        assertTrue(partes != null, "no se pudo desensamblar: " + original);
        String reconstruida = DeviceDynamicPanelBuilder.assembleUrl(
                partes.protocol(), partes.transport(), partes.host(),
                partes.port(), new LinkedHashMap<>(partes.params()));
        assertEquals(original, reconstruida,
                "desensamblar y volver a armar tiene que devolver la misma URL");
    }

    // ------------------------------------------------------- T3.10: IPv6

    @Test
    void leeUnaIpv6ConPuerto() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("s7://[fe80::1c2d]:102");

        assertEquals("fe80::1c2d", partes.host(),
                "los corchetes son solo notacion y no forman parte de la direccion");
        assertEquals("102", partes.port());
    }

    @Test
    void leeUnaIpv6SinPuerto() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("s7://[::1]");

        assertEquals("::1", partes.host(),
                "sin puerto, los dos puntos de '::1' no pueden interpretarse como separador");
        assertEquals("", partes.port());
    }

    @Test
    void unaIpv6ViajaDeIdaYVuelta() {
        assertViajeIdaYVuelta("s7://[fe80::1c2d]:102");
        assertViajeIdaYVuelta("s7://[::1]");
        assertViajeIdaYVuelta("eip://[2001:db8::a]:44818?path=1");
    }

    @Test
    void unHostNormalNoSeEnvuelveEnCorchetes() {
        String url = DeviceDynamicPanelBuilder.assembleUrl(
                "s7", "cotp", "192.168.0.10", "102", new LinkedHashMap<>());

        assertEquals("s7:cotp://192.168.0.10:102", url,
                "T3.10: solo se envuelven los hosts con dos puntos, que son los IPv6");
    }

    @Test
    void unCorcheteSinCerrarNoRompeLaLectura() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("s7://[fe80::1c2d");

        assertTrue(partes != null);
        assertEquals("[fe80::1c2d", partes.host(),
                "malformado, se conserva el texto en vez de cortar la direccion");
        assertEquals("", partes.port());
    }

    // ------------------------------------------------------- T3.10: codificacion

    @Test
    void unValorConReservadosNoRompeElFormato() {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("comentario", "a&b=c");

        String url = DeviceDynamicPanelBuilder.assembleUrl("s7", "", "10.0.0.1", "102", params);
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble(url);

        assertEquals(1, partes.params().size(),
                "un '&' sin codificar partia los parametros en dos");
        assertEquals("a&b=c", partes.params().get("comentario"));
    }

    @Test
    void codificaYDecodificaSimetricamente() {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("ruta", "C:\\maquina\\linea 1");
        params.put("acentos", "presión");

        String url = DeviceDynamicPanelBuilder.assembleUrl("s7", "", "10.0.0.1", "102", params);
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble(url);

        assertEquals("C:\\maquina\\linea 1", partes.params().get("ruta"));
        assertEquals("presión", partes.params().get("acentos"));
    }

    @Test
    void unValorSinCaracteresReservadosSeEscribeIgual() {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("ipa", "10.0.0.1");
        params.put("port", "102");

        String url = DeviceDynamicPanelBuilder.assembleUrl("s7", "cotp", "192.168.0.10", "102", params);

        assertEquals("s7:cotp://192.168.0.10:102?ipa=10.0.0.1&port=102", url,
                "T3.10: las URLs ya guardadas no deben cambiar al pasar a codificar");
    }

    @Test
    void unPorcentajeSueltoNoTumbaLaLectura() {
        UrlDisassembler.Partes partes = UrlDisassembler.disassemble("s7://10.0.0.1?nota=100%");

        assertTrue(partes != null);
        assertEquals("100%", partes.params().get("nota"),
                "un '%' que no forma una secuencia valida se deja como estaba");
    }
}