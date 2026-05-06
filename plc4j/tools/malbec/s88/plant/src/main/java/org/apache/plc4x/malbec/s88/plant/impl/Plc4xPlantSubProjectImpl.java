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

import java.awt.Image;
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

/**
 * Implementation of the Plant sub-project.
 */
public class Plc4xPlantSubProjectImpl implements Project {

    private final FileObject projectDir;
    private final ProjectState state;
    private Lookup lkp;    

    public Plc4xPlantSubProjectImpl(FileObject projectDir, ProjectState state) {
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
                new Plc4xPlantSubProjectInformation(),
                new Plc4xPlantSubProjectLogicalViewProviderImpl(this),
                // Add more providers here as needed (e.g. for S88 physical model elements)
            });
        }
        return lkp;
    }
    
    private class Plc4xPlantSubProjectInformation implements ProjectInformation {

        @StaticResource()
        public static final String PLANT_SUBPROJECT_ICON = "org/apache/plc4x/malbec/s88/plant/nodes/PlantNode.png";    

        @Override
        public String getName() {
            return getProjectDirectory().getName();
        }

        @Override
        public String getDisplayName() {
            return "Plant View";
        }

        @Override
        public Icon getIcon() {
            // Using a fallback if the icon is missing
            Image img = ImageUtilities.loadImage(PLANT_SUBPROJECT_ICON);
            if (img == null) {
                return null; 
            }
            return new ImageIcon(img);
        }

        @Override
        public Project getProject() {
            return Plc4xPlantSubProjectImpl.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
        }
    }
}
