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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.AreaNode;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantSnapshot.Variable;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88RepositoryProvider;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.junit.jupiter.api.Test;

class ImportacionDePlantaTest {

    @Test
    void extensionValida() {
        assertTrue(FormatosDeImportacion.extensionAdmitida(new File("planta.xml")));
        assertTrue(FormatosDeImportacion.extensionAdmitida(new File("PLANTA.XML")),
                "la extension no deberia distinguir mayusculas");
    }

    @Test
    void extensionInvalida() {
        assertFalse(FormatosDeImportacion.extensionAdmitida(new File("planta.txt")));
        assertFalse(FormatosDeImportacion.extensionAdmitida(new File("planta.csv")));
        assertFalse(FormatosDeImportacion.extensionAdmitida(new File("planta")),
                "un archivo sin extension no es una planta");
        assertFalse(FormatosDeImportacion.extensionAdmitida(new File("planta.")),
                "un punto final no cuenta como extension");
    }

    @Test
    void onlyXmlIsDeclared() {
        assertEquals(List.of("xml"), List.copyOf(FormatosDeImportacion.FORMATOS.keySet()));
        assertEquals("Modelo de planta (*.xml)", FormatosDeImportacion.FORMATOS.get("xml"));
    }

    @Test
    void filterAndValidationMatch() {
        for (String extension : FormatosDeImportacion.FORMATOS.keySet()) {
            File archivo = new File("planta." + extension);
            assertTrue(FormatosDeImportacion.extensionAdmitida(archivo),
                    "el filtro deja ver ." + extension + " pero la validacion lo rechaza");
        }
    }

    @Test
    void chooserFilterAcceptsOnlyXml() {
        FilenameFilter filtro = FormatosDeImportacion.fileFilter();
        File directorio = new File(".");

        assertTrue(filtro.accept(directorio, "planta.xml"));
        assertTrue(filtro.accept(directorio, "PLANTA.XML"));
        assertFalse(filtro.accept(directorio, "planta.txt"));
        assertFalse(filtro.accept(directorio, "planta"));
    }

    @Test
    void chooserFilterLetsFoldersThrough() {
        FilenameFilter filtro = FormatosDeImportacion.fileFilter();

        assertTrue(filtro.accept(new File("."), "."),
                "sin esto no se podria navegar a una subcarpeta");
    }

    @Test
    void formatosAdmitidosSeMuestranParaElUsuario() {
        assertTrue(FormatosDeImportacion.allowedFormats().contains("(*.xml)"));
    }

    @Test
    void everyDeclaredFormatIsAcceptedBySomeProvider() {
        assertTrue(FormatosDeImportacion.formatsWithoutProvider(
                        List.of(new ProviderQueSoloAcepta("xml")),
                        FormatosDeImportacion.FORMATOS.keySet()).isEmpty(),
                "xml esta declarado y hay un provider que lo acepta");
    }

    @Test
    void formatWithoutProviderIsDetected() {
        assertEquals(Set.of("xml"), FormatosDeImportacion.formatsWithoutProvider(
                List.of(new ProviderQueSoloAcepta("otro")),
                FormatosDeImportacion.FORMATOS.keySet()),
                "debe avisar si el provider no acepta el formato declarado");
    }

    @Test
    void providerAcceptingSeveralFormatsCoversThemAll() {
        assertTrue(FormatosDeImportacion.formatsWithoutProvider(
                List.of(new ProviderQueSoloAcepta("xml"), new ProviderQueSoloAcepta("axml")),
                List.of("xml", "axml")).isEmpty());
    }

    @Test
    void withoutProvidersEverythingDeclaredHasNoProvider() {
        assertEquals(Set.of("xml"), FormatosDeImportacion.formatsWithoutProvider(
                List.of(), FormatosDeImportacion.FORMATOS.keySet()));
    }

    @Test
    void misalignedFormatsWarningIsIssuedOnlyOnce() throws Exception {
        resetTheWarning();
        assertTrue(FormatosDeImportacion.warnMisalignedFormats(),
                "la primera llamada hace la comprobacion");
        for (int i = 0; i < 4; i++) {
            assertFalse(FormatosDeImportacion.warnMisalignedFormats(),
                    "la accion se construye en cada clic derecho: solo puede comprobarse una vez");
        }
    }

