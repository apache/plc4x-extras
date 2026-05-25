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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeFactorySupport;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.nodes.Node;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;

/**
 * Node factory to populate the Plant project from the S88 model.
 */
@NodeFactory.Registration(projectType = "org-plc4x-plant-project", position = 10)
public class PlantHierarchyNodeFactory implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project p) {
        Plc4xPlantModel model = p.getLookup().lookup(Plc4xPlantModel.class);
        if (model == null) {
            return NodeFactorySupport.fixedNodeList();
        }
        return new PlantHierarchyNodeList(model);
    }

    private static class PlantHierarchyNodeList implements NodeList<String>, ChangeListener {

        private final Plc4xPlantModel model;
        private final ChangeSupport cs = new ChangeSupport(this);

        public PlantHierarchyNodeList(Plc4xPlantModel model) {
            this.model = model;
        }

        @Override
        public List<String> keys() {
            S88PlantModel s88Model = model.getModel();
            if (s88Model != null && s88Model.getRoot() != null) {
                return s88Model.getRoot().getChildren().stream()
                        .map(S88Element::getId)
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public Node node(String key) {
            S88Element et = model.getElementByID(key);
            if (et != null) {
                try {
                    return PlantNodeFactoryUtil.createNode(model.getProject(), et);
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            return null;
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
            cs.addChangeListener(cl);
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
            cs.removeChangeListener(cl);
        }

        @Override
        public void addNotify() {
            model.addChangeListener(this);
        }

        @Override
        public void removeNotify() {
            model.removeChangeListener(this);
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            cs.fireChange();
        }
    }
}
