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

import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.apache.plc4x.malbec.s88.panels.Plc4xGeneralPropertiesImpl;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantSubProjectProviderImpl;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

public class Plc4xProjectImpl implements Project{

    private final FileObject fo;
    private final ProjectState ps;
    private Lookup lkp;    
    
    
    public Plc4xProjectImpl(FileObject fo, ProjectState ps) {
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
                this,
                new Plc4xProjectInfoImpl(),
                new Plc4xProjectLogicalViewImpl(this),
                new Plc4xCustomizerProviderImpl(this),
                new Plc4xGeneralPropertiesImpl(),
                new Plc4xPlantSubProjectProviderImpl(this),              
                 
            });
        }
        return lkp;
    }
    
    private class Plc4xProjectInfoImpl implements ProjectInformation {


        @StaticResource()
        public static final String PROJECT_ICON = "org/apache/plc4x/malbec/s88/impl/Project.png";    

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
            return new ImageIcon(ImageUtilities.loadImage(PROJECT_ICON));
        }

        @Override
        public Project getProject() {
            return Plc4xProjectImpl.this;
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
