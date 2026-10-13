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
import java.io.IOException;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

final class NetBeansProjectStorage implements ProjectStorage {

    private final Project project;
    private FileObject watched;
    private FileChangeAdapter listener;
    private Runnable onChanged;
    private Runnable onDeleted;

    NetBeansProjectStorage(Project project) {
        this.project = project;
    }

    @Override
    public String directoryName() {
        return project.getProjectDirectory().getName();
    }

    @Override
    public boolean has(String fileName) {
        return project.getProjectDirectory().getFileObject(fileName) != null;
    }

    @Override
    public CommConfigStorage config() {
        FileObject file = project.getProjectDirectory().getFileObject(HMICommunicationModel.CONFIG_FILE);
        return file == null ? null : new ProjectFileStorage(file);
    }

    @Override
    public CommConfigStorage createConfig() throws IOException {
        return new ProjectFileStorage(
                project.getProjectDirectory().createData(HMICommunicationModel.CONFIG_FILE));
    }

    @Override
    public File bootDbFolder() {
        return FileUtil.toFile(project.getProjectDirectory());
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
        FileObject current = project.getProjectDirectory()
                .getFileObject(HMICommunicationModel.CONFIG_FILE);
        detach();
        if (current == null || onChanged == null) {
            return;
        }
        listener = new FileChangeAdapter() {
            @Override
            public void fileChanged(FileEvent event) {
                if (onChanged != null) {
                    onChanged.run();
                }
            }

            @Override
            public void fileDeleted(FileEvent event) {
                if (onDeleted != null) {
                    onDeleted.run();
                }
            }
        };
        current.addFileChangeListener(listener);
        watched = current;
    }

    @Override
    public void pauseNotifications() {
        if (watched != null && listener != null) {
            watched.removeFileChangeListener(listener);
        }
    }

    @Override
    public void resumeNotifications() {
        if (watched != null && listener != null && watched.isValid()) {
            watched.addFileChangeListener(listener);
        }
    }

    private void detach() {
        if (watched != null && listener != null) {
            watched.removeFileChangeListener(listener);
        }
        watched = null;
        listener = null;
    }
}
