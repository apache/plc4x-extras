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

/**
 * AREA <br>
 * PROCESS CELL <br>
 * UNIT <br>
 * EQUIPMENT MODULE
 */
public class S88Element {


    private String id;
    private S88Level level;
    private S88Element parent;
    private final List<S88Element> children = new ArrayList<>();
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private S88ElementClass elementClass;
    private final List<S88ElementClass> elementClasses = new ArrayList<>();
    private static final String CHECK = "Check";


    public S88Element setClass(S88ElementClass elementClass){
        this.elementClass = elementClass;
        return this;
    }

    public void setCheck(boolean check){
        setProperty(CHECK, check);
    }

    public boolean isCheck(){
        Object value = getProperty(CHECK);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return false;
    }

    public S88ElementClass getElementClass(){
        return this.elementClass;
    }

    public void addElementClass(S88ElementClass elementClass){
        this.elementClasses.add(elementClass);
    }

    public List<S88ElementClass> getElementClasses(){
        return this.elementClasses;
    }

    public S88Element setId(String id) {
        this.id = id;
        return this;
    }

    public S88Element setLevel(S88Level level) {
        this.level = level;
        return this;
    }

    public S88Element setParent(S88Element parent) {
        this.parent = parent;
        return this;
    }

    public void setProperty(String k, Object v){
        if(v == null){
            this.properties.remove(k);
        } else {
            this.properties.put(k, v);
        }
    }

    public String getId() {
        return id;
    }

    public S88Level getLevel() {
        return level;
    }

    public S88Element getParent() {
        return parent;
    }

    public List<S88Element> getChildren() {
        return children;
    }

    public void addChild(S88Element element){
        this.children.add(element);
        element.setParent(this);
    }

    public void removeChild(S88Element element){
        this.children.remove(element);
        element.setParent(null);
    }

    public String getTypeName() {
        return getClass().getSimpleName();
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Collects the nested (structured) properties held by the given map.
     * <p>
     * The returned map and each nested container are immutable snapshots;
     * modifications must be applied through {@link #setProperty(String, Object)}.
     *
     * @param properties the map to inspect, or {@code null} to inspect the element's own properties
     * @return immutable view of the nested property maps
     */
    public Map<String, Map<String, Object>> getStructuredProperties(Map<String, Object> properties) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        if (properties == null)  properties = this.getProperties();

        for (var entry : properties.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                result.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(typed)));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the property value for the given key.
     *
     * @param k property key
     * @return the stored value, or {@code ""} when the key is absent (unlike
     *         {@link #getProperties()} which exposes {@code null} for missing keys)
     */
    public Object getProperty(String k){
        return this.properties.getOrDefault(k, "");
    }

    public Map<String, Object> getStructuredProperty(String k){
        return this.getStructuredProperties(null).get(k);
    }


}