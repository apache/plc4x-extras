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
package org.apache.plc4x.malbec.s88.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * S88 Physical Model for a plant
 */
public class S88PlantModel {

    private final S88Element root;
    private final List<S88ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, S88Element> idMap = new HashMap<>();

    public S88PlantModel(S88Element root) {
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
        if (element.getId() != null) {
            idMap.put(element.getId(), element);
        }
        for (S88Element child : element.getChildren()) {
            addToIndex(child);
        }
    }


    public S88Element getRoot() {
        return root;
    }

    


    public Optional<S88Element> findById(String id) {
        return Optional.ofNullable(idMap.get(id));
    }


    public void addChangeListener(S88ChangeListener listener) {
        listeners.add(listener);
    }


    public void removeChangeListener(S88ChangeListener listener) {
        listeners.remove(listener);
    }


    public void fireChangeEvent(S88ChangeEvent event) {
        if (null != event.type())
            switch (event.type()) {
                case ADDED -> addToIndex(event.element());
                case REMOVED -> removeFromIndex(event.element());
                case RELOADED -> rebuildIndex();
                default -> {
                }
            }

        for (S88ChangeListener listener : listeners) {
            listener.onS88Change(event);
        }
    }

    private void removeFromIndex(S88Element element) {
        idMap.remove(element.getId());
        for (S88Element child : element.getChildren()) {
            removeFromIndex(child);
        }
    }
}
