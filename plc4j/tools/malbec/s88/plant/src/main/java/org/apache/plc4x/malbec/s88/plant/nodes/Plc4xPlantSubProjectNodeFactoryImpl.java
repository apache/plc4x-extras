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
package org.apache.plc4x.malbec.s88.plant.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantSubProjectProviderImpl;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.netbeans.spi.project.ui.support.NodeFactorySupport;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;

/**
 * Node factory to include the Plant sub-project in the main S88 project tree.
 */
@NodeFactory.Registration(projectType = "org-plc4x-s88-project", position = 15)
public class Plc4xPlantSubProjectNodeFactoryImpl implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project project) {
        System.out.println("S88: Plc4xPlantSubProjectNodeFactoryImpl.createNodes called for " + project.getProjectDirectory().getPath());
        Plc4xPlantSubProjectProviderImpl provider = project.getLookup().lookup(Plc4xPlantSubProjectProviderImpl.class);
        if (provider == null) {
            System.out.println("S88: provider is NULL in lookup");
            return NodeFactorySupport.fixedNodeList();
        }
        Set<? extends Project> subprojects = provider.getSubprojects();
        System.out.println("S88: provider found " + subprojects.size() + " subprojects");
        return new PlantProjectsNodeList(subprojects);
    }
    
    private static class PlantProjectsNodeList implements NodeList<Project> {
        private final Set<? extends Project> subprojects; 
        
        public PlantProjectsNodeList(Set<? extends Project> subprojects) {
            this.subprojects = subprojects;
        }        

        @Override
        public List<Project> keys() {
            return new ArrayList<>(subprojects);
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
        }

        @Override
        public Node node(Project k) {
            try {
                LogicalViewProvider lvp = k.getLookup().lookup(LogicalViewProvider.class);
                if (lvp != null) {
                    return new FilterNode(lvp.createLogicalView());
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            return null;
        }

        @Override
        public void addNotify() {
        }

        @Override
        public void removeNotify() {
        }
    }    
}
