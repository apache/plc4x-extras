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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.SubprojectProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 * Provider to discover the 'plant' sub-project.
 */
public class Plc4xPlantSubProjectProviderImpl implements SubprojectProvider {

    public static final String PLANT_SUBPROJECT_DIRECTORY = "plant";
    
    private final Project project;

    public Plc4xPlantSubProjectProviderImpl(Project project) {
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
    
    public Set<Project> loadProjects(FileObject dir) {
        Set<Project> newProjects = new HashSet<>();
        for (FileObject sub : dir.getChildren()) {
            if (sub.isFolder()) {
                try {
                    Project subp = ProjectManager.getDefault().findProject(sub);
                    if (subp instanceof Plc4xPlantSubProjectImpl) {
                        newProjects.add(subp);
                    }
                } catch (IOException | IllegalArgumentException ex) {
                    // Ignore non-projects
                }
            }
        }
        return Collections.unmodifiableSet(newProjects);
    }    
}
