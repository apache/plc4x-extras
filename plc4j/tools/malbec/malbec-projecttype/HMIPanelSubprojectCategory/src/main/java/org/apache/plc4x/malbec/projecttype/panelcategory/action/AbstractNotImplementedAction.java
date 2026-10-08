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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import javax.swing.Action;

/**
 * Base de las acciones de categoria que todavia no hacen nada.
 *
 * <p>Cumplen K-07: antes cada una imprimia "Accion ejecutada sobre: X" en la consola. Eso es
 * lo peor de las tres opciones: el usuario pulsa la accion, no ocurre nada visible, y solo se
 * entera si abre la consola del IDE. Al menos avisarle de que la funcion sigue sin existir le
 * dice la verdad.
 *
 * <p>Las cuatro acciones que heredan de aqui estan registradas en el menu contextual de sus
 * ficheros, asi que no se pueden eliminar sin quitar una entrada del menu que hoy existe.
 *
 * <p>El aviso es estatico a proposito: {@link NotifyDescriptor} y {@link DialogDisplayer} son
 * de la plataforma, y esta clase no se puede comprobar en el build. Si se maken, por ejemplo
 * con una ventana principal null, el clic no lanza excepcion; se registra y no se avisa.
 */
public abstract class AbstractNotImplementedAction extends AbstractAction {

    private static final Logger LOGGER =
            Logger.getLogger(AbstractNotImplementedAction.class.getName());

    private final FileObject fileObject;

    protected AbstractNotImplementedAction(String name, FileObject fileObject) {
        super(name);
        this.fileObject = fileObject;
    }

    /** Fichero sobre el que se ha ejecutado la accion. */
    protected final FileObject fileObject() {
        return fileObject;
    }

    /** Lo que le dice la accion al usuario al pulsarla. */
    public String mensajeDeNoImplementado() {
        String nombre = getValue(Action.NAME) == null ? getClass().getSimpleName()
                : String.valueOf(getValue(Action.NAME));
        return "La acción '" + nombre + "' todavía no está implementada.";
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String mensaje = mensajeDeNoImplementado();
        LOGGER.log(Level.FINE, "{0} sobre {1}",
                new Object[]{mensaje, fileObject == null ? null : fileObject.getNameExt()});
        try {
            DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(mensaje, NotifyDescriptor.WARNING_MESSAGE));
        } catch (RuntimeException ex) {
            // Sin ventana principal, o en un contexto donde no se puede mostrar un dialogo.
            // El mensaje ya ha quedado en el log, que es lo unico que se puede hacer aqui.
            LOGGER.log(Level.FINE, "No se pudo mostrar el aviso de accion no implementada", ex);
        }
    }
}
