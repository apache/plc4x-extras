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
package org.apache.plc4x.malbec.projecttype.panel.action;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.AbstractAction;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressUtils;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

public class HMIPanelCompileAction extends AbstractAction {

    private final FileObject panel;

    public HMIPanelCompileAction(FileObject panel) {
        super("Exportar panel (ZIP)");
        this.panel = panel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        compilePanel(this.panel);
    }

    private void compilePanel(FileObject panel) {
        File folderOrigen = FileUtil.toFile(panel);
        if (folderOrigen == null || !folderOrigen.isDirectory()) {
            StatusDisplayer.getDefault().setStatusText("No se pudo obtener la carpeta del panel.");
            return;
        }
        File zipDestino = new FileChooserBuilder("ZipOutputKey")
                .setTitle("Guardar ZIP del panel como...")
                .setApproveText("Guardar")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivo ZIP (*.zip)", "zip"))
                .showSaveDialog();
        if (zipDestino == null) {
            return;
        }
        if (!zipDestino.getName().toLowerCase().endsWith(".zip")) {
            zipDestino = new File(zipDestino.getAbsolutePath() + ".zip");
        }
        File destino = zipDestino;
        try {
            ProgressHandle progreso = ProgressHandle.createHandle("Comprimiendo el panel...");
            Exception[] fallo = new Exception[1];
            progreso.start(400);
            ProgressUtils.showProgressDialogAndRun(() -> {
                try {
                    ZipUtils.zipFolder(folderOrigen.toPath(), destino.toPath());
                } catch (Exception ex) {
                    fallo[0] = ex;
                } finally {
                    progreso.finish();
                }
            }, progreso, false);
            if (fallo[0] != null) {
                throw fallo[0];
            }
            StatusDisplayer.getDefault().setStatusText("Panel comprimido: " + destino.getName());
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
