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
import org.w3c.dom.Element;
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
              <fileHeader companyName="CEOS" productName="Malbec Communication" productVersion="1.0" creationDateTime="2000-01-01T00:00:00Z"/>
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
            .setSaveSuggestedPrefixes(Map.of(CommsXmlIdentifiers.DEVICE_NS, "d"));

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
        validateForSave(doc);
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
        ProjectDocument document = ProjectDocument.Factory.parse(file);
        validateDocument(document);
        return document;
    }

    static ProjectDocument parseDocument(String xml) throws Exception {
        ProjectDocument document = ProjectDocument.Factory.parse(xml);
        validateDocument(document);
        return document;
    }

    static void saveDocument(ProjectDocument document, File file) throws Exception {
        validateForSave(document);
        document.save(file, SAVE_OPTIONS);
    }

    static void saveDocument(ProjectDocument document, OutputStream out) throws Exception {
        validateForSave(document);
        document.save(out, SAVE_OPTIONS);
    }

    private static void validateForSave(ProjectDocument document) {
        validateDocument(document);
        validateUniquePvNames(document);
    }

    static void validateDocument(ProjectDocument document) {
        validatePlcOpen(document);
        validateMalbec(document);
        validateMalbecStructure(document);
    }

    private static void validateUniquePvNames(ProjectDocument document) {
        Map<PvNameKey, PvConfigData> seen = new LinkedHashMap<>();
        for (Configuration configuration : configurationsOf(document)) {
            DeviceConfigData device = DynamicVariableXmlMapper.readDevice(configuration);
            for (PvConfigData pv : DynamicVariableXmlMapper.readPvs(configuration, device)) {
                String name = pv.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                PvNameKey key = new PvNameKey(pv.getDeviceName(), name);
                if (seen.putIfAbsent(key, pv) != null) {
                    throw new IllegalStateException(
                            "The XML contains duplicate variable names: device '"
                            + key.deviceName() + "' uses '" + key.name()
                            + "' more than once.");
                }
            }
        }
    }

    private record PvNameKey(String deviceName, String name) {

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

    private static void validateMalbecStructure(ProjectDocument document) {
        Document dom = (Document) document.getDomNode();
        NodeList configurations = dom.getElementsByTagNameNS(
                CommsXmlIdentifiers.PLCOPEN_NS, "configuration");
        for (int i = 0; i < configurations.getLength(); i++) {
            validateMalbecConfiguration((Element) configurations.item(i));
        }
        validateMalbecPlacement(dom);
    }

    private static void validateMalbecConfiguration(Element configuration) {
        String name = configuration.getAttribute("name");
        List<Element> addData = childElements(
                configuration, CommsXmlIdentifiers.PLCOPEN_NS, "addData");
        if (addData.size() != 1) {
            throw malbecError("configuration '" + name
                    + "' must contain exactly one addData element");
        }
        List<Element> data = childElements(
                addData.get(0), CommsXmlIdentifiers.PLCOPEN_NS, "data");
        if (data.size() != 1) {
            throw malbecError("configuration '" + name
                    + "' must contain exactly one data element");
        }
        Element dataElement = data.get(0);
        if (!CommsXmlIdentifiers.DEVICE_DATA_NAME.equals(dataElement.getAttribute("name"))) {
            throw malbecError("configuration '" + name
                    + "' contains an unsupported data element");
        }
        if (!"preserve".equals(dataElement.getAttribute("handleUnknown"))) {
            throw malbecError("configuration '" + name + "' must preserve unknown data");
        }
        List<Element> children = childElements(dataElement, null, null);
        if (children.size() != 1) {
            throw malbecError("configuration '" + name
                    + "' must contain exactly one device element");
        }
        Element device = children.get(0);
        if (!CommsXmlIdentifiers.DEVICE_NS.equals(device.getNamespaceURI())
                || !"device".equals(device.getLocalName())) {
            throw malbecError("configuration '" + name + "' contains an invalid device element");
        }
    }

    private static void validateMalbecPlacement(Document document) {
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Node node = elements.item(i);
            String namespace = node.getNamespaceURI();
            if (CommsXmlIdentifiers.DEVICE_NS.equals(namespace)) {
                if ("device".equals(node.getLocalName())) {
                    if (!isExpectedDeviceLocation(node)) {
                        throw malbecError("unexpected Device element outside a configuration");
                    }
                } else if (!isInsideDevice(node)) {
                    throw malbecError("unexpected Device element outside a configuration");
                }
            } else if (CommsXmlIdentifiers.PVRECORDS_NS.equals(namespace)
                    && !isInsideDevice(node)) {
                throw malbecError("unexpected PvRecords element outside a device");
            } else if (CommsXmlIdentifiers.PLCOPEN_NS.equals(namespace)
                    && ("addData".equals(node.getLocalName())
                            || "data".equals(node.getLocalName()))
                    && !isExpectedDataLocation(node)) {
                throw malbecError("unexpected " + node.getLocalName()
                        + " element outside a configuration");
            }
        }
    }

    private static boolean isExpectedDeviceLocation(Node node) {
        Node data = node.getParentNode();
        if (!(data instanceof Element) || !"data".equals(data.getLocalName())
                || !CommsXmlIdentifiers.PLCOPEN_NS.equals(data.getNamespaceURI())) {
            return false;
        }
        Node addData = data.getParentNode();
        if (!(addData instanceof Element) || !"addData".equals(addData.getLocalName())
                || !CommsXmlIdentifiers.PLCOPEN_NS.equals(addData.getNamespaceURI())) {
            return false;
        }
        Node configuration = addData.getParentNode();
        return configuration instanceof Element
                && "configuration".equals(((Element) configuration).getLocalName())
                && CommsXmlIdentifiers.PLCOPEN_NS.equals(configuration.getNamespaceURI());
    }

    private static boolean isInsideDevice(Node node) {
        for (Node current = node.getParentNode(); current != null;
                current = current.getParentNode()) {
            if (current instanceof Element
                    && CommsXmlIdentifiers.DEVICE_NS.equals(current.getNamespaceURI())
                    && "device".equals(current.getLocalName())
                    && isExpectedDeviceLocation(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExpectedDataLocation(Node node) {
        Node parent = node.getParentNode();
        if (!(parent instanceof Element)) {
            return false;
        }
        Element parentElement = (Element) parent;
        if ("data".equals(node.getLocalName())) {
            Node grandparent = parentElement.getParentNode();
            return "addData".equals(parentElement.getLocalName())
                    && CommsXmlIdentifiers.PLCOPEN_NS.equals(parentElement.getNamespaceURI())
                    && grandparent instanceof Element
                    && "configuration".equals(((Element) grandparent).getLocalName())
                    && CommsXmlIdentifiers.PLCOPEN_NS.equals(grandparent.getNamespaceURI());
        }
        return "addData".equals(node.getLocalName())
                && "configuration".equals(parentElement.getLocalName())
                && CommsXmlIdentifiers.PLCOPEN_NS.equals(parentElement.getNamespaceURI());
    }

    private static List<Element> childElements(
            Element parent, String namespace, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (namespace != null && !namespace.equals(element.getNamespaceURI())) {
                continue;
            }
            if (localName != null && !localName.equals(element.getLocalName())) {
                continue;
            }
            result.add(element);
        }
        return result;
    }

    private static IllegalStateException malbecError(String detail) {
        return new IllegalStateException(
                "The XML does not conform to the Malbec XSD: " + detail);
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
