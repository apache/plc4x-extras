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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
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
            DeviceConfigData device = DeviceXmlMapper.readDevice(configuration);
            if (device != null) {
                config.addDevice(device);
                CommConfigData comms = DeviceXmlMapper.readComms(configuration);
                if (comms != null) {
                    config.upsertComms(device.getUuid(), comms);
                }
            }
        }
        return config;
    }

    @Override
    public void save(CommunicationsConfig config) throws Exception {
        ProjectDocument doc = parseOrNew();
        Configurations configurations = findConfigurations(doc);
        removeAllConfigurations(configurations);
        for (DeviceConfigData device : config.getDevices()) {
            Configuration configuration = DeviceXmlMapper.writeDevice(device, configurations);
            DeviceXmlMapper.writeComms(config.getComms(device.getUuid()), configuration);
        }
        // H-02: primero se serializa entero en memoria y solo despues se toca el destino.
        // Con storage.create() el archivo se vaciaba en el momento de pedir el flujo, de
        // modo que un fallo durante la serializacion dejaba comunicacion.xml vacio y se
        // perdian todos los dispositivos que el usuario habia configurado.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        doc.save(buffer, SAVE_OPTIONS);
        storage.write(buffer.toByteArray());
    }

    private ProjectDocument parseOrNew() throws Exception {
        String text = storage.open();
        if (text == null || text.isBlank()) {
            text = TEMPLATE;
        }
        return ProjectDocument.Factory.parse(text);
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