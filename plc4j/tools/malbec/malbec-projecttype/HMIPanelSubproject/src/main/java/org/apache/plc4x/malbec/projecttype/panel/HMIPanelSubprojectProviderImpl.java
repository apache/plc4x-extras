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
package org.apache.plc4x.malbec.projecttype.panel;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.SubprojectProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

public class HMIPanelSubprojectProviderImpl implements SubprojectProvider{
    
    public static final String PANEL_SUBPROJECT_DIRECTORY = "panel";
    private final Project project;

    public HMIPanelSubprojectProviderImpl(Project project) {
        this.project = project;
    }

    @Override
    public Set<? extends Project> getSubprojects() {
        return loadProjects(project.getProjectDirectory());
    }

    /**
     * No notifica cambios, y es una decision y no un olvido.
     *
     * <p>El contrato de {@link SubprojectProvider} pide avisar cuando la lista de
     * subproyectos cambia. Aqui no se avisa porque <strong>la lista no cambia sin que el
     * proyecto cambie</strong>: los subproyectos son las carpetas de disco del proyecto, y
     * quien las anade o las quita lo hace desde fuera, a traves del sistema de ficheros,
     * que ya tiene su propio aviso.
     *
     * <p>Implementar la notificacion aqui sin avisar de nada seria peor: NetBeans
     * preguntaria por cambios que no existen y se repintaria el arbol sin motivo. Si algun
     * dia los subproyectos pasan guardarse en memoria, hay que implementar esto; mientras
     * se lean del disco, no hace falta.
     */
    @Override
    public void addChangeListener(ChangeListener cl) {
    }

    /** Ver {@link #addChangeListener(ChangeListener)}: no hay cambios que notificar. */
    @Override
    public void removeChangeListener(ChangeListener cl) {
    }

    private Set<Project> loadProjects(FileObject dir) {
        Set<Project> result = new LinkedHashSet<>();

        for (FileObject child : dir.getChildren()) {
            if (!child.isFolder()) {
                continue;
            }
            try {
                Project p = ProjectManager.getDefault().findProject(child);
                if (p instanceof HMIPanelSubprojectImpl) {
                    result.add((HMIPanelSubprojectImpl) p);
                }
            } catch (IOException | IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        return Collections.unmodifiableSet(result);
    }
}
