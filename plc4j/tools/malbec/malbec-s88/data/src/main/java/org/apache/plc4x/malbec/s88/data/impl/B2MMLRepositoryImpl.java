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
import org.mesa.xml.b2MML.EquipmentPropertyType;
import org.mesa.xml.b2MML.EquipmentType;
import org.mesa.xml.b2MML.ValueType;
import org.mesa.xml.b2MML.EquipmentInformationDocument;
import org.mesa.xml.b2MML.EquipmentInformationType;
import org.mesa.xml.b2MML.EquipmentClassType;
import org.mesa.xml.b2MML.EquipmentClassPropertyType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of S88Repository for B2MML XML format.
 * Manages the I/O operations for Malbec default model.
 */
public class B2MMLRepositoryImpl implements S88Repository {
    
    private final S88Storage storage;

    public B2MMLRepositoryImpl(S88Storage storage) {
        this.storage = storage;
    }
    
    @Override
    public S88PlantModel loadPlant() {
        try (InputStream input = storage.openInput()){
            EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.parse(input);
            EquipmentInformationType info = doc.getEquipmentInformation();




            Map<String, S88ElementClass> tempClasses = new LinkedHashMap<>();
            for (EquipmentClassType ecXml : info.getEquipmentClassArray()) {
                S88ElementClass ec = new S88ElementClass();
                ec.setName(ecXml.getID().getStringValue());
                for (EquipmentClassPropertyType prop : ecXml.getEquipmentClassPropertyArray()) {
                    if (prop.getID() == null || prop.sizeOfValueArray() == 0) continue;
                    String id = prop.getID().getStringValue();
                    String val = prop.getValueArray(0).getValueString().getStringValue();
                    if ("targetLevel".equals(id)) {
                        ec.setTargetLevel(S88Level.fromTxt(val));
                    } else {
                        ec.setProperty(id, val);
                    }
                }
                tempClasses.put(ec.getName(), ec);
            }
            EquipmentType rootXml = info.sizeOfEquipmentArray() > 0 ? info.getEquipmentArray(0) : null;
            S88Element root = rootXml != null ? mapToApi(rootXml, tempClasses) : new S88Element();

            S88PlantModel model = new S88PlantModel(root);
            for (S88ElementClass ec : tempClasses.values()) {
                model.registerClass(ec);
            }

            return model;

        } catch (XmlException | IOException e) {
            java.util.logging.Logger.getLogger(B2MMLRepositoryImpl.class.getName())
                    .log(java.util.logging.Level.WARNING, "Failed to load plant model", e);
        }
        return null;
    }

    @Override
    public void savePlant(S88PlantModel model) {
        try (OutputStream output = storage.openOutput()){

            EquipmentInformationDocument doc = EquipmentInformationDocument.Factory.newInstance();
            EquipmentInformationType info = doc.addNewEquipmentInformation();


            for (S88ElementClass ec : model.getClasses().values()) {
                EquipmentClassType ecXml = info.addNewEquipmentClass();
                ecXml.addNewID().setStringValue(ec.getName());
                if (ec.getTargetLevel() != null) {
                    EquipmentClassPropertyType prop = ecXml.addNewEquipmentClassProperty();
                    prop.addNewID().setStringValue("targetLevel");
                    prop.addNewValue().addNewValueString().setStringValue(mapLevelToB2MML(ec.getTargetLevel()));
                }
                for (var entry : ec.getProperties().entrySet()) {
                    EquipmentClassPropertyType prop = ecXml.addNewEquipmentClassProperty();
                    prop.addNewID().setStringValue(entry.getKey());
                    prop.addNewValue().addNewValueString().setStringValue(entry.getValue());
                }
            }


            EquipmentType rootXml = info.addNewEquipment();
            mapToXml(model.getRoot(), rootXml);

            XmlOptions options = new XmlOptions();
            options.setSavePrettyPrint();
            options.setSaveAggressiveNamespaces();
            doc.save(output, options);
        } catch (IOException e) {
            java.util.logging.Logger.getLogger(B2MMLRepositoryImpl.class.getName())
                    .log(java.util.logging.Level.WARNING, "Failed to save plant model", e);
        }
    }

    private S88Element mapToApi(EquipmentType xml, Map<String, S88ElementClass> classMap) {
        S88Element element = new S88Element();
        element.setId(xml.getID() != null ? xml.getID().getStringValue() : "unknown");
        element.setLevel(mapLevelFromB2MML(
                xml.getEquipmentLevel() != null ? xml.getEquipmentLevel().getStringValue() : ""));


        if (xml.sizeOfEquipmentClassIDArray() > 0 && classMap != null) {
            String className = xml.getEquipmentClassIDArray(0).getStringValue();
            element.setClass(classMap.get(className));
        }

        if (classMap != null && element.getLevel() != null && !element.getLevel().isEmpty()) {
            S88Level childLevel = element.getLevel().getChildLevel();
            for (S88ElementClass ec : classMap.values()) {
                if (childLevel.equals(ec.getTargetLevel())) {
                    element.addElementClass(ec);
                }
            }
        }

        buildProperties(xml.getEquipmentPropertyArray(), element);
        buildHierarchy(xml.getEquipmentChildArray(), element, classMap);
        return element;
    }

    private void mapToXml(S88Element element, EquipmentType equipment) {
        equipment.addNewID().setStringValue(element.getId());
        S88Level level = element.getLevel();
        if (level != null && !level.isEmpty()) {
            equipment.addNewEquipmentLevel().setStringValue(mapLevelToB2MML(level));
        }

        if (element.getElementClass() != null) {
            equipment.addNewEquipmentClassID().setStringValue(element.getElementClass().getName());
        }
        String desc = element.getProperty("description");
        if (!desc.isEmpty()) {
            equipment.addNewDescription().setStringValue(desc);
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

    private void buildHierarchy(EquipmentType[] equipment, S88Element element, Map<String, S88ElementClass> classMap) {
        for (EquipmentType childXml : equipment) {
            element.addChild(mapToApi(childXml, classMap));
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
