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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructEntrySupportTest {

    private S88Element element;

    @BeforeEach
    void setUp() {
        element = new S88Element().setId("Element");
    }

    @Test
    void copyContainerReturnsEmptyMapWhenElementIsNull() {
        Map<String, Object> copy = StructEntrySupport.copyContainer(null, "Parameters");
        assertNotNull(copy);
        assertTrue(copy.isEmpty());
    }

    @Test
    void copyContainerReturnsEmptyMapWhenContainerKeyIsNull() {
        assertTrue(StructEntrySupport.copyContainer(element, null).isEmpty());
    }

    @Test
    void copyContainerReturnsEmptyMapWhenValueIsNotAMap() {
        element.setProperty("x", "text");
        assertTrue(StructEntrySupport.copyContainer(element, "x").isEmpty());
    }

    @Test
    void isCheckToleratesAbsentAndNonBooleanValues() {
        assertFalse(element.isCheck());

        element.setProperty("Check", "true");
        assertTrue(element.isCheck());

        element.setProperty("Check", "garbage");
        assertFalse(element.isCheck());

        element.setCheck(true);
        assertTrue(element.isCheck());
    }

    @Test
    void getStructuredPropertyReturnsImmutableSnapshot() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("P1", "v1");
        element.setProperty("Parameters", nested);

        Map<String, Object> view = element.getStructuredProperty("Parameters");
        assertNotNull(view);
        assertThrows(UnsupportedOperationException.class, () -> view.put("P2", "v2"));

        Map<String, Map<String, Object>> all = element.getStructuredProperties(null);
        assertThrows(UnsupportedOperationException.class, () -> all.put("Other", new LinkedHashMap<>()));
    }

    @Test
    void copyContainerIsIndependentFromOriginal() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("P1", "v1");
        element.setProperty("Parameters", nested);

        Map<String, Object> copy = StructEntrySupport.copyContainer(element, "Parameters");
        copy.put("P2", "v2");

        Map<String, Object> original = element.getStructuredProperty("Parameters");
        assertFalse(original.containsKey("P2"), "Original container must not be mutated");
        assertTrue(copy.containsKey("P1"));
    }

    @Test
    void containsEntryAtRoot() {
        element.setProperty("icon", "x.png");
        assertTrue(StructEntrySupport.containsEntry(element, null, "icon"));
        assertFalse(StructEntrySupport.containsEntry(element, null, "missing"));
    }

    @Test
    void containsEntryInNestedContainer() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("P1", "v1");
        element.setProperty("Parameters", nested);

        assertTrue(StructEntrySupport.containsEntry(element, "Parameters", "P1"));
        assertFalse(StructEntrySupport.containsEntry(element, "Parameters", "P2"));
        assertFalse(StructEntrySupport.containsEntry(element, "Unknown", "P1"));
    }

    @Test
    void containsEntryIsNullSafe() {
        assertFalse(StructEntrySupport.containsEntry(null, null, "x"));
        assertFalse(StructEntrySupport.containsEntry(element, null, null));
    }

    @Test
    void getEntryReadsRootAndNestedValues() {
        element.setProperty("icon", "x.png");
        assertEquals("x.png", StructEntrySupport.getEntry(element, null, "icon"));
        assertNull(StructEntrySupport.getEntry(element, null, "missing"));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("P1", "v1");
        element.setProperty("Parameters", nested);

        assertEquals("v1", StructEntrySupport.getEntry(element, "Parameters", "P1"));
        assertNull(StructEntrySupport.getEntry(element, "Parameters", "P2"));
    }
}
