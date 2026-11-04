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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Timer;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.BootDbTagDynamicRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.RebuildResults;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

final class VariableDocumentService {

    private FileObject projectFolder;
    private FileChangeAdapter xmlListener;
    private Timer reloadTimer;
    private Runnable onChange;
    private final AtomicBoolean saving = new AtomicBoolean();
    private String lastKnownXml = "";

    void open(FileObject folder, Runnable onChange) {
        close();
        this.projectFolder = folder;
        this.onChange = onChange;
        this.lastKnownXml = readXml(xmlFileObject());
        if (folder != null) {
            xmlListener = new FileChangeAdapter() {
                @Override
                public void fileChanged(FileEvent event) {
                    scheduleReload(event.getFile());
                }

                @Override
                public void fileDataCreated(FileEvent event) {
                    scheduleReload(event.getFile());
                }

                @Override
                public void fileDeleted(FileEvent event) {
                    scheduleReload(event.getFile());
                }
            };
            folder.addFileChangeListener(xmlListener);
        }
    }

    void close() {
        if (projectFolder != null && xmlListener != null) {
            projectFolder.removeFileChangeListener(xmlListener);
        }
        xmlListener = null;
        if (reloadTimer != null) {
            reloadTimer.stop();
            reloadTimer = null;
        }
        projectFolder = null;
        onChange = null;
        lastKnownXml = "";
    }

    File folder() {
        return projectFolder == null ? null : FileUtil.toFile(projectFolder);
    }

    FileObject xmlFileObject() {
        return projectFolder == null
                ? null
                : projectFolder.getFileObject(DynamicVariableXmlMapper.FILE_NAME);
    }

    void refreshProjectFolder() {
        if (projectFolder != null) {
            projectFolder.refresh();
        }
    }

    ExternalChange readExternalChange() {
        if (saving.get() || projectFolder == null) {
            return null;
        }
        String xml = readXml();
        if (xml.equals(lastKnownXml)) {
            return null;
        }
        lastKnownXml = xml;
        return new ExternalChange(xml, xml.isBlank());
    }

    DynamicVariableXmlMapper.Content readContent(String xml) throws Exception {
        return DynamicVariableXmlMapper.read(xml);
    }

    RebuildResults rebuildBootDb(File folder, DynamicVariableXmlMapper.Content content) {
        return BootDbTagDynamicRepository.rebuildWithReason(
                folder, content.devices(), content.pvs());
    }

    SaveOutcome save(File folder, List<DeviceConfigData> devices,
            List<CreateVariableController.Row> rows, Collection<String> deletedPvUuids) {
        String previousXml = lastKnownXml;
        saving.set(true);
        try {
            CreateVariableController.Result result = CreateVariableController.saveVariables(
                    folder == null ? "" : folder.getAbsolutePath(),
                    devices, rows, deletedPvUuids);

            FileObject xmlFile = xmlFileObject();
            if (xmlFile == null) {
                refreshProjectFolder();
                xmlFile = xmlFileObject();
            }
            String currentXml = previousXml;
            if (xmlFile != null) {
                xmlFile.refresh();
                currentXml = readXml(xmlFile);
            }
            lastKnownXml = currentXml;
            return new SaveOutcome(result, !currentXml.equals(previousXml));
        } finally {
            saving.set(false);
        }
    }

    private void scheduleReload(FileObject file) {
        if (!isCommunicationXml(file) || onChange == null) {
            return;
        }
        if (reloadTimer == null) {
            reloadTimer = new Timer(250, event -> {
                Runnable callback = onChange;
                if (callback != null) {
                    callback.run();
                }
            });
            reloadTimer.setRepeats(false);
        }
        reloadTimer.restart();
    }

    private static boolean isCommunicationXml(FileObject file) {
        return file != null
                && DynamicVariableXmlMapper.FILE_NAME.equals(file.getNameExt());
    }

    private String readXml() {
        return readXml(xmlFileObject());
    }

    private static String readXml(FileObject file) {
        if (file == null || !file.isValid()) {
            return "";
        }
        try (InputStream in = file.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    record ExternalChange(String xml, boolean blank) {
    }

    record SaveOutcome(CreateVariableController.Result result, boolean xmlChanged) {
    }
}
