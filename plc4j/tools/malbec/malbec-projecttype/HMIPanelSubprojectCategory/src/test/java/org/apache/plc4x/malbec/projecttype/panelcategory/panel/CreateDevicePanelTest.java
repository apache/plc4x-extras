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
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.NoopCommunicationStore;

class CreateDevicePanelTest {

    private CreateDevicePanel panelCompleto() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore());
        panel.getCbMarca().setSelectedIndex(1);
        panel.getCbModelo().setSelectedIndex(3);
        panel.registrarNodoS88("MD70", "uuid-md70");
        panel.getCbS88Node().setSelectedIndex(1);
        panel.getTxtDeviceName().setText("Compresor 1");
        panel.getTxtDeviceKey().setText("CMP-001");
        panel.getTxtHost().setText("192.168.0.10");
        panel.getTxtPort().setText("102");
        return panel;
    }

    @Test
    void elNodoS88SeleccionadoEsNullConElPlaceholder() {
        assertNull(new CreateDevicePanel(null, new NoopCommunicationStore()).nodoS88Seleccionado(),
                "el placeholder del combo no puede pasar por nodo elegido");
    }

    @Test
    void unFormularioVacioPideLosSeisCamposObligatorios() {
        List<String> faltantes = new CreateDevicePanel(null, new NoopCommunicationStore()).camposObligatoriosVacios();
        assertEquals(6, faltantes.size(), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Name"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Key"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Marca y modelo"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("Node"), "faltantes: " + faltantes);
        assertTrue(faltantes.contains("UUID"),
                "un dispositivo sin UUID se guardaría sin identidad: " + faltantes);
    }

    @Test
    void noDejaGuardarSinNodoS88AunqueElRestoEsteCompleto() {
        CreateDevicePanel panel = panelCompleto();
        panel.getCbS88Node().setSelectedIndex(0);
        assertTrue(panel.camposObligatoriosVacios().contains("Node"),
                "un dispositivo sin nodo S88 no debería poder guardarse");
    }

    @Test
    void conTodosLosDatosNoFaltaNinguno() {
        assertTrue(panelCompleto().camposObligatoriosVacios().isEmpty(),
                "el formulario completo no debería pedir nada");
    }

    @Test
    void armaLasDocePropiedadesDelDispositivo() {
        DeviceConfigData data = panelCompleto().getDeviceConfigData();
        assertNotNull(data, "el formulario completo tiene que producir un dispositivo");
        assertEquals("Siemens", data.getBrand());
        assertEquals("S7-1500", data.getModel());
        assertEquals("S7", data.getProtocol());
        assertEquals("Compresor 1", data.getDeviceName());
        assertEquals("CMP-001", data.getDeviceKey());
        assertEquals("MD70", data.getS88Node());
        assertEquals("uuid-md70", data.getS88Uuid());
        assertFalse(data.isEnabled());
        assertFalse(data.getUuid() == null || data.getUuid().isEmpty(), "falta el uuid del dispositivo");
    }

    @Test
    void laUrlSeArmaConHostTransporteYPuerto() {
        DeviceConfigData data = panelCompleto().getDeviceConfigData();
        assertNotNull(data);
        String url = data.getSpecificParameters();
        assertTrue(url.contains("192.168.0.10"), "falta el host en la url: " + url);
        assertTrue(url.contains("102"), "falta el puerto en la url: " + url);
    }

    @Test
    void cambiarDeMarcaCambiaElProtocoloDeLaUrl() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore());

        panel.getCbMarca().setSelectedIndex(1);          // Siemens
        panel.getCbModelo().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.10");
        panel.getTxtPort().setText("102");
        String urlSiemens = panel.urlActual();
        assertTrue(urlSiemens.startsWith("s7:"), "Siemens compone s7, no: " + urlSiemens);

        panel.getCbMarca().setSelectedIndex(2);          // Allen Bradley
        panel.getCbModelo().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.20");
        panel.getTxtPort().setText("44818");
        String urlAb = panel.urlActual();
        assertTrue(urlAb.startsWith("eip:"), "Allen Bradley compone eip, no: " + urlAb);
        assertNotEquals(urlSiemens, urlAb,
                "T3.4: cambiar de marca tiene que cambiar el builder y la URL");

        panel.getCbMarca().setSelectedIndex(3);          // Modbus
        panel.getCbModelo().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.30");
        panel.getTxtPort().setText("502");
        assertTrue(panel.urlActual().startsWith("modbus-tcp:"),
                "Modbus compone segun su variante, no: " + panel.urlActual());
    }

    @Test
    void cambiarDeMarcaLimpiaLaConexionDelModeloAnterior() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore());

        panel.getCbMarca().setSelectedIndex(1);          // Siemens
        panel.getCbModelo().setSelectedIndex(0);
        panel.getTxtHost().setText("192.168.0.10");
        panel.getTxtPort().setText("102");
        assertTrue(panel.urlActual().contains("192.168.0.10"), "precondición");

        panel.getCbMarca().setSelectedIndex(3);          // Modbus
        panel.getCbModelo().setSelectedIndex(0);

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

    private DeviceConfigData deviceParaEditar(String uuid, String model, String url) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", model);
        p.put("protocol", "S7");
        p.put("deviceName", "Compresor 1");
        p.put("deviceKey", "CMP-001");
        p.put("description", "Compresor principal");
        p.put("uuid", uuid);
        p.put("enable", true);
        p.put("s88Node", "MD70");
        p.put("s88Uuid", "uuid-md70");
        p.put("specificParameters", url);
        return new DeviceConfigData(p);
    }

    @Test
    void alAbrirUnDispositivoSePrecarganSusDatos() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore(),
                deviceParaEditar("uuid-fijo", "S7-1500", "s7:cotp://192.168.0.10:102"));
        assertEquals("uuid-fijo", panel.getTxtUUID().getText());
        assertEquals("Compresor 1", panel.getTxtDeviceName().getText());
        assertEquals("CMP-001", panel.getTxtDeviceKey().getText());
        assertEquals("S7-1500", panel.getCbModelo().getItemAt(
                panel.getCbModelo().getSelectedIndex()).getModel());
    }

    @Test
    void elUuidNoCambiaAlElegirOtroModelo() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore(),
                deviceParaEditar("uuid-fijo", "S7-1500", "s7:cotp://192.168.0.10:102"));
        panel.getCbMarca().setEnabled(true);   // en edición viene bloqueado
        panel.getCbModelo().setSelectedIndex(1);
        assertEquals("uuid-fijo", panel.getTxtUUID().getText(),
                "regenerar el UUID en modificación guardaría un dispositivo nuevo");
    }

    @Test
    void alEditarSeRecuperanHostPuertoYParametros() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore(), deviceParaEditar("uuid-1",
                "S7-1500", "s7:cotp://192.168.0.10:102?cotp.remote-slot=3&pdu-size=512"));
        assertEquals("192.168.0.10", panel.getTxtHost().getText());
        assertEquals("102", panel.getTxtPort().getText());
        String url = panel.urlActual();
        assertTrue(url.contains("cotp.remote-slot=3"),
                "los parámetros guardados no llegaron al builder: " + url);
        assertTrue(url.contains("pdu-size=512"),
                "los parámetros guardados no llegaron al builder: " + url);
    }

    @Test
    void elNodoGuardadoVuelveAlComboAunqueNoHayaPlantModel() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore(),
                deviceParaEditar("uuid-1", "S7-1500", "s7:cotp://192.168.0.10:102"));
        assertEquals("MD70", panel.nodoS88Seleccionado(),
                "el nodo guardado no llegó al combo: el dispositivo quedaría sin poder modificarse");
        assertTrue(panel.camposObligatoriosVacios().isEmpty(),
                "un dispositivo existente tiene que poder volver a guardarse sin cambios: "
                        + panel.camposObligatoriosVacios());
    }

    @Test
    void alGuardarSeConservaElTransporteDeLaUrlLeida() {
        String url = "s7:cotp://192.168.0.10:102?cotp.remote-slot=3";
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore(),
                deviceParaEditar("uuid-1", "S7-1500", url));
        assertTrue(panel.urlActual().startsWith("s7:cotp://"),
                "el transporte guardado no se restauró: " + panel.urlActual());
    }

    @Test
    void alCambiarElModeloLaConexionSeRehazeConLosDefaultsDelNuevo() {
        CreateDevicePanel panel = new CreateDevicePanel(null, new NoopCommunicationStore(), deviceParaEditar("uuid-1",
                "S7-1500", "s7:cotp://192.168.0.10:102?cotp.remote-slot=3"));
        panel.getCbMarca().setEnabled(true);   // en edición viene bloqueado
        panel.getCbModelo().setSelectedIndex(0);

        assertTrue(panel.getTxtHost().getText().isEmpty(),
                "cambiar de modelo no debe arrastrar el host anterior");
        String url = panel.urlActual();
        assertFalse(url.contains("cotp.remote-slot=3"),
                "los parámetros de otro modelo no aplican: " + url);
        assertTrue(url.contains("controller-type=S7_300"),
                "la URL debería quedar con los defaults del modelo nuevo: " + url);
    }

    @Test
    void elFormularioEsAnchoSuficienteParaPoderLeerse() {
        int ancho = panelCompleto().getPreferredSize().width;
        assertTrue(ancho >= 700,
                "el formulario mide " + ancho + " px de ancho y la ventana se empaqueta"
                        + " a ese tamaño; los campos se están quedando con su mínimo");
    }
}
