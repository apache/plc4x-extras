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

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class CommunicationWizardStateUuidNuloTest {

    private static CommunicationWizardState stateWithOneVariable() {
        CommConfigData.GroupConfig grupo =
                new CommConfigData.GroupConfig("uuid-grupo", "Rapido", "d", "100", true, "md5-g");
        CommConfigData.ItemConfig item = new CommConfigData.ItemConfig(
                "uuid-area", "Area", "desc", "%DB1.DBB0[0..9]:INT", true, "md5-a", "uuid-grupo");
        CommConfigData.PvConfig pv = new CommConfigData.PvConfig(
                "uuid-pv", "Level", "int", "Area 1", "0", "descripcion",
                "100", true, false, "1", "5", "d", "f", "u", "2", "6", "1", "md5-p", "Area/Level");

        CommunicationWizardState state = new CommunicationWizardState();
        state.reset(new CommConfigData("PLC", List.of(grupo), List.of(item), List.of(pv)));
        return state;
    }

    @Test
    void buscarUnaVariableConListaLlenaYUuidNuloNoRevienta() {
        CommunicationWizardState state = stateWithOneVariable();

        assertEquals(-1, state.indexOfPv(null),
                "no hay ninguna fila editada todavia, y eso no es un error");
        assertNull(state.pvByUuid(null), "y por tanto no hay ninguna variable que devolver");
    }

    @Test
    void buscarUnAreaConListaLlenaYUuidNuloNoRevienta() {
        CommunicationWizardState state = stateWithOneVariable();

        assertEquals(-1, state.indexOfItem(null));
        assertNull(state.itemByUuid(null));
    }

    @Test
    void buscarUnGrupoConListaLlenaYUuidNuloNoRevienta() {
        CommunicationWizardState state = stateWithOneVariable();

        assertEquals(-1, state.indexOfGroup(null));
        assertNull(state.groupByUuid(null));
    }

    /** Con la lista vacia tampoco hay que reventar, que es el caso que nunca se notaba. */
    @Test
    void withEmptyListsANullUuidDoesNotBlowUp() {
        CommunicationWizardState state = new CommunicationWizardState();

        assertEquals(-1, state.indexOfPv(null));
        assertEquals(-1, state.indexOfItem(null));
        assertEquals(-1, state.indexOfGroup(null));
    }

    /** La guarda no puede romper la busqueda normal, que es para lo que existe. */
    @Test
    void validUuidStillFindsItsRow() {
        CommunicationWizardState state = stateWithOneVariable();

        assertEquals(0, state.indexOfPv("uuid-pv"));
        assertEquals(0, state.indexOfItem("uuid-area"));
        assertEquals(0, state.indexOfGroup("uuid-grupo"));

        assertEquals("uuid-pv", state.pvByUuid("uuid-pv").getUuid());
        assertEquals("uuid-area", state.itemByUuid("uuid-area").getUuid());
        assertEquals("uuid-grupo", state.groupByUuid("uuid-grupo").getUuid());
    }

    /** Un uuid que no existe sigue siendo "no encontrado", no un fallo. */
    @Test
    void uuidThatDoesNotExistIsStillNotFound() {
        CommunicationWizardState state = stateWithOneVariable();

        assertEquals(-1, state.indexOfPv("uuid-inexistente"));
        assertNull(state.pvByUuid("uuid-inexistente"));
    }

    @Test
    void rowWithNullUuidIsNotFoundWhenSearchingNull() {
        CommConfigData.PvConfig sinUuid = new CommConfigData.PvConfig(
                null, "Level", "int", "Area 1", "0", "d",
                "100", true, false, "1", "5", "d", "f", "u", "2", "6", "1", "md5", "Area/Level");
        CommunicationWizardState state = new CommunicationWizardState();
        state.reset(new CommConfigData("PLC", List.of(), List.of(), List.of(sinUuid)));

        assertEquals(-1, state.indexOfPv(null),
                "buscar null no puede devolver la fila que tambien es null");
    }
}
