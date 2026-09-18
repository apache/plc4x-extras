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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return new S88Element()
                .setId(id)
                .setLevel(level);
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

        area.addChild(pc);
        pc.addChild(unit);
        unit.addChild(em);
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
        assertEquals(0, l4.getChildren().size());
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

    // ========== Round-trip: structured / nested properties ==========

    @Test
    void roundTripNestedStructPropertySingleEntry() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Unit1", S88Level.UNIT);

        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("Type", "REAL");
        bag.put("Eng_Units/Enum", "CEL");
        bag.put("Reference", 7);
        bag.put("StaticValue", "1.5");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("Temperature", bag);
        root.setProperty("Parameters", parameters);

        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        Map<String, Object> loadedParameters = loaded.getRoot().getStructuredProperty("Parameters");
        assertNotNull(loadedParameters);
        assertEquals(1, loadedParameters.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> loadedBag = (Map<String, Object>) loadedParameters.get("Temperature");
        assertNotNull(loadedBag);
        assertEquals("REAL", loadedBag.get("Type"));
        assertEquals("CEL", loadedBag.get("Eng_Units/Enum"));
        assertEquals("1.5", loadedBag.get("StaticValue"));
        // Integers are serialized as "int" and read back as Long.
        assertEquals(7, ((Number) loadedBag.get("Reference")).intValue());
    }

    @Test
    void roundTripNestedStructPropertyMultipleEntries() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Unit1", S88Level.UNIT);

        Map<String, Object> temperature = new LinkedHashMap<>();
        temperature.put("Type", "REAL");
        Map<String, Object> pressure = new LinkedHashMap<>();
        pressure.put("Type", "INTEGER");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("Temperature", temperature);
        parameters.put("Pressure", pressure);
        root.setProperty("Parameters", parameters);

        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        Map<String, Object> loadedParameters = loaded.getRoot().getStructuredProperty("Parameters");
        assertNotNull(loadedParameters);
        assertEquals(List.of("Temperature", "Pressure"), List.copyOf(loadedParameters.keySet()));

        @SuppressWarnings("unchecked")
        Map<String, Object> loadedTemperature = (Map<String, Object>) loadedParameters.get("Temperature");
        @SuppressWarnings("unchecked")
        Map<String, Object> loadedPressure = (Map<String, Object>) loadedParameters.get("Pressure");
        assertEquals("REAL", loadedTemperature.get("Type"));
        assertEquals("INTEGER", loadedPressure.get("Type"));
    }

    @Test
    void roundTripUnitAttributeAsTopLevelMap() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Unit1", S88Level.UNIT);

        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("Type", "REAL");
        attribute.put("Eng_Units/Enum", "CEL");
        attribute.put("StaticValue", "20.0");
        root.setProperty("Level", attribute);

        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        Map<String, Object> loadedAttribute = loaded.getRoot().getStructuredProperty("Level");
        assertNotNull(loadedAttribute);
        assertEquals("REAL", loadedAttribute.get("Type"));
        assertEquals("CEL", loadedAttribute.get("Eng_Units/Enum"));
        assertEquals("20.0", loadedAttribute.get("StaticValue"));
    }

    @Test
    void roundTripDeeplyNestedStruct() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Unit1", S88Level.UNIT);

        Map<String, Object> range = new LinkedHashMap<>();
        range.put("Unit", "CEL");

        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("Type", "REAL");
        bag.put("Range", range);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("Temperature", bag);
        root.setProperty("Parameters", parameters);

        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        @SuppressWarnings("unchecked")
        Map<String, Object> loadedBag = (Map<String, Object>) loaded.getRoot()
                .getStructuredProperty("Parameters").get("Temperature");
        assertNotNull(loadedBag);
        assertEquals("REAL", loadedBag.get("Type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> loadedRange = (Map<String, Object>) loadedBag.get("Range");
        assertNotNull(loadedRange);
        assertEquals("CEL", loadedRange.get("Unit"));
    }

    @Test
    void emptyStructPropertyIsDropped() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Unit1", S88Level.UNIT);
        root.setProperty("Parameters", new LinkedHashMap<String, Object>());

        repo.savePlant(model(root));

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);
        assertNull(loaded.getRoot().getStructuredProperty("Parameters"));
        assertEquals("", loaded.getRoot().getProperty("Parameters"));
    }

    @Test
    void roundTripEquipmentClassWithNestedProperties() {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88PlantModel model = model(element("Plant", S88Level.NULL));

        S88ElementClass sc = new S88ElementClass();
        sc.setName("MotorClass");
        sc.setTargetLevel(S88Level.EQUIPMENTMODULE);

        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("Type", "REAL");
        sc.setProperty("Speed", bag);
        model.registerClass(sc);

        repo.savePlant(model);

        S88PlantModel loaded = repo.loadPlant();
        assertNotNull(loaded);

        S88ElementClass loadedClass = loaded.findClass("MotorClass");
        assertNotNull(loadedClass);
        assertEquals(S88Level.EQUIPMENTMODULE, loadedClass.getTargetLevel());

        @SuppressWarnings("unchecked")
        Map<String, Object> loadedBag = (Map<String, Object>) loadedClass.getProperty("Speed");
        assertNotNull(loadedBag);
        assertEquals("REAL", loadedBag.get("Type"));
    }

    @Test
    void savedXmlContainsNestedPropertyStructure() throws XmlException, IOException {
        var storage = new InMemoryStorage();
        var repo = newRepo(storage);

        S88Element root = element("Unit1", S88Level.UNIT);

        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("Type", "REAL");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("Temperature", bag);
        root.setProperty("Parameters", parameters);

        repo.savePlant(model(root));

        EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.parse(new ByteArrayInputStream(storage.data));
        EquipmentType xml = doc.getEquipmentInformation().getEquipmentArray(0);

        var parametersXml = xml.getEquipmentPropertyArray(0);
        assertEquals("Parameters", parametersXml.getID().getStringValue());
        assertEquals(1, parametersXml.sizeOfEquipmentPropertyChildArray());

        var temperatureXml = parametersXml.getEquipmentPropertyChildArray(0);
        assertEquals("Temperature", temperatureXml.getID().getStringValue());
        assertEquals(1, temperatureXml.sizeOfEquipmentPropertyChildArray());
        assertEquals("Type", temperatureXml.getEquipmentPropertyChildArray(0).getID().getStringValue());
    }
}
