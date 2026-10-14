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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationStore;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;

public class CommunicationWizardController {

    // El asistente no calcula hashes. Se deja vacio hasta que se decida si la columna
    // Md5 se usa; los md5 que ya vienen de un comunicacion.xml existente se respetan.
    private static final String MD5_PENDIENTE = "";

    private final CommunicationStore store;
    private final CommunicationWizardState state;

    /** Tags ya desglosados, para no repetir el parseo en cada validacion (T3.7). */
    private final Map<String, MemoryTag> cacheTags = new HashMap<>();

    public CommunicationWizardController(CommunicationStore store, CommunicationWizardState state) {
        this.store = store;
        this.state = state;
    }

    public CommunicationWizardState getState() {
        return state;
    }

    public CommunicationStore getStore() {
        return store;
    }

    // --- Device ---------------------------------------------------------

    public List<DeviceConfigData> getAvailableDevices() {
        return store != null ? store.getDevices() : new ArrayList<>();
    }

    public void selectDevice(DeviceConfigData device) {
        state.setSelectedDevice(device);
        if (device != null && store != null) {
            state.reset(store.getComms(device.getUuid()));
        } else {
            state.reset(null);
        }
    }

    // --- Groups --------------------------------------------------------------

    public void addGroup(String uuid, String name, String desc, String scan, boolean enable) {
        state.getGroups().add(
                new CommConfigData.GroupConfig(uuid, name, desc, scan, enable, MD5_PENDIENTE));
        state.setCambiosSinGuardar(true);
    }

    public void updateGroup(String uuid, String desc, String scan, boolean enable) {
        CommConfigData.GroupConfig actual = state.groupByUuid(uuid);
        if (actual == null) {
            return;
        }
        String scantime = scan != null && !scan.isEmpty() ? scan : actual.getScantime();
        int idx = state.indexOfGroup(uuid);
        state.getGroups().set(idx, actual
                .withDescription(desc)
                .withScantime(scantime)
                .withEnable(enable));
        state.setCambiosSinGuardar(true);
    }

    public List<String> deleteGroup(String uuid) {
        CommConfigData.GroupConfig group = state.groupByUuid(uuid);
        if (group == null) {
            return List.of();
        }
        List<String> areasDelGrupo = new ArrayList<>();
        for (CommConfigData.ItemConfig i : state.getItems()) {
            if (group.getUuid().equals(i.getGroupUuid())) {
                areasDelGrupo.add(i.getName());
            }
        }
        if (!areasDelGrupo.isEmpty()) {
            return areasDelGrupo;
        }
        state.getGroups().remove(state.indexOfGroup(uuid));
        state.setCambiosSinGuardar(true);
        return List.of();
    }

    // --- Items -------------------------------------------------------

    public void addItem(String uuid, String name, String desc, String tag, boolean enable, String groupUuid) {
        state.getItems().add(new CommConfigData.ItemConfig(
                uuid, name, desc, tag, enable, MD5_PENDIENTE, groupUuid));
        state.setCambiosSinGuardar(true);
        cacheTags.clear();
    }

    public void updateItem(String uuid, String desc, String tag, boolean enable) {
        CommConfigData.ItemConfig actual = state.itemByUuid(uuid);
        if (actual == null) {
            return;
        }
        int idx = state.indexOfItem(uuid);
        state.getItems().set(idx, actual
                .withDescription(desc)
                .withTag(tag)
                .withEnable(enable));
        state.setCambiosSinGuardar(true);
        cacheTags.clear();
    }

    public List<String> deleteItem(String uuid) {
        CommConfigData.ItemConfig item = state.itemByUuid(uuid);
        if (item == null) {
            return List.of();
        }
        List<String> nombres = new ArrayList<>();
        for (CommConfigData.PvConfig pv : state.getPvs()) {
            if (item.getUuid().equals(pv.getAreaUuid())) {
                nombres.add(pv.getName());
            }
        }
        state.getPvs().removeIf(pv -> uuid.equals(pv.getAreaUuid()));
        state.getItems().remove(state.indexOfItem(uuid));
        state.setCambiosSinGuardar(true);
        cacheTags.clear();
        return nombres;
    }

    // --- Pv Records -----------------------------------------------------

    public void addPv(CommConfigData.PvConfig pv) {
        state.getPvs().add(pv);
        state.setCambiosSinGuardar(true);
    }

    public void updatePv(String uuid, String descriptor, String scanTime,
            boolean scanEnable, boolean writeEnable, String displayLimitLow,
            String displayLimitHigh, String displayDescription, String displayFormat,
            String displayUnits, String controlLimitLow, String controlLimitHigh,
            String controlMinStep) {
        CommConfigData.PvConfig actual = state.pvByUuid(uuid);
        if (actual == null) {
            return;
        }
        int idx = state.indexOfPv(uuid);
        state.getPvs().set(idx, actual
                .withDescriptor(descriptor)
                .withScanTime(scanTime)
                .withScanEnable(scanEnable)
                .withWriteEnable(writeEnable)
                .withDisplayLimitLow(displayLimitLow)
                .withDisplayLimitHigh(displayLimitHigh)
                .withDisplayDescription(displayDescription)
                .withDisplayFormat(displayFormat)
                .withDisplayUnits(displayUnits)
                .withControlLimitLow(controlLimitLow)
                .withControlLimitHigh(controlLimitHigh)
                .withControlMinStep(controlMinStep));
        state.setCambiosSinGuardar(true);
    }

