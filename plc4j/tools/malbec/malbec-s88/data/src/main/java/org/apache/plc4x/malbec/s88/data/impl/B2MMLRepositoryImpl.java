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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

            Map<S88Level, List<S88ElementClass>> classesByTargetLevel = new LinkedHashMap<>();

            for (EquipmentClassType ecXml : info.getEquipmentClassArray()) {
                S88ElementClass ec = new S88ElementClass();
                ec.setName(ecXml.getID().getStringValue());
                for (EquipmentClassPropertyType prop : ecXml.getEquipmentClassPropertyArray()) {
                    if (prop.getID() == null) continue;
                    String id = prop.getID().getStringValue();
                    if ("targetLevel".equals(id)) {
                        if (prop.sizeOfValueArray() > 0 && prop.getValueArray(0).getValueString() != null) {
                            ec.setTargetLevel(S88Level.fromTxt(prop.getValueArray(0).getValueString().getStringValue()));
                        }
                    } else {
                        ec.setProperty(id, readClassPropertyValue(prop));
                    }
                }
                tempClasses.put(ec.getName(), ec);
                if (ec.getTargetLevel() != null) {
                    classesByTargetLevel
                            .computeIfAbsent(ec.getTargetLevel(), k -> new ArrayList<>())
                            .add(ec);
                }
            }

            EquipmentType rootXml = info.sizeOfEquipmentArray() > 0 ? info.getEquipmentArray(0) : null;
            S88Element root = rootXml != null ? mapToApi(rootXml, tempClasses, classesByTargetLevel) : new S88Element();

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
                    writeClassProperty(ecXml.addNewEquipmentClassProperty(), entry.getKey(), entry.getValue());
                }

//                for (var entry : ec.getProperties().entrySet()) {
//                    EquipmentClassPropertyType prop = ecXml.addNewEquipmentClassProperty();
//                    prop.addNewID().setStringValue(entry.getKey());
//                    prop.addNewValue().addNewValueString().setStringValue(entry.getValue());
//                }
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

    private S88Element mapToApi(EquipmentType xml, Map<String, S88ElementClass> classMap,
                                Map<S88Level, List<S88ElementClass>> classesByTargetLevel) {
        S88Element element = new S88Element();
        element.setId(xml.getID() != null ? xml.getID().getStringValue() : "unknown");
        element.setLevel(mapLevelFromB2MML(
                xml.getEquipmentLevel() != null ? xml.getEquipmentLevel().getStringValue() : ""));

        if (xml.sizeOfEquipmentClassIDArray() > 0 && classMap != null) {
            String className = xml.getEquipmentClassIDArray(0).getStringValue();
            element.setClass(classMap.get(className));
        }


        if (classesByTargetLevel != null && element.getLevel() != null && !element.getLevel().isEmpty()) {
            S88Level childLevel = element.getLevel().getChildLevel();
            List<S88ElementClass> matches = classesByTargetLevel.get(childLevel);
            if (matches != null) {
                for (S88ElementClass ec : matches) {
                    element.addElementClass(ec);
                }
            }
        }

        buildProperties(xml.getEquipmentPropertyArray(), element);
        buildHierarchy(xml.getEquipmentChildArray(), element, classMap, classesByTargetLevel);
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
        Object desc = element.getProperty("description");
        if (desc != null && !String.valueOf(desc).isEmpty()) {
            equipment.addNewDescription().setStringValue(String.valueOf(desc));
        }

        for (var entry : element.getProperties().entrySet()) {
            writeProperty(equipment.addNewEquipmentProperty(), entry.getKey(), entry.getValue());
        }

        for (S88Element childApi : element.getChildren()) {
            mapToXml(childApi, equipment.addNewEquipmentChild());
        }
    }


    private void writeProperty(EquipmentPropertyType propXml, String key, Object value) {
        propXml.addNewID().setStringValue(key);
        if (value instanceof Map<?, ?> nested) {
            for (var e : nested.entrySet()) {
                writeProperty(propXml.addNewEquipmentPropertyChild(), String.valueOf(e.getKey()), e.getValue());
            }
        } else if (value != null) {
            ValueType v = propXml.addNewValue();
            v.addNewValueString().setStringValue(String.valueOf(value));
            v.addNewDataType().setStringValue(inferDataType(value));
        }
    }

    private void writeClassProperty(EquipmentClassPropertyType propXml, String key, Object value) {
        propXml.addNewID().setStringValue(key);
        if (value instanceof Map<?, ?> nested) {
            for (var e : nested.entrySet()) {
                writeClassProperty(propXml.addNewEquipmentClassPropertyChild(), String.valueOf(e.getKey()), e.getValue());
            }
        } else if (value != null) {
            ValueType v = propXml.addNewValue();
            v.addNewValueString().setStringValue(String.valueOf(value));
            v.addNewDataType().setStringValue(inferDataType(value));
        }
    }

    private Object readClassPropertyValue(EquipmentClassPropertyType prop) {
        if (prop.sizeOfEquipmentClassPropertyChildArray() > 0) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (EquipmentClassPropertyType child : prop.getEquipmentClassPropertyChildArray()) {
                if (child.getID() == null) continue;
                nested.put(child.getID().getStringValue(), readClassPropertyValue(child));
            }
            return nested;
        }
        if (prop.sizeOfValueArray() > 0 && prop.getValueArray(0).getValueString() != null) {
            ValueType val = prop.getValueArray(0);
            String dataType = val.getDataType() != null ? val.getDataType().getStringValue() : null;
            return parseTypedValue(val.getValueString().getStringValue(), dataType);
        }
        return null;
    }

    private String inferDataType(Object value) {
        if (value instanceof Integer || value instanceof Long) return "int";
        if (value instanceof Double || value instanceof Float) return "double";
        if (value instanceof Boolean) return "boolean";
        return "string";
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

    private void buildHierarchy(EquipmentType[] equipment, S88Element element, Map<String, S88ElementClass> classMap,
                                Map<S88Level, List<S88ElementClass>> classesByTargetLevel) {
        for (EquipmentType childXml : equipment) {
            element.addChild(mapToApi(childXml, classMap, classesByTargetLevel));
        }
    }

    private void buildProperties(EquipmentPropertyType[] properties, S88Element element) {
        for (EquipmentPropertyType prop : properties) {
            if (prop.getID() == null) continue;
            element.setProperty(prop.getID().getStringValue(), readPropertyValue(prop));
        }
    }

    private Object readPropertyValue(EquipmentPropertyType prop) {
        if (prop.sizeOfEquipmentPropertyChildArray() > 0) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (EquipmentPropertyType child : prop.getEquipmentPropertyChildArray()) {
                if (child.getID() == null) continue;
                nested.put(child.getID().getStringValue(), readPropertyValue(child));
            }
            return nested;
        }
        if (prop.sizeOfValueArray() > 0 && prop.getValueArray(0).getValueString() != null) {
            ValueType val = prop.getValueArray(0);
            String dataType = val.getDataType() != null ? val.getDataType().getStringValue() : null;
            return parseTypedValue(val.getValueString().getStringValue(), dataType);
        }
        return null;
    }

    private Object parseTypedValue(String raw, String dataType) {
        if (raw == null) return null;
        try {
            return switch (dataType != null ? dataType.toLowerCase() : "string") {
                case "int", "integer", "long" -> Long.parseLong(raw);
                case "double", "float", "real" -> Double.parseDouble(raw);
                case "boolean", "bool" -> Boolean.parseBoolean(raw);
                default -> raw;
            };
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}