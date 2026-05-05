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
package org.apache.plc4x.malbec.s88.nodes;

import java.awt.Image;
import static org.apache.plc4x.malbec.s88.nodes.Plc4xLanguageNode.MODULE_LANGUAGE_ICON;
import org.netbeans.api.annotations.common.StaticResource;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.ImageUtilities;

public class Plc4xVersionControlNode extends AbstractNode {
    
    @StaticResource()
    public static final String MODULE_VERPRJ_ICON = "org/apache/plc4x/malbec/s88/nodes/VersionesProyecto.png"; 
    
    public Plc4xVersionControlNode(Children children) {
        super(children);
    }
    
    @Override
    public Image getIcon(int type) {
        return ImageUtilities.loadImage(MODULE_VERPRJ_ICON, true);
    }      
    
}
