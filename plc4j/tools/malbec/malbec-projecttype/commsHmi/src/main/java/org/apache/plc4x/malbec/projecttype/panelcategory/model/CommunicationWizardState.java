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
import java.util.List;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

public class CommunicationWizardState {

    private DeviceConfigData selectedDevice;
    private final List<CommConfigData.GroupConfig> groups = new ArrayList<>();
    private final List<CommConfigData.ItemConfig> items = new ArrayList<>();
    private final List<CommConfigData.PvConfig> pvs = new ArrayList<>();
    private boolean cambiosSinGuardar = false;
    private boolean missingGroupsWarned = false;
    
    public void reset(CommConfigData comms) {
        groups.clear();
        items.clear();
        pvs.clear();
        cambiosSinGuardar = false;
        missingGroupsWarned = false;

        if (comms != null) {
            if (comms.getGroups() != null) {
                groups.addAll(comms.getGroups());
            }
            if (comms.getItems() != null) {
                items.addAll(comms.getItems());
            }
            if (comms.getPvs() != null) {
                pvs.addAll(comms.getPvs());
            }
        }
    }

    public CommConfigData.GroupConfig groupByUuid(String uuid) {
        int idx = indexOfGroup(uuid);
        return idx < 0 ? null : groups.get(idx);
    }

    public CommConfigData.ItemConfig itemByUuid(String uuid) {
        int idx = indexOfItem(uuid);
        return idx < 0 ? null : items.get(idx);
    }

    public CommConfigData.PvConfig pvByUuid(String uuid) {
        int idx = indexOfPv(uuid);
        return idx < 0 ? null : pvs.get(idx);
    }
    public int indexOfGroup(String uuid) {
        return buscar(groups, g -> mismoUuid(uuid, g.getUuid()));
    }

    public int indexOfItem(String uuid) {
        return buscar(items, i -> mismoUuid(uuid, i.getUuid()));
    }

    public int indexOfPv(String uuid) {
        return buscar(pvs, p -> mismoUuid(uuid, p.getUuid()));
    }

    private static boolean mismoUuid(String uuid, String otro) {
        return uuid != null && uuid.equals(otro);
    }

    private static <T> int buscar(List<T> lista, java.util.function.Predicate<T> coincide) {
        for (int i = 0; i < lista.size(); i++) {
            if (coincide.test(lista.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public String groupNameOf(String itemUuid) {
        CommConfigData.ItemConfig item = itemByUuid(itemUuid);
        if (item == null || item.getGroupUuid() == null || item.getGroupUuid().isEmpty()) {
            return "";
        }
        for (CommConfigData.GroupConfig g : groups) {
            if (g.getUuid().equals(item.getGroupUuid())) {
                return g.getName();
            }
        }
        return "";
    }

    public List<CommConfigData.ItemConfig> areasSinGrupo() {
        List<CommConfigData.ItemConfig> sinGrupo = new ArrayList<>();
        for (CommConfigData.ItemConfig i : items) {
            if (groupNameOf(i.getUuid()).isEmpty()) {
                sinGrupo.add(i);
            }
        }
        return sinGrupo;
    }

    public List<CommConfigData.PvConfig> pvsOfArea(String areaUuid) {
        List<CommConfigData.PvConfig> result = new ArrayList<>();
        if (areaUuid == null) {
            return result;
        }
        for (CommConfigData.PvConfig pv : pvs) {
            if (areaUuid.equals(pv.getAreaUuid())) {
                result.add(pv);
            }
        }
        return result;
    }

    public DeviceConfigData getSelectedDevice() {
        return selectedDevice;
    }

    public void setSelectedDevice(DeviceConfigData selectedDevice) {
        this.selectedDevice = selectedDevice;
    }

    public List<CommConfigData.GroupConfig> getGroups() {
        return groups;
    }

    public List<CommConfigData.ItemConfig> getItems() {
        return items;
    }

    public List<CommConfigData.PvConfig> getPvs() {
        return pvs;
    }

    

    public boolean isCambiosSinGuardar() {
        return cambiosSinGuardar;
    }

    public void setCambiosSinGuardar(boolean cambiosSinGuardar) {
        this.cambiosSinGuardar = cambiosSinGuardar;
    }

    public boolean isMissingGroupsWarned() {
        return missingGroupsWarned;
    }

    public void setMissingGroupsWarned(boolean missingGroupsWarned) {
        this.missingGroupsWarned = missingGroupsWarned;
    }
}
