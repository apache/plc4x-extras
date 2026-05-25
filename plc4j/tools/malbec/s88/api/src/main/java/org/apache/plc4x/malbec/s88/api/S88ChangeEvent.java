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
 * Event representing a change in the S88 model.
 */
public class S88ChangeEvent {
    
    public enum Type {
        ADDED,
        REMOVED,
        UPDATED,
        MOVED,
        RELOADED
    }
    
    private final Type type;
    private final S88Element element;
    private final String propertyName;

    public S88ChangeEvent(Type type, S88Element element) {
        this(type, element, null);
    }

    public S88ChangeEvent(Type type, S88Element element, String propertyName) {
        this.type = type;
        this.element = element;
        this.propertyName = propertyName;
    }

    public Type getType() {
        return type;
    }

    public S88Element getElement() {
        return element;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
