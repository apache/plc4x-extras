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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final CommConfigStorage storage;

    public PlcOpenCommConfigRepositoryImpl(CommConfigStorage storage) {
        this.storage = storage;
    }

    @Override
    public CommunicationsConfig load() throws Exception {
        ProjectDocument doc = parseOrNew();
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
        List<XmlError> errors = new ArrayList<>();
        XmlOptions validationOptions = new XmlOptions().setErrorListener(errors);
        if (!document.validate(validationOptions)) {
            XmlError first = errors.isEmpty() ? null : errors.get(0);
            String detail = first == null ? "unknown validation error" : first.getMessage();
            throw new IllegalStateException(
                    "The XML does not conform to the PlcOpen XSD: " + detail);
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
