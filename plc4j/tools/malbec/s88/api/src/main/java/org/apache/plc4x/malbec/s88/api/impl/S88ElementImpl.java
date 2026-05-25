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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;

/**
 * Implementation of S88Element.
 */
public class S88ElementImpl implements S88Element {
    
    private String id;
    private S88Level level;
    private String description;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final List<S88Element> children = new ArrayList<>();
    private S88Element parent;

    public S88ElementImpl(String id, S88Level level) {
        this.id = id;
        this.level = level;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public S88Level getLevel() {
        return level;
    }

    @Override
    public void setLevel(S88Level level) {
        this.level = level;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public String getProperty(String key) {
        return properties.get(key);
    }

    @Override
    public void setProperty(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

    @Override
    public List<S88Element> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void addChild(S88Element child) {
        if (child != null) {
            children.add(child);
            child.setParent(this);
        }
    }

    @Override
    public void removeChild(S88Element child) {
        if (child != null && children.remove(child)) {
            child.setParent(null);
        }
    }

    @Override
    public S88Element getParent() {
        return parent;
    }

    @Override
    public void setParent(S88Element parent) {
        this.parent = parent;
    }
}
