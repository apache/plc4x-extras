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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.s88.EquipmentXmlManager;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentType;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;

/**
 * Centralized model provider for B2MML Equipment data.
 * Manages the plant.xml manifest lifecycle and provides cached access to elements.
 */
public class Plc4xPlantModel {

    private final Project project;
    private final ChangeSupport cs = new ChangeSupport(this);
    private EquipmentDocument doc;
    private final Map<String, EquipmentType> cache = new HashMap<>();
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
                doc = null;
                cache.clear();
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
            try (InputStream is = plantXml.getInputStream()) {
                doc = EquipmentXmlManager.loadDocument(is);
                rebuildCache();
                cs.fireChange();
            } catch (Exception ex) {
                // Keep old doc if parsing fails during edit
            }
        } else {
            doc = null;
            cache.clear();
            cs.fireChange();
        }
    }

    private void rebuildCache() {
        cache.clear();
        if (doc != null && doc.getEquipment() != null) {
            addToCache(doc.getEquipment());
        }
    }

    private void addToCache(EquipmentType et) {
        if (et.getID() != null) {
            cache.put(et.getID().getStringValue(), et);
        }
        for (EquipmentType child : et.getEquipmentChildArray()) {
            addToCache(child);
        }
    }

    public EquipmentDocument getDocument() {
        return doc;
    }

    public EquipmentType getElementByID(String id) {
        return cache.get(id);
    }

    public void addChangeListener(ChangeListener cl) {
        cs.addChangeListener(cl);
    }

    public void removeChangeListener(ChangeListener cl) {
        cs.removeChangeListener(cl);
    }

    public void save() throws IOException {
        if (doc == null) return;
        plantXml = project.getProjectDirectory().getFileObject("plant.xml");
        if (plantXml == null) {
            plantXml = project.getProjectDirectory().createData("plant.xml");
            plantXml.addFileChangeListener(fileListener);
        }
        try (OutputStream os = plantXml.getOutputStream()) {
            EquipmentXmlManager.saveDocument(doc, os);
        }
        // No need to reload manually, the file listener will trigger it
    }
    
    public EquipmentType createRoot(String id) {
        doc = EquipmentDocument.Factory.newInstance();
        EquipmentType root = doc.addNewEquipment();
        root.addNewID().setStringValue(id);
        root.addNewEquipmentLevel().setStringValue("Area");
        rebuildCache();
        return root;
    }

    public Project getProject() {
        return project;
    }
}
