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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

class CreateDevicePanelTest {

    private CreateDevicePanel panelConNodo(Path projectDir, String node) {
        CreateDevicePanel panel = new CreateDevicePanel(projectDir);
        panel.getCbMarca().setSelectedIndex(1);
        panel.getCbModel().setSelectedIndex(3);
        if (node != null) {
            panel.getCbS88Node().setSelectedItem(node);
        }
        panel.getTxtDeviceName().setText("Compresor 1");
        panel.getTxtDeviceKey().setText("CMP-001");
        panel.getTxtHost().setText("192.168.0.10");
        panel.getTxtPort().setText("102");
        return panel;
    }

    private CreateDevicePanel panelCompleto() {
        CreateDevicePanel panel = panelConNodo(null, null);
        panel.registrarNodoS88("MD70", "uuid-md70");
        panel.getCbS88Node().setSelectedItem("MD70");
        return panel;
    }

    private static List<String> comboItems(CreateDevicePanel panel) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < panel.getCbS88Node().getItemCount(); i++) {
            items.add(panel.getCbS88Node().getItemAt(i));
        }
        return items;
    }

    @Test
    void selectedS88NodeIsNullWithThePlaceholder() {
        assertNull(new CreateDevicePanel(null).selectedS88Node(),
                "el placeholder del combo no puede pasar por nodo elegido");
    }

    @Test
    void emptyFormAsksForTheSixRequiredFields() {
        List<String> faltantes = new CreateDevicePanel(null).missingRequiredFields();
        assertEquals(6, faltantes.size(), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Name"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Key"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Brand and Model"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Node"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("UUID"),
                "un dispositivo sin UUID se guardaría sin identidad: " + faltantes);
    }

    @Test
    void doesNotAllowSavingWithoutS88NodeEvenIfTheRestIsComplete() {
        CreateDevicePanel panel = panelCompleto();
        panel.getCbS88Node().setSelectedIndex(0);
        assertTrue(panel.missingRequiredFields().contains("Node"),
                "un dispositivo sin nodo S88 no debería poder guardarse");
    }

    @Test
    void withAllDataNoneIsMissing() {
        assertTrue(panelCompleto().missingRequiredFields().isEmpty(),
                "el formulario completo no debería pedir nada");
    }

    @Test
    void armaLasDocePropiedadesDelDispositivo() {
        DeviceConfigData data = panelCompleto().getDeviceConfigData();
        assertNotNull(data, "el formulario completo tiene que producir un dispositivo");
        assertEquals("Siemens", data.getBrand());
        assertEquals("s7-1500", data.getModel());
        assertEquals("s7", data.getProtocol());
        assertEquals("Compresor 1", data.getDeviceName());
        assertEquals("CMP-001", data.getDeviceKey());
        assertEquals("MD70", data.getS88Node());
        assertEquals("uuid-md70", data.getS88Uuid());
        assertFalse(data.isEnabled());
        assertFalse(data.getUuid() == null || data.getUuid().isEmpty(), "falta el uuid del dispositivo");
    }

    @Test
    void urlIsBuiltWithHostTransportAndPort() {
        DeviceConfigData data = panelCompleto().getDeviceConfigData();
        assertNotNull(data);
        String url = data.getSpecificParameters();
        assertTrue(url.contains("192.168.0.10"), "falta el host en la url: " + url);
        assertTrue(url.contains("102"), "falta el puerto en la url: " + url);
    }

    @Test
    void cambiarDeMarcaCambiaElProtocoloDeLaUrl() {
        CreateDevicePanel panel = new CreateDevicePanel(null);

        panel.getCbMarca().setSelectedIndex(1);          // Siemens
        panel.getCbModel().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.10");
        panel.getTxtPort().setText("102");
        String urlSiemens = panel.urlActual();
        assertTrue(urlSiemens.startsWith("s7:"), "Siemens compone s7, no: " + urlSiemens);

        panel.getCbMarca().setSelectedIndex(2);          // Allen Bradley
        panel.getCbModel().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.20");
        panel.getTxtPort().setText("44818");
        String urlAb = panel.urlActual();
        assertTrue(urlAb.startsWith("eip:"), "Allen Bradley compone eip, no: " + urlAb);
        assertNotEquals(urlSiemens, urlAb,
                "T3.4: cambiar de marca tiene que cambiar el builder y la URL");

        panel.getCbMarca().setSelectedIndex(3);          // Modbus
        panel.getCbModel().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.30");
        panel.getTxtPort().setText("502");
        assertTrue(panel.urlActual().startsWith("modbus-tcp:"),
                "Modbus compone segun su variante, no: " + panel.urlActual());
    }

    @Test
    void cambiarDeMarcaLimpiaLaConexionDelModeloAnterior() {
        CreateDevicePanel panel = new CreateDevicePanel(null);

        panel.getCbMarca().setSelectedIndex(1);          // Siemens
        panel.getCbModel().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.10");
        panel.getTxtPort().setText("102");
        assertTrue(panel.urlActual().contains("192.168.0.10"), "precondición");

        panel.getCbMarca().setSelectedIndex(3);          // Modbus
        panel.getCbModel().setSelectedIndex(0);

        assertFalse(panel.urlActual().contains("192.168.0.10"),
                "la dirección del modelo anterior no puede sobrevivir al cambio: "
                        + panel.urlActual());
        assertTrue(panel.urlActual().startsWith("modbus-tcp:"),
                "y el protocolo nuevo sí debe estar: " + panel.urlActual());
    }

    private static void assertNotEquals(String a, String b, String mensaje) {
        if (a.equals(b)) {
            org.junit.jupiter.api.Assertions.fail(mensaje);
        }
    }

    private DeviceConfigData deviceToEdit(String uuid, String model, String url) {
        return deviceToEdit(uuid, model, url, "MD70", "uuid-md70");
    }

    private DeviceConfigData deviceToEdit(String uuid, String model, String url,
            String s88Node, String s88Uuid) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", model);
        p.put("protocol", "s7");
        p.put("deviceName", "Compresor 1");
        p.put("deviceKey", "CMP-001");
        p.put("description", "Compresor principal");
        p.put("uuid", uuid);
        p.put("enable", true);
        p.put("s88Node", s88Node);
        p.put("s88Uuid", s88Uuid);
        p.put("specificParameters", url);
        return new DeviceConfigData(p);
    }

    @Test
    void alAbrirUnDispositivoSePrecarganSusDatos() {
        CreateDevicePanel panel = new CreateDevicePanel(null,
                deviceToEdit("uuid-fijo", "s7-1500", "s7:cotp://192.168.0.10:102"));
        assertEquals("uuid-fijo", panel.getTxtUUID().getText());
        assertEquals("Compresor 1", panel.getTxtDeviceName().getText());
        assertEquals("CMP-001", panel.getTxtDeviceKey().getText());
        assertEquals("s7-1500", panel.getCbModel().getItemAt(
                panel.getCbModel().getSelectedIndex()).getModel());
    }

    @Test
    void uuidDoesNotChangeWhenChoosingAnotherModel() {
        CreateDevicePanel panel = new CreateDevicePanel(null,
                deviceToEdit("uuid-fijo", "s7-1500", "s7:cotp://192.168.0.10:102"));
        panel.getCbMarca().setEnabled(true);   // en edición viene bloqueado
        panel.getCbModel().setSelectedIndex(1);
        assertEquals("uuid-fijo", panel.getTxtUUID().getText(),
                "regenerar el UUID en modificación guardaría un dispositivo nuevo");
    }

    @Test
    void alEditarSeRecuperanHostPuertoYParametros() {
        CreateDevicePanel panel = new CreateDevicePanel(null, deviceToEdit("uuid-1",
                "s7-1500", "s7:cotp://192.168.0.10:102?cotp.remote-slot=3&pdu-size=512"));
        assertEquals("192.168.0.10", panel.getTxtHost().getText());
        assertEquals("102", panel.getTxtPort().getText());
        String url = panel.urlActual();
        assertTrue(url.contains("cotp.remote-slot=3"),
                "los parámetros guardados no llegaron al builder: " + url);
        assertTrue(url.contains("pdu-size=512"),
                "los parámetros guardados no llegaron al builder: " + url);
    }

    @Test
    void savedNodeComesBackToTheComboEvenWithoutPlantModel() {
        CreateDevicePanel panel = new CreateDevicePanel(null,
                deviceToEdit("uuid-1", "s7-1500", "s7:cotp://192.168.0.10:102"));
        assertEquals("MD70", panel.selectedS88Node(),
                "el nodo guardado no llegó al combo: el dispositivo quedaría sin poder modificarse");
        assertTrue(panel.missingRequiredFields().isEmpty(),
                "un dispositivo existente tiene que poder volver a guardarse sin cambios: "
                        + panel.missingRequiredFields());
    }

    @Test
    void dummyPlantIsAvailableWithoutPlantModel() {
        CreateDevicePanel panel = new CreateDevicePanel(null);
        assertTrue(panel.getCbS88Node().isEnabled(),
                "sin planta importada el combo debe seguir permitiendo crear dispositivos");
        assertEquals(List.of("-- Seleccione --", DummyPlant.ID), comboItems(panel));
    }

    @Test
    void dummyPlantIsRenderedAsAnEmptyPlant() {
        CreateDevicePanel panel = new CreateDevicePanel(null);
        Component rendered = panel.getCbS88Node().getRenderer()
                .getListCellRendererComponent(new JList<String>(), DummyPlant.ID, 0, false, false);
        assertEquals(DummyPlant.LABEL, ((JLabel) rendered).getText());
    }

    @Test
    void dummyPlantCanBeSavedWithoutPlantModel() {
        DeviceConfigData data = panelConNodo(null, DummyPlant.ID).getDeviceConfigData();
        assertNotNull(data);
        assertEquals(DummyPlant.ID, data.getS88Node());
        assertEquals(DummyPlant.UUID, data.getS88Uuid());
    }

    @Test
    void openingTheFormDoesNotCreatePlantModel(@TempDir Path dir) {
        panelConNodo(dir, DummyPlant.ID);
        assertFalse(Files.exists(dir.resolve("plant-model.xml")),
                "la dummy es virtual y no debe materializarse en el modelo de planta");
    }

    @Test
    void dummyPlantIsAppendedAfterRealAreas(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("plant-model.xml"),
                "<plant-model><area id=\"TANQUE_1\" uuid=\"u1\"/></plant-model>");

        CreateDevicePanel panel = new CreateDevicePanel(dir);

        assertEquals(List.of("-- Seleccione --", "TANQUE_1", DummyPlant.ID), comboItems(panel));
    }

    @Test
    void existingDummyAreaKeepsItsUuid(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("plant-model.xml"),
                "<plant-model><area id=\"dummyplant\" uuid=\"uuid-real\"/></plant-model>");

        DeviceConfigData data = panelConNodo(dir, DummyPlant.ID).getDeviceConfigData();

        assertNotNull(data);
        assertEquals("uuid-real", data.getS88Uuid(),
                "si el XML ya declara dummyplant hay que respetar su UUID");
    }

    @Test
    void savedDummyComesBackToTheComboEvenWithoutPlantModel() {
        DeviceConfigData device = deviceToEdit("uuid-1", "s7-1500",
                "s7:cotp://192.168.0.10:102", DummyPlant.ID, DummyPlant.UUID);
        CreateDevicePanel panel = new CreateDevicePanel(null, device);

        assertEquals(DummyPlant.ID, panel.selectedS88Node());
        assertTrue(panel.missingRequiredFields().isEmpty(),
                "un dispositivo dummy existente debe poder volver a guardarse: "
                        + panel.missingRequiredFields());
    }

    @Test
    void twoDevicesCanUseTheSameArea(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("plant-model.xml"),
                "<plant-model><area id=\"TANQUE_1\" uuid=\"u1\"/></plant-model>");

        DeviceConfigData first = panelConNodo(dir, "TANQUE_1").getDeviceConfigData();
        DeviceConfigData second = panelConNodo(dir, "TANQUE_1").getDeviceConfigData();

        assertNotNull(first);
        assertNotNull(second);
        assertEquals("TANQUE_1", first.getS88Node());
        assertEquals("TANQUE_1", second.getS88Node());
    }

    @Test
    void alGuardarSeConservaElTransporteDeLaUrlLeida() {
        String url = "s7:cotp://192.168.0.10:102?cotp.remote-slot=3";
        CreateDevicePanel panel = new CreateDevicePanel(null,
                deviceToEdit("uuid-1", "s7-1500", url));
        assertTrue(panel.urlActual().startsWith("s7:cotp://"),
                "el transporte guardado no se restauró: " + panel.urlActual());
    }

    @Test
    void transportDropdownDoesNotAcceptFreeText() {
        CreateDevicePanel panel = new CreateDevicePanel(null,
                deviceToEdit("uuid-1", "s7-1500", "s7://10.0.0.1:102"));

        assertFalse(panel.transporteEditable(),
                "el transporte solo se elige de la lista: escribirlo a mano genera URLs "
                        + "que el PLC no entiende");
    }

    @Test
    void transportOutsideTheListStillSurvives() {
        CreateDevicePanel panel = new CreateDevicePanel(null,
                deviceToEdit("uuid-1", "s7-1500", "s7:iso-on-tcp://10.0.0.1:102"));

        assertTrue(panel.urlActual().contains("iso-on-tcp"),
                "si el transporte no estaba en la lista hay que conservarlo igualmente: "
                        + panel.urlActual());
    }

    @Test
    void alCambiarElModeloLaConexionSeRehazeConLosDefaultsDelNuevo() {
        CreateDevicePanel panel = new CreateDevicePanel(null, deviceToEdit("uuid-1",
                "s7-1500", "s7:cotp://192.168.0.10:102?cotp.remote-slot=3"));
        panel.getCbMarca().setEnabled(true);   // en edición viene bloqueado
        panel.getCbModel().setSelectedIndex(0);

        assertTrue(panel.getTxtHost().getText().isEmpty(),
                "cambiar de modelo no debe arrastrar el host anterior");
        String url = panel.urlActual();
        assertFalse(url.contains("cotp.remote-slot=3"),
                "los parámetros de otro modelo no aplican: " + url);
        assertTrue(url.contains("controller-type=s7_300"),
                "la URL debería quedar con los defaults del modelo nuevo: " + url);
    }

    @Test
    void formIsWideEnoughToBeReadable() {
        int width = panelCompleto().getPreferredSize().width;
        assertTrue(width >= 700,
                "el formulario mide " + width + " px de ancho y la ventana se empaqueta"
                        + " a ese tamaño; los campos se están quedando con su mínimo");
    }
}
