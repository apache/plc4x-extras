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

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Hierarchy;
import org.apache.plc4x.malbec.s88.api.S88Identity;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PropertyBag;

/**
 * Implementation of S88Element.
 */
public class S88ElementImpl implements S88Element {
    
    private S88Identity identity;
    private S88Hierarchy hierarchy;
    private S88PropertyBag properties;

    public S88ElementImpl(S88Identity identity, S88Hierarchy hierarchy, S88PropertyBag properties) {
        this.identity = identity != null ? identity : new S88IdentityImpl("unknown", S88Level.NULL);
        this.hierarchy = hierarchy != null ? hierarchy : new S88HierarchyImpl(this);
        if (this.hierarchy instanceof S88HierarchyImpl) {
            ((S88HierarchyImpl)this.hierarchy).setOwner(this);
        }
        this.properties = properties != null ? properties : new S88PropertyBagImpl();
    }
    
    public S88ElementImpl(){
        this(null, null, null);
    }


    @Override
    public void setS88Identity(S88Identity identity) {
        this.identity = identity;
    }

    @Override
    public S88Identity getS88Identity() {
        return this.identity;
    }

    @Override
    public void setS88Hierarchy(S88Hierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    @Override
    public S88Hierarchy getS88Hierarchy() {
        return this.hierarchy;
    }

    @Override
    public void setS88PropertyBag(S88PropertyBag properties) {
        this.properties = properties;
    }

    @Override
    public S88PropertyBag getS88PropertyBag() {
        return this.properties;
    }
}
