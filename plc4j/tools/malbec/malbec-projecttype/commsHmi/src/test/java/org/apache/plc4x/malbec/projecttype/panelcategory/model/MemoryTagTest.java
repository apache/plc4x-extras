/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MemoryTagTest {

    @Test
    void laFormaGeneralConRangoYTipo() {
        MemoryTag t = MemoryTag.parse("%DB231.DBB0[0..1848]:BYTE");
        assertEquals("DB231", t.block());
        assertEquals("DBB", t.code());
        assertEquals(0, t.baseByte());
        assertEquals(0, t.firstByte());
        assertEquals(1848, t.lastByte());
        assertEquals("BYTE", t.family());
        assertTrue(t.hasRange());
        assertEquals(1849, t.byteCapacity());
    }

    @Test
    void laCantidadVaDespuesDelTipo() {
        MemoryTag t = MemoryTag.parse("%DB231.DBB0:BYTE[1849]");
        assertEquals("DB231", t.block());
        assertEquals("DBB", t.code());
        assertEquals("BYTE", t.family());
        assertFalse(t.hasRange(), "aquí no hay rango, hay cantidad");
        assertEquals(1849, t.byteCapacity());
    }

    @Test
    void unIndiceSueltoAntesDelTipoEsUnSoloElemento() {
        MemoryTag t = MemoryTag.parse("%DB1.DBB0[10]:BYTE");
        assertEquals(10, t.firstByte());
        assertEquals(10, t.lastByte());
        assertEquals(1, t.byteCapacity(), "un índice es un elemento, no diez");
    }

    @Test
    void laFormaCortaDeBloqueSeAcepta() {
        MemoryTag t = MemoryTag.parse("%DB1:0:INT");
        assertEquals("DB1", t.block());
        assertEquals(0, t.baseByte());
        assertEquals("INT", t.family());
        assertEquals(-1, t.bit(), "sin punto final no hay offset de bit");
    }

    @Test
    void lasDireccionesDeBitLlevanElOffsetAlFinal() {
        MemoryTag m = MemoryTag.parse("%M0.0:BOOL");
        assertEquals("M", m.code());
        assertEquals(0, m.baseByte());
        assertEquals(0, m.bit());
        assertEquals("BOOL", m.family());
        assertEquals(1, m.codeBits());

        MemoryTag db = MemoryTag.parse("%DB1.DBX0.0:BOOL");
        assertEquals("DB1", db.block());
        assertEquals("DBX", db.code());
        assertEquals(0, db.bit());
        assertEquals(1, db.codeBits());
    }

    @Test
    void laFormaCortaTambienAdmiteBit() {
        MemoryTag t = MemoryTag.parse("%DB1:0.0:BOOL");
        assertEquals("DB1", t.block());
        assertEquals(0, t.baseByte());
        assertEquals(0, t.bit());
        assertEquals(1, t.codeBits());
    }

    @Test
    void elPorcentajeYElTipoSonOpcionales() {
        MemoryTag con = MemoryTag.parse("%MW20:INT");
        MemoryTag sin = MemoryTag.parse("MW20");
        assertEquals("MW", con.code());
        assertEquals("MW", sin.code());
        assertEquals("INT", con.family());
        assertNull(sin.family());
        assertEquals(con.codeBits(), sin.codeBits(), "el % no cambia el código");
        assertEquals(2, sin.byteCapacity(), "un word son dos bytes aunque no declare nada");
    }

    @Test
    void unCodigoDeAreaPeladoDaSuTamano() {
        assertEquals(1, MemoryTag.parse("%MB20").byteCapacity());
        assertEquals(2, MemoryTag.parse("%MW20").byteCapacity());
        assertEquals(4, MemoryTag.parse("%MD20").byteCapacity());
        assertEquals(0, MemoryTag.parse("%M0.0").byteCapacity(),
                "un bit no ocupa un byte entero, así que no acota");
    }

    @Test
    void lasMarcasEntradasYSalidasSeAceptan() {
        assertEquals(1, MemoryTag.parse("%I0.0").codeBits());
        assertEquals(1, MemoryTag.parse("%Q0.0").codeBits());
        assertEquals("QB", MemoryTag.parse("%QB20").code());
    }

    @Test
    void laLongitudDeclaradaDeUnStringSeAcepta() {
        MemoryTag t = MemoryTag.parse("%DB1.DBB0:STRING(20)");
        assertEquals("STRING", t.family());
        assertEquals(20, t.byteCapacity());
    }


    @Test
    void elCodigoDeAreaDaElTamano() {
        assertEquals(1, MemoryTag.parse("%DB21.DBX7[0..3]").codeBits());
        assertEquals(8, MemoryTag.parse("%DB21.DBB7[0..3]").codeBits());
        assertEquals(16, MemoryTag.parse("%DB21.DBW7[0..3]").codeBits());
        assertEquals(32, MemoryTag.parse("%DB21.DBD7[0..3]").codeBits());
        assertEquals(64, MemoryTag.parse("%DB21.DBLD7[0..3]").codeBits(),
                "DBLD es de ocho bytes, no de cuatro");
        assertEquals(8, MemoryTag.parse("%MB7[0..3]").codeBits());
        assertEquals(32, MemoryTag.parse("%MD7[0..3]").codeBits());
    }

    @Test
    void unCodigoQueNoSeReconoceNoDaUnTamanoInventado() {
        assertEquals(0, MemoryTag.parse("%DB21.DBZ7[0..3]").codeBits(),
                "sin código conocido sólo se puede usar el rango");
        assertEquals(4, MemoryTag.parse("%DB21.DBZ7[0..3]").byteCapacity(),
                "el rango sigue mandando aunque el código no se conozca");
    }

    @Test
    void elRangoEsRelativoAlByteBaseDelTag() {
        MemoryTag t = MemoryTag.parse("%DB21.DBB4[0..9]:BYTE");
        assertEquals(4, t.startByteEffective());
        assertEquals(13, t.endByteEffective());
        assertEquals(10, t.byteCapacity());
    }

    @Test
    void unRangoQueEmpiezaEnOtroSitioSeDesplazaIgual() {
        MemoryTag t = MemoryTag.parse("%DB21.DBB4[10..19]:BYTE");
        assertEquals(4, t.baseByte());
        assertEquals(10, t.firstByte());
        assertEquals(19, t.lastByte());
        assertEquals(14, t.startByteEffective());
        assertEquals(23, t.endByteEffective());
    }

    @Test
    void unRangoDeUnSoloByteCabe() {
        MemoryTag t = MemoryTag.parse("%DB21.DBB0[7..7]:BYTE");
        assertEquals(1, t.byteCapacity());
        assertEquals(7, t.startByteEffective());
        assertEquals(7, t.endByteEffective());
    }

    @Test
    void elRangoAlRevesSeMarcaEnLieuDeRechazarse() {
        MemoryTag t = MemoryTag.parse("%DB21.DBB0[1848..0]:BYTE");
        assertFalse(t.rangoValido(), "es un error de tecleo y hay que avisar");
        assertEquals(0, t.byteCapacity(), "pero no se calcula nada con él");
    }

    @Test
    void unBloqueSinDireccionNoEsUnaDireccion() {
        assertFalse(MemoryTag.parse("%DB1[]").tieneDireccion(),
                "un bloque sin dirección no se puede leer del PLC");
        assertFalse(MemoryTag.parse("%DB1[0..9]").tieneDireccion(),
                "el rango no sustituye a la dirección que falta");
        assertFalse(MemoryTag.parse("%DBI3").tieneDireccion(),
                "DBI tampoco vale como código de área");
    }

    @Test
    void losCodigosDeAreaDelDriverSeAceptan() {
        assertEquals("MW", MemoryTag.parse("%MW20").code());
        assertEquals("MD", MemoryTag.parse("%MD100").code());
        assertEquals("IB", MemoryTag.parse("%IB0.0").code());
        assertEquals("C", MemoryTag.parse("%C5").code());
        assertEquals("T", MemoryTag.parse("%T5").code());
        assertTrue(MemoryTag.parse("%MW20").tieneDireccion());
    }

    @Test
    void unaBasuraConLetrasYNumerosNoEsUnaDireccion() {
        assertFalse(MemoryTag.parse("basura1").tieneDireccion());
        assertFalse(MemoryTag.parse("LD0").tieneDireccion());
        assertFalse(MemoryTag.parse("contador7").tieneDireccion());
    }

    @Test
    void sinRangoElTamanoSaleDelTipoDeclarado() {
        assertEquals(2, MemoryTag.parse("%DB1:10:INT").byteCapacity());
        assertEquals(2, MemoryTag.parse("%DB1:10:UINT").byteCapacity());
        assertEquals(4, MemoryTag.parse("%DB1:10:DINT").byteCapacity());
        assertEquals(4, MemoryTag.parse("%DB1:10:REAL").byteCapacity());
        assertEquals(8, MemoryTag.parse("%DB1:10:LINT").byteCapacity());
        assertEquals(8, MemoryTag.parse("%DB1:10:LREAL").byteCapacity());
        assertEquals(1, MemoryTag.parse("%DB1:10:SINT").byteCapacity());
    }

    @Test
    void elEspacioAlrededorYEnMedioSeIgnora() {
        assertEquals(1849, MemoryTag.parse("  %DB231.DBB0[0..1848]:BYTE  ").byteCapacity());
        MemoryTag conEspacio = MemoryTag.parse("%DB20. DBB4[0..16]:REAL");
        assertEquals("DB20", conEspacio.block());
        assertEquals("DBB", conEspacio.code());
        assertEquals(4, conEspacio.baseByte());
        assertEquals("REAL", conEspacio.family());
        assertEquals(17, conEspacio.byteCapacity());
    }

    @Test
    void losEjemplosDelDriverSeInterpretanComoSeDice() {
        MemoryTag bit = MemoryTag.parse("%DB1.DBX0.0:BOOL");
        assertEquals("DB1", bit.block());
        assertEquals("DBX", bit.code());
        assertEquals(0, bit.baseByte());
        assertEquals(0, bit.bit());
        assertEquals("BOOL", bit.family());

        MemoryTag entero = MemoryTag.parse("%DB10.DBW20:INT");
        assertEquals("DB10", entero.block());
        assertEquals("DBW", entero.code());
        assertEquals(20, entero.baseByte());
        assertEquals(2, entero.byteCapacity());

        assertEquals("I", MemoryTag.parse("%I0.0:BOOL").code());
        assertEquals(0, MemoryTag.parse("%I0.0:BOOL").bit());

        MemoryTag marca = MemoryTag.parse("%MD100:DINT");
        assertEquals("MD", marca.code());
        assertEquals(100, marca.baseByte());
        assertEquals("DINT", marca.family());

        assertEquals(10, MemoryTag.parse("%DB1.DBB0[0..9]:BYTE").byteCapacity());
        
        MemoryTag corto = MemoryTag.parse("%DB5:10:INT");
        assertEquals("DB5", corto.block());
        assertEquals(10, corto.baseByte());
        assertEquals("INT", corto.family());

        MemoryTag crudo = MemoryTag.parse("%DB1.DBB0[0..15]:RAW_BYTE_ARRAY");
        assertEquals(16, crudo.byteCapacity());
        assertEquals("RAW_BYTE_ARRAY", crudo.family());

        assertEquals("DB1", MemoryTag.parse("%DB1.DB0:STRING(80)").block());
        assertEquals("", MemoryTag.parse("%DB1.DB0:STRING(80)").code());
        assertEquals(80, MemoryTag.parse("%DB1.DB0:STRING(80)").byteCapacity());
        assertEquals("STRING", MemoryTag.parse("%DB1.DB0:STRING").family());
    }

    @Test
    void lasDireccionesEspecialesNoSonDireccionesDeMemoria() {
        MemoryTag alarma = MemoryTag.parse("ALM");
        assertFalse(alarma.tieneDireccion());
        assertFalse(DataType.candidatos(alarma.family(), alarma.codeBits()).size() > 0,
                "no debe ofrecer tipos");

        MemoryTag consulta = MemoryTag.parse("QUERY:ALARM_S");
        assertFalse(consulta.tieneDireccion());
        assertTrue(DataType.candidatos(consulta.family(), consulta.codeBits()).isEmpty());
    }

    @Test
    void unaDireccionDeModbusSeAceptaTalCual() {
        MemoryTag t = MemoryTag.parse("40001");
        assertEquals(40001, t.baseByte());
        assertNull(t.family());
        assertEquals(0, t.byteCapacity(), "sin código ni rango no se puede acotar");
        assertEquals("", t.code());
        assertEquals("", t.block());
        assertTrue(t.tieneDireccion(), "el número es una dirección y sí se puede comparar");
        assertFalse(t.solapaCon(MemoryTag.parse("40002")));
        assertTrue(t.solapaCon(MemoryTag.parse("40001")));
    }

    @Test
    void unTagSimbolicoNoSeInterpreta() {
        MemoryTag t = MemoryTag.parse("Control_Panel.Start_Button");
        assertNotNull(t, "un tag simbólico nunca debe rechazarse");
        assertEquals("Control_Panel.Start_Button", t.raw());
        assertEquals("", t.code());
        assertEquals(-1, t.baseByte(), "no se inventa un número de byte");
        assertEquals(0, t.byteCapacity());
        assertFalse(t.tieneDireccion(), "y por tanto no se compara con nadie");
    }

    @Test
    void unTagSimbolicoConNumeroAlFinalNoSeConfundeConUnBit() {
        MemoryTag t = MemoryTag.parse("Data_Block_1.Motor1");
        assertEquals(-1, t.bit(), "el 1 final es parte del nombre, no un bit");
        assertEquals("", t.code(), "no es un bloque con código, es un nombre");
        assertFalse(t.tieneDireccion());
    }

    @Test
    void unTagSimbolicoNoSeParteEnDosPorUnosPuntos() {
        MemoryTag t = MemoryTag.parse("Struct.Field:Subfield");
        assertEquals(0, DataType.candidatos(t.family(), t.codeBits()).size(),
                "una familia inventada no debe ofrecer tipos");
    }

    @Test
    void soloUnTagVacioDevuelveNull() {
        assertNull(MemoryTag.parse(null));
        assertNull(MemoryTag.parse(""));
        assertNull(MemoryTag.parse("   "));
    }

    @Test
    void cualquierTagRaroSeAceptaYNoRevienta() {
        String[] raros = {
            "DB21.DBB0[0..9",              
            "%DB21.DBB0[0..9]:BYTE:B",      
            "%DB21.DBB0[0,9]",              
            "%DB21.DBB0[",                 
            "%DB21.DBB0:STRING(",           
            "basura",
            "%%",
            "%",
            "Data.1.Struct.Field",
            "%DB231.DBB0[0..1848]:BOOL:extra",
        };
        for (String tag : raros) {
            assertNotNull(MemoryTag.parse(tag), "el tag '" + tag + "' no debería rechazarse");
        }
    }
    
    @Test
    void elTagOriginalSeConservaParaLosMensajes() {
        assertEquals("%DB20. DBB4[0..16]:REAL",
                MemoryTag.parse("%DB20. DBB4[0..16]:REAL").raw());
        assertEquals("  %DB21.DBB4[0..9]:BYTE  ",
                MemoryTag.parse("  %DB21.DBB4[0..9]:BYTE  ").raw());
    }

    @Test
    void laDireccionEsLaParteSinSeleccionNiTipo() {
        MemoryTag conRango = MemoryTag.parse("%DB21.DBB4[0..9]:INTEGER");
        assertEquals("DB21", conRango.block());
        assertEquals("DBB", conRango.code());
        assertEquals(4, conRango.baseByte());
        assertEquals(0, conRango.firstByte());
        assertEquals(9, conRango.lastByte());

        MemoryTag sinRango = MemoryTag.parse("%DB21.DBB4:INT");
        assertEquals("DB21", sinRango.block());
        assertEquals("DBB", sinRango.code());
        assertEquals(4, sinRango.baseByte());
        assertEquals(-1, sinRango.firstByte(), "sin corchete no hay selección");

        MemoryTag palabra = MemoryTag.parse("%MW66:WORD");
        assertEquals("", palabra.block());
        assertEquals("MW", palabra.code());
        assertEquals(66, palabra.baseByte());

        MemoryTag bit = MemoryTag.parse("%M0.0:BOOL");
        assertEquals(0, bit.baseByte());
        assertEquals(0, bit.bit(), "el bit forma parte de la dirección");

        MemoryTag formaCorta = MemoryTag.parse("%DB1:0.0:BOOL");
        assertEquals("DB1", formaCorta.block());
        assertEquals("", formaCorta.code(), "en la forma corta el código no está, pero el sitio sí");
        assertEquals(0, formaCorta.baseByte());
        assertEquals(0, formaCorta.bit());

        MemoryTag modbus = MemoryTag.parse("40001");
        assertEquals(40001, modbus.baseByte());
        assertEquals("", modbus.code());
    }

    @Test
    void unTagSinDireccionDevuelveElTagEnteroComoIdentidad() {
        assertEquals("Control_Panel.Start_Button",
                MemoryTag.parse("Control_Panel.Start_Button").block());
        assertEquals("basura", MemoryTag.parse("basura").block());
    }

    @Test
    void dosRangosSeguidosEnLaMismaDireccionNoSePisan() {
        MemoryTag primera = MemoryTag.parse("%DB22.DBB4[10..16]:REAL");
        MemoryTag segunda = MemoryTag.parse("%DB22.DBB4[17..22]:REAL");
        assertEquals(14, primera.startByteEffective());
        assertEquals(20, primera.endByteEffective());
        assertEquals(21, segunda.startByteEffective());
        assertEquals(26, segunda.endByteEffective());
        assertFalse(primera.solapaCon(segunda), "son dos áreas legítimas");
        assertFalse(segunda.solapaCon(primera));
        assertEquals(primera.block(), segunda.block(),
                "mismo bloque y mismo byte de arranque, y aun así no se pisan");
        assertEquals(primera.baseByte(), segunda.baseByte());
    }

    @Test
    void dosAreasDelMismoBloqueSeDetectan() {
        MemoryTag grande = MemoryTag.parse("%DB231.DBB0[0..1848]:BYTE");
        assertTrue(grande.solapaCon(MemoryTag.parse("%DB231.DBB10[0..5]:BYTE")));
        assertTrue(grande.solapaCon(MemoryTag.parse("DB231.DBB1848[0..0]:BYTE")),
                "da igual si el usuario pone el % o no");
        assertFalse(grande.solapaCon(MemoryTag.parse("%DB231.DBB3000[0..5]:BYTE")));
    }

    @Test
    void areasDeBloquesDistintosNoSePisan() {
        MemoryTag a = MemoryTag.parse("%DB231.DBB0[0..1848]:BYTE");
        assertFalse(a.solapaCon(MemoryTag.parse("%DB232.DBB0[0..1848]:BYTE")),
                "DB231 y DB232 son sitios distintos");
    }

    @Test
    void conRangoSeComparanLosBytesAunqueElCodigoSeaDistinto() {
        MemoryTag a = MemoryTag.parse("%DB231.DBB0[0..1848]:BYTE");
        assertTrue(a.solapaCon(MemoryTag.parse("%DB231.DBW0[0..100]:WORD")),
                "los dos ocupan los mismos bytes del mismo bloque");
    }

    @Test
    void cadaBitEsUnSitioDistinto() {
        MemoryTag bit0 = MemoryTag.parse("%M0.0:BOOL");
        assertTrue(bit0.solapaCon(MemoryTag.parse("%M0.0:BOOL")), "el mismo bit sí se repite");
        assertFalse(bit0.solapaCon(MemoryTag.parse("%M0.1:BOOL")), "otro bit es otro sitio");
        assertFalse(bit0.solapaCon(MemoryTag.parse("%M1.0:BOOL")), "otro byte es otro sitio");
        assertFalse(bit0.solapaCon(MemoryTag.parse("%DB1.DBX0.0:BOOL")),
                "otra área de memoria es otro sitio");
    }

@Test
    void unAreaSinDireccionNoPisaANadie() {
        MemoryTag grande = MemoryTag.parse("%DB231.DBB0[0..1848]:BYTE");
        assertFalse(grande.solapaCon(MemoryTag.parse("Control_Panel.Start")));
        assertFalse(grande.solapaCon(MemoryTag.parse("40001")),
                "una direcciA3n Modbus no se mide en bytes de bloque");
        assertFalse(grande.solapaCon(null));
    }

    @Test
    void unNumeroDemasiadoGrandeNoLanza() {
        assertDoesNotThrow(() -> MemoryTag.parse("%DB1.DBB0[99999999999]:BYTE"));
        assertDoesNotThrow(() -> MemoryTag.parse("%DB99999999999.DBB1"));
        assertDoesNotThrow(() -> MemoryTag.parse("%DB1.DBB0[0..99999999999]:BYTE"));
        assertDoesNotThrow(() -> MemoryTag.parse("%DB1.DBB0[99999999999]"));
        assertDoesNotThrow(() -> MemoryTag.parse("%DB1.DBB0:STRING(99999999999)"));
        assertDoesNotThrow(() -> MemoryTag.parse("99999999999"));
    }

    @Test
    void unNumeroDemasiadoGrandeSeQuedaSinRango() {
        MemoryTag tag = MemoryTag.parse("%DB1.DBB0[99999999999]:BYTE");

        assertTrue(tag.rangoValido(), "no se puede decir que un rango que no existe vaya al revés");
        assertEquals(-1, tag.firstByte());
    }

    @Test
    void losNumerosQueSiCabenSeInterpretanIgualQueAntes() {
        MemoryTag tag = MemoryTag.parse("%DB231.DBB0[0..1848]:BYTE");
        assertEquals(0, tag.firstByte());
        assertEquals(1848, tag.lastByte());
    }
}
