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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * El resultado de guardar deja de ser un booleano (T4.6, paso 1).
 *
 * <p>{@code save()} devolvia un {@code boolean} que era, en realidad, "se actualizo
 * boot.db". Con ahi dos salidas distintas -no se escribio nada porque la carga habia
 * fallado, o el XML se escribio y boot.db no- el que llamaba no podia distinguirlas, y por eso
 * el dialogo de borrado tiene tres avisos distintos en lugar de uno.
 *
 * <p>Estos tests fijan la forma del resultado y, sobre todo, que sus dos banderas signifiquen
 * cosas distintas: {@code xmlSaved} es la garantia de H-01 de que no se escribe sobre un
 * modelo que no se ha podido leer, y {@code databaseUpdated} es lo que antes viajaba dentro
 * del booleano.
 */
class SaveResultTest {

    @Test
    void unGuardadoCompletoDiceQueSiEnLasDosCosas() {
        SaveResult resultado = SaveResult.exito();

        assertTrue(resultado.xmlSaved());
        assertTrue(resultado.databaseUpdated());
        assertTrue(resultado.isComplete());
        assertFalse(resultado.isPartial());
    }

    @Test
    void unGuardadoParcialDiceQueElXmlSiYLaBaseNo() {
        SaveResult resultado = SaveResult.parcial("boot.db no se pudo actualizar");

        assertTrue(resultado.xmlSaved(),
                "el XML si se escribio: el usuario tiene que saberlo para no repetir el guardado");
        assertFalse(resultado.databaseUpdated());
        assertFalse(resultado.isComplete());
        assertTrue(resultado.isPartial(),
                "es justo el caso que el booleano de antes no podia expresar");
    }

    @Test
    void unGuardadoQueNoEscribeNadaLoDiceEnLasDosFlags() {
        SaveResult resultado = SaveResult.noGuardado("la carga anterior fallo");

        assertFalse(resultado.xmlSaved(),
                "H-01: con la carga fallida no se puede escribir comunicacion.xml");
        assertFalse(resultado.databaseUpdated(),
                "y tampoco se reconstruye boot.db desde un modelo que no se ha leido");
        assertFalse(resultado.isComplete());
        assertFalse(resultado.isPartial(),
                "no es un guardado parcial: no se escribio nada");
    }

    @Test
    void elMensajeExplicaLoQueHaPasado() {
        assertEquals("boot.db no se pudo actualizar",
                SaveResult.parcial("boot.db no se pudo actualizar").message());
        assertEquals("la carga anterior fallo",
                SaveResult.noGuardado("la carga anterior fallo").message());
    }

    @Test
    void unGuardadoCorrectoNoTieneMensajeDeError() {
        SaveResult resultado = SaveResult.exito();

        assertTrue(resultado.message().isEmpty(),
                "no hay nada que avisar cuando todo va bien: " + resultado.message());
    }

    @Test
    void unGuardadoParcialSinMotivoNoSirveDeNada() {
        // El mensaje es lo que evita el fallo silencioso de antes: un booleano no decia por
        // que habia vuelto false, y el que llamaba escribia su propio texto.
        assertThrows(IllegalArgumentException.class, () -> SaveResult.parcial(""),
                "un guardado parcial sin motivo no sirve de nada");
        assertThrows(IllegalArgumentException.class, () -> SaveResult.noGuardado("  "),
                "ni tampoco uno que solo tenga espacios");
    }
}