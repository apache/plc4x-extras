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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.plcopen.xml.tc60201.ProjectDocument;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public final class PlcOpenCommConfigRepositoryImpl implements CommConfigRepository {

    public static final String TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://www.plcopen.org/xml/tc6_0201"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://www.plcopen.org/xml/tc6_0201 http://www.plcopen.org/xml/tc6_0201.xsd">
              <fileHeader companyName="Malbec" productName="Malbec Communication" productVersion="1.0" creationDateTime="2000-01-01T00:00:00Z"/>
              <contentHeader name="MalbecCommunication">
                <coordinateInfo>
                  <fbd><scaling x="1" y="1"/></fbd>
                  <ld><scaling x="1" y="1"/></ld>
                  <sfc><scaling x="1" y="1"/></sfc>
                </coordinateInfo>
              </contentHeader>
              <types>
                <dataTypes/>
                <pous/>
              </types>
              <instances>
                <configurations/>
              </instances>
            </project>
            """;

    private static final XmlOptions SAVE_OPTIONS = new XmlOptions()
            .setSavePrettyPrint()
            .setSavePrettyPrintIndent(2)
            .setSaveAggressiveNamespaces()
            .setSaveSuggestedPrefixes(Map.of(
                    CommsXmlIdentifiers.DEVICE_NS, "d",
                    CommsXmlIdentifiers.COMMS_NS, "c"));

    private static final String MALBEC_DEVICE_XSD = "malbec-device.xsd";
    private static final Schema MALBEC_SCHEMA = loadMalbecSchema();

    private final CommConfigStorage storage;

    public PlcOpenCommConfigRepositoryImpl(CommConfigStorage storage) {
        this.storage = storage;
    }

    @Override
    public CommunicationsConfig load() throws Exception {
        ProjectDocument doc = parseOrNew();
        validateDocument(doc);
        CommunicationsConfig config = new CommunicationsConfig();
        for (Configuration configuration : configurationsOf(doc)) {
            DeviceConfigData device = DynamicVariableXmlMapper.readDevice(configuration);
            if (device == null) {
                continue;
            }
            config.addDevice(device);
            List<PvConfigData> pvs = DynamicVariableXmlMapper.readPvs(configuration, device);
            if (!pvs.isEmpty()) {
                config.upsertVariables(device.getUuid(), pvs);
            }
        }
        return config;
    }

    @Override
    public void save(CommunicationsConfig config) throws Exception {
        ProjectDocument doc = parseOrNew();
        Configurations configurations = findConfigurations(doc);
        Map<String, List<PvConfigData>> pvsByDevice = readPvsByDevice(configurations);
        removeAllConfigurations(configurations);
        for (DeviceConfigData device : config.getDevices()) {
            Configuration configuration = DynamicVariableXmlMapper.writeDevice(device, configurations);
            DynamicVariableXmlMapper.writePvs(
                    configuration, pvsByDevice.getOrDefault(device.getUuid(), List.of()));
        }
        validateDocument(doc);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        doc.save(buffer, SAVE_OPTIONS);
        storage.write(buffer.toByteArray());
    }

    private static Map<String, List<PvConfigData>> readPvsByDevice(
            Configurations configurations) {
        Map<String, List<PvConfigData>> result = new LinkedHashMap<>();
        for (Configuration configuration : configurations.getConfigurationList()) {
            DeviceConfigData device = DynamicVariableXmlMapper.readDevice(configuration);
            if (device == null || device.getUuid() == null) {
                continue;
            }
            List<PvConfigData> pvs = DynamicVariableXmlMapper.readPvs(
                    configuration, device);
            if (!pvs.isEmpty()) {
                result.put(device.getUuid(), pvs);
            }
        }
        return result;
    }

    private ProjectDocument parseOrNew() throws Exception {
        String text = storage.open();
        if (text == null || text.isBlank()) {
            text = TEMPLATE;
        }
        return ProjectDocument.Factory.parse(text);
    }

    static ProjectDocument parseDocument(File file) throws Exception {
        return ProjectDocument.Factory.parse(file);
    }

    static void saveDocument(ProjectDocument document, File file) throws Exception {
        validateDocument(document);
        document.save(file, SAVE_OPTIONS);
    }

    static void saveDocument(ProjectDocument document, OutputStream out) throws Exception {
        validateDocument(document);
        document.save(out, SAVE_OPTIONS);
    }

    private static void validateDocument(ProjectDocument document) {
        validatePlcOpen(document);
        validateMalbec(document);
    }

    private static void validatePlcOpen(ProjectDocument document) {
        List<XmlError> errors = new ArrayList<>();
        XmlOptions validationOptions = new XmlOptions().setErrorListener(errors);
        if (!document.validate(validationOptions)) {
            XmlError first = errors.isEmpty() ? null : errors.get(0);
            String detail = first == null ? "unknown validation error" : first.getMessage();
            throw new IllegalStateException(
                    "The XML does not conform to the PlcOpen XSD: " + detail);
        }
    }

    private static void validateMalbec(ProjectDocument document) {
        NodeList devices = ((Document) document.getDomNode())
                .getElementsByTagNameNS(CommsXmlIdentifiers.DEVICE_NS, "device");
        for (int i = 0; i < devices.getLength(); i++) {
            validateMalbecDevice(devices.item(i));
        }
    }

    private static void validateMalbecDevice(Node device) {
        Validator validator = MALBEC_SCHEMA.newValidator();
        List<SAXParseException> errors = new ArrayList<>();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
            }

            @Override
            public void error(SAXParseException exception) {
                errors.add(exception);
            }

            @Override
            public void fatalError(SAXParseException exception) {
                errors.add(exception);
            }
        });
        try {
            validator.validate(new DOMSource(device));
        } catch (SAXException | IOException ex) {
            throw new IllegalStateException(
                    "The XML does not conform to the Malbec XSD: " + ex.getMessage(), ex);
        }
        if (!errors.isEmpty()) {
            SAXParseException first = errors.get(0);
            throw new IllegalStateException(
                    "The XML does not conform to the Malbec XSD: " + first.getMessage(), first);
        }
    }

    private static Schema loadMalbecSchema() {
        URL resource = PlcOpenCommConfigRepositoryImpl.class.getResource(MALBEC_DEVICE_XSD);
        if (resource == null) {
            throw new IllegalStateException(
                    "The Malbec XSD could not be found: " + MALBEC_DEVICE_XSD);
        }
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream input = resource.openStream()) {
            StreamSource source = new StreamSource(input);
            source.setSystemId(resource.toExternalForm());
            return factory.newSchema(source);
        } catch (IOException | SAXException ex) {
            throw new IllegalStateException(
                    "The Malbec XSD could not be loaded: " + ex.getMessage(), ex);
        }
    }

    private static List<Configuration> configurationsOf(ProjectDocument doc) {
        return new ArrayList<>(findConfigurations(doc).getConfigurationList());
    }

    private static Configurations findConfigurations(ProjectDocument doc) {
        return doc.getProject().getInstances().getConfigurations();
    }

    private static void removeAllConfigurations(Configurations configurations) {
        configurations.setConfigurationArray(new Configuration[0]);
    }
}
