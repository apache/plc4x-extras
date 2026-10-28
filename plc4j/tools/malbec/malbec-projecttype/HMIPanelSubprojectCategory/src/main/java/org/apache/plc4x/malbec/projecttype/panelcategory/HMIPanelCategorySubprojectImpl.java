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
package org.apache.plc4x.malbec.projecttype.panelcategory;

import java.awt.Image;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.Icon;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.HMICategoryCreateCommAction;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.HMICategoryCreateDeviceAction;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.HMICategoryCreateDisplayAction;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans.HMICommunicationModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.ProjectIds;
import org.apache.plc4x.malbec.projecttype.panelcategory.action.CreateVariable;
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
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.Lookup;

public class HMIPanelCategorySubprojectImpl implements Project {

    private static final java.util.logging.Logger ExceptionsLOGGER =
            java.util.logging.Logger.getLogger(HMICategoryCreateCommAction.class.getName());
    private final FileObject fo;
    private final ProjectState ps;
    private Lookup lkp;

    public HMIPanelCategorySubprojectImpl(FileObject fo, ProjectState ps) {
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
            ArrayList<Object> services = new ArrayList<>(4);
            services.add(this);
            services.add(new HMIPanelCategorySubprojectInfoImpl());
            services.add(new HMIPanelCategorySubprojectLogicalViewImpl(this));
            try {
                services.add(new HMICommunicationModel(this));
            } catch (Throwable t) {
                Exceptions.attachMessage(t,
                        "El modelo de comunicaciones no pudo inicializarse; "
                        + "se omite del Lookup para no bloquear el árbol de proyectos.");
                ExceptionsLOGGER.log(
                        java.util.logging.Level.SEVERE,
                        "No se pudo crear el modelo de comunicaciones del proyecto "
                                + getProjectDirectory().getName()
                                + "; el asistente de comunicación quedará sin funcionar.",
                        t);
            }
            lkp = Lookups.fixed(services.toArray());
        }
        return lkp;
    }

    public class HMIPanelCategorySubprojectInfoImpl implements ProjectInformation {

        @StaticResource()
        public static final String PROJECT_ICON = "org/apache/plc4x/malbec/projecttype/hmipanelsubprojectcategory/FolderBlue.png";

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
            return HMIPanelCategorySubprojectImpl.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
//            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
//            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }

    public class HMIPanelCategorySubprojectLogicalViewImpl implements LogicalViewProvider {

        @StaticResource()
        public static final String CATEGORY_SUBPROJECT_ICON = "org/apache/plc4x/malbec/projecttype/hmipanelsubprojectcategory/FolderBlue.png";

        private final Project project;

        public HMIPanelCategorySubprojectLogicalViewImpl(Project project) {
            this.project = project;
        }

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
                                "Projects/org-apache-plc4x-category/Nodes"),

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

            public Action[] getActions(boolean context) {
                Optional<String> detected = DeteccionCategoria.detect(
                        FileObjectTree.fromFileObject(this.project.getProjectDirectory()));
                if (detected.isEmpty()) {
                    return getActionForName(new Action[0]);
                }

                // Identificar la acción específica asociada al archivo
                Action[] customAction = switch (detected.get()) {
                    case "template.bob" ->
                        new Action[]{
                            new HMICategoryCreateDisplayAction(this.project),                        
                        };
                    case ProjectIds.COMMUNICATION_CONFIG_FILE ->
                        new Action[]{
                            new HMICategoryCreateCommAction(this.project),
                            new HMICategoryCreateDeviceAction(this.project),
                            new CreateVariable(this.project),
                        };
//                    case "base.oppc" ->
//                        new HMICategoryCreateOperatorPanelConfigAction(this.project);
//                    case "record.record" ->
//                        new HMICategoryCreateRecordAction(this.project);
//                    case "base.rcp" ->
//                        new HMICategoryCreateRecipeAction(this.project);
//                    case "base.rpt" ->
//                        new HMICategoryCreateReportAction(this.project);
//                    case "base.scp" ->
//                        new HMICategoryCreateScriptsAction(this.project);
//                    case "base.wmg" ->
//                        new HMICategoryCreateWarningManagementAction(this.project);
                    default ->
                        new Action[0];
                };
                return getActionForName(customAction);
            }

            public Action[] getActionForName(Action[] customAction) {
                List<Action> actionList = new ArrayList<>();

                // 2. Si existen acciones personalizadas, agregamos separador y el listado
                if (customAction.length > 0) {
                    actionList.addAll(Arrays.asList(customAction));
                }

                return actionList.toArray(Action[]::new);
            }

            @Override
            public Image getIcon(int type) {
                return ImageUtilities.loadImage(CATEGORY_SUBPROJECT_ICON);
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
