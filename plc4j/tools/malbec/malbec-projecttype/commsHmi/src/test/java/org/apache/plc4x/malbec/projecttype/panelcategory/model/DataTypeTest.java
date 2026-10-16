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
    void primitivesHaveTheWeightTheyShould() {
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
    void onlyBooleanIsBitAddressed() {
        assertEquals(1, DataType.BOOLEAN.slotBits());
        assertTrue(DataType.BOOLEAN.bitAddressed());
        assertEquals(8, DataType.BYTE.slotBits());
        assertEquals(64, DataType.LONG.slotBits());
        assertFalse(DataType.BYTE.bitAddressed());
    }

    @Test
    void nameIsStoredLowercaseAndLookedUpExactly() {
        assertEquals("boolean", DataType.BOOLEAN.label());
        assertEquals("ubyte", DataType.UBYTE.label());
        assertEquals(DataType.FLOAT, DataType.find("float"));
        assertEquals(DataType.ULONG, DataType.find("ulong"));
    }

    @Test
    void unknownNameDoesNotGuessASizeByDefault() {
        assertNull(DataType.find("inter"));
        assertNull(DataType.find("INTER"));
        assertNull(DataType.find(""));
        assertNull(DataType.find(null));
        assertNull(DataType.find("Long"), "el nombre es en minúsculas");
    }

    @Test
    void byteFamilyAcceptsByteAndUbyteOnlyOnOneByte() {
        assertEquals(List.of(DataType.BYTE, DataType.UBYTE),
                DataType.candidates("BYTE", 8));
        assertTrue(DataType.candidates("BYTE", 16).isEmpty(),
                "un byte no cabe en un DBW sin cambiar el código de área");
    }

    @Test
    void integerSeResuelveConElCodigoDeArea() {
        assertEquals(List.of(DataType.SHORT, DataType.USHORT),
                DataType.candidates("INTEGER", 16));
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidates("INTEGER", 32));
        assertEquals(List.of(DataType.LONG, DataType.ULONG),
                DataType.candidates("INTEGER", 64));
        assertTrue(DataType.candidates("INTEGER", 8).isEmpty());
    }

    @Test
    void realOfreceFloatYDoubleSiElAreaEsAncha() {
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidates("REAL", 32));
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidates("REAL", 64));
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidates("REAL", 128));
        assertTrue(DataType.candidates("REAL", 16).isEmpty(),
                "ni float ni double caben en dos bytes");
        assertTrue(DataType.candidates("REAL", 8).isEmpty());
        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE),
                DataType.candidates("REAL", 0),
                "sin código de área no se sabe el ancho, así que se ofrecen ambos");
    }

    @Test
    void boolAdmiteUnBitEnDbxYOchoEnDbb() {
        assertEquals(List.of(DataType.BOOLEAN), DataType.candidates("BOOL", 1));
        assertEquals(List.of(DataType.BOOLEAN), DataType.candidates("BOOL", 8));
        assertTrue(DataType.candidates("BOOL", 32).isEmpty());
    }

    @Test
    void wordSoloCabeEnDosBytes() {
        assertEquals(List.of(DataType.WORD), DataType.candidates("WORD", 16));
        assertTrue(DataType.candidates("WORD", 32).isEmpty());
    }

    @Test
    void unknownFamilyReturnsNothing() {
        assertTrue(DataType.candidates("STRING", 8).isEmpty());
        assertTrue(DataType.candidates(null, 8).isEmpty());
    }

    @Test
    void theWholePlc4xTypeTableIsCovered() {
        assertEquals(List.of(DataType.BOOLEAN), DataType.candidates("BOOL", 1));
        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidates("BYTE", 8));
        assertEquals(List.of(DataType.WORD), DataType.candidates("WORD", 16));
        assertEquals(List.of(DataType.INT, DataType.UINT), DataType.candidates("DWORD", 32));
        assertEquals(List.of(DataType.LONG, DataType.ULONG), DataType.candidates("LWORD", 64));

        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidates("SINT", 8));
        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidates("USINT", 8));
        assertEquals(List.of(DataType.SHORT, DataType.USHORT), DataType.candidates("INT", 16));
        assertEquals(List.of(DataType.SHORT, DataType.USHORT), DataType.candidates("UINT", 16));
        assertEquals(List.of(DataType.INT, DataType.UINT), DataType.candidates("DINT", 32));
        assertEquals(List.of(DataType.INT, DataType.UINT), DataType.candidates("UDINT", 32));
        assertEquals(List.of(DataType.LONG, DataType.ULONG), DataType.candidates("LINT", 64));
        assertEquals(List.of(DataType.LONG, DataType.ULONG), DataType.candidates("ULINT", 64));

        assertEquals(List.of(DataType.FLOAT, DataType.DOUBLE), DataType.candidates("REAL", 32));
        assertEquals(List.of(DataType.DOUBLE), DataType.candidates("LREAL", 64));

        assertEquals(List.of(DataType.BYTE, DataType.UBYTE), DataType.candidates("CHAR", 8));
        assertEquals(List.of(DataType.WORD), DataType.candidates("WCHAR", 16));
    }

    @Test
    void variableLengthTypesOfferNothing() {
        for (String temporal : new String[]{"STRING", "WSTRING", "S5TIME", "TIME",
                "LTIME", "DATE", "TIME_OF_DAY", "DATE_AND_TIME"}) {
            assertTrue(DataType.candidates(temporal, 64).isEmpty(),
                    temporal + " no debería ofrecer tipos porque no se puede medir");
        }
    }

    @Test
    void rawByteArrayEsUnAliasDeByte() {
        assertEquals(DataType.candidates("BYTE", 8), DataType.candidates("RAW_BYTE_ARRAY", 8));
        MemoryTag t = MemoryTag.parse("%DB1.DBB0[0..15]:RAW_BYTE_ARRAY");
        assertEquals("RAW_BYTE_ARRAY", t.family());
        assertEquals(16, t.byteCapacity());
        assertFalse(DataType.candidates(t.family(), t.codeBits()).isEmpty(),
                "y el área tiene que poder usarse con ese alias");
    }

    @Test
    void plc4xTypeNamesKnowTheirOwnSize() {
        assertEquals(List.of(DataType.SHORT, DataType.USHORT),
                DataType.candidates("INT", 0));
        assertEquals(List.of(DataType.SHORT, DataType.USHORT),
                DataType.candidates("UINT", 32), "el nombre manda sobre el código de área");
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidates("DINT", 0));
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidates("DWORD", 0));
        assertEquals(List.of(DataType.LONG, DataType.ULONG),
                DataType.candidates("LINT", 0));
        assertEquals(List.of(DataType.LONG, DataType.ULONG),
                DataType.candidates("LWORD", 0));
    }

    @Test
    void plc4xLrealIsADoubleNotAFloat() {
        assertEquals(List.of(DataType.DOUBLE), DataType.candidates("LREAL", 64));
        assertEquals(List.of(DataType.DOUBLE), DataType.candidates("LREAL", 0));
    }

    @Test
    void siemensTagWithPlc4xTypeFindsItsTypes() {
        MemoryTag tag = MemoryTag.parse("%DB1.DBD0:DINT");
        assertEquals("DINT", tag.family());
        assertEquals(32, tag.codeBits());
        assertEquals(List.of(DataType.INT, DataType.UINT),
                DataType.candidates(tag.family(), tag.codeBits()));

        MemoryTag largo = MemoryTag.parse("%DB1.DBLD0:LREAL");
        assertEquals("LREAL", largo.family());
        assertEquals(64, largo.codeBits());
        assertEquals(List.of(DataType.DOUBLE),
                DataType.candidates(largo.family(), largo.codeBits()));
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
    void primitivesCanBeChosen() {
        for (DataType t : DataType.values()) {
            if (t.byteSize() > 0) {
                assertTrue(t.isEnabled(), t.label() + " debería poder elegirse");
            }
        }
    }

    // ------------------------------------------------------- T3.6

    @Test
    void nameLookupUsesTheIndexNotAWalk() {
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
    void caseInsensitiveLookupCoversAllTypes() {
        for (DataType t : DataType.values()) {
            assertEquals(t, DataType.findIgnoreCase(t.label()),
                    "el indice debe contener " + t.label());
            assertEquals(t, DataType.findIgnoreCase(t.label().toUpperCase()),
                    "el indice sin mayusculas debe cubrir " + t.label());
        }
        assertNull(DataType.findIgnoreCase(null));
    }

    @Test
    void theNameListIsAlwaysTheSame() {
        String primera = DataType.names();
        assertEquals(primera, DataType.names(),
                "ahora es una constante calculada al cargar la clase");
        assertTrue(primera.contains("byte") && primera.contains("string"));
    }

    @Test
    void siemensTypesAreDistinguishedFromStandardOnes() {
        for (DataType t : List.of(DataType.S5TIME, DataType.S7DATE, DataType.S7TIME,
                DataType.S7TOD, DataType.S7DAT, DataType.S7COUNTER, DataType.S7DI,
                DataType.S7AI, DataType.S7AO, DataType.S7VALVE, DataType.S7VLV,
                DataType.S7AVLV, DataType.S7MOTOR)) {
            assertTrue(t.isProtocolSpecific(),
                    t.label() + " solo existe en Siemens");
        }
    }

    @Test
    void standardTypesAreNotProtocolSpecific() {
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