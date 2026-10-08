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
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lee los nodos S88 del area de un proyecto, desde {@code plant-model.xml}.
 *
 * <p>Estaba dentro de {@code CreateDevicePanel} y era lo unico del panel que no era
 * Swing: leia un XML y llenaba un desplegable. Aqui solo lee, y el panel se encarga de
 * llenar el desplegable con lo que devuelva.
 *
 * <p>La separacion no es estetica: leer el XML no necesita pantalla, asi que esto se
 * puede probar de verdad, que antes no se podia porque para llegar habia que construir el
 * panel entero.
 */
final class PlantNodeLoader {

    private static final Logger LOGGER = Logger.getLogger(PlantNodeLoader.class.getName());

    private PlantNodeLoader() {
    }

    /**
     * Los nodos de area del proyecto, del id a su uuid.
     *
     * <p>El orden es el del fichero, porque el desplegable lo respeta y el usuario
     * reconoce mejor los nodos en el orden en que estan declarados.
     *
     * @return los nodos; un mapa vacio si el fichero existe pero no declara ninguno, y
     *         {@code null} si no se encontro el fichero o no se pudo leer, que son
     *         dos cosas distintas de "el proyecto no tiene areas"
     */
    static Map<String, String> areasDe(Path projectDir) {
        Path plantModel = buscarFichero(projectDir);
        if (plantModel == null) {
            return null;
        }
        Map<String, String> areas = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
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

    /**
     * Busca {@code plant-model.xml} subiendo desde el proyecto hasta la raiz.
     *
     * <p>Se mantiene la subida porque es como funcionaba antes de partir el panel:
     * algunos proyectos tienen el modelo de planta un nivel por encima, en la carpeta
     * que agrupa a las maquinas. Acotarlo a la raiz del proyecto, que es lo que sugiere
     * el plan, cambiaria el comportamiento de esos proyectos y no se hace aqui sin
     * decidinglo antes: habria que comprobar donde lo tienen guardado cada uno.
     */
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