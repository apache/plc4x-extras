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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.AreaNode;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El formato de {@code plant-model.xml}.
 *
 * <p>Este fichero se escribe dentro del panel del usuario y lo leen otras partes de la
 * aplicacion, asi que un cambio aqui no se ve en un test de la importacion: se ve mas
 * tarde, en una planta ya instalada. Estos tests fijan el formato.
 */
class PlantModelXmlTest {

    private static AreaNode area(String id) {
        return new AreaNode(id, "area",
                List.of(new Variable("Temperatura", "REAL")),
                List.of(new Variable("Presion", "INT")),
                List.of(),
                List.of());
    }

    private static Document leer(File target) throws Exception {
        return javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(target);
    }

    @Test
    void escribeElAreaConSusIdYUuid(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();

        PlantModelXml.export(area("CELDA1"), target);

        Document doc = leer(target);
        assertEquals("plant-model", doc.getDocumentElement().getTagName());
        Element area = (Element) doc.getDocumentElement().getElementsByTagName("area").item(0);
        assertEquals("area", area.getTagName());
        assertEquals("CELDA1", area.getAttribute("id"));
        assertTrue(!area.getAttribute("uuid").isEmpty(), "el area necesita uuid");
    }

    /**
     * El atributo se escribe como {@code Type} con mayuscula.
     *
     * <p>Es lo que leen los {@code plant-model.xml} que ya hay en plantas instaladas.
     * Pasarlo a minuscula haria que esos ficheros dejaran de interpretarse al reimportar.
     */
    @Test
    void elTipoSeEscribeConMayusculaComoEnLosFicherosExistentes(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();

        PlantModelXml.export(area("CELDA1"), target);

        String contenido = Files.readString(target.toPath(), StandardCharsets.UTF_8);
        assertTrue(contenido.contains("Type=\"REAL\""),
                "los lectores existentes buscan 'Type' con mayuscula: " + contenido);
    }

    @Test
    void reparteLasVariablesEnSuSeccion(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();

        PlantModelXml.export(area("CELDA1"), target);

        Element area = (Element) leer(target).getDocumentElement().getElementsByTagName("area").item(0);
        assertEquals("variables", ((Element) area.getElementsByTagName("variables").item(0))
                .getTagName());
        assertNotNull(area.getElementsByTagName("parameters").item(0));
        assertEquals(0, area.getElementsByTagName("report").getLength(),
                "una seccion sin elementos no debe escribirse, se leeria como vacia de todos modos");
    }

    /** Reimportar la misma area la sustituye, no la duplica. */
    @Test
    void reimportarElMismoAreaLoSustituye(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();
        PlantModelXml.export(area("CELDA1"), target);

        PlantModelXml.export(area("CELDA1"), target);

        assertEquals(1, leer(target).getDocumentElement().getElementsByTagName("area").getLength(),
                "dos areas con el mismo id serian ambiguas para el lector");
    }

    /** El uuid se conserva: otras partes del panel lo usan para identificar la planta. */
    @Test
    void elUuidSeConservaAlReimportar(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();
        PlantModelXml.export(area("CELDA1"), target);
        String uuidOriginal = ((Element) leer(target).getDocumentElement().getElementsByTagName("area").item(0))
                .getAttribute("uuid");

        PlantModelXml.export(area("CELDA1"), target);

        assertEquals(uuidOriginal,
                ((Element) leer(target).getDocumentElement().getElementsByTagName("area").item(0)).getAttribute("uuid"),
                "cambiar el uuid haria que el operador perdiera lo ya configurado");
    }

    @Test
    void areasDistintasConviven(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();

        PlantModelXml.export(area("CELDA1"), target);
        PlantModelXml.export(area("CELDA2"), target);

        assertEquals(2, leer(target).getDocumentElement().getElementsByTagName("area").getLength());
    }

    /**
     * Un {@code plant-model.xml} que ya existe pero esta roto no se pisa.
     *
     * <p>Podria perderse configuracion que el usuario ya tenia, y el mensaje dice
     * exactamente eso en vez de dejar un fallo generico.
     */
    @Test
    void unFicheroExistenteQueNoSePuedeLeerNoSeSobrescribe(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();
        Files.writeString(target.toPath(), "<plant-model><area id=\"CELDA1\"");
        String original = Files.readString(target.toPath(), StandardCharsets.UTF_8);

        IOException ex = assertThrows(IOException.class, () -> PlantModelXml.export(area("CELDA9"), target),
                "no se puede decidir nada sobre un fichero ilegible");

        assertTrue(ex.getMessage().contains(PlantModelXml.FILE_NAME),
                "el mensaje debe nombrar el fichero: " + ex.getMessage());
        assertEquals(original, Files.readString(target.toPath(), StandardCharsets.UTF_8),
                "el fichero que el usuario tenia debe seguir intacto");
    }

    /** H-08(e): sin esto, una DOCTYPE podria leer ficheros locales o hacer peticiones salientes. */
    @Test
    void unPlantModelConEntidadExternaNoSeLee(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();
        Files.writeString(target.toPath(),
                "<!DOCTYPE plant-model [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<plant-model><area id=\"&xxe;\"/></plant-model>");

        assertThrows(IOException.class, () -> PlantModelXml.export(area("CELDA1"), target),
                "una entidad externa debe rechazarse, no resolverse");
    }

    /** Un fallo al escribir no deja un temporal tirado en el panel. */
    @Test
    void noQuedanFicherosTemporales(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();

        PlantModelXml.export(area("CELDA1"), target);

        try (var archivos = Files.list(dir)) {
            List<Path> sobrantes = archivos.filter(p -> p.getFileName().toString().endsWith(".tmp"))
                    .toList();
            assertEquals(List.of(), sobrantes, "el temporal deberia haberse movido o borrado");
        }
    }

    @Test
    void elXmlGeneradoSeVuelveALeer(@TempDir Path dir) throws Exception {
        File target = dir.resolve(PlantModelXml.FILE_NAME).toFile();

        PlantModelXml.export(area("CELDA1"), target);

        // Si esto falla, el fichero que se acaba de escribir no serviria para nada.
        assertNotNull(leer(target).getDocumentElement());
    }
}
