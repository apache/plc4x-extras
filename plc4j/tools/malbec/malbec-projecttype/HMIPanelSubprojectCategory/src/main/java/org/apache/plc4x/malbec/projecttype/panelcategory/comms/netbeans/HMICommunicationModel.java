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

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationsConfig;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommunicationStore;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.BootDbRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.RebuildResults;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.ProjectIds;
import org.netbeans.api.project.Project;
import org.openide.util.ChangeSupport;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.SaveResult;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class HMICommunicationModel implements CommunicationStore {

    public static final String CONFIG_FILE = ProjectIds.COMMUNICATION_CONFIG_FILE;
    private static final Logger LOGGER = Logger.getLogger(HMICommunicationModel.class.getName());
    private final ProjectStorage storage;
    private final ChangeSupport cs = new ChangeSupport(this);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private CommConfigRepository repository;
    private CommunicationsConfig config;
    private Exception loadError;

    public HMICommunicationModel(Project project) {
        this(new NetBeansProjectStorage(project));
    }

    HMICommunicationModel(ProjectStorage storage) {
        this.storage = storage;
        storage.setOnChanged(this::reload);
        storage.setOnDeleted(this::ensureFile);
        reload();
    }

    public final void reload() {
        lock.writeLock().lock();
        try {
            reloadInternal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void reloadInternal() {
        boolean commProject = isCommunicationProject();
        if (!commProject) {
            forgetConfiguration();
            loadError = null;
            notifyChange();
            return;
        }

        boolean created = false;
        CommConfigStorage configStorage = storage.config();
        if (configStorage == null) {
            try {
                configStorage = storage.createConfig();
                created = true;
            } catch (IOException ex) {
                configStorage = null;
            }
        }
        if (configStorage == null) {
            loadError = new IOException("no se pudo crear " + CONFIG_FILE
                    + " en el proyecto " + storage.directoryName());
            config = new CommunicationsConfig();
            LOGGER.log(Level.SEVERE, loadError.getMessage());
            notifyChange();
            return;
        }

        storage.watchForChanges();
        repository = CommConfigRepositoryLookup.createRepository(configStorage);
        CommConfigLoader.Result carga;
        try {
            carga = new CommConfigLoader(repository).load();
        } catch (Exception ex) {

            loadError = ex;
            if (config == null) {
                config = new CommunicationsConfig();
            }
            LOGGER.log(Level.SEVERE, "No se pudo cargar " + CONFIG_FILE
                    + "; se conserva la configuración anterior.", ex);
            notifyChange();
            return;
        }
        loadError = null;
        config = carga.config();
        if (created || carga.uuidReparados()) {
            write(repository, created);
        }
        notifyChange();
    }

    private boolean isCommunicationProject() {
        return HMICategoryDefinition.COMUNICATION.matchesFolder(storage.directoryName())
                || storage.has(CONFIG_FILE)
                || storage.has("comm.merlot");
    }

    private void forgetConfiguration() {
        lock.writeLock().lock();
        try {
            repository = null;
            config = new CommunicationsConfig();
            loadError = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureFile() {
        forgetConfiguration();
        if (!isCommunicationProject()) {
            return;
        }
        CommConfigStorage destino = null;
        try {
            destino = storage.config();
            if (destino == null) {
                destino = storage.createConfig();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "No se pudo recrear " + CONFIG_FILE, ex);
            return;
        }
        lock.writeLock().lock();
        try {
            CommConfigRepository repo = CommConfigRepositoryLookup.createRepository(destino);
            write(repo, true);
            storage.watchForChanges();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo escribir " + CONFIG_FILE + " recien creado", ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void write(CommConfigRepository destino, boolean archivoNuevo) {
        storage.pauseNotifications();
        try {
            destino.save(config);
        } catch (Exception ex) {
            if (archivoNuevo) {
                LOGGER.log(Level.FINE, "Se dejara para el primer guardado: " + ex.getMessage());
            } else {
                LOGGER.log(Level.WARNING,
                        "No se pudo guardar el UUID reparado de " + CONFIG_FILE, ex);
            }
        } finally {
            storage.resumeNotifications();
        }
    }

    static boolean repararUuidsAusentes(CommunicationsConfig config) {
        boolean reparado = false;
        for (int i = 0; i < config.getDevices().size(); i++) {
            DeviceConfigData device = config.getDevices().get(i);
            if (device.getUuid() != null && !device.getUuid().isBlank()) {
                continue;
            }
            reparado = true;
            String uuid = UUID.randomUUID().toString();
            LOGGER.log(Level.WARNING, CONFIG_FILE + ": el dispositivo '{0}' no tenía UUID "
                    + "y recibió {1} al cargar.",
                    new Object[]{device.getDeviceName(), uuid});
            Properties p = new Properties();
            p.put("brand", device.getBrand());
            p.put("model", device.getModel());
            p.put("protocol", device.getProtocol());
            p.put("deviceName", device.getDeviceName());
            p.put("deviceKey", device.getDeviceKey() == null ? "" : device.getDeviceKey());
            p.put("description", device.getDescription());
            p.put("uuid", uuid);
            p.put("enable", device.isEnabled());
            p.put("s88Node", device.getS88Node() == null ? "" : device.getS88Node());
            p.put("s88Uuid", device.getS88Uuid() == null ? "" : device.getS88Uuid());
            p.put("specificParameters",
                    device.getSpecificParameters() == null ? "" : device.getSpecificParameters());
            config.replaceDevice(i, new DeviceConfigData(p));
        }
        return reparado;
    }

    public Optional<Exception> getLoadError() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(loadError);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<DeviceConfigData> getDevices() {
        lock.readLock().lock();
        try {
            return List.copyOf(config.getDevices());
        } finally {
            lock.readLock().unlock();
        }
    }

    public DeviceConfigData findByUuid(String uuid) {
        lock.readLock().lock();
        try {
            return config.findByUuid(uuid);
        } finally {
            lock.readLock().unlock();
        }
    }

    public CommConfigData getComms(String deviceUuid) {
        lock.readLock().lock();
        try {
            return config.getComms(deviceUuid);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void upsertDevice(DeviceConfigData device) {
        lock.writeLock().lock();
        try {
            config.upsertDevice(device);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void upsertComms(String deviceUuid, CommConfigData commConfig) {
        lock.writeLock().lock();
        try {
            config.upsertComms(deviceUuid, commConfig);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeDevice(String uuid) {
        lock.writeLock().lock();
        try {
            return config.removeDevice(uuid);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SaveResult save() {
        lock.writeLock().lock();
        try {
            return saveInternal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private SaveResult saveInternal() {
        if (loadError != null) {
            String reason = "la carga de " + CONFIG_FILE + " fallo, asi que el modelo en "
                    + "memoria no es de fiar";
            LOGGER.log(Level.SEVERE, "No se guarda nada: " + reason, loadError);
            return SaveResult.notSaved(reason);
        }
        CommConfigStorage configStorage = storage.config();
        if (configStorage == null) {
            try {
                configStorage = storage.createConfig();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "No se pudo crear el almacen de la configuracion", ex);
                return SaveResult.notSaved(
                        "no se pudo preparar el fichero de configuracion: " + ex.getMessage());
            }
        }
        final CommConfigRepository repo = CommConfigRepositoryLookup.createRepository(configStorage);
        try {
            storage.pauseNotifications();
            try {
                repo.save(config);
            } finally {
                storage.resumeNotifications();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "No se pudo escribir " + CONFIG_FILE, ex);
            return SaveResult.notSaved(
                    "no se pudo escribir " + CONFIG_FILE + ": " + ex.getMessage());
        }
        repository = repo;
        boolean databaseUpdated;
        String rebuildReason = null;
        try {
            RebuildResults resultado =
                    BootDbRepository.rebuildWithReason(storage.bootDbFolder(), config);
            databaseUpdated = resultado.success();
            rebuildReason = resultado.reason();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error volcando la configuracion a boot.db", ex);
            databaseUpdated = false;
        }
        notifyChange();
        if (databaseUpdated) {
            return SaveResult.success();
        }
        LOGGER.warning("boot.db no pudo actualizarse; revise el registro de la aplicacion.");
        String reason = rebuildReason;
        return SaveResult.partial("se escribio " + CONFIG_FILE + " pero no se pudo actualizar boot.db"
                + (reason == null || reason.isBlank() ? "" : ": " + reason));
    }

    private void notifyChange() {
        if (SwingUtilities.isEventDispatchThread()) {
            cs.fireChange();
            return;
        }
        SwingUtilities.invokeLater(cs::fireChange);
    }

    public void addChangeListener(ChangeListener cl) {
        cs.addChangeListener(cl);
    }

    public void removeChangeListener(ChangeListener cl) {
        cs.removeChangeListener(cl);
    }
}
