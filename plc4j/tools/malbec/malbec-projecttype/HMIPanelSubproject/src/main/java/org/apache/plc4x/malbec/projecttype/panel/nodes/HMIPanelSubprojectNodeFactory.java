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
package org.apache.plc4x.malbec.projecttype.panel.nodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.plc4x.malbec.projecttype.panel.HMIPanelSubprojectImpl;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeFactorySupport;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.filesystems.AbstractFileSystem.List;
import org.openide.filesystems.FileObject;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;


@NodeFactory.Registration(projectType = "org-plc4x-hmi-project", position = 10)
public class HMIPanelSubprojectNodeFactory implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project project) {

        ArrayList<Node> nodes = new ArrayList<>();

        FileObject dir = project.getProjectDirectory();

        for (Project panelProject : getAllSubProject(dir)) {

            LogicalViewProvider lvp = panelProject.getLookup().lookup(LogicalViewProvider.class);
            if (lvp != null) {
                nodes.add(lvp.createLogicalView());
            }
        }

        return NodeFactorySupport.fixedNodeList(nodes.toArray(new Node[0]));
    }

    private ArrayList<Project> getAllSubProject(FileObject dir) {
        ArrayList<Project> result = new ArrayList<>();

        for (FileObject child : dir.getChildren()) {
            if (!child.isFolder()) {
                continue;
            }
            try {
                Project p = ProjectManager.getDefault().findProject(child);
                if (p instanceof HMIPanelSubprojectImpl) {
                    result.add((HMIPanelSubprojectImpl) p);
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return result;

    }

}
