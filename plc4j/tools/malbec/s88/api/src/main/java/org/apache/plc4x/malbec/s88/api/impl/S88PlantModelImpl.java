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
package org.apache.plc4x.malbec.s88.api.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88ChangeListener;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

/**
 * Implementation of S88PlantModel 
 */
public class S88PlantModelImpl implements S88PlantModel {
    
    private final S88Element root;
    private final List<S88ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, S88Element> idMap = new HashMap<>();

    public S88PlantModelImpl(S88Element root) {
        this.root = root;
        rebuildIndex();
    }

    private void rebuildIndex() {
        idMap.clear();
        if (root != null) {
            addToIndex(root);
        }
    }

    private void addToIndex(S88Element element) {
        if (element.getS88Identity().getId() != null) {
            idMap.put(element.getS88Identity().getId(), element);
        }
        for (S88Element child : element.getS88Hierarchy().getChildren()) {
            addToIndex(child);
        }
    }

    @Override
    public S88Element getRoot() {
        return root;
    }
    


    @Override
    public Optional<S88Element> findById(String id) {
        return Optional.ofNullable(idMap.get(id));
    }

    @Override
    public void addChangeListener(S88ChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeChangeListener(S88ChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    @SuppressWarnings("SuspiciousIndentAfterControlStatement")
    public void fireChangeEvent(S88ChangeEvent event) {
        if (null != event.getType()) 
        switch (event.getType()) {
            case ADDED -> addToIndex(event.getElement());
            case REMOVED -> removeFromIndex(event.getElement());
            case RELOADED -> rebuildIndex();
            default -> {
            }
        }
        
        for (S88ChangeListener listener : listeners) {
            listener.onS88Change(event);
        }
    }

    private void removeFromIndex(S88Element element) {
        idMap.remove(element.getS88Identity().getId());
        for (S88Element child : element.getS88Hierarchy().getChildren()) {
            removeFromIndex(child);
        }
    }
}
