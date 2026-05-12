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
package org.apache.plc4x.malbec.s88.plant.nodes;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.api.s88.EquipmentXmlManager;
import org.mesa.xml.b2MML.EquipmentDocument;
import org.mesa.xml.b2MML.EquipmentType;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.nodes.Node;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;

/**
 * Node factory to populate the Plant project from a master plant.xml manifest.
 */
@NodeFactory.Registration(projectType = "org-plc4x-plant-project", position = 10)
public class PlantHierarchyNodeFactory implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project p) {
        return new PlantHierarchyNodeList(p);
    }

    private static class PlantHierarchyNodeList extends FileChangeAdapter implements NodeList<EquipmentType> {

        private final Project project;
        private final ChangeSupport cs = new ChangeSupport(this);
        private FileObject plantXml;

        public PlantHierarchyNodeList(Project project) {
            this.project = project;
        }

        @Override
        public List<EquipmentType> keys() {
            FileObject dir = project.getProjectDirectory();
            if (dir == null) {
                return Collections.emptyList();
            }
            if (plantXml == null) {
                plantXml = dir.getFileObject("plant.xml");
                if (plantXml != null) {
                    plantXml.addFileChangeListener(this);
                }
            }
            if (plantXml != null) {
                try (InputStream is = plantXml.getInputStream()) {
                    EquipmentDocument doc = EquipmentXmlManager.loadDocument(is);
                    return doc.getEquipment().getEquipmentChildList();
                } catch (Exception ex) {
                    // Ignore parsing errors during editing
                }
            }
            return Collections.emptyList();
        }

        @Override
        public void fileChanged(FileEvent fe) {
            cs.fireChange();
        }

        @Override
        public Node node(EquipmentType key) {
            return new PlantElementNode(project, key);
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
            cs.addChangeListener(cl);
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
            cs.removeChangeListener(cl);
        }

        @Override
        public void addNotify() {
        }

        @Override
        public void removeNotify() {
            if (plantXml != null) {
                plantXml.removeFileChangeListener(this);
            }
        }
    }
}
