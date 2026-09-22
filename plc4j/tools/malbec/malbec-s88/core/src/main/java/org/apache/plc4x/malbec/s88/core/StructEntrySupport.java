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
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal helpers to read and write entries of a structured property.
 * <p>
 * A {@code containerKey} of {@code null} addresses the element root (a top level property),
 * while a non null {@code containerKey} addresses a nested map stored under that key
 * (e.g. "Parameters" or "Reports").
 * <p>
 * Writes are copy-on-write: the container is cloned before being modified so the maps
 * held by the element are never mutated in place.
 */
final class StructEntrySupport {

    private StructEntrySupport() {
        /* This utility class should not be instantiated */
    }

    static Map<String, Object> copyContainer(S88Element element, String containerKey) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (element == null || containerKey == null) {
            return copy;
        }
        Object raw = element.getProperties().get(containerKey);
        if (raw instanceof Map<?, ?> nested) {
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copy;
    }

    static boolean containsEntry(S88Element element, String containerKey, String entryKey) {
        if (element == null || entryKey == null) {
            return false;
        }
        if (containerKey == null) {
            return element.getProperties().containsKey(entryKey);
        }
        Object raw = element.getProperties().get(containerKey);
        return raw instanceof Map<?, ?> nested && nested.containsKey(entryKey);
    }

    static Object getEntry(S88Element element, String containerKey, String entryKey) {
        if (element == null || entryKey == null) {
            return null;
        }
        if (containerKey == null) {
            return element.getProperties().get(entryKey);
        }
        Object raw = element.getProperties().get(containerKey);
        if (raw instanceof Map<?, ?> nested) {
            return nested.get(entryKey);
        }
        return null;
    }

    static void writeEntry(S88Element element, String containerKey, String entryKey, Object entryValue) {
        if (containerKey == null) {
            element.setProperty(entryKey, entryValue);
            return;
        }
        Map<String, Object> container = copyContainer(element, containerKey);
        container.put(entryKey, entryValue);
        element.setProperty(containerKey, container);
    }

    static void removeEntry(S88Element element, String containerKey, String entryKey) {
        if (containerKey == null) {
            element.setProperty(entryKey, null);
            return;
        }
        Map<String, Object> container = copyContainer(element, containerKey);
        container.remove(entryKey);
        element.setProperty(containerKey, container);
    }
}
