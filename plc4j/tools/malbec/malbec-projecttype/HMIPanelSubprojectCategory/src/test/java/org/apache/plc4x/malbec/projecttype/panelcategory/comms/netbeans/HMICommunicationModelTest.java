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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans;

import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HMICommunicationModelTest {

    private DeviceConfigData device(String uuid, String name) {
        Properties p = new Properties();
        p.put("brand", "Siemens");
        p.put("model", "S7-1500");
        p.put("protocol", "S7");
        p.put("deviceName", name);
        p.put("deviceKey", "K-" + name);
        p.put("description", "descripcion de " + name);
        p.put("uuid", uuid);
        p.put("enable", Boolean.TRUE);
        p.put("s88Node", "MD70");
        p.put("s88Uuid", "uuid-md70");
        p.put("specificParameters", "s7:cotp://192.168.0.10:102");
        return new DeviceConfigData(p);
    }

    @Test
    void aUnDispositivoSinUuidSeLeAsignaAlCargar() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Compresor viejo"));

        assertTrue(HMICommunicationModel.repararUuidsAusentes(config),
                "un UUID ausente tiene que reportarse como reparado");
        assertFalse(config.getDevices().get(0).getUuid().isEmpty(),
                "el dispositivo sigue sin identidad después de la reparación");
    }

    @Test
    void repairDoesNotLoseTheRestOfTheData() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Compresor viejo"));
        HMICommunicationModel.repararUuidsAusentes(config);

        DeviceConfigData reparado = config.getDevices().get(0);
        assertEquals("Compresor viejo", reparado.getDeviceName());
        assertEquals("K-Compresor viejo", reparado.getDeviceKey());
        assertEquals("descripcion de Compresor viejo", reparado.getDescription());
        assertEquals("Siemens", reparado.getBrand());
        assertEquals("S7-1500", reparado.getModel());
        assertEquals("MD70", reparado.getS88Node());
        assertEquals("uuid-md70", reparado.getS88Uuid());
        assertEquals("s7:cotp://192.168.0.10:102", reparado.getSpecificParameters());
        assertTrue(reparado.isEnabled());
    }

    @Test
    void deviceWithUuidIsNotTouched() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("uuid-original", "Compresor"));

        assertFalse(HMICommunicationModel.repararUuidsAusentes(config),
                "no hay nada que reparar y no hay que persistir de más");
        assertEquals("uuid-original", config.getDevices().get(0).getUuid());
    }

    @Test
    void eachDeviceWithoutUuidGetsADistinctOne() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "A"));
        config.upsertDevice(device("", "B"));

        HMICommunicationModel.repararUuidsAusentes(config);

        String uuidA = config.getDevices().get(0).getUuid();
        String uuidB = config.getDevices().get(1).getUuid();
        assertNotEquals(uuidA, uuidB,
                "compartir UUID haría que los dos dispositivos fueran el mismo");
    }

    @Test
    void repairedDeviceCanNowBeModified() {
        CommunicationsConfig config = new CommunicationsConfig();
        config.upsertDevice(device("", "Compresor viejo"));
        HMICommunicationModel.repararUuidsAusentes(config);

        String uuidReparado = config.getDevices().get(0).getUuid();
        config.upsertDevice(device(uuidReparado, "Compresor editado"));

        assertEquals(1, config.getDevices().size(),
                "tras la reparación, modificar tiene que reemplazar y no duplicar");
        assertEquals("Compresor editado", config.getDevices().get(0).getDeviceName());
    }
}