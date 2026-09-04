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
package org.apache.plc4x.malbec.s88.data.impl;

import org.apache.plc4x.malbec.s88.api.*;
import org.apache.xmlbeans.XmlException;
import org.junit.jupiter.api.Test;
import org.mesa.xml.b2MML.EquipmentInformationDocument;
import org.mesa.xml.b2MML.EquipmentInformationType;
import org.mesa.xml.b2MML.EquipmentType;

import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class B2MMLRepositoryImplTest {

    // ========== Test Fixtures ==========

    /**
     * In-memory S88Storage that captures the XML output so it can be re-read,
     * enabling true round-trip tests without files or mocks.
     */
    private static class InMemoryStorage implements S88Storage {
        byte[] data;

        @Override
        public InputStream openInput() {
            return data != null
                ? new ByteArrayInputStream(data)
                : new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream openOutput() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    data = toByteArray();
                }
            };
        }
    }

    private static B2MMLRepositoryImpl newRepo(InMemoryStorage storage) {
        return new B2MMLRepositoryImpl(storage);
    }

    private static S88Element element(String id, S88Level level) {
        S88Element e = new S88Element();
        e.setId(id);
        e.setLevel(level);
        return e;
    }

    private static S88PlantModel model(S88Element root) {
        return new S88PlantModel(root);
    }

    // ========== Round-trip: Basic Structure ==========

    @Test
    void roundTripRootOnly() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        repo.savePlant(model(element("Plant", S88Level.NULL)));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("Plant", loaded.getRoot().getId());
        assertEquals(S88Level.NULL, loaded.getRoot().getLevel());
        assertTrue(loaded.getRoot().getChildren().isEmpty());
    }

    @Test
    void roundTripRootWithLevel() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        repo.savePlant(model(element("MyArea", S88Level.AREA)));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("MyArea", loaded.getRoot().getId());
        assertEquals(S88Level.AREA, loaded.getRoot().getLevel());
    }

    @Test
    void roundTripWithSingleChild() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.AREA);
        root.addChild(element("Child", S88Level.PROCESSCELL));
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        List<S88Element> children = loaded.getRoot().getChildren();
        assertEquals(1, children.size());
        assertEquals("Child", children.get(0).getId());
        assertEquals(S88Level.PROCESSCELL, children.get(0).getLevel());
        assertSame(loaded.getRoot(), children.get(0).getParent());
    }

    @Test
    void roundTripDeepHierarchy() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element area = element("Area1", S88Level.AREA);
        S88Element pc = element("PC1", S88Level.PROCESSCELL);
        S88Element unit = element("Unit1", S88Level.UNIT);
        S88Element em = element("EM1", S88Level.EQUIPMENTMODULE);
        S88Element cm = element("CM1", S88Level.CONTROLMODULE);

        area.addChild(pc);
        pc.addChild(unit);
        unit.addChild(em);
        em.addChild(cm);
        repo.savePlant(model(area));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        S88Element l1 = loaded.getRoot();
        assertEquals("Area1", l1.getId());
        assertEquals(1, l1.getChildren().size());

        S88Element l2 = l1.getChildren().get(0);
        assertEquals("PC1", l2.getId());
        assertSame(l1, l2.getParent());
        assertEquals(1, l2.getChildren().size());

        S88Element l3 = l2.getChildren().get(0);
        assertEquals("Unit1", l3.getId());
        assertEquals(1, l3.getChildren().size());

        S88Element l4 = l3.getChildren().get(0);
        assertEquals("EM1", l4.getId());
        assertEquals(1, l4.getChildren().size());

        S88Element l5 = l4.getChildren().get(0);
        assertEquals("CM1", l5.getId());
        assertEquals(0, l5.getChildren().size());
    }

    @Test
    void roundTripControlModulePreservesConcreteType() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element area = element("Area1", S88Level.AREA);
        S88ControlModule motor = ControlModules.MOTOR.create();
        motor.setId("M1");
        motor.setProperty("iMode", 3);
        area.addChild(motor);
        repo.savePlant(model(area));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        S88ControlModule cm = assertInstanceOf(S88ControlModule.class,
                loaded.getRoot().getChildren().get(0));
        assertEquals("Motor", cm.getTypeName());
        assertEquals("M1", cm.getId());
        assertEquals(S88Level.CONTROLMODULE, cm.getLevel());
        assertSame(loaded.getRoot(), cm.getParent());
        assertEquals(3L, cm.getProperty("iMode"));
        assertNull(cm.getProperty("controlModuleType"));
    }

    @Test
    void roundTripMultipleChildren() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.AREA);
        root.addChild(element("A", S88Level.PROCESSCELL));
        root.addChild(element("B", S88Level.PROCESSCELL));
        root.addChild(element("C", S88Level.PROCESSCELL));
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        List<S88Element> children = loaded.getRoot().getChildren();
        assertEquals(3, children.size());
        assertEquals("A", children.get(0).getId());
        assertEquals("B", children.get(1).getId());
        assertEquals("C", children.get(2).getId());
    }

    // ========== Round-trip: Properties ==========

    @Test
    void roundTripWithProperties() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.AREA);
        root.setProperty("author", "John Doe");
        root.setProperty("icon", "custom.png");
        root.setProperty("version", "2.1");
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        S88Element loadedRoot = loaded.getRoot();
        assertEquals("John Doe", loadedRoot.getProperty("author"));
        assertEquals("custom.png", loadedRoot.getProperty("icon"));
        assertEquals("2.1", loadedRoot.getProperty("version"));
    }

    @Test
    void roundTripEmptyStringProperty() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.NULL);
        root.setProperty("note", "");
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("", loaded.getRoot().getProperty("note"));
    }

    @Test
    void nullPropertyNotPersistedAsEntry() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.NULL);
        root.setProperty("author", "StillHere");
        root.setProperty("icon", null);
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("StillHere", loaded.getRoot().getProperty("author"));
        assertEquals("", loaded.getRoot().getProperty("icon"));
    }

    // ========== Description handling ==========

    @Test
    void descriptionPersistedAsBothDedicatedElementAndProperty() throws XmlException, IOException {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.AREA);
        root.setProperty("description", "My description");
        repo.savePlant(model(root));

        EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.parse(new ByteArrayInputStream(storage.data));
        EquipmentInformationType info = doc.getEquipmentInformation();
        EquipmentType xml = info.getEquipmentArray(0);

        assertTrue(xml.sizeOfDescriptionArray() > 0);
        assertEquals("My description", xml.getDescriptionArray(0).getStringValue());
    }

    // ========== Level round-trip ==========

    @Test
    void levelMappingRoundTrip() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        for (S88Level level : S88Level.values()) {
            storage.data = null;
            repo.savePlant(model(element("Test", level)));

            S88PlantModel loaded = repo.loadPlant();
            assertNotNull(loaded, "Failed for level: " + level);
            assertEquals(level, loaded.getRoot().getLevel(),
                "Level mismatch for: " + level);
        }
    }

    // ========== Property key with special characters ==========

    @Test
    void propertyKeyWithSpecialCharacters() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.AREA);
        root.setProperty("my.custom-key_123", "value");
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("value", loaded.getRoot().getProperty("my.custom-key_123"));
    }

    // ========== Id with special characters ==========

    @Test
    void elementIdWithSpecialCharacters() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root-123_Area", S88Level.AREA);
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("Root-123_Area", loaded.getRoot().getId());
    }

    // ========== Error Handling ==========

    @Test
    void loadReturnsNullOnEmptyStorage() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);
        storage.data = new byte[0];

        assertNull(repo.loadPlant());
    }

    @Test
    void loadReturnsNullOnInvalidXml() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);
        storage.data = "not valid xml".getBytes();

        assertNull(repo.loadPlant());
    }

    @Test
    void loadReturnsNullOnPartiallyValidXml() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);
        storage.data = "<?xml version=\"1.0\"?><Equipment></Equipment>".getBytes();

        assertNull(repo.loadPlant());
    }

    // ========== Direct XML parsing: verify saved structure ==========

    @Test
    void savedXmlContainsExpectedIdsAndLevels() throws XmlException, IOException {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Top", S88Level.AREA);
        root.addChild(element("Mid", S88Level.PROCESSCELL));
        repo.savePlant(model(root));

        EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.parse(new ByteArrayInputStream(storage.data));
        EquipmentInformationType info = doc.getEquipmentInformation();
        EquipmentType xml = info.getEquipmentArray(0);

        assertEquals("Top", xml.getID().getStringValue());
        assertEquals("Area", xml.getEquipmentLevel().getStringValue());

        assertEquals(1, xml.sizeOfEquipmentChildArray());
        assertEquals("Mid", xml.getEquipmentChildArray(0).getID().getStringValue());
        assertEquals("ProcessCell", xml.getEquipmentChildArray(0).getEquipmentLevel().getStringValue());
    }

    @Test
    void savedXmlContainsPropertyElements() throws XmlException, IOException {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Root", S88Level.NULL);
        root.setProperty("author", "Jane");
        root.setProperty("icon", "icon.png");
        repo.savePlant(model(root));

        EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.parse(new ByteArrayInputStream(storage.data));
        EquipmentInformationType info = doc.getEquipmentInformation();
        EquipmentType xml = info.getEquipmentArray(0);

        assertEquals(2, xml.sizeOfEquipmentPropertyArray());

        String id0 = xml.getEquipmentPropertyArray(0).getID().getStringValue();
        String id1 = xml.getEquipmentPropertyArray(1).getID().getStringValue();
        assertTrue((id0.equals("author") && id1.equals("icon"))
                 || (id0.equals("icon") && id1.equals("author")));
    }

    // ========== Save with suppressed level (NULL) ==========

    @Test
    void savedXmlOmitsLevelForNullLevel() throws XmlException, IOException {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        repo.savePlant(model(element("Root", S88Level.NULL)));

        EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.parse(new ByteArrayInputStream(storage.data));
        EquipmentInformationType info = doc.getEquipmentInformation();
        EquipmentType xml = info.getEquipmentArray(0);

        assertNull(xml.getEquipmentLevel());
    }

    // ========== Multiple saves round-trip ==========

    @Test
    void multipleSaveCycles() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("V1", S88Level.UNIT);
        root.setProperty("x", "1");
        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertEquals("V1", loaded.getRoot().getId());
        assertEquals("1", loaded.getRoot().getProperty("x"));

        loaded.getRoot().setId("V2");
        loaded.getRoot().setProperty("x", "2");
        repo.savePlant(loaded);

        S88PlantModel loaded2 = repo.loadPlant();
        assertNotNull(loaded2);
        assertEquals("V2", loaded2.getRoot().getId());
        assertEquals("2", loaded2.getRoot().getProperty("x"));
    }
}
