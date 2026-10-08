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

/**
 * Lectura de los nodos S88 desde plant-model.xml.
 *
 * <p>Esta lectura estaba dentro de {@code CreateDevicePanel} y no se podia probar: para
 * llegar a ella habia que construir el panel entero con todos sus desplegables. Al salir
 * a su propia clase se prueba sola, sin Swing.
 *
 * <p>Los tres casos que se distinguen aqui -no hay fichero, hay fichero pero no se puede
 * leer, y hay fichero con areas- son los tres que el panel trata de forma distinta: en el
 * primero y el segundo deja el desplegable deshabilitado, y en el tercero lo habilita.
 * Por eso devolver {@code null} y un mapa vacio no es lo mismo.
 */
class PlantNodeLoaderTest {

    private static void escribir(Path destino, String contenido) throws IOException {
        Files.writeString(destino, contenido);
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
        escribir(dir.resolve("plant-model.xml"), XML_CON_DOS_AREAS);

        Map<String, String> areas = PlantNodeLoader.areasDe(dir);

        assertNotNull(areas);
        assertEquals(2, areas.size());
        assertEquals("uuid-t1", areas.get("TANQUE_1"));
        assertEquals("uuid-t2", areas.get("TANQUE_2"));
    }

    /**
     * El orden del fichero se respeta, porque el desplegable lo muestra tal cual y el
     * usuario reconoce los nodos en el orden en que estan declarados.
     */
    @Test
    void conservaElOrdenDelFichero(@TempDir Path dir) throws IOException {
        escribir(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <area id="ZULU" uuid="u1"/>
                  <area id="ALFA" uuid="u2"/>
                  <area id="MIKE" uuid="u3"/>
                </project>
                """);

        Map<String, String> areas = PlantNodeLoader.areasDe(dir);

        assertEquals("[ZULU, ALFA, MIKE]", areas.keySet().toString(),
                "el desplegable tiene que salir en el mismo orden que el fichero");
    }

    /** Un fichero que existe pero no declara areas es distinto de no tener fichero. */
    @Test
    void unFicheroSinDevuelveMapaVacioYNoNulo(@TempDir Path dir) throws IOException {
        escribir(dir.resolve("plant-model.xml"),
                "<?xml version=\"1.0\"?><project/>");

        Map<String, String> areas = PlantNodeLoader.areasDe(dir);

        assertNotNull(areas, "el fichero existe y se leyo, aunque no declare areas");
        assertTrue(areas.isEmpty());
    }

    /** Sin fichero no hay nada que habilitar, y hay que poder distinguirlo. */
    @Test
    void sinFicheroDevuelveNulo(@TempDir Path dir) {
        assertNull(PlantNodeLoader.areasDe(dir),
                "no hay plant-model.xml en ninguna carpeta hacia arriba");
    }

    /** Un XML roto se registra y no se lanza: el panel se queda sin areas, pero abre. */
    @Test
    void unXmlRotoDevuelveNuloYNoRevienta(@TempDir Path dir) throws IOException {
        escribir(dir.resolve("plant-model.xml"), "<project><area id=\"X\"</project>");

        assertNull(PlantNodeLoader.areasDe(dir),
                "un XML ilegible se trata como si no hubiera areas, no como un error fatal");
    }

    /** Solo se leen los elementos area; el resto del XML se ignora. */
    @Test
    void seIgnoranLosElementosQueNoSonArea(@TempDir Path dir) throws IOException {
        escribir(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <equipo id="NO_ES_UN_AREA"/>
                  <area id="SI_LO_ES" uuid="u1"/>
                  <variable id="TAMBIEN_NO"/>
                </project>
                """);

        Map<String, String> areas = PlantNodeLoader.areasDe(dir);

        assertEquals(1, areas.size());
        assertTrue(areas.containsKey("SI_LO_ES"));
    }

    /** Un area sin id no sirve para identificarla, asi que no entra. */
    @Test
    void unAreaSinIdSeDescarta(@TempDir Path dir) throws IOException {
        escribir(dir.resolve("plant-model.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <area uuid="sin-id"/>
                  <area id="con-id" uuid="u1"/>
                </project>
                """);

        Map<String, String> areas = PlantNodeLoader.areasDe(dir);

        assertEquals(1, areas.size());
        assertTrue(areas.containsKey("con-id"));
    }

    /**
     * El fichero se busca subiendo desde el proyecto, no solo en su raiz.
     *
     * <p>Asi funciona desde antes de partir el panel, y hay proyectos con el modelo de
     * planta un nivel por encima, en la carpeta que agrupa las maquinas. El test lo fija
     * para que, si alguien decide acotarlo a la raiz, se entere de que cambia el
     * comportamiento de esos proyectos.
     */
    @Test
    void buscaElFicheroSubiendoCarpetas(@TempDir Path dir) throws IOException {
        Path proyecto = Files.createDirectories(dir.resolve("maquina/proyecto"));
        escribir(dir.resolve("plant-model.xml"), XML_CON_DOS_AREAS);

        Map<String, String> areas = PlantNodeLoader.areasDe(proyecto);

        assertNotNull(areas, "el fichero esta un nivel por encima del proyecto");
        assertEquals(2, areas.size());
    }

    /** Si hay varios, gana el mas cercano, que es el del proyecto. */
    @Test
    void siHayVariosGanaElMasCercano(@TempDir Path dir) throws IOException {
        Path proyecto = Files.createDirectories(dir.resolve("maquina/proyecto"));
        escribir(dir.resolve("plant-model.xml"),
                "<project><area id=\"DE_ARRIBA\" uuid=\"u\"/></project>");
        escribir(proyecto.resolve("plant-model.xml"),
                "<project><area id=\"DEL_PROYECTO\" uuid=\"u\"/></project>");

        Map<String, String> areas = PlantNodeLoader.areasDe(proyecto);

        assertEquals(1, areas.size());
        assertTrue(areas.containsKey("DEL_PROYECTO"),
                "el del proyecto tiene prioridad sobre el de la carpeta padre");
    }
}