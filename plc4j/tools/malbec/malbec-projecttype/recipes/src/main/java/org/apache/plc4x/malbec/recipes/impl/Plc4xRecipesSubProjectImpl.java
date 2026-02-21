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
package org.apache.plc4x.malbec.recipes.impl;

import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

public class Plc4xRecipesSubProjectImpl implements  Project  {

    private final FileObject projectDir;
    private final ProjectState state;
    private Lookup lkp;      

    public Plc4xRecipesSubProjectImpl(FileObject projectDir, ProjectState state) {
        this.projectDir = projectDir;
        this.state = state;
    }
    
    @Override
    public FileObject getProjectDirectory() {
        return projectDir;
    }

    @Override
    public Lookup getLookup() {
        if (lkp == null) {
            lkp = Lookups.fixed(new Object[]{
            // register your features here
                new Plc4xRecipesSubProjectInformation(),
                new Plc4xRecipesSubProjectLogicalViewProviderImpl(this),
            });
        }
        return lkp;
    }
    
    private class Plc4xRecipesSubProjectInformation implements ProjectInformation {

        @StaticResource()
        public static final String RECIPES_SUBPROJECT_ICON = "org/apache/plc4x/malbec/recipes/nodes/FolderBlue.png";    

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
            return new ImageIcon(ImageUtilities.loadImage(RECIPES_SUBPROJECT_ICON));
        }

        @Override
        public Project getProject() {
            return Plc4xRecipesSubProjectImpl.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
            //
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
            //
        }

    }    
    
}
