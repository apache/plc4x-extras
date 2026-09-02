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

/**
 * Enumeration of ISA-88 Hierarchy Levels.
 */
public enum S88Level {
    /**
     * A Site logic division grouped by production type.
     */
    AREA("Area"),
    
    /**
     * Zone with Units and Equipments that work together for batch production.
     */
    PROCESSCELL("Process Cell"),
    
    /**
     * Principal equipment that carries physical/chemical transformations.
     */
    UNIT("Unit"),
    
    /**
     * Mechanical components that carries auxiliary functions.
     */
    EQUIPMENTMODULE("Equipment Module"),
    
    /**
     * Lowest level that executes control activities (phases).
     */
    CONTROLMODULE("Control Module"),
    NULL("");

    private final String displayName;

    S88Level(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEmpty() {
        return this == NULL;
    }

    public S88Level getChildLevel() {
        return switch (this) {
            case NULL -> AREA;
            case AREA -> PROCESSCELL;
            case PROCESSCELL -> UNIT;
            case UNIT -> EQUIPMENTMODULE;
            default -> CONTROLMODULE;
        };
    }
    
    
    /**
     * Given a text, returns a valid S88Level that matches.
     * 
     * @param txt input text
     * @return S88Level
     */
    public static S88Level fromTxt(String txt) {
        if (txt == null || txt.trim().isEmpty()) {
            return NULL;
        }
        for (S88Level level : S88Level.values()) {
            if (level.name().equalsIgnoreCase(txt) || level.displayName.equalsIgnoreCase(txt)) {
                return level;
            }
        }
        return NULL;
    }
}
