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
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 * Factory to recognize the 'plant' folder as a sub-project.
 */
@ServiceProvider(service=ProjectFactory.class)
public class Plc4xPlantSubProjectFactoryImpl implements ProjectFactory {
    
    public static final String PLANT_PROJECT_DIRECTORY = "plant";     
    
    @Override
    public boolean isProject(FileObject fo) {
        return fo.isFolder() && fo.getFileObject("plant.xml") != null;   
    }

    @Override
    public Project loadProject(FileObject fo, ProjectState ps) throws IOException {
        return isProject(fo) ? new Plc4xPlantSubProjectImpl(fo, ps) : null;
    }

    @Override
    public void saveProject(Project prjct) throws IOException, ClassCastException {
        // No special saving needed for now
    }
}
