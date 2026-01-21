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
package org.apache.plc4x.malbec.projecttype.nodes;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.impl.Plc4xProjectImpl;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;

@NodeFactory.Registration(projectType = "org-plc4x-project", position = 20)
public class Plc4xProjectNodeFactoryImpl implements NodeFactory {

    @Override
    public NodeList<Node> createNodes(Project prjct) {
        Plc4xProjectImpl p = prjct.getLookup().lookup(Plc4xProjectImpl.class);
        assert p != null;        
        return new  Plc4xProjectNodeList(p);
    }
        
    private class Plc4xProjectNodeList implements NodeList<Node> {

        private final Plc4xProjectImpl project;        

        private Plc4xProjectNodeList(Plc4xProjectImpl project) {
            this.project = project;
        }
        
        @Override
        public List<Node> keys() {
            List<Node> result = new ArrayList<Node>();
            
            Node node1 = new Plc4xHMINode(Children.LEAF);           
            node1.setDisplayName("X1");
            result.add(node1);
            
            Node node2 = new Plc4xLanguageNode(Children.LEAF);           
            node2.setDisplayName("X2"); 
            result.add(node2);            
            
            Node node3 = new Plc4xUDTNode(Children.LEAF);           
            node3.setDisplayName("X3"); 
            result.add(node3);             
            
            Node node4 = new Plc4xVersionControlNode(Children.LEAF);           
            node4.setDisplayName("X4");               
            result.add(node4);    
            
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
        public Node node(Node k) {
            return new FilterNode(k);
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
