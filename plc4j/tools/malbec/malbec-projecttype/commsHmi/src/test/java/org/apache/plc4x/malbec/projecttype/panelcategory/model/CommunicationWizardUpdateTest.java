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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.NoopCommunicationStore;
import org.junit.jupiter.api.Test;

class CommunicationWizardUpdateTest {

    private static final String GRUPO_A = "grupo-a";
    private static final String GRUPO_B = "grupo-b";
    private static final String AREA = "area-1";
    private static final String PV = "pv-1";

    private static CommunicationWizardController controller() {
        return new CommunicationWizardController(
                new NoopCommunicationStore(), new CommunicationWizardState());
    }

    private static void twoGroups(CommunicationWizardController c) {
        c.addGroup(GRUPO_A, "Rapido", "desc rapido", "500", true);
        c.addGroup(GRUPO_B, "Lento", "desc lento", "2000", false);
    }

    @Test
    void actualizarUnGrupoCambiaSoloEseGrupo() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        c.updateGroup(GRUPO_B, "Lento editado", "900", true);

        assertEquals("Rapido", c.getState().groupByUuid(GRUPO_A).getName(),
                "el grupo que no se edito no puede cambiar");
        assertEquals("500", c.getState().groupByUuid(GRUPO_A).getScantime());
        assertEquals("Lento editado", c.getState().groupByUuid(GRUPO_B).getDescription());
        assertEquals("900", c.getState().groupByUuid(GRUPO_B).getScantime());
        assertTrue(c.getState().groupByUuid(GRUPO_B).isEnable());
    }

    @Test
    void actualizarUnGrupoMantieneElOrdenDeLaLista() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        c.updateGroup(GRUPO_A, "Rapido editado", "100", false);

        List<CommConfigData.GroupConfig> grupos = c.getState().getGroups();
        assertEquals(2, grupos.size());
        assertEquals(GRUPO_A, grupos.get(0).getUuid(), "el grupo editado sigue en su posicion");
        assertEquals(GRUPO_B, grupos.get(1).getUuid());
    }

    @Test
    void dosGruposIdenticosSalvoElUuidSeEditanElElegido() {
        CommunicationWizardController c = controller();
        
        c.addGroup(GRUPO_A, "Mismo nombre", "desc", "500", true);
        c.addGroup(GRUPO_B, "Mismo nombre", "desc", "500", true);
        c.updateGroup(GRUPO_B, "Editado", "500", true);

        List<CommConfigData.GroupConfig> grupos = c.getState().getGroups();
        assertEquals("desc", grupos.get(0).getDescription(),
                "T3.1: con equals estructural, indexOf puede devolver la fila gemela equivocada");
        assertEquals("Editado", grupos.get(1).getDescription(),
                "la edicion tiene que caer en la fila cuyo UUID se pidio");
    }

    @Test
    void dosRegistrosIdenticosSalvoElUuidSeEditanElElegido() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, "Area 1", "mismo nombre"));
        c.addPv(pv("pv-2", "Area 1", "mismo nombre"));

        c.updatePv("pv-2", "mismo nombre", "Editado", "500", true, true,
                "0", "1", "", "", "", "0", "1", "1");

        List<CommConfigData.PvConfig> pvs = c.getState().getPvs();
        assertEquals("descriptor", pvs.get(0).getDescriptorText(),
                "T3.1: el registro gemelo no puede absorber la edicion");
        assertEquals("Editado", pvs.get(1).getDescriptorText());
    }

    @Test
    void actualizarPvPuedeCambiarElNombre() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, "Area 1", "Nombre viejo"));

        c.updatePv(PV, "Nombre nuevo", "descriptor", "500", true, true,
                "0", "1", "", "", "", "0", "1", "1");

        assertEquals("Nombre nuevo", c.getState().pvByUuid(PV).getName());
    }

    @Test
    void scantimeVacioConservaElAnterior() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        c.updateGroup(GRUPO_A, "Rapido", "", true);

        assertEquals("500", c.getState().groupByUuid(GRUPO_A).getScantime(),
                "un scantime vacio no debe borrar el que ya habia");
    }

    @Test
    void actualizarUnGrupoInexistenteNoRompeNada() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        c.updateGroup("no-existe", "x", "1", true);

        assertEquals(2, c.getState().getGroups().size());
    }

    @Test
    void borrarUnGrupoSinAreasLoElimina() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        assertTrue(c.deleteGroup(GRUPO_A).isEmpty(), "no hay areas, asi que no hay nada que avisar");
        assertEquals(1, c.getState().getGroups().size());
        assertNull(c.getState().groupByUuid(GRUPO_A));
        assertEquals(GRUPO_B, c.getState().getGroups().get(0).getUuid(),
                "el otro grupo debe quedarse en su sitio");
    }

    @Test
    void borrarUnGrupoConAreasAvisarYNoBorra() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);

        List<String> areas = c.deleteGroup(GRUPO_A);

        assertEquals(1, areas.size(), "debe devolver las areas que bloquean el borrado");
        assertEquals("Area 1", areas.get(0));
        assertEquals(2, c.getState().getGroups().size(), "el grupo no se puede borrar mientras tenga areas");
        assertNotNull(c.getState().groupByUuid(GRUPO_A), "y debe seguir en la lista");
    }

    @Test
    void borrarUnGrupoInexistenteNoDevuelveAreas() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        assertTrue(c.deleteGroup("no-existe").isEmpty());
        assertEquals(2, c.getState().getGroups().size());
    }

    @Test
    void actualizarUnAreaMantieneSuGrupoYSuMd5() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "desc", "%DB1.DBB0:INT", true, GRUPO_A);
        CommConfigData.ItemConfig antes = c.getState().itemByUuid(AREA);

        c.updateItem(AREA, "desc editada", "%DB1.DBB2:INT", false);

        CommConfigData.ItemConfig despues = c.getState().itemByUuid(AREA);
        assertEquals("desc editada", despues.getDescription());
        assertEquals("%DB1.DBB2:INT", despues.getTag());
        assertFalse(despues.isEnable());
        assertEquals(GRUPO_A, despues.getGroupUuid(), "editar un area no puede sacarla de su grupo");
        assertEquals(antes.getMd5(), despues.getMd5(), "el md5 lo fija el modelo, no la edicion");
        assertEquals(1, c.getState().getItems().size());
    }

    @Test
    void actualizarUnRegistroMantieneElOrdenYElArea() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, "Area 1", "pv-0"));
        c.addPv(pv("pv-2", "Area 1", "pv-2"));
        c.addPv(pv("pv-3", "Area 1", "pv-3"));

        c.updatePv(PV, "pv-0", "descriptor nuevo", "750", false, true,
                "-1", "1", "Desc", "%.3f", "u", "-9", "9", "0.5");

        List<CommConfigData.PvConfig> pvs = c.getState().getPvs();
        assertEquals(3, pvs.size());
        assertEquals(PV, pvs.get(0).getUuid(), "el registro editado sigue el primero");
        assertEquals("descriptor nuevo", pvs.get(0).getDescriptorText());
        assertEquals("750", pvs.get(0).getScanTime());
        assertFalse(pvs.get(0).isScanEnable());
        assertTrue(pvs.get(0).isWriteEnable());
        assertEquals("%.3f", pvs.get(0).getDisplayFormat());
        assertEquals("Area 1", pvs.get(0).getAreaName(), "editar un registro no puede cambiar su area");
        assertEquals("pv-2", pvs.get(1).getUuid(), "los demas no se tocan");
    }

    @Test
    void actualizarUnRegistroInexistenteNoRompeNada() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, "Area 1", "pv-0"));

        c.updatePv("no-existe", "nombre", "d", "1", true, true,
                "0", "1", "", "", "", "0", "1", "1");

        assertEquals(1, c.getState().getPvs().size());
        assertEquals(PV, c.getState().getPvs().get(0).getUuid());
    }

    @Test
    void borrarUnRegistroDevuelveFalseSiNoExiste() {
        CommunicationWizardController c = controller();

        assertFalse(c.deletePv("no-existe"));
    }

    @Test
    void borrarUnAreaSeLevaSusRegistros() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, "Area 1", "pv-0"));
        c.addPv(pv("pv-otro", "otra-area", "pv-x"));

        List<String> names = c.deleteItem(AREA);

        assertEquals(List.of("pv-0"), names, "solo los registros del area borrada se avisan");
        assertEquals(1, c.getState().getPvs().size(), "los del area desaparecida se van");
        assertEquals("pv-otro", c.getState().getPvs().get(0).getUuid());
        assertTrue(c.getState().getItems().isEmpty());
    }

    @Test
    void changesMarkTheStateAsDirty() {
        CommunicationWizardState state = new CommunicationWizardState();
        assertFalse(state.hasUnsavedChanges(), "precondición: recién creado está limpio");

        CommunicationWizardController c =
                new CommunicationWizardController(new NoopCommunicationStore(), state);
        c.addGroup(GRUPO_A, "Rapido", "desc", "500", true);

        assertTrue(state.hasUnsavedChanges(),
                "crear un grupo ya es un cambio pendiente de guardar");
    }

    @Test
    void editarUnGrupoQueNoExisteNoMarcaCambios() {
        CommunicationWizardState state = new CommunicationWizardState();
        CommunicationWizardController c =
                new CommunicationWizardController(new NoopCommunicationStore(), state);

        c.updateGroup("no-existe", "x", "1", true);

        assertFalse(state.hasUnsavedChanges(),
                "una edicion que no localiza su grupo no puede ensuciar el estado");
    }

    @Test
    void areaWithoutGroupHasNoGroupName() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, "");

        assertEquals("", c.getState().groupNameOf(AREA));
        assertEquals(List.of(c.getState().itemByUuid(AREA)), c.getState().areasWithoutGroup());
    }

    @Test
    void areaPointingToAMissingGroupCountsAsWithoutGroup() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, "grupo-fantasma");

        assertEquals("", c.getState().groupNameOf(AREA),
                "un grupo que no existe no puede devolver un nombre");
        assertEquals(List.of(c.getState().itemByUuid(AREA)), c.getState().areasWithoutGroup());
    }

