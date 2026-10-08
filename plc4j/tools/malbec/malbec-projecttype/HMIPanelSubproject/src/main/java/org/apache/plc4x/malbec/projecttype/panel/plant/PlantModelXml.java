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
package org.apache.plc4x.malbec.projecttype.panel.plant;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.plc4x.malbec.projecttype.panelcategory.xml.XmlSafe;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.AreaNode;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.Variable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Lee y escribe {@code plant-model.xml}, el volcado del modelo de planta dentro del panel.
 *
 * <p>Estaba dentro de la accion de Swing, junto con la llamada al sistema de ficheros. Se
 * separa porque es lo unico aqui que tiene un formato en disco y unos riesgos propios
 * (entidades externas, escritura a medias, perder el fichero anterior), y porque asi se
 * puede probar sin abrir ninguna ventana.
 */
public final class PlantModelXml {

    public static final String FILE_NAME = "plant-model.xml";

    private static final String ROOT_TAG = "plant-model";

    private PlantModelXml() {
    }

    /**
     * Anade o sustituye el area del snapshot en {@code target}.
     *
     * <p>Si el area ya estaba, se conserva su {@code uuid}: otras partes del panel lo usan
     * para identificar la planta, y cambiarlo en cada importacion haria que el operador
     * perdiera la correspondencia con lo que ya habia configurado.
     *
     * @throws IOException si el fichero existe y no se puede leer, o si el generado no
     *                     vuelve a ser legible. En ninguno de los dos casos se toca el
     *                     archivo existente.
     */
    public static void export(AreaNode snapshot, File target) throws IOException {
        Document doc = loadOrCreateDocument(target);
        String areaId = snapshot.id();
        String areaUuid = findAreaUuid(doc, areaId);
        Element areaXml = buildElementXml(doc, snapshot);
        areaXml.setAttribute("uuid", areaUuid != null ? areaUuid : UUID.randomUUID().toString());
        removePlant(doc, areaId);
        doc.getDocumentElement().appendChild(areaXml);
        writeSnapshotDocument(target, doc);
    }

    private static String findAreaUuid(Document doc, String id) {
        Element root = doc.getDocumentElement();
        if (root == null) {
            return null;
        }
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if ("area".equals(element.getTagName()) && id.equals(element.getAttribute("id"))) {
                String uuid = element.getAttribute("uuid");
                return uuid.isEmpty() ? null : uuid;
            }
        }
        return null;
    }

    private static Document loadOrCreateDocument(File target) throws IOException {
        if (target.exists()) {
            try {
                // SinXmlSafe, una DOCTYPE con entidades externas permitiria leer ficheros
                // locales o hacer peticiones salientes desde un plant-model.xml manipulado.
                return builder().parse(target);
            } catch (SAXException | ParserConfigurationException ex) {
                throw new IOException(FILE_NAME + " ya existe pero no se puede leer, "
                        + "por lo que la importación se canceló para no perder su contenido. "
                        + "Revisá el archivo y, si ya no sirve, borralo a mano.", ex);
            }
        }
        try {
            Document doc = builder().newDocument();
            doc.appendChild(doc.createElement(ROOT_TAG));
            return doc;
        } catch (ParserConfigurationException ex) {
            throw new IOException(ex);
        }
    }

    /** Quita el area con ese id, para que la reimportacion la sustituya en vez de duplicarla. */
    private static void removePlant(Document doc, String id) {
        Element root = doc.getDocumentElement();
        if (root == null) {
            return;
        }
        NodeList nodes = root.getChildNodes();
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if ("area".equals(element.getTagName()) && id.equals(element.getAttribute("id"))) {
                root.removeChild(node);
            }
        }
    }

    private static Element buildElementXml(Document doc, AreaNode element) {
        Element xml = doc.createElement(element.tag());
        xml.setAttribute("id", element.id());
        appendSectionXml(doc, xml, "variables", element.variables());
        appendSectionXml(doc, xml, "parameters", element.parameters());
        appendSectionXml(doc, xml, "report", element.report());

        for (AreaNode child : element.children()) {
            xml.appendChild(buildElementXml(doc, child));
        }
        return xml;
    }

    private static void appendSectionXml(Document doc, Element parent, String tag,
            List<Variable> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Element section = doc.createElement(tag);
        for (Variable variable : variables) {
            Element variableXml = doc.createElement("variable");
            variableXml.setAttribute("name", variable.name());
            // El atributo se sigue escribiendo como "Type" con mayuscula porque es lo que
            // leen los ficheros existentes; los lectores aceptan las dos formas.
            variableXml.setAttribute("Type", variable.type());
            section.appendChild(variableXml);
        }
        parent.appendChild(section);
    }

    /**
     * Escribe por atomicidad: primero a un temporal, se comprueba que se puede leer y
     * solo entonces se mueve encima del destino. Un fallo a mitad de escritura deja el
     * {@code plant-model.xml} anterior intacto en vez de uno corrupto.
     */
    private static void writeSnapshotDocument(File target, Document doc) throws IOException {
        Path temporal = Files.createTempFile(target.toPath().getParent(), FILE_NAME, ".tmp");
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(temporal.toFile()));
            verificarLegible(temporal.toFile());
            Files.move(temporal, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (TransformerException ex) {
            throw new IOException(ex);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static void verificarLegible(File archivo) throws IOException {
        try {
            builder().parse(archivo);
        } catch (SAXException | ParserConfigurationException ex) {
            throw new IOException("El " + FILE_NAME + " generado no se pudo leer, "
                    + "por lo que no se reemplazó el archivo existente.", ex);
        }
    }

    /** Parser sin DTD ni entidades externas, que es lo que {@code XmlSafe} garantiza. */
    private static DocumentBuilder builder() throws ParserConfigurationException {
        return XmlSafe.newFactory().newDocumentBuilder();
    }
}