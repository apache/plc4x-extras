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
package org.apache.plc4x.malbec.projecttype.panelcategory.action;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.swing.AbstractAction;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HMICategoryDuplicateDisplayAction extends AbstractAction{

    private static final Logger LOGGER = Logger.getLogger(HMICategoryDuplicateDisplayAction.class.getName());
     private final FileObject bobFile;

    public HMICategoryDuplicateDisplayAction(FileObject bobFile) {
        super("Duplicar pantalla");
        this.bobFile = bobFile;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        RequestProcessor.getDefault().post(() -> duplicate(this.bobFile));
    }
    
    private void duplicate(FileObject nuevobobFile){
        FileObject parent = nuevobobFile.getParent();
        if (parent == null) {
            return;
        }
        //Nombre base sin extensión
        String baseName = nuevobobFile.getName();
        String ext = nuevobobFile.getExt();
        //Buscar un nombre libre: base - copia, base - copia2, ...
        String newName = baseName + "_copia";
        int n = 2;
        while (parent.getFileObject(newName, ext) != null) {
            newName = baseName + "_copia" + n++;
        }
        try {
            FileObject newFile = parent.createData(newName, ext);
            try (InputStream in = nuevobobFile.getInputStream();
                 OutputStream out = newFile.getOutputStream()) {
                in.transferTo(out);
            }
                LOGGER.log(Level.INFO, "[Duplicar] Creado: {0}", newFile.getNameExt());
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
