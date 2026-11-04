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
package org.apache.plc4x.malbec.projecttype.panel;

import java.awt.Image;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.Icon;
import org.apache.plc4x.malbec.projecttype.panel.action.HMIPanelCompileAction;
import org.apache.plc4x.malbec.projecttype.panel.action.HMIPanelImportAction;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.NodeFactorySupport;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

public class HMIPanelSubprojectImpl implements Project {

    private final FileObject fo;
    private final ProjectState ps;
    private Lookup lkp;

    public HMIPanelSubprojectImpl(FileObject fo, ProjectState ps) {
        this.fo = fo;
        this.ps = ps;
    }

    @Override
    public FileObject getProjectDirectory() {
        return fo;
    }

    @Override
    public Lookup getLookup() {
        if (lkp == null) {
            lkp = Lookups.fixed(new Object[]{
                // register your features here
                this,
                new HMIPanelSubprojectInfoImpl(),
                new HMIPanelSubprojectLogicalViewImpl(this),
                new HMIPanelCategorySubprojectProviderImpl(this),            
            });
        }
        return lkp;
    }

    public class HMIPanelSubprojectInfoImpl implements ProjectInformation {

        @StaticResource()
        public static final String PROJECT_ICON = "org/apache/plc4x/malbec/projecttype/hmipanelsubproject/PanelOperador.png";

        @Override
        public String getName() {
            return getProjectDirectory().getName();
        }

        @Override
        public String getDisplayName() {
            return getName();
        }

        @Override
        public Icon getIcon() {
            return ImageUtilities.loadImageIcon(PROJECT_ICON, false);
        }

        @Override
        public Project getProject() {
            return HMIPanelSubprojectImpl.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
        }

    }

    public class HMIPanelSubprojectLogicalViewImpl implements LogicalViewProvider {

        @StaticResource()
        public static final String PANEL_SUBPROJECT_ICON = "org/apache/plc4x/malbec/projecttype/hmipanelsubproject/PanelOperador.png";

        private final Project project;

        public HMIPanelSubprojectLogicalViewImpl(Project project) {
            this.project = project;
        }

        @Override
        public Node createLogicalView() {
            try {
                //Obtain the project directory's node:
                FileObject projectDirectory = project.getProjectDirectory();
                DataFolder projectFolder = DataFolder.findFolder(projectDirectory);
                Node nodeOfProjectFolder = projectFolder.getNodeDelegate();
                return new PanelProjectNode(nodeOfProjectFolder, project);
            } catch (DataObjectNotFoundException donfe) {
                Exceptions.printStackTrace(donfe);
                return new AbstractNode(Children.LEAF);
            }
        }

        @Override
        public Node findPath(Node node, Object o) {
            return null;
        }

        private final class PanelProjectNode extends FilterNode {

            final Project project;

            public PanelProjectNode(Node node, Project project)
                    throws DataObjectNotFoundException {
                super(node,
                        NodeFactorySupport.createCompositeChildren(project,
                                "Projects/org-apache-plc4x-panel/Nodes"),
                        new ProxyLookup(
                                new Lookup[]{
                                    Lookups.singleton(project),
                                    node.getLookup()
                                }));
                this.project = project;
            }

            @Override
            public boolean canRename() {
                return false;
            }

            @Override
            public Action[] getActions(boolean context) {
                FileObject projectDir = this.project.getProjectDirectory();
                //TODO: Agregar acciones para los proyectos
//                Action[] defaultActions = super.getActions(context);
                
                List<Action> allActions = new ArrayList<>();
                allActions.add(new HMIPanelCompileAction(projectDir));
                allActions.add(new HMIPanelImportAction(projectDir)); 
                return allActions.toArray(new Action[0]);
            }

            @Override
            public Image getIcon(int type) {
                return ImageUtilities.loadImage(PANEL_SUBPROJECT_ICON);
            }

            @Override
            public Image getOpenedIcon(int type) {
                return getIcon(type);
            }

            @Override
            public String getDisplayName() {
                return project.getProjectDirectory().getName();
            }

        }

    }

}
