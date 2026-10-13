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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataTypeTest {

    @Test
    void losPrimitivosTienenElPesoQueLesCorresponde() {
        assertEquals(1, DataType.BOOLEAN.byteSize());
        assertEquals(1, DataType.BYTE.byteSize());
        assertEquals(1, DataType.UBYTE.byteSize());
        assertEquals(2, DataType.WORD.byteSize());
        assertEquals(2, DataType.SHORT.byteSize());
        assertEquals(2, DataType.USHORT.byteSize());
        assertEquals(4, DataType.INT.byteSize());
        assertEquals(4, DataType.UINT.byteSize());
        assertEquals(4, DataType.FLOAT.byteSize());
        assertEquals(8, DataType.LONG.byteSize());
        assertEquals(8, DataType.ULONG.byteSize());
        assertEquals(8, DataType.DOUBLE.byteSize());
    }

    @Test
    void longYUlongOcupanOchoBytesYNoCuatro() {
        assertEquals(8, DataType.find("long").byteSize());
        assertEquals(8, DataType.find("ulong").byteSize());
    }

    @Test
    void soloElBooleanoSeDireccionaPorBit() {
        assertEquals(1, DataType.BOOLEAN.slotBits());
        assertTrue(DataType.BOOLEAN.bitAddressed());
        assertEquals(8, DataType.BYTE.slotBits());
        assertEquals(64, DataType.LONG.slotBits());
        assertFalse(DataType.BYTE.bitAddressed());
    }

    @Test
    void elNombreSePersisteEnMinusculasYSeBuscaExacto() {
        assertEquals("boolean", DataType.BOOLEAN.label());
        assertEquals("ubyte", DataType.UBYTE.label());
        assertEquals(DataType.FLOAT, DataType.find("float"));
        assertEquals(DataType.ULONG, DataType.find("ulong"));
    }

    @Test
    void unNombreDesconocidoNoAdivinaUnTamanoPorDefecto() {
        assertNull(DataType.find("inter"));
        assertNull(DataType.find("INTER"));
        assertNull(DataType.find(""));
        assertNull(DataType.find(null));
        assertNull(DataType.find("Long"), "el nombre es en minúsculas");
    }

    @Test
    void laFamiliaByteAdmiteByteYUbyteYSoloEnUnByte() {
        assertEquals(List.of(DataType.BYTE, DataType.UBYTE),
                DataType.candidatos("BYTE", 8));
        assertTrue(DataType.candidatos("BYTE", 16).isEmpty(),
                "un byte no cabe en un DBW sin cambiar el código de área");
    }

    @Test
    void integerSeResuelveConElCodigoDeArea() {
        assertEquals(List.of(DataType.SHORT, DataType.USHORT),
                DataType.candidatos("INTEGER", 16));
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidatos("INTEGER", 32));
        assertEquals(List.of(DataType.LONG, DataType.ULONG),
                DataType.candidatos("INTEGER", 64));
        assertTrue(DataType.candidatos("INTEGER", 8).isEmpty());
    }

    @Test
    void realOfreceFloatYDoubleSiElAreaEsAncha() {
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidatos("REAL", 32));
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidatos("REAL", 64));
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidatos("REAL", 128));
        assertTrue(DataType.candidatos("REAL", 16).isEmpty(),
                "ni float ni double caben en dos bytes");
        assertTrue(DataType.candidatos("REAL", 8).isEmpty());
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidatos("REAL", 0),
                "sin código de área no se sabe el ancho, así que se ofrecen ambos");
    }

    @Test
    void boolAdmiteUnBitEnDbxYOchoEnDbb() {
        assertEquals(List.of(DataType.BOOLEAN), DataType.candidatos("BOOL", 1));
        assertEquals(List.of(DataType.BOOLEAN), DataType.candidatos("BOOL", 8));
        assertTrue(DataType.candidatos("BOOL", 32).isEmpty());
    }

    @Test
    void wordSoloCabeEnDosBytes() {
        assertEquals(List.of(DataType.WORD), DataType.candidatos("WORD", 16));
        assertTrue(DataType.candidatos("WORD", 32).isEmpty());
    }

    @Test
    void unaFamiliaDesconocidaNoDevuelveNada() {
        assertTrue(DataType.candidatos("STRING", 8).isEmpty());
        assertTrue(DataType.candidatos(null, 8).isEmpty());
    }

    @Test
    void seCubreTodaLaTablaDeTiposDePlc4x() {
        assertEquals(List.of(DataType.BOOLEAN), DataType.candidatos("BOOL", 1));
        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidatos("BYTE", 8));
        assertEquals(List.of(DataType.WORD), DataType.candidatos("WORD", 16));
        assertEquals(List.of(DataType.INT, DataType.UINT), DataType.candidatos("DWORD", 32));
        assertEquals(List.of(DataType.LONG, DataType.ULONG), DataType.candidatos("LWORD", 64));

        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidatos("SINT", 8));
        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidatos("USINT", 8));
        assertEquals(List.of(DataType.SHORT, DataType.USHORT), DataType.candidatos("INT", 16));
        assertEquals(List.of(DataType.SHORT, DataType.USHORT), DataType.candidatos("UINT", 16));
        assertEquals(List.of(DataType.INT, DataType.UINT), DataType.candidatos("DINT", 32));
        assertEquals(List.of(DataType.INT, DataType.UINT), DataType.candidatos("UDINT", 32));
        assertEquals(List.of(DataType.LONG, DataType.ULONG), DataType.candidatos("LINT", 64));
        assertEquals(List.of(DataType.LONG, DataType.ULONG), DataType.candidatos("ULINT", 64));

        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE), DataType.candidatos("REAL", 32));
        assertEquals(List.of(DataType.DOUBLE), DataType.candidatos("LREAL", 64));

        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidatos("CHAR", 8));
        assertEquals(List.of(DataType.WORD), DataType.candidatos("WCHAR", 16));
    }

    @Test
    void losTiposDeLongitudVariableNoOfrecenNada() {
        for (String temporal : new String[]{"STRING", "WSTRING", "S5TIME", "TIME",
                "LTIME", "DATE", "TIME_OF_DAY", "DATE_AND_TIME"}) {
            assertTrue(DataType.candidatos(temporal, 64).isEmpty(),
                    temporal + " no debería ofrecer tipos porque no se puede medir");
        }
    }

    @Test
    void rawByteArrayEsUnAliasDeByte() {
        assertEquals(DataType.candidatos("BYTE", 8), DataType.candidatos("RAW_BYTE_ARRAY", 8));
        MemoryTag t = MemoryTag.parse("%DB1.DBB0[0..15]:RAW_BYTE_ARRAY");
        assertEquals("RAW_BYTE_ARRAY", t.family());
        assertEquals(16, t.byteCapacity());
        assertFalse(DataType.candidatos(t.family(), t.codeBits()).isEmpty(),
                "y el área tiene que poder usarse con ese alias");
    }

    @Test
    void losNombresDeTipoDePlc4xSabenSuPropioTamano() {
        assertEquals(List.of(DataType.SHORT, DataType.USHORT),
                DataType.candidatos("INT", 0));
        assertEquals(List.of(DataType.SHORT, DataType.USHORT),
                DataType.candidatos("UINT", 32), "el nombre manda sobre el código de área");
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidatos("DINT", 0));
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidatos("DWORD", 0));
        assertEquals(List.of(DataType.LONG, DataType.ULONG),
                DataType.candidatos("LINT", 0));
        assertEquals(List.of(DataType.LONG, DataType.ULONG),
                DataType.candidatos("LWORD", 0));
    }

    @Test
    void unLrealDePlc4xEsUnDoubleYNoUnFloat() {
        assertEquals(List.of(DataType.DOUBLE), DataType.candidatos("LREAL", 64));
        assertEquals(List.of(DataType.DOUBLE), DataType.candidatos("LREAL", 0));
    }

    @Test
    void unTagDeSiemensConTipoDePlc4xEncuentraSusTipos() {
        MemoryTag tag = MemoryTag.parse("%DB1.DBD0:DINT");
        assertEquals("DINT", tag.family());
        assertEquals(32, tag.codeBits());
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidatos(tag.family(), tag.codeBits()));

        MemoryTag largo = MemoryTag.parse("%DB1.DBLD0:LREAL");
        assertEquals("LREAL", largo.family());
        assertEquals(64, largo.codeBits());
        assertEquals(List.of(DataType.DOUBLE),
                DataType.candidatos(largo.family(), largo.codeBits()));
    }

    @Test
    void stringYLosTiposS5S7NoTienenPesoNiSePuedenElegir() {
        List<DataType> sinPeso = List.of(DataType.STRING, DataType.S5TIME, DataType.S7DATE,
                DataType.S7TIME, DataType.S7TOD, DataType.S7DAT, DataType.S7COUNTER,
                DataType.S7DI, DataType.S7AI, DataType.S7AO, DataType.S7VALVE,
                DataType.S7VLV, DataType.S7AVLV, DataType.S7MOTOR);
        for (DataType t : sinPeso) {
            assertEquals(0, t.byteSize(), t.label() + " no debería tener peso todavía");
            assertFalse(t.isEnabled(), t.label() + " no debería poder elegirse");
        }
    }

    @Test
    void losPrimitivosSiSePuedenElegir() {
        for (DataType t : DataType.values()) {
            if (t.byteSize() > 0) {
                assertTrue(t.isEnabled(), t.label() + " debería poder elegirse");
            }
        }
    }

    // ------------------------------------------------------- T3.6

    @Test
    void elBusquedaPorNombreUsaElIndiceYNoUnRecorrido() {
        // Mismo resultado que el recorrido lineal, pero ahora sale de un mapa. Los
        // nombres se guardan en minuscula, asi que find es exacto: "BYTE" no existe.
        assertEquals(DataType.BYTE, DataType.find("byte"));
        assertNull(DataType.find("BYTE"),
                "find es exacto: el nombre guardado es 'byte' en minuscula");
        assertEquals(DataType.BYTE, DataType.findIgnoreCase("BYTE"),
                "para las mayusculas esta findIgnoreCase");
        assertNull(DataType.find("bytez"));
        assertNull(DataType.find(""));
        assertNull(DataType.find(null));
    }

    @Test
    void elBusquedaSinMayusculasCubreTodosLosTipos() {
        for (DataType t : DataType.values()) {
            assertEquals(t, DataType.findIgnoreCase(t.label()),
                    "el indice debe contener " + t.label());
            assertEquals(t, DataType.findIgnoreCase(t.label().toUpperCase()),
                    "el indice sin mayusculas debe cubrir " + t.label());
        }
        assertNull(DataType.findIgnoreCase(null));
    }

    @Test
    void laListaDeNombresEsLaMismaSiempre() {
        String primera = DataType.nombres();
        assertEquals(primera, DataType.nombres(),
                "ahora es una constante calculada al cargar la clase");
        assertTrue(primera.contains("byte") && primera.contains("string"));
    }

    @Test
    void losTiposDeSiemensSeDistinguenDeLosEstandar() {
        for (DataType t : List.of(DataType.S5TIME, DataType.S7DATE, DataType.S7TIME,
                DataType.S7TOD, DataType.S7DAT, DataType.S7COUNTER, DataType.S7DI,
                DataType.S7AI, DataType.S7AO, DataType.S7VALVE, DataType.S7VLV,
                DataType.S7AVLV, DataType.S7MOTOR)) {
            assertTrue(t.isProtocolSpecific(),
                    t.label() + " solo existe en Siemens");
        }
    }

    @Test
    void losTiposEstandarNoSonDeProtocolo() {
        for (DataType t : List.of(DataType.BYTE, DataType.INT, DataType.FLOAT,
                DataType.BOOLEAN, DataType.WORD, DataType.DOUBLE, DataType.STRING)) {
            assertFalse(t.isProtocolSpecific(),
                    t.label() + " es un tipo estandar, no de Siemens");
        }
    }

    @Test
    void stringNoEsDeProtocoloAunqueTampocoSeOfrezca() {
        // STRING no se ofrece al usuario, pero existe en IEC: no es de Siemens.
        assertFalse(DataType.STRING.isProtocolSpecific());
        assertFalse(DataType.STRING.isEnabled());
    }
}