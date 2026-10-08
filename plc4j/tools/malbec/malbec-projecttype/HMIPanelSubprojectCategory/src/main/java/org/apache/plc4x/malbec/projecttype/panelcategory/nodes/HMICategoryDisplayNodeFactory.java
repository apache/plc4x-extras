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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;


import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.HMICategoryDuplicateDisplayAction;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.HMICategoryOpenPhoebusAction;
import org.apache.plc4x.malbec.projecttype.panelcategory.CategoryIcons;
import org.apache.plc4x.malbec.projecttype.panelcategory.ProjectIds;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.loaders.DataNode;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;

@NodeFactory.Registration(projectType = ProjectIds.CATEGORY_PROJECT_TYPE, position = 60)
public class HMICategoryDisplayNodeFactory implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project project) {
        FileObject projectDir = project.getProjectDirectory();

        return new HMICategoryDisplayNodeList(projectDir);
    }

    private static class HMICategoryDisplayNodeList implements NodeList<FileObject>, FileChangeListener {

        private final FileObject folder;
        private final List<ChangeListener> listeners = new ArrayList<>();
        private final List<FileObject> keys = new ArrayList<>();
        private final static String HIDE_FILE_BOB = "template.bob";

        public HMICategoryDisplayNodeList(FileObject folder) {
            this.folder = folder;
            refreshKeys();
        }

        // 1. Devuelve la lista de llaves (archivos .bob) actual
        @Override
        public synchronized List<FileObject> keys() {
            return List.copyOf(keys);
        }

        // 2. Transforma cada llave (FileObject) en su Node correspondiente
        @Override
        public Node node(FileObject key) {
            try {

                DataObject dataObj = DataObject.find(key);
                return new HMIBobNode(dataObj, key);

            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }

        }

        // 3. Registro de Listeners requerido por la interfaz NodeList
        @Override
        public void addChangeListener(ChangeListener cl) {
            listeners.add(cl);
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
            listeners.remove(cl);
        }

        // Notifica a la interfaz de NetBeans que los datos cambiaron
        private void fireChange() {
            ChangeEvent event = new ChangeEvent(this);
            for (ChangeListener listener : new ArrayList<>(listeners)) {
                listener.stateChanged(event);
            }
        }

        @Override
        public void addNotify() {
            folder.addFileChangeListener(this);
            refreshKeys();
        }

        @Override
        public void removeNotify() {
            folder.removeFileChangeListener(this);
        }

        private synchronized void refreshKeys() {
            keys.clear();
            for (FileObject child : folder.getChildren()) {
                if (!child.isFolder() && "bob".equalsIgnoreCase(child.getExt())) {

                    if (!HIDE_FILE_BOB.equalsIgnoreCase(child.getNameExt())) {
                        keys.add(child);
                    }

                }
            }
            // Disparar evento para re-renderizar el árbol
            fireChange();
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            if ("bob".equalsIgnoreCase(fe.getFile().getExt())) {
                refreshKeys();
            }
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            if ("bob".equalsIgnoreCase(fe.getFile().getExt())) {
                refreshKeys();
            }
        }

        @Override
        public void fileRenamed(FileRenameEvent fre) {
            refreshKeys();
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
            refreshKeys();
        }

        @Override
        public void fileChanged(FileEvent fe) {
            //TODO
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fae) {
            //TODO
        }

    }

    private static class HMIBobNode extends DataNode {

        private final FileObject fileObject;

        public HMIBobNode(DataObject dataObject, FileObject fileObject) {
            super(dataObject, Children.LEAF);
            this.fileObject = fileObject;

            String iconBase = CategoryIcons.DEFAULT;
            if (iconBase != null && !iconBase.isEmpty()) {
                setIconBaseWithExtension(iconBase);
            }
        }

        @Override
        public Action[] getActions(boolean context) {
            Action[] defaultActions = super.getActions(context);

            List<Action> allActions = new ArrayList<>();    
            allActions.add(new HMICategoryOpenPhoebusAction(fileObject));
            allActions.add(null);
            allActions.add(new HMICategoryDuplicateDisplayAction(fileObject));
            allActions.add(null);
            for (Action action : defaultActions) {
                allActions.add(action);
            }
            return allActions.toArray(new Action[0]);
        }
    }
}
