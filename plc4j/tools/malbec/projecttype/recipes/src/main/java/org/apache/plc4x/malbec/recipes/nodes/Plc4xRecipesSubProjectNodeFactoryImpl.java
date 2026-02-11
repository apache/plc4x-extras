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
package org.apache.plc4x.malbec.recipes.nodes;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.recipes.impl.Plc4xRecipesSubProjectProviderImpl;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;

@NodeFactory.Registration(projectType = "org-plc4x-hmi-project", position = 4000)
public class Plc4xRecipesSubProjectNodeFactoryImpl implements NodeFactory {

    @StaticResource()
    public static final String RECIPES_SUBPROJECT_ICON = "org/apache/plc4x/malbec/recipes/nodes/FolderBlue.png";      
    
    @Override
    public NodeList<?> createNodes(Project project) {
        Plc4xRecipesSubProjectProviderImpl rsp = project.getLookup().
            lookup(Plc4xRecipesSubProjectProviderImpl.class);
        assert rsp != null;
        return new ProjectsNodeList(rsp.getSubprojects());
    }
    
    private class ProjectsNodeList implements NodeList<Project> {
        Set<? extends Project> subprojects; 
        
        public ProjectsNodeList(Set<? extends Project> subprojects) {
            this.subprojects = subprojects;
        }        

        @Override
        public List<Project> keys() {
            List<Project> result = new ArrayList<Project>();
            for (Project oneReportSubProject : subprojects) {
                result.add(oneReportSubProject);
            }
            return result;
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
            //
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
            //
        }

        @Override
        public Node node(Project k) {
             FilterNode fn = null;
            try {
                fn = new FilterNode(DataObject.find(k.
                        getProjectDirectory()).getNodeDelegate()){
                    @Override
                    public Image getIcon(int type) {
                        return ImageUtilities.loadImage(RECIPES_SUBPROJECT_ICON );
                    }
                    @Override
                    public Image getOpenedIcon(int type) {
                        return ImageUtilities.loadImage(RECIPES_SUBPROJECT_ICON );
                    }
                };
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            }
            return fn;
        }

        @Override
        public void addNotify() {
            //
        }

        @Override
        public void removeNotify() {
            //
        }
    }       
    
}