    public boolean deletePv(String uuid) {
        if (state.pvByUuid(uuid) == null) {
            return false;
        }
        state.getPvs().remove(state.indexOfPv(uuid));
        state.setCambiosSinGuardar(true);
        return true;
    }

    public static String pvMd5() {
        return MD5_PENDIENTE;
    }

    public static String groupMd5() {
        return MD5_PENDIENTE;
    }

    private MemoryTag parseTag(String tag) {
        if (tag == null) {
            return null;
        }
        return cacheTags.computeIfAbsent(tag, MemoryTag::parse);
    }

    private MemoryTag tagOf(String areaUuid) {
        CommConfigData.ItemConfig item = state.itemByUuid(areaUuid);
        return item == null ? null : parseTag(item.getTag());
    }

    public DataType lockedType(String areaUuid) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag != null && tag.family() != null) {
            List<DataType> candidatos = DataType.candidatos(tag.family(), tag.codeBits());
            if (candidatos.size() == 1) {
                return candidatos.get(0);
            }
        }
        return tipoDePrimeraVariable(areaUuid);
    }

    private List<DataType> candidatosDelTag(String areaUuid) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null || tag.family() == null) {
            return List.of();
        }
        return DataType.candidatos(tag.family(), tag.codeBits());
    }

    private DataType tipoDePrimeraVariable(String areaUuid) {
        List<CommConfigData.PvConfig> areaPvs = state.pvsOfArea(areaUuid);
        return areaPvs.isEmpty() ? null : DataType.find(areaPvs.get(0).getType());
    }

    public List<DataType> typesAllowed(String areaUuid) {
        if (state.itemByUuid(areaUuid) == null) {
            return List.of();
        }
        List<DataType> candidatos = candidatosDelTag(areaUuid);
        DataType elegido = tipoDePrimeraVariable(areaUuid);
        if (elegido != null && (candidatos.isEmpty() || candidatos.contains(elegido))) {
            return List.of(elegido);
        }
        if (!candidatos.isEmpty()) {
            return candidatos;
        }
        DataType fijado = lockedType(areaUuid);
        if (fijado != null) {
            return List.of(fijado);
        }
        List<DataType> todos = new ArrayList<>();
        for (DataType t : DataType.values()) {
            if (t.isEnabled()) {
                todos.add(t);
            }
        }
        return todos;
    }

    public String nextOffset(String areaUuid, DataType type) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null || type == null || !type.isEnabled() || tag.byteCapacity() <= 0) {
            return null;
        }
        Set<Integer> ocupados = bitsOcupados(areaUuid);
        int byteInicio = Math.max(0, tag.firstByte());
        int desde = byteInicio * 8;
        int hasta = desde + tag.byteCapacity() * 8;
        int ancho = type.slotBits();
        
        for (int bits = desde; bits + ancho <= hasta; bits += ancho) {
            if (libre(bits, ancho, ocupados)) {
                return type.bitAddressed()
                        ? (bits / 8) + "." + (bits % 8)
                        : String.valueOf(bits / 8);
            }
        }
        return null;
    }

    private static boolean libre(int desde, int ancho, Set<Integer> ocupados) {
        for (int bit = desde; bit < desde + ancho; bit++) {
            if (ocupados.contains(bit)) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> bitsOcupados(String areaUuid) {
        Set<Integer> ocupados = new HashSet<>();
        for (CommConfigData.PvConfig pv : state.pvsOfArea(areaUuid)) {
            int bits = bitsDeOffset(pv.getOffset());
            if (bits < 0) {
                continue;
            }
            DataType type = DataType.find(pv.getType());
            int ancho = type == null ? 8 : type.slotBits();
            for (int bit = bits; bit < bits + ancho; bit++) {
                ocupados.add(bit);
            }
        }
        return ocupados;
    }

    private static int bitsDeOffset(String offset) {
        if (offset == null || offset.isEmpty()) {
            return -1;
        }
        try {
            int punto = offset.indexOf('.');
            int byteOffset = Integer.parseInt(punto < 0 ? offset : offset.substring(0, punto));
            int bitOffset = punto < 0 ? 0 : Integer.parseInt(offset.substring(punto + 1));
            return byteOffset * 8 + bitOffset;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public int areaCapacityBytes(String areaUuid) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null) {
            return -1;
        }
        int bytes = tag.byteCapacity();
        return bytes > 0 ? bytes : -1;
    }

    public int areaFreeSlots(String areaUuid, DataType type) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null || type == null || !type.isEnabled()) {
            return -1;
        }
        int bytes = tag.byteCapacity();
        if (bytes <= 0) {
            return -1;
        }
        return Math.max(0, (bytes * 8) / type.slotBits() - state.pvsOfArea(areaUuid).size());
    }

    public int areaFreeBytes(String areaUuid) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null || tag.byteCapacity() <= 0) {
            return -1;
        }

        return Math.max(0, tag.byteCapacity() - bytesOcupados(areaUuid));
    }

    private int bytesOcupados(String areaUuid) {
        int total = 0;
        for (CommConfigData.PvConfig pv : state.pvsOfArea(areaUuid)) {
            DataType type = DataType.find(pv.getType());
            if (type == null) {
                continue;
            }
            total += (type.slotBits() + 7) / 8;
        }
        return total;
    }

    public CommConfigData.ItemConfig areaQueChoca(String tag, String uuidEnEdicion) {
        MemoryTag nuevo = parseTag(tag);
        if (nuevo == null) {
            return null;
        }
        for (CommConfigData.ItemConfig i : state.getItems()) {
            if (uuidEnEdicion != null && uuidEnEdicion.equals(i.getUuid())) {
                continue;
            }
            if (nuevo.solapaCon(parseTag(i.getTag()))) {
                return i;
            }
        }
        return null;
    }

    public CommConfigData.ItemConfig areaConMismoTag(String tag, String uuidEnEdicion) {
        MemoryTag t = parseTag(tag);
        if (t == null || t.tieneDireccion()) {
            return null;
        }
        String buscado = normalizaTexto(tag);
        if (buscado.isEmpty()) {
            return null;
        }
        for (CommConfigData.ItemConfig i : state.getItems()) {
            if (uuidEnEdicion != null && uuidEnEdicion.equals(i.getUuid())) {
                continue;
            }
            MemoryTag suyo = parseTag(i.getTag());
            if (suyo != null && suyo.tieneDireccion()) {
                continue;
            }
            if (buscado.equals(normalizaTexto(i.getTag()))) {
                return i;
            }
        }
        return null;
    }

    private static String normalizaTexto(String tag) {
        if (tag == null) {
            return "";
        }
        String t = tag.trim();
        if (t.startsWith("%")) {
            t = t.substring(1).trim();
        }
        int dosPuntos = t.lastIndexOf(':');
        if (dosPuntos > 0) {
            t = t.substring(0, dosPuntos).trim();
        }
        return t.replaceAll("\\s+", "").toUpperCase();
    }

    public MemoryTag areaTag(String areaUuid) {
        return tagOf(areaUuid);
    }

    public List<DataType> typesForCombo(String areaUuid) {
        List<DataType> combo = new ArrayList<>(typesAllowed(areaUuid));
        for (DataType t : DataType.values()) {
            if (!combo.contains(t) && !t.isEnabled()) {
                combo.add(t);
            }
        }
        if (combo.isEmpty()) {
            for (DataType t : DataType.values()) {
                if (t.isEnabled()) {
                    combo.add(t);
                }
            }
        }
        return combo;
    }

    public boolean typeAllowed(String areaUuid, DataType type) {
        return type != null && type.isEnabled()
                && typesAllowed(areaUuid).contains(type);
    }

    public boolean areaAdmiteTipo(String areaUuid, String plantType, DataType locked) {
        if (locked == null || plantType == null || plantType.isEmpty()) {
            return true;
        }

        if (!typesAllowed(areaUuid).contains(locked)) {
            return false;
        }
        MemoryTag tag = tagOf(areaUuid);
        List<DataType> posibles = tag == null
                ? List.of()
                : DataType.candidatos(plantType, tag.codeBits());
        if (!posibles.isEmpty()) {
            return posibles.contains(locked);
        }
        DataType exacto = DataType.findIgnoreCase(plantType);
        return exacto == null || exacto == locked;
    }

    // --- variables de planta ya asignadas -----------------------------------

    public static String pvKey(String path, String name) {
        return (path == null || path.isEmpty() ? "" : path + "/") + name;
    }

    public Set<String> getUsedPvKeys() {
        Set<String> usedKeys = new HashSet<>();
        for (CommConfigData.PvConfig pv : state.getPvs()) {
            usedKeys.add(pvKey(pv.getS88Path(), pv.getName()));
        }
        return usedKeys;
    }
    public List<String> areasSinGrupoNames() {
        List<String> nombres = new ArrayList<>();
        for (CommConfigData.ItemConfig i : state.areasSinGrupo()) {
            nombres.add(i.getName());
        }
        return nombres;
    }

    public boolean hasAreasWithoutGroup() {
        return !state.areasSinGrupo().isEmpty();
    }

    public SaveResult saveAll() {
        if (store == null || state.getSelectedDevice() == null) {
            return SaveResult.noGuardado(
                    "no hay ningun dispositivo seleccionado sobre el que guardar");
        }
        CommConfigData config = new CommConfigData(
                state.getSelectedDevice().getDeviceName(),
                new ArrayList<>(state.getGroups()),
                new ArrayList<>(state.getItems()),
                new ArrayList<>(state.getPvs()));
        store.upsertComms(state.getSelectedDevice().getUuid(), config);
        SaveResult resultado = store.save();
            state.setCambiosSinGuardar(!resultado.isComplete());
            return resultado;
    }
}
