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

/**
 * Comportamiento de las actualizaciones "en memoria" del asistente.
 *
 * <p>El asistente edita grupos, areas y registros sustituyendo el objeto de la lista por
 * uno nuevo con los campos cambiados. El indice losacaba con {@code indexOf(anterior)}, que
 * con las clases antiguas se resolvia por identidad porque no tenian {@code equals}.
 *
 * <p>T3.1 las convierte en records, y ahi {@code equals} pasa a ser estructural. Estos tests
 * existen para que ese cambio no pueda alterar en silencio que elemento se sustituye: con dos
 * grupos identicos salvo el nombre, el segundo no puede ser reescrito por error.
 */
class CommunicationWizardUpdateTest {

    private static final String GRUPO_A = "grupo-a";
    private static final String GRUPO_B = "grupo-b";
    private static final String AREA = "area-1";
    private static final String PV = "pv-1";

    private static CommunicationWizardController controller() {
        return new CommunicationWizardController(
                new NoopCommunicationStore(), new CommunicationWizardState());
    }

    private static void dosGrupos(CommunicationWizardController c) {
        c.addGroup(GRUPO_A, "Rapido", "desc rapido", "500", true);
        c.addGroup(GRUPO_B, "Lento", "desc lento", "2000", false);
    }

    @Test
    void actualizarUnGrupoCambiaSoloEseGrupo() {
        CommunicationWizardController c = controller();
        dosGrupos(c);

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
        dosGrupos(c);

        c.updateGroup(GRUPO_A, "Rapido editado", "100", false);

        List<CommConfigData.GroupConfig> grupos = c.getState().getGroups();
        assertEquals(2, grupos.size());
        assertEquals(GRUPO_A, grupos.get(0).getUuid(), "el grupo editado sigue en su posicion");
        assertEquals(GRUPO_B, grupos.get(1).getUuid());
    }

    /**
     * T3.1 cambia {@code equals} de identidad a estructural, y el asistente localiza la fila
     * a sustituir con {@code indexOf}. Con identidad eso era inequivoco; con igualdad por
     * valores, dos filas indistinguibles harian que {@code indexOf} devolviese la primera.
     *
     * <p>Este test construye justo esa situacion: dos grupos identicos en todos sus campos,
     * distinguidos solo por el UUID, que es lo que de verdad no puede repetirse. Editar el
     * segundo tiene que tocar el segundo. Si {@code indexOf} devolviera el primero, el cambio
     * se aplicaria a la fila equivocada y el usuario veria como su edicion desaparece.
     */
    @Test
    void dosGruposIdenticosSalvoElUuidSeEditanElElegido() {
        CommunicationWizardController c = controller();
        // addGroup mete el mismo MD5 a todos, asi que solo el UUID, el nombre y la
        // descripcion distinguen estas dos filas.
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
        c.addPv(pv(PV, AREA, "mismo nombre"));
        c.addPv(pv("pv-2", AREA, "mismo nombre"));

        c.updatePv("pv-2", "Editado", "500", true, true,
                "0", "1", "", "", "", "0", "1", "1");

        List<CommConfigData.PvConfig> pvs = c.getState().getPvs();
        assertEquals("descriptor", pvs.get(0).getDescriptor(),
                "T3.1: el registro gemelo no puede absorber la edicion");
        assertEquals("Editado", pvs.get(1).getDescriptor());
    }

    @Test
    void scantimeVacioConservaElAnterior() {
        CommunicationWizardController c = controller();
        dosGrupos(c);

        c.updateGroup(GRUPO_A, "Rapido", "", true);

        assertEquals("500", c.getState().groupByUuid(GRUPO_A).getScantime(),
                "un scantime vacio no debe borrar el que ya habia");
    }

    @Test
    void actualizarUnGrupoInexistenteNoRompeNada() {
        CommunicationWizardController c = controller();
        dosGrupos(c);

        c.updateGroup("no-existe", "x", "1", true);

        assertEquals(2, c.getState().getGroups().size());
    }

    @Test
    void borrarUnGrupoSinAreasLoElimina() {
        CommunicationWizardController c = controller();
        dosGrupos(c);

        assertTrue(c.deleteGroup(GRUPO_A).isEmpty(), "no hay areas, asi que no hay nada que avisar");
        assertEquals(1, c.getState().getGroups().size());
        assertNull(c.getState().groupByUuid(GRUPO_A));
        assertEquals(GRUPO_B, c.getState().getGroups().get(0).getUuid(),
                "el otro grupo debe quedarse en su sitio");
    }

    @Test
    void borrarUnGrupoConAreasAvisarYNoBorra() {
        CommunicationWizardController c = controller();
        dosGrupos(c);
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
        dosGrupos(c);

        assertTrue(c.deleteGroup("no-existe").isEmpty());
        assertEquals(2, c.getState().getGroups().size());
    }

