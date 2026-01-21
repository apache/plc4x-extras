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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.impl.Plc4xProjectImpl;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;

@NodeFactory.Registration(projectType = "org-plc4x-project", position = 10)
public class TextsNodeFactory implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project prjct) {
        Plc4xProjectImpl p = prjct.getLookup().lookup(Plc4xProjectImpl.class);
        assert p != null;
        return new TextsNodeList(p);
    }
    
    private class TextsNodeList implements NodeList<Node> {

        Plc4xProjectImpl project;

        public TextsNodeList(Plc4xProjectImpl project) {
            this.project = project;
        }

        @Override
        public List<Node> keys() {
            FileObject textsFolder =
                project.getProjectDirectory().getFileObject("texts");

            List<Node> result = new ArrayList<Node>();
            
            if (textsFolder != null) {
                for (FileObject textsFolderFile : textsFolder.getChildren()) {
                    try {
                        result.add(DataObject.find(textsFolderFile).getNodeDelegate());
                    } catch (DataObjectNotFoundException ex) {
                        Exceptions.printStackTrace(ex);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            return result;
        }

        @Override
        public Node node(Node node) {
            return new FilterNode(node);
        }

        @Override
        public void addNotify() {
        }

        @Override
        public void removeNotify() {
        }

        @Override
        public void addChangeListener(ChangeListener cl) {
        }

        @Override
        public void removeChangeListener(ChangeListener cl) {
        }

    }    
    
}
