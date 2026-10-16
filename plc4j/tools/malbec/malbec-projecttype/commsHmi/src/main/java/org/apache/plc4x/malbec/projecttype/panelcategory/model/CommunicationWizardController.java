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
        state.setUnsavedChanges(true);
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
        state.setUnsavedChanges(true);
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
        state.setUnsavedChanges(true);
        return List.of();
    }

    // --- Items -------------------------------------------------------

    public void addItem(String uuid, String name, String desc, String tag, boolean enable, String groupUuid) {
        state.getItems().add(new CommConfigData.ItemConfig(
                uuid, name, desc, tag, enable, MD5_PENDIENTE, groupUuid));
        state.setUnsavedChanges(true);
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
        state.setUnsavedChanges(true);
        cacheTags.clear();
    }

    public List<String> deleteItem(String uuid) {
        CommConfigData.ItemConfig item = state.itemByUuid(uuid);
        if (item == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (CommConfigData.PvConfig pv : state.getPvs()) {
            if (item.getUuid().equals(pv.getAreaUuid())) {
                names.add(pv.getName());
            }
        }
        state.getPvs().removeIf(pv -> uuid.equals(pv.getAreaUuid()));
        state.getItems().remove(state.indexOfItem(uuid));
        state.setUnsavedChanges(true);
        cacheTags.clear();
        return names;
    }

    // --- Pv Records -----------------------------------------------------

    public void addPv(CommConfigData.PvConfig pv) {
        state.getPvs().add(pv);
        state.setUnsavedChanges(true);
    }

    public void updatePv(String uuid, String descriptorText, String scanTime,
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
                .withDescriptorText(descriptorText)
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
        state.setUnsavedChanges(true);
    }

    public boolean deletePv(String uuid) {
        if (state.pvByUuid(uuid) == null) {
            return false;
        }
        state.getPvs().remove(state.indexOfPv(uuid));
        state.setUnsavedChanges(true);
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
            List<DataType> candidates = DataType.candidates(tag.family(), tag.codeBits());
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
        }
        return typeOfFirstVariable(areaUuid);
    }

    private List<DataType> tagCandidates(String areaUuid) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null || tag.family() == null) {
            return List.of();
        }
        return DataType.candidates(tag.family(), tag.codeBits());
    }

    private DataType typeOfFirstVariable(String areaUuid) {
        List<CommConfigData.PvConfig> areaPvs = state.pvsOfArea(areaUuid);
        return areaPvs.isEmpty() ? null : DataType.find(areaPvs.get(0).getType());
    }

    public List<DataType> typesAllowed(String areaUuid) {
        if (state.itemByUuid(areaUuid) == null) {
            return List.of();
        }
        List<DataType> candidates = tagCandidates(areaUuid);
        DataType elegido = typeOfFirstVariable(areaUuid);
        if (elegido != null && (candidates.isEmpty() || candidates.contains(elegido))) {
            return List.of(elegido);
        }
        if (!candidates.isEmpty()) {
            return candidates;
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
        int width = type.slotBits();
        
        for (int bits = desde; bits + width <= hasta; bits += width) {
            if (free(bits, width, ocupados)) {
                return type.bitAddressed()
                        ? (bits / 8) + "." + (bits % 8)
                        : String.valueOf(bits / 8);
            }
        }
        return null;
    }

    private static boolean free(int desde, int width, Set<Integer> ocupados) {
        for (int bit = desde; bit < desde + width; bit++) {
            if (ocupados.contains(bit)) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> bitsOcupados(String areaUuid) {
        Set<Integer> ocupados = new HashSet<>();
        for (CommConfigData.PvConfig pv : state.pvsOfArea(areaUuid)) {
            int bits = bitsOfOffset(pv.getOffset());
            if (bits < 0) {
                continue;
            }
            DataType type = DataType.find(pv.getType());
            int width = type == null ? 8 : type.slotBits();
            for (int bit = bits; bit < bits + width; bit++) {
                ocupados.add(bit);
            }
        }
        return ocupados;
    }

    private static int bitsOfOffset(String offset) {
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

    public int areaCapacityInBytes(String areaUuid) {
        MemoryTag tag = tagOf(areaUuid);
        if (tag == null) {
            return -1;
        }
        int bytes = tag.byteCapacity();
        return bytes > 0 ? bytes : -1;
    }

    public int freeAreaSlots(String areaUuid, DataType type) {
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

    public int freeAreaBytes(String areaUuid) {
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

    public CommConfigData.ItemConfig overlappingArea(String tag, String uuidEnEdicion) {
        MemoryTag nuevo = parseTag(tag);
        if (nuevo == null) {
            return null;
        }
        for (CommConfigData.ItemConfig i : state.getItems()) {
            if (uuidEnEdicion != null && uuidEnEdicion.equals(i.getUuid())) {
                continue;
            }
            if (nuevo.overlaps(parseTag(i.getTag()))) {
                return i;
            }
        }
        return null;
    }

    public CommConfigData.ItemConfig areaWithSameTag(String tag, String uuidEnEdicion) {
        MemoryTag t = parseTag(tag);
        if (t == null || t.hasAddress()) {
            return null;
        }
        String buscado = normalizeText(tag);
        if (buscado.isEmpty()) {
            return null;
        }
        for (CommConfigData.ItemConfig i : state.getItems()) {
            if (uuidEnEdicion != null && uuidEnEdicion.equals(i.getUuid())) {
                continue;
            }
            MemoryTag suyo = parseTag(i.getTag());
            if (suyo != null && suyo.hasAddress()) {
                continue;
            }
            if (buscado.equals(normalizeText(i.getTag()))) {
                return i;
            }
        }
        return null;
    }

    private static String normalizeText(String tag) {
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

    private static final List<DataType> TIPOS_PLC_ESTANDAR = List.of(
        DataType.BOOLEAN, DataType.BYTE, DataType.WORD, DataType.DWORD,
        DataType.SINT, DataType.USINT, DataType.INT, DataType.UINT,
        DataType.DINT, DataType.UDINT, DataType.LINT, DataType.ULINT,
        DataType.REAL, DataType.LREAL, DataType.TIME, DataType.DATE,
        DataType.TIME_OF_DAY, DataType.DATE_AND_TIME, DataType.STRING
    );

    public List<DataType> typesForCombo(String areaUuid) {
        List<DataType> combo = new ArrayList<>(typesAllowed(areaUuid));
        for (DataType t : DataType.values()) {
            if (!combo.contains(t) && !t.isEnabled()) {
                combo.add(t);
            }
        }
        if (combo.isEmpty()) {
            combo.addAll(TIPOS_PLC_ESTANDAR);
        }
        return combo;
    }

    public boolean typeAllowed(String areaUuid, DataType type) {
        return type != null && type.isEnabled()
                && typesAllowed(areaUuid).contains(type);
    }

    public boolean areaAcceptsType(String areaUuid, String plantType, DataType locked) {
        if (locked == null || plantType == null || plantType.isEmpty()) {
            return true;
        }

        if (!typesAllowed(areaUuid).contains(locked)) {
            return false;
        }
        MemoryTag tag = tagOf(areaUuid);
        List<DataType> posibles = tag == null
                ? List.of()
                : DataType.candidates(plantType, tag.codeBits());
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
    public List<String> areasWithoutGroupNames() {
        List<String> names = new ArrayList<>();
        for (CommConfigData.ItemConfig i : state.areasWithoutGroup()) {
            names.add(i.getName());
        }
        return names;
    }

    public boolean hasAreasWithoutGroup() {
        return !state.areasWithoutGroup().isEmpty();
    }

    public SaveResult saveAll() {
        if (store == null || state.getSelectedDevice() == null) {
            return SaveResult.notSaved(
                    "no hay ningun dispositivo seleccionado sobre el que guardar");
        }
        CommConfigData config = new CommConfigData(
                state.getSelectedDevice().getDeviceName(),
                new ArrayList<>(state.getGroups()),
                new ArrayList<>(state.getItems()),
                new ArrayList<>(state.getPvs()));
        store.upsertComms(state.getSelectedDevice().getUuid(), config);
        SaveResult resultado = store.save();
            state.setUnsavedChanges(!resultado.isComplete());
            return resultado;
    }
}
