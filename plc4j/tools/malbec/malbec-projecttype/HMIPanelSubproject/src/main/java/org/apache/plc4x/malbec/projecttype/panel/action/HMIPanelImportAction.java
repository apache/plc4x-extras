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

import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.apache.plc4x.malbec.projecttype.panel.plant.FormatosDeImportacion;
import org.apache.plc4x.malbec.projecttype.panel.plant.ImportResult;
import org.apache.plc4x.malbec.projecttype.panel.plant.ImportResult.Exito;
import org.apache.plc4x.malbec.projecttype.panel.plant.ImportResult.Fallo;
import org.apache.plc4x.malbec.projecttype.panel.plant.PlantImporter;
import org.netbeans.api.progress.ProgressUtils;
import org.openide.filesystems.FileObject;
import org.openide.windows.WindowManager;

/**
 * Importa un modelo de planta en el panel.
 *
 * <p>Esta clase solo se ocupa de la ventana: elegir el fichero, enseñar el progreso y
 * mostrar como termino. Lo que hay que hacer con ese fichero vive en
 * {@link PlantImporter}, que no sabe nada de Swing y por eso se puede probar.
 *
 * <p>Antes eran 716 lineas aqui, con el volcado a {@code plant-model.xml}, la validacion de
 * nombres y la creacion de la estructura de carpetas mezcladas con los dialogos.
 */
public class HMIPanelImportAction extends AbstractAction {

    private static final String TITULO = "Importando modelo de planta...";

    private final FileObject panel;

    public HMIPanelImportAction(FileObject panel) {
        super("Importar proyecto");
        this.panel = panel;
        FormatosDeImportacion.avisarFormatosDesalineados();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        importPlant();
    }

    private void importPlant() {
        FileDialog chooser =
                new FileDialog(WindowManager.getDefault().getMainWindow(),
                        "Seleccionar modelo de planta", FileDialog.LOAD);
        chooser.setDirectory(System.getProperty("user.home"));
        chooser.setFilenameFilter(FormatosDeImportacion.filtroDeArchivo());
        chooser.setVisible(true);

        File[] seleccion = chooser.getFiles();
        if (seleccion == null || seleccion.length == 0) {
            return;
        }
        File archivo = seleccion[0];
        if (!FormatosDeImportacion.extensionAdmitida(archivo)) {
            avisarFallo(archivo, "solo se admiten "
                    + FormatosDeImportacion.formatosAdmitidos() + ".");
            return;
        }

        ImportResult resultado = ProgressUtils.showProgressDialogAndRun(
                handle -> {
                    handle.setDisplayName(TITULO);
                    handle.start(400);
                    try {
                        return PlantImporter.importPlant(archivo, panel);
                    } finally {
                        handle.finish();
                    }
                },
                TITULO,
                false);
        mostrar(resultado);
    }

    private void mostrar(ImportResult resultado) {
        if (resultado instanceof Exito exito) {
            avisarExito(exito.archivo(), exito.areaId());
        } else if (resultado instanceof Fallo fallo) {
            avisarFallo(fallo.archivo(), fallo.motivo());
        }
    }

    private void avisarExito(File file, String areaId) {
        JOptionPane.showMessageDialog(null,
                "Se importó el modelo de planta '" + areaId + "' desde '" + file.getName() + "'.",
                "Importación exitosa", JOptionPane.INFORMATION_MESSAGE);
    }

    private void avisarFallo(File file, String motivo) {
        JOptionPane.showMessageDialog(null,
                "No se pudo importar '" + file.getName() + "'.\n\n" + motivo,
                "Error de importación", JOptionPane.ERROR_MESSAGE);
    }
}