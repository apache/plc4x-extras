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

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrencia de {@link HMICommunicationModel}.
 *
 * <p>Estas pruebas usan hilos de verdad, no simulaciones: lo que se comprueba es que el
 * bloqueo existe, y un doble de prueba no lo comprobaria. Para que el entrelazado sea
 * determinista, {@link InMemoryProjectStorage#bloquearEscritura} retiene el guardado a
 * media escritura, de modo que se sabe exactamente cuando esta dentro y no hay que
 * confiar en el planificador.
 *
 * <p>Que el editor notifique cambios de {@code comunicacion.xml} mientras el usuario
 * pulsa Guardar son dos hilos distintos de verdad en la aplicacion. Sin exclusion
 * mutua, el guardado puede tomar la configuracion antes de una recarga y reconstruir
 * {@code boot.db} despues de ella, con lo que los dos ficheros pasan a describir
 * cosas distintas sin que nada avise.
 */
class HMICommunicationModelConcurrenciaTest {

    /**
     * Proyecto PLCopen bien formado con un dispositivo. Es el mismo XML que usa
     * HMICommunicationModelIntegrityTest: el formato real de comunicacion.xml, no uno
     * inventado aqui, porque un XML que el cargador no entiende dejaria loadError
     * puesto y save() se negaria a escribir, que es justo lo que estas pruebas miden.
     */
    private static final String XML_CON_UN_DISPOSITIVO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://www.plcopen.org/xml/tc6_0201"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://www.plcopen.org/xml/tc6_0201 http://www.plcopen.org/xml/tc6_0201.xsd">
              <fileHeader companyName="Malbec" productName="Malbec Communication" productVersion="1.0" creationDateTime="2000-01-01T00:00:00Z"/>
              <contentHeader name="MalbecCommunication">
                <coordinateInfo>
                  <fbd><scaling x="1" y="1"/></fbd>
                  <ld><scaling x="1" y="1"/></ld>
                  <sfc><scaling x="1" y="1"/></sfc>
                </coordinateInfo>
              </contentHeader>
              <types>
                <dataTypes/>
                <pous/>
              </types>
              <instances>
                <configurations>
                  <configuration name="PLC_Control">
                    <addData>
                      <data name="http://apache.org/plc4x/malbec/device" handleUnknown="preserve">
                        <device uuid="uuid-control" enabled="true" s88Uuid="s88-control" xmlns="Device">
                          <brand>Siemens</brand>
                          <model>S7-1500</model>
                          <protocol>S7</protocol>
                          <deviceKey>key-control</deviceKey>
                          <description>PLC de control</description>
                          <s88Node>S88.1</s88Node>
                          <specificParameters>ipa=10.0.0.1,port=102</specificParameters>
                        </device>
                      </data>
                    </addData>
                  </configuration>
                </configurations>
              </instances>
            </project>
            """;
    private static DeviceConfigData dispositivo(String nombre) {
        Properties p = new Properties();
        p.put("brand", "PhoenixContact");
        p.put("model", "EL-1234");
        p.put("protocol", "PhoenixContact EL-1234");
        p.put("deviceName", nombre);
        p.put("deviceKey", "");
        p.put("description", "");
        p.put("uuid", java.util.UUID.randomUUID().toString());
        p.put("enable", "true");
        return new DeviceConfigData(p);
    }

    /**
     * El guardado en curso tiene que impedir que entre una recarga.
     *
     * <p>Este es el invariante que fija el bloqueo de escritura: la recarga comparte
     * estado con el guardado, porque {@code config}, {@code loadError} y
     * {@code repository} se asignan por separado y no de una vez.
     */
    @Test
    void unaRecargaNoSeColaEnUnGuardadoEnCurso(@TempDir File bootDbFolder) throws Exception {
        CountDownLatch escrituraEmpezada = new CountDownLatch(1);
        CountDownLatch escrituraLiberada = new CountDownLatch(1);
        InMemoryProjectStorage storage = new InMemoryProjectStorage("Comunicacion", bootDbFolder)
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO)
                .bloquearEscritura(escrituraEmpezada, escrituraLiberada);
        HMICommunicationModel model = new HMICommunicationModel(storage);

        AtomicReference<Throwable> falloDelGuardado = new AtomicReference<>();
        Thread guardado = new Thread(() -> {
            try {
                model.save();
            } catch (Throwable ex) {
                falloDelGuardado.set(ex);
            }
        }, "guardado");
        guardado.start();

        assertTrue(escrituraEmpezada.await(10, TimeUnit.SECONDS),
                "el guardado no llego a escribir el XML");

        // El guardado esta dentro. Una recarga que se cuele ahora es la carrera.
        AtomicBoolean recargaTerminada = new AtomicBoolean();
        Thread recarga = new Thread(() -> {
            model.reload();
            recargaTerminada.set(true);
        }, "recarga");
        recarga.start();

        // Margen para que la recarga se ejecutara si no hubiera bloqueo. Con el lock
        // puesto, sigue esperando y esto da false.
        Thread.sleep(300);
        assertFalse(recargaTerminada.get(),
                "la recarga se metio mientras el guardado tenia el fichero a medias");

        escrituraLiberada.countDown();
        guardado.join(10_000);
        recarga.join(10_000);

        assertNull(falloDelGuardado.get(), "el guardado fallo");
        assertTrue(recargaTerminada.get(),
                "al soltar el guardado, la recarga deberia poder entrar");
    }

    /**
    /**
     * Una lectura no se cuela en un guardado en curso.
     *
     * <p>Los accesores toman el bloqueo de lectura, asi que mientras un guardado tiene el
     * fichero a medias, quien consulta el modelo espera en lugar de obtener una lista
     * que el guardado esta usando. Esta prueba es la contraparte de la anterior por el
     * lado de la lectura, y falla sin el bloqueo de igual forma.
     */
    @Test
    void unaLecturaEsperaAQueTermineElGuardado(@TempDir File bootDbFolder) throws Exception {
        CountDownLatch escrituraEmpezada = new CountDownLatch(1);
        CountDownLatch escrituraLiberada = new CountDownLatch(1);
        InMemoryProjectStorage storage = new InMemoryProjectStorage("Comunicacion", bootDbFolder)
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO)
                .bloquearEscritura(escrituraEmpezada, escrituraLiberada);
        HMICommunicationModel model = new HMICommunicationModel(storage);

        AtomicReference<Throwable> falloDelGuardado = new AtomicReference<>();
        Thread guardado = new Thread(() -> {
            try {
                model.save();
            } catch (Throwable ex) {
                falloDelGuardado.set(ex);
            }
        }, "guardado");
        guardado.start();
        assertTrue(escrituraEmpezada.await(10, TimeUnit.SECONDS),
                "el guardado no llego a escribir el XML");

        AtomicBoolean lecturaTerminada = new AtomicBoolean();
        AtomicReference<List<DeviceConfigData>> leido = new AtomicReference<>();
        Thread lector = new Thread(() -> {
            leido.set(model.getDevices());
            lecturaTerminada.set(true);
        }, "lector");
        lector.start();

        Thread.sleep(300);
        assertFalse(lecturaTerminada.get(),
                "una lectura devolvio datos mientras el guardado escribia");

        escrituraLiberada.countDown();
        guardado.join(10_000);
        lector.join(10_000);

        assertNull(falloDelGuardado.get(), "el guardado fallo");
        assertTrue(lecturaTerminada.get(),
                "al terminar el guardado, la lectura deberia poder pasar");
        assertNotNull(leido.get());
        assertFalse(leido.get().isEmpty(),
                "la lectura deberia devolver el dispositivo del XML de partida");
    }

    /**
     * Un listener que vuelve a leer el modelo no se cuelga.
     *
     * <p>Este es el caso de la aplicacion, no uno inventado:
     * {@code CommunicationNodeList.stateChanged} llama a {@code refreshKeys()}, que hace
     * {@code model.getDevices()}. O sea que el guardado dispara el evento con el bloqueo
     * de escritura tomado y el listener pide un bloqueo de lectura en el mismo hilo.
     *
     * <p>{@link ReentrantReadWriteLock} lo permite porque un hilo con la escritura
     * tomada puede tomar la lectura, pero eso es justo lo que hay que comprobar: si
     * algun dia se cambia el tipo de lock, este test se queda colgado colando el IDE en
     * vez de dar un fallo limpio. Por eso se espera con limite.
     */
    @Test
    void unListenerQueVuelveALeerNoSeCuelga(@TempDir File bootDbFolder) throws Exception {
        InMemoryProjectStorage storage = new InMemoryProjectStorage("Comunicacion", bootDbFolder)
                .withFile(HMICommunicationModel.CONFIG_FILE, XML_CON_UN_DISPOSITIVO);
        HMICommunicationModel model = new HMICommunicationModel(storage);
        AtomicInteger lecturasDesdeElListener = new AtomicInteger();
        model.addChangeListener(e -> lecturasDesdeElListener.incrementAndGet());
        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        try {
            // Con limite a proposito: un bloqueo mutuo aqui no daria excepcion,
            // simplemente no volveria nunca.
            Future<SaveResult> guardado = ejecutor.submit(model::save);
            assertTrue(guardado.get(20, TimeUnit.SECONDS).isComplete(),
                    "el guardado con listener deberia terminar");
            Future<Void> recarga = ejecutor.submit(() -> {
                model.reload();
                return null;
            });
            recarga.get(20, TimeUnit.SECONDS);
        } finally {
            ejecutor.shutdownNow();
        }
        assertTrue(lecturasDesdeElListener.get() >= 1,
                "el listener deberia haberse llamado");
    }
}
