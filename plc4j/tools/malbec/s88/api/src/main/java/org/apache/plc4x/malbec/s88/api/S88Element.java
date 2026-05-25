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
    
    String getId();
    
    void setId(String id);
    
    S88Level getLevel();
    
    void setLevel(S88Level level);
    
    String getDescription();
    
    void setDescription(String description);
    
    Map<String, String> getProperties();
    
    String getProperty(String key);
    
    void setProperty(String key, String value);
    
    List<S88Element> getChildren();
    
    void addChild(S88Element child);
    
    void removeChild(S88Element child);
    
    S88Element getParent();
    
    void setParent(S88Element parent);
}
