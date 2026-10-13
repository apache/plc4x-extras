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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.nodes.Node;

class CommunicationNodeList implements NodeList<String>, ChangeListener {

    private final Project project;
    private final HMICommunicationModel model;
    private final List<ChangeListener> listeners = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final Map<String, DeviceNode> nodesUuid = new LinkedHashMap<>();

    CommunicationNodeList(Project project, HMICommunicationModel model) {
        this.project = project;
        this.model = model;
        refreshKeys();
    }

    @Override
    public synchronized List<String> keys() {
            return List.copyOf(keys);
    }

    @Override
    public Node node(String uuid) {
        if (model == null) {
            return null;
        }
        if (device(uuid) == null) {
            return null;
        }
        DeviceNode existente = nodesUuid.get(uuid);
        if (existente != null) {
            return existente;
        }
        DeviceNode creado = new DeviceNode(uuid, project, this);
        nodesUuid.put(uuid, creado);
        return creado;
    }

    /** Dispositivo vigente con ese UUID, o null si no hay modelo o no existe. */
    DeviceConfigData device(String uuid) {
        return model == null || uuid == null ? null : model.findByUuid(uuid);
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        listeners.add(cl);
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        listeners.remove(cl);
    }

    private void fireChange() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(listeners)) {
            listener.stateChanged(event);
        }
    }

    @Override
    public void addNotify() {
        if (model != null) {
            model.addChangeListener(this);
        }
        refreshKeys();
    }

    @Override
    public void removeNotify() {
        if (model != null) {
            model.removeChangeListener(this);
        }
    }

    private synchronized void refreshKeys() {
        keys.clear();
        if (model != null) {
            for (DeviceConfigData device : model.getDevices()) {
                keys.add(device.getUuid());
            }
        }
        fireChange();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        refreshKeys();
        refreshNodes();
    }

    private void refreshNodes() {
        nodesUuid.values().removeIf(nodo -> nodo.device() == null);
        for (DeviceNode nodo : new ArrayList<>(nodesUuid.values())) {
            nodo.refresh();
        }
    }
}
