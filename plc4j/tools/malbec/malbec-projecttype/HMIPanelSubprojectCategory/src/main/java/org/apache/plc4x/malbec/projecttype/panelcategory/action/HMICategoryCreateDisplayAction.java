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
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.apache.plc4x.malbec.projecttype.panelcategory.panel.CreateDisplay;
import org.netbeans.api.project.Project;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;


public class HMICategoryCreateDisplayAction extends AbstractAction implements ContextAwareAction {

    private final static String FILE_NAME_DISPLAY_EXT = "bob";
    public final static String DISPLAY_TEMPLATE_PATH =
            "org/apache/plc4x/malbec/projecttype/panelcategory/ftype/DisplayTemplate.bob";
    private Project project;

    public HMICategoryCreateDisplayAction(Project project) {
        super("Crear pantalla");
        this.project = project;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        FileObject targetFolder = project.getProjectDirectory();

        CreateDisplay cd = new CreateDisplay();

        DialogDescriptor descriptor = new DialogDescriptor(
                cd,
                "Crear Display",
                true,
                new Object[0],
                null,
                DialogDescriptor.DEFAULT_ALIGN,
                null,
                null
        );

        DialogDisplayer.getDefault().notify(descriptor);

        if (cd.isConfirmed()) {
            String fileName = cd.getFileName();
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream(DISPLAY_TEMPLATE_PATH)) {

                if (in == null) {
                    throw new IOException("No se encontro la plantilla " + DISPLAY_TEMPLATE_PATH);
                }

                FileObject newFile = targetFolder.createData(fileName, FILE_NAME_DISPLAY_EXT);

                try (OutputStream out = newFile.getOutputStream()) {
                    in.transferTo(out);
                }

            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                JOptionPane.showMessageDialog(null,
                        "No se pudo crear la pantalla: " + ex.getMessage(),
                        "Crear pantalla", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return null;
    }
    

}
