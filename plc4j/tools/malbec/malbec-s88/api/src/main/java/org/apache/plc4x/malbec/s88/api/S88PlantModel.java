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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * S88 Physical Model for a plant
 */
public class S88PlantModel {

    private final S88Element root;
    private final List<S88ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, S88Element> idMap = new HashMap<>();
    private final Map<String, S88ElementClass> classes = new LinkedHashMap<>();

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

    public void registerClass(S88ElementClass ec) { classes.put(ec.getName(), ec); }

    public S88ElementClass findClass(String name) { return classes.get(name); }

    public Map<String, S88ElementClass> getClasses(){
        return classes;
    }

    public static final String ENUM_CLASS_PREFIX = "ENUM_";

    public static boolean isEnumerationClass(S88ElementClass ec) {
        return ec != null && ec.getName() != null && ec.getName().startsWith(ENUM_CLASS_PREFIX);
    }

    public S88Enumeration toEnumeration(S88ElementClass ec) {
        if (!isEnumerationClass(ec)) {
            return null;
        }
        S88Enumeration enumeration = new S88Enumeration(ec.getName().substring(ENUM_CLASS_PREFIX.length()));
        for (var entry : ec.getProperties().entrySet()) {
            Object raw = entry.getValue();
            if (raw instanceof Map<?, ?> nested) {
                Object idx = nested.get("index");
                Integer value = null;
                if (idx instanceof Number n) {
                    value = n.intValue();
                } else if (idx != null) {
                    try {
                        value = Integer.parseInt(String.valueOf(idx).trim());
                    } catch (NumberFormatException ignored) {
                        value = null;
                    }
                }
                if (value != null) {
                    enumeration.setValue(entry.getKey(), value);
                }
            }
        }
        return enumeration;
    }

    public S88ElementClass fromEnumeration(S88Enumeration enumeration) {
        S88ElementClass ec = new S88ElementClass();
        ec.setName(ENUM_CLASS_PREFIX + enumeration.getName());
        for (var entry : enumeration.getValues().entrySet()) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("index", entry.getValue());
            ec.setProperty(entry.getKey(), props);
        }
        return ec;
    }

    public void registerEnumeration(S88Enumeration enumeration) {
        if (enumeration == null || enumeration.getName() == null) {
            return;
        }
        classes.put(ENUM_CLASS_PREFIX + enumeration.getName(), fromEnumeration(enumeration));
    }

    public void unregisterEnumeration(String name) {
        if (name == null) {
            return;
        }
        classes.remove(ENUM_CLASS_PREFIX + name);
    }

    public S88Enumeration findEnumeration(String name) {
        S88ElementClass ec = classes.get(ENUM_CLASS_PREFIX + name);
        return ec != null ? toEnumeration(ec) : null;
    }

    public List<S88Enumeration> getEnumerations() {
        List<S88Enumeration> result = new ArrayList<>();
        for (S88ElementClass ec : classes.values()) {
            if (isEnumerationClass(ec)) {
                result.add(toEnumeration(ec));
            }
        }
        return result;
    }

    public List<S88Element> findInstancesOf(String className) {
        List<S88Element> result = new ArrayList<>();
        if (root != null) {
            collectInstances(root, className, result);
        }
        return result;
    }

    private void collectInstances(S88Element element, String className, List<S88Element> result) {
        S88ElementClass ec = element.getElementClass();
        if (ec != null && className.equals(ec.getName())) {
            result.add(element);
        }
        for (S88Element child : element.getChildren()) {
            collectInstances(child, className, result);
        }
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
