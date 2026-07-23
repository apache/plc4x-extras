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
package org.apache.plc4x.malbec.s88.plant.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.*;
import javax.swing.event.ChangeListener;

import org.apache.plc4x.malbec.s88.api.*;
import org.apache.plc4x.malbec.s88.plant.services.S88ProjectServices;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;

/**
 * NetBeans-aware wrapper for S88PlantModel. Manages the plant.xml manifest
 * lifecycle and synchronization.
 */
public class Plc4xPlantModel implements S88ChangeListener {

    private final Project project;
    private final ChangeSupport cs = new ChangeSupport(this);
    private S88PlantModel model;
    private FileObject plantXml;
    private final FileChangeAdapter fileListener;

    public Plc4xPlantModel(Project project) {
        this.project = project;
        this.fileListener = new FileChangeAdapter() {
            @Override
            public void fileChanged(FileEvent fe) {
                reload();
            }

            @Override
            public void fileDeleted(FileEvent fe) {
                model = null;
                cs.fireChange();
            }
        };
        reload();
    }

    public final void reload() {
        plantXml = project.getProjectDirectory().getFileObject("plant.xml");
        if (plantXml != null) {
            plantXml.removeFileChangeListener(fileListener);
            plantXml.addFileChangeListener(fileListener);
            if (model != null) {
                model.removeChangeListener(this);
            }
            S88Repository repo = S88ProjectServices.createRepository("xml", new FileObjectStorage(plantXml));
            model = repo.loadPlant();
            if (model != null) {
                model.addChangeListener(this);
            }
            cs.fireChange();
        } else {
            model = null;
            cs.fireChange();
        }
    }

    public S88PlantModel getModel() {
        return model;
    }

    public S88Element getElementByID(String id) {
        return model != null ? model.findById(id).orElse(null) : null;
    }

    public void addChangeListener(ChangeListener cl) {
        cs.addChangeListener(cl);
    }

    public void removeChangeListener(ChangeListener cl) {
        cs.removeChangeListener(cl);
    }

    @Override
    public void onS88Change(S88ChangeEvent event) {
        cs.fireChange();
    }

    public void save() throws IOException {
        if (model == null) {
            return;
        }
        plantXml = project.getProjectDirectory().getFileObject("plant.xml");
        if (plantXml == null) {
            plantXml = project.getProjectDirectory().createData("plant.xml");
            plantXml.addFileChangeListener(fileListener);
        }
        S88Repository repo = S88ProjectServices.createRepository("xml", new FileObjectStorage(plantXml));
        repo.savePlant(model);
    }

    public S88Element createRoot(String id) {
        model = new S88PlantModel(new S88Element());
        model.getRoot().setId(id);
        model.getRoot().setLevel(S88Level.AREA);
        model.addChangeListener(this);
        cs.fireChange();
        return model.getRoot();
    }

    public Project getProject() {
        return project;
    }

    public void export(String outputPath) {
        try {
            File file = new File(outputPath);
            S88Storage storage = new S88Storage() {
                @Override
                public InputStream openInput() throws IOException {
                    return new FileInputStream(file);
                }

                @Override
                public OutputStream openOutput() throws IOException {
                    return new FileOutputStream(file);
                }
            };
            S88Repository repo = S88ProjectServices.createRepository("axml", storage);
            repo.savePlant(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class FileObjectStorage implements S88Storage {

        private final FileObject fo;

        FileObjectStorage(FileObject fo) {
            this.fo = fo;
        }

        @Override
        public InputStream openInput() throws IOException {
            return fo.getInputStream();
        }

        @Override
        public OutputStream openOutput() throws IOException {
            return fo.getOutputStream();
        }
    }
}
