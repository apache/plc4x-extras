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

/**
 * Configuracion de comunicaciones en memoria (T3.2).
 *
 * <p><b>Por que hay dos estructuras.</b> Una lista sola obliga a recorrerla en cada
 * busqueda por UUID, y la busqueda se hace por cada nodo del arbol. Un mapa solo no
 * vale, porque dos dispositivos sin UUID comparten la clave vacia y uno pisaria al
 * otro. Asi que la lista manda en el orden y el mapa resuelve las busquedas de los que
 * si tienen UUID, que son todos menos los que estan a medio crear.
 */
public final class CommunicationsConfig {

    private static final Logger LOGGER =
            Logger.getLogger(CommunicationsConfig.class.getName());

    private final List<DeviceConfigData> dispositivos = new ArrayList<>();

    /** UUID -> posicion en {@link #dispositivos}. Solo los UUID con contenido. */
    private final Map<String, Integer> indicePorUuid = new HashMap<>();

    private final Map<String, CommConfigData> comms = new HashMap<>();

    public CommunicationsConfig() {
    }

    public CommunicationsConfig(List<DeviceConfigData> devices) {
        for (DeviceConfigData device : devices == null ? List.<DeviceConfigData>of() : devices) {
            addDevice(device);
        }
    }

    /** Vista inmutable y en el orden de alta. */
    public List<DeviceConfigData> getDevices() {
        return Collections.unmodifiableList(dispositivos);
    }

    public void addDevice(DeviceConfigData device) {
        dispositivos.add(device);
        indexar(device);
    }

    /**
     * Anade el dispositivo o, si ya existe su UUID, lo reemplaza en su sitio.
     *
     * <p><b>Un dispositivo sin UUID se anade como nuevo</b> y no se rechaza. Rechazarlo
     * lo haria desaparecer sin que nadie lo dijera: el dialogo de creacion no comprueba
     * el UUID antes de llamar aqui, y seguiria anunciando que se guardo. Asignarle el
     * UUID lo hace en {@code HMICommunicationModel.repararUuidsAusentes} al cargar.
     */
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
        // Al quitar uno se desplazan los siguientes: el indice queda desalineado y se
        // reconstruye entero, que es O(n) pero solo pasa al borrar.
        reindexar();
        comms.remove(uuid);
        return true;
    }

    public void replaceDevice(int index, DeviceConfigData device) {
        DeviceConfigData anterior = dispositivos.set(index, device);
        if (anterior != null) {
            desindexar(anterior);
        }
        // La posicion es la que nos han pasado, no el final de la lista: aqui no se
        // ha añadido nada, asi que el indice no está al final.
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
            // Ojo: la posicion es la del bucle. Usar el tamaño de la lista aqui
            // apuntaria todos los dispositivos al final.
            indexar(dispositivos.get(i), i);
        }
    }
}
