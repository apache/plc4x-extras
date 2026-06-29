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
import org.apache.xmlbeans.XmlOptions;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentPropertyType;
import org.mesa.xml.b2MML.EquipmentType;
import org.mesa.xml.b2MML.ValueType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of S88Repository for B2MML XML format.
 */
public class B2MMLRepositoryImpl implements S88Repository {
    
    private final S88Storage storage;

    public B2MMLRepositoryImpl(S88Storage storage) {
        this.storage = storage;
    }
    
    @Override
    public S88PlantModel loadPlant() {
        try (InputStream input = storage.openInput()){
            EquipmentDocument doc = EquipmentDocument.Factory.parse(input);
            S88Element root = mapToApi(doc.getEquipment());
            return new S88PlantModel(root);
        } catch (XmlException | IOException ignored) {
            
        }
        return null;
    }

    @Override
    public void savePlant(S88PlantModel model) {
        try (OutputStream output = storage.openOutput()){
            EquipmentDocument doc = EquipmentDocument.Factory.newInstance();
            EquipmentType rootXml = doc.addNewEquipment();
            mapToXml(model.getRoot(), rootXml);

            XmlOptions options = new XmlOptions();
            options.setSavePrettyPrint();
            options.setSaveAggressiveNamespaces();
            doc.save(output, options);
        } catch (IOException ignored) {

        }
    }

    private S88Element mapToApi(EquipmentType xml) {
        S88Element element = new S88Element();

        String id = xml.getID() != null ? xml.getID().getStringValue() : "unknown";
        String lvlTxt = xml.getEquipmentLevel() != null ? xml.getEquipmentLevel().getStringValue() : "";
        
        element.setId(id);
        element.setLevel(mapLevelFromB2MML(lvlTxt));
        
        if (xml.sizeOfDescriptionArray() > 0) {
            element.setProperty("description", xml.getDescriptionArray(0).getStringValue());
        }
        
        buildProperties(xml.getEquipmentPropertyArray(), element);
        buildHierarchy(xml.getEquipmentChildArray(), element);
        
        return element;
    }

    private void mapToXml(S88Element element, EquipmentType equipment) {
        equipment.addNewID().setStringValue(element.getId());
        S88Level level = element.getLevel();
        if (level != null && !level.isEmpty()) {
            equipment.addNewEquipmentLevel().setStringValue(mapLevelToB2MML(level));
        }
        if (element.getProperty("description") != null) {
            equipment.addNewDescription().setStringValue(element.getProperty("description"));
        }

        for (var entry : element.getProperties().entrySet()) {
            EquipmentPropertyType prop = equipment.addNewEquipmentProperty();
            prop.addNewID().setStringValue(entry.getKey());
            prop.addNewValue().addNewValueString().setStringValue(entry.getValue());
        }

        for (S88Element childApi : element.getChildren()) {
            mapToXml(childApi, equipment.addNewEquipmentChild());
        }
    }

    private String mapLevelToB2MML(S88Level level) {
        return switch (level) {
            case AREA -> "Area";
            case PROCESSCELL -> "ProcessCell";
            case UNIT -> "Unit";
            case EQUIPMENTMODULE -> "EquipmentModule";
            case CONTROLMODULE -> "ControlModule";
            default -> "";
        };
    }

    private S88Level mapLevelFromB2MML(String txt) {
        if (txt == null || txt.isBlank()) return S88Level.NULL;
        return switch (txt) {
            case "Area" -> S88Level.AREA;
            case "ProcessCell" -> S88Level.PROCESSCELL;
            case "Unit" -> S88Level.UNIT;
            case "EquipmentModule" -> S88Level.EQUIPMENTMODULE;
            case "ControlModule" -> S88Level.CONTROLMODULE;
            default -> S88Level.fromTxt(txt);
        };
    }

    private void buildHierarchy(EquipmentType[] equipment, S88Element element) {
        for (EquipmentType childXml : equipment) {
            element.addChild(mapToApi(childXml));
        }
    }

    private void buildProperties(EquipmentPropertyType[] properties, S88Element element) {
        for (EquipmentPropertyType prop : properties) {
            if (prop.getID() == null) continue;
            String propId = prop.getID().getStringValue();
            if (prop.sizeOfValueArray() > 0) {
                ValueType val = prop.getValueArray(0);
                if (val.getValueString() != null) {
                    element.setProperty(propId, val.getValueString().getStringValue());
                }
            }
        }
    }
}