@Test
    void vaciarElEstadoOlvidaLosAgrupamientos() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);

        c.getState().reset(null);

        assertEquals("", c.getState().groupNameOf(AREA));
        assertTrue(c.getState().areasWithoutGroup().isEmpty(), "no quedan areas tras vaciar");
    }

    @Test
    void recargarElEstadoRecuperaLosAgrupamientosDesdeElModelo() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);

        c.getState().reset(new CommConfigData("PLC",
                List.of(new CommConfigData.GroupConfig(GRUPO_A, "Rapido", "d", "500", true, "m"),
                        new CommConfigData.GroupConfig(GRUPO_B, "Lento", "d", "2000", false, "m")),
                List.of(new CommConfigData.ItemConfig(AREA, "Area 1", "", "%DB1.DBB0:INT",
                        true, "m", GRUPO_A)),
                List.of()));

        assertEquals("Rapido", c.getState().groupNameOf(AREA),
                "C-02: tras recargar, el grupo sale del propio area");
    }

    @Test
    void moverUnAreaDeGrupoCambiaSuNombreDeGrupo() {
        CommunicationWizardController c = controller();
        twoGroups(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);

        assertEquals("Rapido", c.getState().groupNameOf(AREA));

        c.getState().getItems().set(0, new CommConfigData.ItemConfig(
                AREA, "Area 1", "", "%DB1.DBB0:INT", true, "md5", GRUPO_B));

        assertEquals("Lento", c.getState().groupNameOf(AREA),
                "C-02: el grupo se lee del area, asi que seguir el area basta");
        assertTrue(c.getState().areasWithoutGroup().isEmpty());
    }

    @Test
    void afterEditingAGroupTheStateIsDirtyAndCanBeDiscarded() {
        CommunicationWizardController c = controller();
        twoGroups(c);

        c.updateGroup(GRUPO_A, "Rapido editado", "100", false);

        assertTrue(c.getState().hasUnsavedChanges());
        c.getState().reset(null);
        assertFalse(c.getState().hasUnsavedChanges(),
                "descartar los cambios tiene que dejar el estado limpio");
        assertTrue(c.getState().getGroups().isEmpty());
    }

    private static CommConfigData.PvConfig pv(String uuid, String area, String name) {
        return new CommConfigData.PvConfig(uuid, name, "INT", area, "0", "descriptor",
                "500", true, true, "0", "10", "Desc", "%.2f", "bar", "0", "10", "0.1",
                "md5-pv", "S88.1");
    }
}