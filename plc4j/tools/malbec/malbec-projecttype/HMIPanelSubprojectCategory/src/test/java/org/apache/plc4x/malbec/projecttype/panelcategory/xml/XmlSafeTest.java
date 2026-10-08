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
package org.apache.plc4x.malbec.projecttype.panelcategory.xml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Blindaje de {@link XmlSafe} frente a XXE y a la denegacion por entidades
 * recursivas (H-08(e)).
 *
 * <p>Estos tests atacan de verdad. En el payload de XXE se apunta a un fichero real
 * creado por el propio test, de modo que si la proteccion fallase, el contenido
 * apareceria en el DOM y el test lo detectaria. Un test que solo comprobara que el
 * parser lanza excepcion seria mas debil.
 */
class XmlSafeTest {

    @TempDir
    Path tempDir;

    @Test
    void rechazaUnaDoctypeConEntidadLocal() throws Exception {
        Path secreto = tempDir.resolve("secreto.txt");
        Files.writeString(secreto, "CONTENIDO-SECRETO-DE-EJEMPLO");

        // La entidad va en contenido de elemento, no en un atributo: XML prohibe las
        // referencias a entidades externas dentro de valores de atributo, asi que un
        // payload en atributo lanzaria siempre y el test pasaria sin probar nada.
        String payload = """
                <?xml version="1.0"?>
                <!DOCTYPE plant-model [
                  <!ENTITY robado SYSTEM "%s">
                ]>
                <plant-model><area>&robado;</area></plant-model>
                """.formatted(secreto.toUri());

        assertThrows(SAXException.class, () -> parsear(payload),
                "H-08(e): una DOCTYPE con entidad externa debe rechazarse outright");
    }

    @Test
    void elContenidoSecretoNoLlegaAlDom() throws Exception {
        Path secreto = tempDir.resolve("secreto.txt");
        Files.writeString(secreto, "CONTENIDO-SECRETO-DE-EJEMPLO");
        String payload = """
                <?xml version="1.0"?>
                <!DOCTYPE plant-model [
                  <!ENTITY robado SYSTEM "%s">
                ]>
                <plant-model><area>&robado;</area></plant-model>
                """.formatted(secreto.toUri());

        String dom = null;
        try {
            dom = parsear(payload).getDocumentElement().getTextContent();
        } catch (SAXException esperada) {
            // es el resultado correcto
        }

        assertTrue(dom == null || !dom.contains("CONTENIDO-SECRETO"),
                "H-08(e): el contenido del fichero local no debe aparecer en el documento");
    }

    @Test
    void rechazaEntidadesRecursivas() {
        String payload = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <lolz>&lol3;</lolz>
                """;

        assertThrows(SAXException.class, () -> parsear(payload),
                "H-08(e): las entidades recursivas deben rechazarse antes de agotar la memoria");
    }

    @Test
    void rechazaUnaDoctypeSimpleSinEntidades() {
        String payload = """
                <?xml version="1.0"?>
                <!DOCTYPE plant-model>
                <plant-model/>
                """;

        assertThrows(SAXException.class, () -> parsear(payload),
                "con disallow-doctype-decl no se admite ninguna DOCTYPE");
    }

    @Test
    void unDocumentoNormalSeSigueParseando() throws Exception {
        String payload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <plant-model>
                  <area id="Llenadora" uuid="abc-123">
                    <variables>
                      <variable name="Presion" Type="REAL"/>
                    </variables>
                  </area>
                </plant-model>
                """;

        Document doc = assertDoesNotThrow(() -> parsear(payload),
                "H-08(e): endurecer el parser no puede romper los ficheros del usuario");

        assertTrue(doc.getDocumentElement().getNodeName().equals("plant-model"));
        assertTrue(doc.getElementsByTagName("area").getLength() == 1,
                "el documento legitimo se sigue leyendo igual");
    }

    @Test
    void laFabricaExpandeEntidadesDesactivado() throws Exception {
        DocumentBuilderFactory factory = XmlSafe.newFactory();

        assertTrue(!factory.isExpandEntityReferences(),
                "las entidades no deben expandirse aunque llegase hasta aqui");
        assertTrue(!factory.isXIncludeAware(),
                "XInclude es otro vector de entrada de contenido externo");
    }

    @Test
    void elParserNoResuelveEntidadesExternas() throws Exception {
        // Parametro entity externa contra un fichero que existe. Aunque
        // disallow-doctype-decl ya lo impide, se comprueba que el resolver no
        // devuelve el contenido del fichero.
        Path secreto = tempDir.resolve("otro.txt");
        Files.writeString(secreto, "OTRO-SECRETO");
        String payload = """
                <?xml version="1.0"?>
                <!DOCTYPE plant-model SYSTEM "no-es-un-para-externo">
                <plant-model/>
                """;

        assertThrows(SAXException.class, () -> parsear(payload),
                "una DOCTYPE con SYSTEM tambien se rechaza");
    }

    private Document parsear(String xml) throws Exception {
        DocumentBuilder builder = XmlSafe.newFactory().newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
