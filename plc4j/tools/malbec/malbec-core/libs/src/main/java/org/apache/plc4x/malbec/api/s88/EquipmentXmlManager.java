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
package org.apache.plc4x.malbec.api.s88;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentType;

/**
 * Utility to manage B2MML Equipment XML Manifest.
 */
public class EquipmentXmlManager {

    private EquipmentXmlManager() {
        // Utility class
    }

    public static EquipmentDocument loadDocument(InputStream is) throws IOException, XmlException {
        return EquipmentDocument.Factory.parse(is);
    }

    public static void saveDocument(EquipmentDocument doc, OutputStream os) throws IOException {
        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setSaveAggressiveNamespaces();
        doc.save(os, options);
    }

    public static EquipmentType createRoot(String id) {
        EquipmentDocument doc = EquipmentDocument.Factory.newInstance();
        EquipmentType root = doc.addNewEquipment();
        root.addNewID().setStringValue(id);
        root.addNewEquipmentLevel().setStringValue("ProcessCell");
        return root;
    }
}
