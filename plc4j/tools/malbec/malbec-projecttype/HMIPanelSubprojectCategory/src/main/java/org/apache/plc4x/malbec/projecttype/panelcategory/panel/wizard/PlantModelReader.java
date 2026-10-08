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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel.wizard;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.plc4x.malbec.projecttype.panelcategory.xml.XmlSafe;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PlantVariable;

public class PlantModelReader {

    private static final String PLANT_MODEL_FILE = "plant-model.xml";
    private final Path projectDir;

    public PlantModelReader(Path projectDir) {
        this.projectDir = projectDir;
    }

    public List<PlantVariable> loadAreaVariables(String areaId) throws Exception {
        List<PlantVariable> result = new ArrayList<>();
        if (areaId == null || areaId.trim().isEmpty()) {
            return result;
        }

        Path plantModel = findPlantModelFile(projectDir);
        if (plantModel == null) {
            return result;
        }

        // Protección contra ataques XXE y procesamiento seguro. El criterio vive ya en
        // XmlSafe para que importador y lector no puedan divergir (H-08(e)).
        DocumentBuilderFactory factory = XmlSafe.newFactory();

        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream is = Files.newInputStream(plantModel)) {
            Document doc = builder.parse(is);
            Element root = doc.getDocumentElement();
            if (root == null) {
                return result;
            }

            Element area = findAreaRecursive(root, areaId);
            if (area != null) {
                collectPlantVariables(area, areaId, result);
            }
        }

        return result;
    }

    private Element findAreaRecursive(Element element, String areaId) {
        if ("area".equals(element.getTagName()) && areaId.equals(element.getAttribute("id"))) {
            return element;
        }
        
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element found = findAreaRecursive((Element) node, areaId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void collectPlantVariables(Element element, String currentPath, List<PlantVariable> result) {
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element child = (Element) node;
            if ("variable".equals(child.getTagName())) {
                String name = child.getAttribute("name");
                if (!name.isEmpty()) {
                    result.add(new PlantVariable(currentPath, name, child.getAttribute("Type")));
                }
                continue;
            }

            String id = child.getAttribute("id");
            String childPath = id.isEmpty() ? currentPath : currentPath + "/" + id;
            collectPlantVariables(child, childPath, result);
        }
    }

    private Path findPlantModelFile(Path projectDir) {
        Path dir = projectDir;
        while (dir != null) {
            Path xml = dir.resolve(PLANT_MODEL_FILE);
            if (Files.isRegularFile(xml)) {
                return xml;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
