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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

public final class CommunicationsConfig {

    private static final Logger LOGGER
            = Logger.getLogger(CommunicationsConfig.class.getName());
    private final List<DeviceConfigData> dispositivos = new ArrayList<>();
    private final Map<String, Integer> indicePorUuid = new HashMap<>();
    private final Map<String, CommConfigData> comms = new HashMap<>();

    public CommunicationsConfig() {
    }

    public CommunicationsConfig(List<DeviceConfigData> devices) {
        for (DeviceConfigData device : devices == null ? List.<DeviceConfigData>of() : devices) {
            addDevice(device);
        }
    }

    public List<DeviceConfigData> getDevices() {
        return Collections.unmodifiableList(dispositivos);
    }

    public void addDevice(DeviceConfigData device) {
        dispositivos.add(device);
        indexar(device);
    }

    public void upsertDevice(DeviceConfigData device) {
        String uuid = device.getUuid();
        if (uuid == null || uuid.isBlank()) {
            String nombre = device.getDeviceName();
            LOGGER.log(Level.FINE, () -> "El dispositivo '" + nombre + "' llega sin UUID;"
                    + " se agrega como nuevo y se le asignará al cargar.");
            dispositivos.add(device);
            return;
        }
        Integer posicion = indicePorUuid.get(uuid);
        if (posicion == null) {
            dispositivos.add(device);
            indexar(device);
            return;
        }
        int i = posicion;
        dispositivos.set(i, device);
    }

    public DeviceConfigData findByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        Integer posicion = indicePorUuid.get(uuid);
        return posicion == null ? null : dispositivos.get(posicion);
    }

    public Map<String, CommConfigData> getComms() {
        return Collections.unmodifiableMap(comms);
    }

    public CommConfigData getComms(String deviceUuid) {
        return comms.get(deviceUuid);
    }

    public void upsertComms(String deviceUuid, CommConfigData commConfig) {
        if (commConfig == null) {
            comms.remove(deviceUuid);
        } else {
            comms.put(deviceUuid, commConfig);
        }
    }

    public void removeComms(String deviceUuid) {
        comms.remove(deviceUuid);
    }

    public boolean removeDevice(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        Integer posicion = indicePorUuid.remove(uuid);
        if (posicion == null) {
            return false;
        }
        dispositivos.remove(posicion.intValue());
        reindexar();
        comms.remove(uuid);
        return true;
    }

    public void replaceDevice(int index, DeviceConfigData device) {
        DeviceConfigData anterior = dispositivos.set(index, device);
        if (anterior != null) {
            desindexar(anterior);
        }
        indexar(device, index);
    }

    public void clear() {
        dispositivos.clear();
        indicePorUuid.clear();
        comms.clear();
    }

    private void indexar(DeviceConfigData device) {
        indexar(device, dispositivos.size() - 1);
    }

    private void indexar(DeviceConfigData device, int posicion) {
        String uuid = device == null ? null : device.getUuid();
        if (uuid != null && !uuid.isBlank()) {
            indicePorUuid.put(uuid, posicion);
        }
    }

    private void desindexar(DeviceConfigData device) {
        String uuid = device.getUuid();
        if (uuid != null && !uuid.isBlank()) {
            indicePorUuid.remove(uuid);
        }
    }

    private void reindexar() {
        indicePorUuid.clear();
        for (int i = 0; i < dispositivos.size(); i++) {
            indexar(dispositivos.get(i), i);
        }
    }
}
