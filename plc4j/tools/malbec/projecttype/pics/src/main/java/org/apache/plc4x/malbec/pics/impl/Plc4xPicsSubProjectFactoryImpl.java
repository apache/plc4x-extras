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
package org.apache.plc4x.malbec.pics.impl;

import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=ProjectFactory.class)
public class Plc4xPicsSubProjectFactoryImpl implements ProjectFactory {

    public static final String COMMS_SUBPROJECT_DIRECTORY = "pics"; 
    
    @Override
    public boolean isProject(FileObject fo) {
        boolean isCommsFolder = fo.getName().equalsIgnoreCase(COMMS_SUBPROJECT_DIRECTORY) && fo.isFolder();   
        return isCommsFolder;
    }

    @Override
    public Project loadProject(FileObject fo, ProjectState ps) throws IOException {
        return isProject(fo) ? new Plc4xPicsSubProjectImpl(fo, ps) : null;
    }

    @Override
    public void saveProject(Project prjct) throws IOException, ClassCastException {
        //
    }
    
}
