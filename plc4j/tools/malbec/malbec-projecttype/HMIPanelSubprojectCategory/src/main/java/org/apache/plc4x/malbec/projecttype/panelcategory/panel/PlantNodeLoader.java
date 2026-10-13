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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import org.apache.plc4x.malbec.projecttype.panelcategory.xml.XmlSafe;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class PlantNodeLoader {

    private static final Logger LOGGER = Logger.getLogger(PlantNodeLoader.class.getName());

    private PlantNodeLoader() {
    }

    static Map<String, String> areasDe(Path projectDir) {
        Path plantModel = buscarFichero(projectDir);
        if (plantModel == null) {
            return null;
        }
        Map<String, String> areas = new LinkedHashMap<>();
        try {
            DocumentBuilder builder = XmlSafe.newFactory().newDocumentBuilder();
            NodeList nodes = builder.parse(plantModel.toFile()).getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                if (!"area".equals(element.getTagName())) {
                    continue;
                }
                String id = element.getAttribute("id");
                if (id.isEmpty()) {
                    continue;
                }
                areas.put(id, element.getAttribute("uuid"));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo leer " + plantModel, ex);
            return null;
        }
        return areas;
    }

    private static Path buscarFichero(Path projectDir) {
        Path dir = projectDir;
        while (dir != null) {
            Path xml = dir.resolve("plant-model.xml");
            if (Files.isRegularFile(xml)) {
                return xml;
            }
            dir = dir.getParent();
        }
        return null;
    }
}