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

class SaveResultTest {

    @Test
    void completeSaveReportsSuccessOnBoth() {
        SaveResult resultado = SaveResult.success();

        assertTrue(resultado.xmlSaved());
        assertTrue(resultado.databaseUpdated());
        assertTrue(resultado.isComplete());
        assertFalse(resultado.isPartial());
    }

    @Test
    void partialSaveReportsXmlYesAndDbNo() {
        SaveResult resultado = SaveResult.partial("boot.db no se pudo actualizar");

        assertTrue(resultado.xmlSaved(),
                "el XML si se escribio: el usuario tiene que saberlo para no repetir el guardado");
        assertFalse(resultado.databaseUpdated());
        assertFalse(resultado.isComplete());
        assertTrue(resultado.isPartial(),
                "es justo el caso que el booleano de antes no podia expresar");
    }

    @Test
    void saveThatWritesNothingReportsBothFlags() {
        SaveResult resultado = SaveResult.notSaved("la carga anterior fallo");

        assertFalse(resultado.xmlSaved(),
                "H-01: con la carga fallida no se puede escribir comunicacion.xml");
        assertFalse(resultado.databaseUpdated(),
                "y tampoco se reconstruye boot.db desde un modelo que no se ha leido");
        assertFalse(resultado.isComplete());
        assertFalse(resultado.isPartial(),
                "no es un guardado parcial: no se escribio nada");
    }

    @Test
    void messageExplainsWhatHappened() {
        assertEquals("boot.db no se pudo actualizar",
                SaveResult.partial("boot.db no se pudo actualizar").message());
        assertEquals("la carga anterior fallo",
                SaveResult.notSaved("la carga anterior fallo").message());
    }

    @Test
    void successfulSaveHasNoErrorMessage() {
        SaveResult resultado = SaveResult.success();

        assertTrue(resultado.message().isEmpty(),
                "no hay nada que avisar cuando todo va bien: " + resultado.message());
    }

    @Test
    void partialSaveWithoutReasonIsUseless() {
        // El mensaje es lo que evita el fallo silencioso de antes: un booleano no decia por
        // que habia vuelto false, y el que llamaba escribia su propio texto.
        assertThrows(IllegalArgumentException.class, () -> SaveResult.partial(""),
                "un guardado parcial sin motivo no sirve de nada");
        assertThrows(IllegalArgumentException.class, () -> SaveResult.notSaved("  "),
                "ni tampoco uno que solo tenga espacios");
    }
}