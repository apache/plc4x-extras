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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.NoopCommunicationStore;
import org.junit.jupiter.api.Test;

class CommunicationWizardControllerCacheTest {

    private static final String AREA = "area-1";
    private static final String OTRO = "area-2";
    private static final String GRUPO = "grupo-1";

    private static CommunicationWizardController controller() {
        return new CommunicationWizardController(
                new NoopCommunicationStore(), new CommunicationWizardState());
    }

    private static void add(CommunicationWizardController c, String uuid, String tag) {
        c.addItem(uuid, "Area " + uuid, "", tag, true, GRUPO);
    }

    @Test
    void detectaElChoqueEntreDosTagsQueSeSolapan() {
        CommunicationWizardController c = controller();
        add(c, AREA, "%DB1.DBB0[0..9]:INT");

        assertNotNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null),
                "dos rangos del mismo bloque que se pisan tienen que avisar");
    }

    @Test
    void updatedTagIsReflectedInValidation() {
        CommunicationWizardController c = controller();
        add(c, AREA, "%DB1.DBB0[0..9]:INT");
        assertNotNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null), "precondición: chocan");

        // El usuario corrige el area para que ya no se solape.
        c.updateItem(AREA, "", "%DB2.DBB0[0..9]:INT", true);

        assertNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null),
                "T3.7: tras editar el tag, la cache no puede seguir devolviendo el solape viejo");
    }

    @Test
    void newTagAfterEditingIsAlsoValidated() {
        CommunicationWizardController c = controller();
        add(c, AREA, "%DB9.DBB0[0..9]:INT");
        assertNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null), "precondición: no chocan");

        c.updateItem(AREA, "", "%DB1.DBB0[0..9]:INT", true);

        assertNotNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null),
                "T3.7: el tag nuevo tiene que entrar en la validacion");
    }

    @Test
    void borrarUnAreaLiberaSuEspacio() {
        CommunicationWizardController c = controller();
        add(c, AREA, "%DB1.DBB0[0..9]:INT");
        assertNotNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null), "precondición: chocan");

        c.deleteItem(AREA);

        assertNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null),
                "T3.7: un area borrada no puede seguir bloqueando el rango");
    }

    @Test
    void areaBeingEditedIsExcludedFromOverlapCalculation() {
        CommunicationWizardController c = controller();
        add(c, AREA, "%DB1.DBB0[0..9]:INT");
        add(c, OTRO, "%DB1.DBB5[0..9]:INT");

        // Excluyendo el area-1, el unico solape legitimo es con el area-2, que se
        // pisa de verdad. Si el area en edicion no se excluyera, saldria el area-1.
        CommConfigData.ItemConfig choque =
                c.overlappingArea("%DB1.DBB0[0..9]:INT", AREA);

        assertNotNull(choque, "el area-2 pisa el rango, asi que debe salir ella");
        assertEquals(OTRO, choque.getUuid(),
                "el area en edicion no debe chocar consigo misma");
    }

    @Test
    void sameTagIsReusedWithoutReparsing() {
        CommunicationWizardController c = controller();
        add(c, AREA, "%DB1.DBB0[0..9]:INT");

        // Dos llamadas seguidas con el mismo tag tienen que devolver el mismo objeto,
        // que es justo lo que significa que la cache se esta usando.
        MemoryTag primero = c.areaTag("Area " + AREA);
        MemoryTag segundo = c.areaTag("Area " + AREA);

        assertSame(primero, segundo,
                "T3.7: el tag ya desglosado tiene que reutilizarse, no reparsearse");
    }

    @Test
    void areaWithoutAddressDoesNotCollideWithARange() {
        CommunicationWizardController c = controller();
        add(c, AREA, "Zona_Manual");

        assertNull(c.overlappingArea("%DB1.DBB5[0..9]:INT", null),
                "un nombre simbolico no tiene bytes, asi que no puede pisar un rango");
    }

    @Test
    void repeatedSymbolicTagIsDetected() {
        CommunicationWizardController c = controller();
        add(c, AREA, "Zona_Manual");

        // Este si es el comportamiento wanted: dos areas con el mismo nombre son un
        // duplicado, y para eso existe areaConMismoTag.
        CommConfigData.ItemConfig duplicado = c.areaWithSameTag("Zona_Manual", null);

        assertNotNull(duplicado, "un tag simbolico repetido tiene que avisar");
        assertEquals(AREA, duplicado.getUuid());
    }
}
