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


import java.util.List;
import java.util.Map;

/**
 * Represents an ISA-88 Plant Element.
 */
public interface S88Element {
   
    
    void setS88Identity(S88Identity identity);
    
    S88Identity getS88Identity();
    
    void setS88Hierarchy(S88Hierarchy hierarchy);
    
    S88Hierarchy getS88Hierarchy();
    
    void setS88PropertyBag(S88PropertyBag properties);
    
    S88PropertyBag getS88PropertyBag();
    
    // Convenience methods
    default String getId() { return getS88Identity().getId(); }
    default void setId(String id) { getS88Identity().setId(id); }
    default S88Level getLevel() { return getS88Identity().getLevel(); }
    default void setLevel(S88Level level) { getS88Identity().setLevel(level); }
    
    default String getProperty(String key) { return getS88PropertyBag().getProperty(key); }
    default void setProperty(String key, String value) { getS88PropertyBag().setProperty(key, value); }
    default Map<String, String> getProperties() { return getS88PropertyBag().getProperties(); }
    
    default List<S88Element> getChildren() { return getS88Hierarchy().getChildren(); }
    default void addChild(S88Element child) { getS88Hierarchy().addChild(child); }
    default void removeChild(S88Element child) { getS88Hierarchy().removeChild(child); }
}
