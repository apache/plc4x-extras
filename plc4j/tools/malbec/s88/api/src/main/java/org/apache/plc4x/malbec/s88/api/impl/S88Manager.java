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
package org.apache.plc4x.malbec.s88.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentPropertyType;
import org.mesa.xml.b2MML.EquipmentType;
import org.mesa.xml.b2MML.ValueType;

/**
 * Service to manage S88 model persistence and mapping.
 */
public class S88Manager {

    public S88PlantModel load(InputStream is) throws IOException, XmlException {
        EquipmentDocument doc = EquipmentDocument.Factory.parse(is);
        S88Element root = mapToApi(doc.getEquipment());
        return new S88PlantModelImpl(root);
    }

    public void save(S88PlantModel model, OutputStream os) throws IOException {
        EquipmentDocument doc = EquipmentDocument.Factory.newInstance();
        EquipmentType rootXml = doc.addNewEquipment();
        mapToXml(model.getRoot(), rootXml);

        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setSaveAggressiveNamespaces();
        doc.save(os, options);
    }

    private S88Element mapToApi(EquipmentType xml) {
        String id = xml.getID() != null ? xml.getID().getStringValue() : "unknown";
        String lvl = xml.isSetEquipmentLevel() ? xml.getEquipmentLevel().getStringValue() : "";

        S88Level level = S88Level.fromTxt(lvl);

        S88ElementImpl element = new S88ElementImpl(id, level);
        if (xml.sizeOfDescriptionArray() > 0) {
            element.setDescription(xml.getDescriptionArray(0).getStringValue());
        }

        for (EquipmentPropertyType prop : xml.getEquipmentPropertyArray()) {
            String propId = prop.getID().getStringValue();
            if (prop.sizeOfValueArray() > 0) {
                ValueType val = prop.getValueArray(0);
                if (val.getValueString() != null) {
                    element.setProperty(propId, val.getValueString().getStringValue());
                }
            }
        }

        for (EquipmentType childXml : xml.getEquipmentChildArray()) {
            element.addChild(mapToApi(childXml));
        }

        return element;
    }

    private void mapToXml(S88Element api, EquipmentType xml) {
        xml.addNewID().setStringValue(api.getId());
        if (api.getLevel() != null && !api.getLevel().isEmpty()) {
            xml.addNewEquipmentLevel().setStringValue(api.getLevel().getB2MMLValue());
        }
        if (api.getDescription() != null) {
            xml.addNewDescription().setStringValue(api.getDescription());
        }

        for (var entry : api.getProperties().entrySet()) {
            EquipmentPropertyType prop = xml.addNewEquipmentProperty();
            prop.addNewID().setStringValue(entry.getKey());
            prop.addNewValue().addNewValueString().setStringValue(entry.getValue());
        }

        for (S88Element childApi : api.getChildren()) {
            mapToXml(childApi, xml.addNewEquipmentChild());
        }
    }

    public S88PlantModel createNew(String rootId) {
        S88Element root = new S88ElementImpl(rootId, S88Level.AREA);
        return new S88PlantModelImpl(root);
    }
}
