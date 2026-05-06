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
package org.apache.plc4x.malbec.s88.plant.ftype;

import java.io.IOException;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

@Messages({
    "LBL_Plc4xPlantFileType_LOADER=Files of Plc4xPlantFileType"
})
@MIMEResolver.ExtensionRegistration(
    displayName="#LBL_Plc4xPlantFileType_LOADER",
    mimeType="text/x-plant+xml",
    extension={ "plant", "PLANT" }
)
@DataObject.Registration(
    mimeType = "text/x-plant+xml", 
    iconBase = "org/apache/plc4x/malbec/s88/plant/nodes/PlantNode.png",
    displayName="#LBL_Plc4xPlantFileType_LOADER",
    position=300
)
@ActionReferences({
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="System", id="org.openide.actions.OpenAction"),
        position=100, 
        separatorAfter=200
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="Edit", id="org.openide.actions.CutAction"),
        position=300
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="Edit", id="org.openide.actions.CopyAction"),
        position=400,
        separatorAfter=500
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="Edit", id="org.openide.actions.DeleteAction"),
        position=600
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="System", id="org.openide.actions.RenameAction"),
        position=700,
        separatorAfter=800
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="System", id="org.openide.actions.FileSystemAction"),
        position=1100,
        separatorAfter=1200
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="System", id="org.openide.actions.ToolsAction"),
        position=1300
    ),
    @ActionReference(
        path="Loaders/text/x-plant+xml/Actions", 
        id=@ActionID(category="System", id="org.openide.actions.PropertiesAction"),
        position=1400
    )
})
public class Plc4xPlantFileTypeDataObject extends MultiDataObject {

    public Plc4xPlantFileTypeDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor("text/x-plant+xml", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
        displayName = "#LBL_Plc4xPlantFileType_EDITOR",
        iconBase = "org/apache/plc4x/malbec/s88/plant/nodes/PlantNode.png",
        mimeType = "text/x-plant+xml",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        preferredID = "Plc4xPlantFileType",
        position = 1000
    )
    @Messages("LBL_Plc4xPlantFileType_EDITOR=Source")
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }

}