    private static void resetTheWarning() throws Exception {
        java.lang.reflect.Field field =
                FormatosDeImportacion.class.getDeclaredField("AVISO_DADO");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) field.get(null)).set(false);
    }

    @Test
    void misalignedFormatsWarningCanBeCalledWithoutProvider() {
        assertDoesNotThrow(FormatosDeImportacion::warnMisalignedFormats);
    }

    private record ProviderQueSoloAcepta(String formato) implements S88RepositoryProvider {

        @Override
        public boolean accepts(String format) {
            return formato.equalsIgnoreCase(format);
        }

        @Override
        public S88Repository createRepository(S88Storage storage) {
            return null;
        }
    }

    @Test
    void collectElementMapeaAreaProcessCellYEquipo() {
        S88Element area = new S88Element().setId("CELDA1").setLevel(S88Level.AREA);
        S88Element cell = new S88Element().setId("CELDA1_P1").setLevel(S88Level.PROCESSCELL);
        S88Element equipo = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        cell.addChild(equipo);
        area.addChild(cell);

        AreaNode snapshot = PlantSnapshot.collectElement(area);

        assertEquals("CELDA1", snapshot.id());
        assertEquals("area", snapshot.tag());

        List<AreaNode> hijos = snapshot.children();
        assertEquals(1, hijos.size());

        AreaNode celda = hijos.get(0);
        assertEquals("CELDA1_P1", celda.id());
        assertEquals("processcell", celda.tag());

        AreaNode tanque = celda.children().get(0);
        assertEquals("TANQUE_1", tanque.id());
        assertEquals("element", tanque.tag(),
                "UNIT no tiene tag propio y cae en element; ver el javadoc de tagFor()");
    }

    @Test
    void tagForCaeEnElementParaLosNivelesQueNoTienenTagPropio() {
        assertEquals("element", PlantSnapshot.tagFor(new S88Element()));
        assertEquals("element",
                PlantSnapshot.tagFor(new S88Element().setLevel(S88Level.EQUIPMENTMODULE)));
        assertEquals("element",
                PlantSnapshot.tagFor(new S88Element().setLevel(S88Level.NULL)));
        assertEquals("area",
                PlantSnapshot.tagFor(new S88Element().setLevel(S88Level.AREA)));
        assertEquals("processcell",
                PlantSnapshot.tagFor(new S88Element().setLevel(S88Level.PROCESSCELL)));
    }

    @Test
    void collectElementSeparaVariablesParametrosYReport() {
        S88Element area = new S88Element().setId("CELDA1").setLevel(S88Level.AREA);

        Map<String, Object> directa = new LinkedHashMap<>();
        directa.put("Type", "REAL");
        area.setProperty("Temperatura", directa);

        Map<String, Object> enParametros = new LinkedHashMap<>();
        Map<String, Object> tipo = new LinkedHashMap<>();
        tipo.put("Type", "INT");
        enParametros.put("Presion", tipo);
        area.setProperty("Parameters", new LinkedHashMap<>(Map.of("Presion", enParametros)));

        Map<String, Object> enReports = new LinkedHashMap<>();
        Map<String, Object> tipoReporte = new LinkedHashMap<>();
        tipoReporte.put("Type", "STRING");
        enReports.put("Historico", tipoReporte);
        area.setProperty("Reports", new LinkedHashMap<>(Map.of("Historico", enReports)));

        AreaNode snapshot = PlantSnapshot.collectElement(area);

        assertEquals(1, snapshot.variables().size());
        assertEquals(1, snapshot.parameters().size());
        assertEquals(1, snapshot.report().size());

        Variable variable = snapshot.variables().get(0);
        assertEquals("Temperatura", variable.name());
        assertEquals("REAL", variable.type());
    }

    @Test
    void collectVariablesIgnoraNodosSinType() {
        Map<String, Object> nodo = new LinkedHashMap<>();
        Map<String, Object> sinTipo = new LinkedHashMap<>();
        sinTipo.put("Otro", "valor");
        nodo.put("VariableSinTipo", sinTipo);

        List<Variable> collected = PlantSnapshot.collectVariables(nodo);

        assertTrue(collected.isEmpty(), "una variable sin Type no debe entrar en el snapshot");
    }

    @Test
    void variableNormalizaElTipoAusente() {
        assertEquals("", PlantSnapshot.variable("Nombre", null).type());
    }

    @Test
    void areaWithoutIdDoesNotBreakTheSnapshot() {
        AreaNode snapshot =
                PlantSnapshot.collectElement(new S88Element().setLevel(S88Level.AREA));
        assertEquals("", snapshot.id());
        assertTrue(snapshot.variables().isEmpty(),
                "sin propiedades la lista queda vacia, no nula");
        assertTrue(snapshot.children().isEmpty());
    }

    @Test
    void missingImageFolderImportFailsExplicitly() {
        String problema = PlantImporter.targetProblem(null);

        assertTrue(problema != null && !problema.isBlank(),
                "H-08(a): missing 'image' folder must be reported");
        assertTrue(problema.contains("image"),
                "message must mention missing image folder: " + problema);
        assertTrue(problema.contains("nothing was created"),
                "message must state nothing was created: " + problema);
    }

    @Test
    void traversalIdIsRejected() {
        String problema = PlantImporter.nameProblem("../../etc/passwd");

        assertTrue(problema != null, "H-08(d): '..' cannot be used as a folder name");
        assertTrue(problema.contains("outside the panel"),
                "message must explain the risk: " + problema);
    }

    @Test
    void idWithPathSeparatorIsRejected() {
        for (String id : new String[]{"a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b"}) {
            String problema = PlantImporter.nameProblem(id);

            assertTrue(problema != null,
                    "H-08(d): el identificador '" + id + "' no puede usarse como carpeta");
        }
    }

    @Test
    void idWithTraversalIsRejectedByTheDotSlash() {
        assertTrue(PlantImporter.nameProblem("..") != null);
        assertTrue(PlantImporter.nameProblem("area/../otra") != null);
        assertTrue(PlantImporter.nameProblem("a..b") != null);
    }

    @Test
    void nullOrEmptyIdIsRejected() {
        assertTrue(PlantImporter.nameProblem(null) != null);
        assertTrue(PlantImporter.nameProblem("") != null);
        assertTrue(PlantImporter.nameProblem("   ") != null);
    }

    @Test
    void normalIdIsAccepted() {
        for (String id : new String[]{"Llenadora", "area_1", "Zona-A", "tanque02", "Área Ñ"}) {
            assertTrue(PlantImporter.nameProblem(id) == null,
                    "el identificador '" + id + "' es valido y debe aceptarse");
        }
    }

    @Test
    void validIdIsTruncatedBeforeChecking() {
        assertTrue(PlantImporter.nameProblem("  Llenadora  ") == null,
                "los espacios alrededor se recortan, como ya se hacia con areaId");
    }

    @Test
    void firstInvalidIdIsDetectedInWholeTree() {
        S88Element raiz = nodo("Llenadora");
        S88Element intermedio = nodo("Zona1");
        S88Element culpable = nodo("sub/carpeta");
        intermedio.addChild(culpable);
        raiz.addChild(intermedio);

        String problema = PlantImporter.firstInvalidName(raiz);

        assertTrue(problema != null && problema.contains("sub/carpeta"),
                "H-08(d): must find invalid id even when nested: " + problema);
    }

    @Test
    void fullyValidTreeHasNoProblem() {
        S88Element raiz = nodo("Llenadora");
        S88Element hijo = nodo("Zona1");
        hijo.addChild(nodo("Tanque-02"));
        raiz.addChild(hijo);

        assertTrue(PlantImporter.firstInvalidName(raiz) == null);
    }

    @Test
    void emptyTreeHasNoProblem() {
        assertTrue(PlantImporter.firstInvalidName(null) == null);
        assertTrue(PlantImporter.firstInvalidName(nodo("SinHijos")) == null);
    }

    private static S88Element nodo(String id) {
        return new S88Element().setId(id);
    }
    @Test
    void laImportacionDeshaceLoCreadoSiAlgoFallaDespues() throws Exception {
        String codigo = Files.readString(Path.of("src", "main", "java", "org", "apache",
                "plc4x", "malbec", "projecttype", "panel", "plant", "PlantImporter.java"));

        assertTrue(codigo.contains("reverseChildStack(child, imageFo, creados)"),
                "lo que se crea tiene que quedar registrado para poder deshacerlo");
        assertTrue(codigo.contains("List<FileObject> creados"),
                "hace falta la lista de lo creado");
        assertEquals(3, countOf(codigo, "undo(creados)"),
                "los tres fallos posteriores tienen que deshacer: nodos a medias,"
                        + " carpeta del panel irresoluble y plant-model.xml no escrito");
    }

    @Test
    void deshacerBorraAlRevesParaQueLosHijosNoQuedenColgados() throws Exception {
        String codigo = Files.readString(Path.of("src", "main", "java", "org", "apache",
                "plc4x", "malbec", "projecttype", "panel", "plant", "PlantImporter.java"));
        int desde = codigo.indexOf("static void undo(");

        assertTrue(desde > 0, "tiene que existir deshacer()");
        String cuerpo = codigo.substring(desde, codigo.indexOf('}', desde));
        assertTrue(cuerpo.contains("creados.size() - 1"),
                "borrar al reves: si se borra la carpeta padre primero, los hijos ya no"
                        + " estan y su borrado falla");
        assertTrue(cuerpo.contains("isValid()"),
                "no hay que borrar lo que el usuario haya borrado por su cuenta");
    }

    private static int countOf(String codigo, String aguja) {
        int total = 0;
        int desde = codigo.indexOf(aguja);
        while (desde >= 0) {
            total++;
            desde = codigo.indexOf(aguja, desde + aguja.length());
        }
        return total;
    }

    @Test
    void folderNameIsTheOneThatGetsValidated() {
        assertEquals("CELDA1", PlantImporter.validFolderName("  CELDA1\n"),
                "validar el id recortado y crear con el crudo deja carpetas con espacios "
                        + "que el operador no escribio");
        assertEquals("CELDA1", PlantImporter.validFolderName("\tCELDA1 "));
    }

    @Test
    void invalidIdGivesNoFolderName() {
        assertNull(PlantImporter.validFolderName("../escape"));
        assertNull(PlantImporter.validFolderName("CELDA:1"));
        assertNull(PlantImporter.validFolderName("   "));
        assertNull(PlantImporter.validFolderName(null));
    }
}