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
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.HMICategoryDefinition;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeFactorySupport;
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

public abstract class CategoryFilesNodeFactory implements NodeFactory {

    protected abstract HMICategoryDefinition category();
    protected Action[] ownActions(FileObject file) {
        return new Action[0];
    }

    @Override
    public NodeList<?> createNodes(Project project) {
        FileObject projectDir = project.getProjectDirectory();

        HMICategoryDefinition definition = category();
        if (!definition.matchesFolder(projectDir.getName())) {
            return NodeFactorySupport.fixedNodeList();
        }

        return new CategoryFilesNodeList(projectDir, definition, this::ownActions);
    }

    private static final class CategoryFilesNodeList
            implements NodeList<FileObject>, FileChangeListener {

        private final FileObject folder;
        private final HMICategoryDefinition definition;
        private final Function<FileObject, Action[]> ownActions;
        private final List<ChangeListener> listeners = new ArrayList<>();
        private final List<FileObject> keys = new ArrayList<>();

        CategoryFilesNodeList(FileObject folder, HMICategoryDefinition definition,
                Function<FileObject, Action[]> ownActions) {
            this.folder = folder;
            this.definition = definition;
            this.ownActions = ownActions;
            refreshKeys();
        }

        @Override
        public synchronized List<FileObject> keys() {
                return List.copyOf(keys);
        }

        @Override
        public Node node(FileObject key) {
            try {
                DataObject dataObj = DataObject.find(key);
                return new CategoryFileNode(dataObj, key, definition, ownActions);
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
                return null;
            }
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
            listeners.add(cl);
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
            listeners.remove(cl);
        }

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
                if (definition.acepta(child.getNameExt(), child.getExt(), child.isFolder())) {
                    keys.add(child);
                }
            }
            fireChange();
        }

        private boolean isOfThisCategory(FileEvent evento) {
            String extension = definition.getFileExtension();
            return extension != null && extension.equalsIgnoreCase(evento.getFile().getExt());
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            if (isOfThisCategory(fe)) {
                refreshKeys();
            }
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            if (isOfThisCategory(fe)) {
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
            // TODO
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fae) {
            // TODO
        }
    }

    private static final class CategoryFileNode extends DataNode {

        private final FileObject fileObject;
        private final Function<FileObject, Action[]> ownActions;

        CategoryFileNode(DataObject dataObject, FileObject fileObject,
                HMICategoryDefinition definition, Function<FileObject, Action[]> ownActions) {
            super(dataObject, Children.LEAF);
            this.fileObject = fileObject;
            this.ownActions = ownActions;

            String iconBase = definition.getIconPath();
            if (iconBase != null && !iconBase.isEmpty()) {
                setIconBaseWithExtension(iconBase);
            }
        }

        @Override
        public Action[] getActions(boolean context) {
            Action[] defaultActions = super.getActions(context);

            List<Action> allActions = new ArrayList<>();
            for (Action action : ownActions.apply(fileObject)) {
                allActions.add(action);
            }
            allActions.add(null);
            for (Action action : defaultActions) {
                allActions.add(action);
            }
            return allActions.toArray(new Action[0]);
        }
    }
}