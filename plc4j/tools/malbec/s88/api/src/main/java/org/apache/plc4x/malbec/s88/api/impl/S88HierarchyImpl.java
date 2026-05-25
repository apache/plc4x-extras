/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Hierarchy;

/**
 *
 * @author Starblend
 */
public class S88HierarchyImpl implements S88Hierarchy {
    
    private final List<S88Element> children = new ArrayList<>();
    private S88Element parent;
    private S88Element owner;

    public S88HierarchyImpl(S88Element owner) {
        this.owner = owner;
    }
    
    public S88HierarchyImpl() {}

    public void setOwner(S88Element owner) {
        this.owner = owner;
    }

    @Override
    public List<S88Element> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void addChild(S88Element child) {
        if (child != null) {
            children.add(child);
            child.getS88Hierarchy().setParent(owner);
        }
    }

    @Override
    public void removeChild(S88Element child) {
        if (child != null && children.remove(child)) {
            child.getS88Hierarchy().setParent(null);
        }
    }

    @Override
    public S88Element getParent() {
        return this.parent;
    }

    @Override
    public void setParent(S88Element parent) {
        this.parent = parent;
    }
    
}
