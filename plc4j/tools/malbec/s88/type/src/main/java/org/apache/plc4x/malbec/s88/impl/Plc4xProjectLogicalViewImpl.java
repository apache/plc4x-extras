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
package org.apache.plc4x.malbec.s88.impl;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.CommonProjectActions;
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
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

public class Plc4xProjectLogicalViewImpl implements LogicalViewProvider {

    
    @StaticResource()
    public static final String HMI_SUBPROJECT_ICON = "org/apache/plc4x/malbec/s88/impl/Project.png";       
    
    private final Plc4xProjectImpl project;

    public Plc4xProjectLogicalViewImpl(Plc4xProjectImpl project) {
        this.project = project;
    }
    
    @Override
    public Node createLogicalView() {    
        try {
            FileObject projectDirectory = project.getProjectDirectory();
            DataFolder projectFolder = DataFolder.findFolder(projectDirectory);
            Node nodeOfProjectFolder = projectFolder.getNodeDelegate();
            return new ProjectNode(nodeOfProjectFolder, project);
        } catch (DataObjectNotFoundException donfe) {
            Exceptions.printStackTrace(donfe);
            return new AbstractNode(Children.LEAF);
        }
    }

    @Override
    public Node findPath(Node node, Object o) {
        return null;
    }
    
    private final class ProjectNode extends FilterNode {

        final Plc4xProjectImpl project;

        public ProjectNode(Node node, Plc4xProjectImpl project)
            throws DataObjectNotFoundException {
            super(node,
                  NodeFactorySupport.createCompositeChildren(project,
                            "Projects/org-plc4x-s88-project/Nodes"),
                  new ProxyLookup(
                  new Lookup[]{
                  Lookups.singleton(project),
                  node.getLookup()
            }));
            this.project = project;
        }

        @Override
        public Action[] getActions(boolean arg0) {
            List<Action> actions = new ArrayList<>();
            actions.addAll(Utilities.actionsForPath("Projects/org-plc4x-s88-project/Actions"));
            actions.add(null);
            actions.add(CommonProjectActions.copyProjectAction());
            actions.add(CommonProjectActions.deleteProjectAction());
            actions.add(CommonProjectActions.closeProjectAction());
            actions.add(null);
            actions.add(CommonProjectActions.customizeProjectAction());
            return actions.toArray(new Action[0]);
        }

        @Override
        public Image getIcon(int type) {
            return ImageUtilities.loadImage(HMI_SUBPROJECT_ICON);
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
