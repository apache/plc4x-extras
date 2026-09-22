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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EquipmentClass in B2MML. Template for S88Element grouping.
 */

public class S88ElementClass {
    private String name;
    private S88Level targetLevel;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public  S88ElementClass() {}

    public void setName(String name){
        this.name = name;
    }

    public String getName(){
        return this.name;
    }

    public void setProperty(String key, Object value){
        this.properties.put(key, value);
    }

    public Object getProperty(String key){
        return this.properties.get(key);
    }

    public Map<String,Object> getProperties(){
        return this.properties;
    }

    public void setTargetLevel(S88Level targetLevel) { this.targetLevel = targetLevel; }

    public S88Level getTargetLevel() { return this.targetLevel; }

}