    @Test
    void actualizarUnAreaMantieneSuGrupoYSuMd5() {
        CommunicationWizardController c = controller();
        dosGrupos(c);
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
        c.addPv(pv(PV, AREA, "pv-0"));
        c.addPv(pv("pv-2", AREA, "pv-2"));
        c.addPv(pv("pv-3", AREA, "pv-3"));

        c.updatePv(PV, "descriptor nuevo", "750", false, true,
                "-1", "1", "Desc", "%.3f", "u", "-9", "9", "0.5");

        List<CommConfigData.PvConfig> pvs = c.getState().getPvs();
        assertEquals(3, pvs.size());
        assertEquals(PV, pvs.get(0).getUuid(), "el registro editado sigue el primero");
        assertEquals("descriptor nuevo", pvs.get(0).getDescriptor());
        assertEquals("750", pvs.get(0).getScanTime());
        assertFalse(pvs.get(0).isScanEnable());
        assertTrue(pvs.get(0).isWriteEnable());
        assertEquals("%.3f", pvs.get(0).getDisplayFormat());
        assertEquals(AREA, pvs.get(0).getAreaUuid(), "editar un registro no puede cambiar su area");
        assertEquals("pv-2", pvs.get(1).getUuid(), "los demas no se tocan");
    }

    @Test
    void actualizarUnRegistroInexistenteNoRompeNada() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, AREA, "pv-0"));

        c.updatePv("no-existe", "d", "1", true, true, "0", "1", "", "", "", "0", "1", "1");

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
        dosGrupos(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);
        c.addPv(pv(PV, AREA, "pv-0"));
        c.addPv(pv("pv-otro", "otra-area", "pv-x"));

        List<String> nombres = c.deleteItem(AREA);

        assertEquals(List.of("pv-0"), nombres, "solo los registros del area borrada se avisan");
        assertEquals(1, c.getState().getPvs().size(), "los del area desaparecida se van");
        assertEquals("pv-otro", c.getState().getPvs().get(0).getUuid());
        assertTrue(c.getState().getItems().isEmpty());
    }

    @Test
    void losCambiosMarcanElEstadoComoSucio() {
        CommunicationWizardState state = new CommunicationWizardState();
        assertFalse(state.isCambiosSinGuardar(), "precondición: recién creado está limpio");

        CommunicationWizardController c =
                new CommunicationWizardController(new NoopCommunicationStore(), state);
        c.addGroup(GRUPO_A, "Rapido", "desc", "500", true);

        assertTrue(state.isCambiosSinGuardar(),
                "crear un grupo ya es un cambio pendiente de guardar");
    }

    @Test
    void editarUnGrupoQueNoExisteNoMarcaCambios() {
        CommunicationWizardState state = new CommunicationWizardState();
        CommunicationWizardController c =
                new CommunicationWizardController(new NoopCommunicationStore(), state);

        c.updateGroup("no-existe", "x", "1", true);

        assertFalse(state.isCambiosSinGuardar(),
                "una edicion que no localiza su grupo no puede ensuciar el estado");
    }

    @Test
    void unAreaSinGrupoNoTieneNombreDeGrupo() {
        CommunicationWizardController c = controller();
        dosGrupos(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, "");

        assertEquals("", c.getState().groupNameOf(AREA));
        assertEquals(List.of(c.getState().itemByUuid(AREA)), c.getState().areasSinGrupo());
    }

    @Test
    void unAreaQueApuntaAUnGrupoInexistenteCuentaComoSinGrupo() {
        CommunicationWizardController c = controller();
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, "grupo-fantasma");

        assertEquals("", c.getState().groupNameOf(AREA),
                "un grupo que no existe no puede devolver un nombre");
        assertEquals(List.of(c.getState().itemByUuid(AREA)), c.getState().areasSinGrupo());
    }

@Test
    void vaciarElEstadoOlvidaLosAgrupamientos() {
        CommunicationWizardController c = controller();
        dosGrupos(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);

        c.getState().reset(null);

        assertEquals("", c.getState().groupNameOf(AREA));
        assertTrue(c.getState().areasSinGrupo().isEmpty(), "no quedan areas tras vaciar");
    }

    @Test
    void recargarElEstadoRecuperaLosAgrupamientosDesdeElModelo() {
        CommunicationWizardController c = controller();
        dosGrupos(c);
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
        dosGrupos(c);
        c.addItem(AREA, "Area 1", "", "%DB1.DBB0:INT", true, GRUPO_A);

        assertEquals("Rapido", c.getState().groupNameOf(AREA));

        c.getState().getItems().set(0, new CommConfigData.ItemConfig(
                AREA, "Area 1", "", "%DB1.DBB0:INT", true, "md5", GRUPO_B));

        assertEquals("Lento", c.getState().groupNameOf(AREA),
                "C-02: el grupo se lee del area, asi que seguir el area basta");
        assertTrue(c.getState().areasSinGrupo().isEmpty());
    }

    @Test
    void trasEditarUnGrupoElEstadoQuedaSucioYSePuedeDescartar() {
        CommunicationWizardController c = controller();
        dosGrupos(c);

        c.updateGroup(GRUPO_A, "Rapido editado", "100", false);

        assertTrue(c.getState().isCambiosSinGuardar());
        c.getState().reset(null);
        assertFalse(c.getState().isCambiosSinGuardar(),
                "descartar los cambios tiene que dejar el estado limpio");
        assertTrue(c.getState().getGroups().isEmpty());
    }

    private static CommConfigData.PvConfig pv(String uuid, String area, String name) {
        return new CommConfigData.PvConfig(uuid, name, "INT", area, "0", "descriptor",
                "500", true, true, "0", "10", "Desc", "%.2f", "bar", "0", "10", "0.1",
                "md5-pv", "S88.1");
    }
}