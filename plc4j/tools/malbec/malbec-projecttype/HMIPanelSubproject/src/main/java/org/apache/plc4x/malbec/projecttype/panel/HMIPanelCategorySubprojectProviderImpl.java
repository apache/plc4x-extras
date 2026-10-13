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

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.ProjectIds;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.SubprojectProvider;

import org.openide.filesystems.FileObject;

public class HMIPanelCategorySubprojectProviderImpl implements SubprojectProvider {

private static final Logger LOGGER =
            Logger.getLogger(HMIPanelCategorySubprojectProviderImpl.class.getName());

    private Project project;

    public HMIPanelCategorySubprojectProviderImpl(Project project) {
        this.project = project;

    }

    @Override
    public Set<? extends Project> getSubprojects() {
        return loadProjects(project.getProjectDirectory());
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
    }

    private Set<Project> loadProjects(FileObject dir) {
        Set<Project> subProjects = new HashSet<>();
        if (dir == null) {
            return subProjects;
        }

        for (FileObject child : dir.getChildren()) {
            if (!child.isFolder() || child.getFileObject(ProjectIds.CATEGORY_MARKER_FILE) == null) {
                continue;
            }
            try {
                Project subProject = ProjectManager.getDefault().findProject(child);
                if (subProject != null) {
                    subProjects.add(subProject);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "No se pudo cargar el subproyecto de categoría '" + child.getName()
                                + "'; no aparecerá en el árbol de proyectos.", e);
            }
        }
        return subProjects;
    }
}
