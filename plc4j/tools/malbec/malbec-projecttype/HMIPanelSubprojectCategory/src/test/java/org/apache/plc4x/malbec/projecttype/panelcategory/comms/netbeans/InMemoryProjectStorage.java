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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class InMemoryProjectStorage implements ProjectStorage {

    private final String directoryName;
    private final File bootDbFolder;
    private final Map<String, String> files = new LinkedHashMap<>();

    private Runnable onChanged;
    private Runnable onDeleted;
    private boolean notificationsPaused;
    private boolean watched;
    private int configWrites;
    private int lecturasFallidas;
    private CountDownLatch escrituraAlEmpezar;
    private CountDownLatch escrituraAlTerminar;

    InMemoryProjectStorage(String directoryName, File bootDbFolder) {
        this.directoryName = directoryName;
        this.bootDbFolder = bootDbFolder;
    }

    InMemoryProjectStorage failNextReads(int veces) {
        this.lecturasFallidas = veces;
        return this;
    }

    InMemoryProjectStorage withFile(String fileName, String content) {
        files.put(fileName, content);
        return this;
    }

    String contentOf(String fileName) {
        return files.get(fileName);
    }

    /** Numero de veces que se ha escrito {@code comunicacion.xml}. */
    int configWrites() {
        return configWrites;
    }

    boolean notificationsPaused() {
        return notificationsPaused;
    }

    @Override
    public String directoryName() {
        return directoryName;
    }

    @Override
    public boolean has(String fileName) {
        return files.containsKey(fileName);
    }

    @Override
    public CommConfigStorage config() {
        if (!files.containsKey(HMICommunicationModel.CONFIG_FILE)) {
            return null;
        }
        return new AlmacenEnMemoria();
    }

    @Override
    public CommConfigStorage createConfig() {
        files.putIfAbsent(HMICommunicationModel.CONFIG_FILE, "");
        return new AlmacenEnMemoria();
    }

    @Override
    public File bootDbFolder() {
        return bootDbFolder;
    }

    @Override
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public void setOnDeleted(Runnable onDeleted) {
        this.onDeleted = onDeleted;
    }

    @Override
    public void watchForChanges() {
        watched = true;
    }

    boolean isWatched() {
        return watched;
    }

    @Override
    public void pauseNotifications() {
        notificationsPaused = true;
    }

    @Override
    public void resumeNotifications() {
        notificationsPaused = false;
    }

    /** Simula que el archivo cambio por fuera. Sin efecto si estan pausadas. */
    void fireChanged() {
        if (!notificationsPaused && onChanged != null) {
            onChanged.run();
        }
    }

    /** Simula que el archivo se borro. Sin efecto si estan pausadas. */
    void fireDeleted() {
        if (notificationsPaused) {
            return;
        }
        files.remove(HMICommunicationModel.CONFIG_FILE);
        if (onDeleted != null) {
            onDeleted.run();
        }
    }

    InMemoryProjectStorage bloquearEscritura(CountDownLatch alEmpezar, CountDownLatch alTerminar) {
        this.escrituraAlEmpezar = alEmpezar;
        this.escrituraAlTerminar = alTerminar;
        return this;
    }

    private final class AlmacenEnMemoria implements CommConfigStorage {

        @Override
        public String open() {
            if (lecturasFallidas > 0) {
                lecturasFallidas--;
                throw new IllegalStateException("el archivo está bloqueado por otra herramienta");
            }
            return files.getOrDefault(HMICommunicationModel.CONFIG_FILE, "");
        }

        @Override
        public OutputStream create() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() {
                    if (escrituraAlEmpezar != null) {
                        escrituraAlEmpezar.countDown();
                        try {
                            escrituraAlTerminar.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("espera interrumpida", ex);
                        }
                    }
                    configWrites++;
                    files.put(HMICommunicationModel.CONFIG_FILE,
                            new String(toByteArray(), StandardCharsets.UTF_8));
                }
            };
        }
    }
}
