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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantNodeLoaderTest {

    private static void write(Path destino, String content) throws IOException {
        Files.writeString(destino, content);
    }

    private static final String XML_CON_DOS_AREAS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <area id="TANQUE_1" uuid="uuid-t1"/>
              <area id="TANQUE_2" uuid="uuid-t2"/>
            </project>
            """;

    @Test
    void leeLasAreasConSuUuid(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"), XML_CON_DOS_AREAS);

        Map<String, String> areas = PlantNodeLoader.areasOf(dir);

        assertNotNull(areas);
        assertEquals(2, areas.size());
        assertEquals("uuid-t1", areas.get("TANQUE_1"));
        assertEquals("uuid-t2", areas.get("TANQUE_2"));
    }

    @Test
    void conservaElOrdenDelFichero(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <area id="ZULU" uuid="u1"/>
                  <area id="ALFA" uuid="u2"/>
                  <area id="MIKE" uuid="u3"/>
                </project>
                """);

        Map<String, String> areas = PlantNodeLoader.areasOf(dir);

        assertEquals("[ZULU, ALFA, MIKE]", areas.keySet().toString(),
                "el desplegable tiene que salir en el mismo orden que el fichero");
    }

    @Test
    void unFicheroSinDevuelveMapaVacioYNoNulo(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"),
                "<?xml version=\"1.0\"?><project/>");

        Map<String, String> areas = PlantNodeLoader.areasOf(dir);

        assertNotNull(areas, "el fichero existe y se leyo, aunque no declare areas");
        assertTrue(areas.isEmpty());
    }

    @Test
    void missingFileReturnsNull(@TempDir Path dir) {
        assertNull(PlantNodeLoader.areasOf(dir),
                "no hay plant-model.xml en ninguna carpeta hacia arriba");
    }

    @Test
    void unXmlRotoDevuelveNuloYNoRevienta(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"), "<project><area id=\"X\"</project>");

        assertNull(PlantNodeLoader.areasOf(dir),
                "un XML ilegible se trata como si no hubiera areas, no como un error fatal");
    }

    @Test
    void niUnaDoctypeSimpleSeAdmite(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE project>
                <project>
                  <area id="TANQUE_1" uuid="u1"/>
                </project>
                """);

        assertNull(PlantNodeLoader.areasOf(dir),
                "este lector usa el parser endurecido, que no admite ninguna DOCTYPE. Con el "
                        + "parser sin endurecer este fichero se leeria con normalidad y las "
                        + "areas saldrian, asi que el fallo distingue una defensa de la otra");
    }

    @Test
    void unaDoctypeConEntidadExternaNoSeLee(@TempDir Path dir) throws IOException {
        Path secreto = Files.createTempFile("no-deberia-leerse", ".txt");
        Files.writeString(secreto, "CONTENIDO-SECRETO-DE-ESTE-ORDENADOR");
        write(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE project [
                  <!ENTITY robado SYSTEM "%s">
                ]>
                <project>
                  <area id="TANQUE_1" uuid="u1">&robado;</area>
                </project>
                """.formatted(secreto.toUri()));

        assertNull(PlantNodeLoader.areasOf(dir),
                "la entidad va en contenido de elemento, que es donde XML si admite una "
                        + "referencia externa. Al rechazarse la DOCTYPE entera, el fichero "
                        + "local no llega a leerse y areasDe no puede devolver areas");
    }

    @Test
    void seIgnoranLosElementosQueNoSonArea(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <equipo id="NO_ES_UN_AREA"/>
                  <area id="SI_LO_ES" uuid="u1"/>
                  <variable id="TAMBIEN_NO"/>
                </project>
                """);

        Map<String, String> areas = PlantNodeLoader.areasOf(dir);

        assertEquals(1, areas.size());
        assertTrue(areas.containsKey("SI_LO_ES"));
    }

    @Test
    void unAreaSinIdSeDescarta(@TempDir Path dir) throws IOException {
        write(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <area uuid="sin-id"/>
                  <area id="con-id" uuid="u1"/>
                </project>
                """);

        Map<String, String> areas = PlantNodeLoader.areasOf(dir);

        assertEquals(1, areas.size());
        assertTrue(areas.containsKey("con-id"));
    }

    @Test
    void buscaElFicheroSubiendoCarpetas(@TempDir Path dir) throws IOException {
        Path proyecto = Files.createDirectories(dir.resolve("maquina/proyecto"));
        write(dir.resolve("plant-model.xml"), XML_CON_DOS_AREAS);

        Map<String, String> areas = PlantNodeLoader.areasOf(proyecto);

        assertNotNull(areas, "el fichero esta un nivel por encima del proyecto");
        assertEquals(2, areas.size());
    }

    @Test
    void siHayVariosGanaElMasCercano(@TempDir Path dir) throws IOException {
        Path proyecto = Files.createDirectories(dir.resolve("maquina/proyecto"));
        write(dir.resolve("plant-model.xml"),
                "<project><area id=\"DE_ARRIBA\" uuid=\"u\"/></project>");
        write(proyecto.resolve("plant-model.xml"),
                "<project><area id=\"DEL_PROYECTO\" uuid=\"u\"/></project>");

        Map<String, String> areas = PlantNodeLoader.areasOf(proyecto);

        assertEquals(1, areas.size());
        assertTrue(areas.containsKey("DEL_PROYECTO"),
                "el del proyecto tiene prioridad sobre el de la carpeta padre");
    }
}