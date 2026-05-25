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

import org.apache.plc4x.malbec.s88.api.S88Identity;
import org.apache.plc4x.malbec.s88.api.S88Level;

/**
 *
 * @author Starblend
 */
public class S88IdentityImpl implements S88Identity {
    
    private String id;
    private S88Level level;
    
    
    public S88IdentityImpl(String id, S88Level level){
        this.id = id;
        this.level = level;
    }
    
    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public S88Level getLevel() {
        return this.level;
    }

    @Override
    public void setLevel(S88Level level) {
        this.level = level;
    }
    
}
