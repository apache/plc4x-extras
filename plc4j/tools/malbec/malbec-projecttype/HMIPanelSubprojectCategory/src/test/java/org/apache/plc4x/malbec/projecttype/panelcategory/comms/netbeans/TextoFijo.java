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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.netbeans;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;

/**
 * Almacen en memoria que devuelve siempre el mismo texto.
 *
 * <p>Existia como clase privada dentro de {@code HMICommunicationModelIntegrityTest}; al
 * necesitarlo tambien la busqueda del repositorio (T3.9) paso a fichero propio, para no
 * duplicarlo otra vez.
 */
final class TextoFijo implements CommConfigStorage {

    private final String texto;
    private String escrito;

    TextoFijo(String texto) {
        this.texto = texto;
    }

    @Override
    public String open() {
        return texto;
    }

    @Override
    public OutputStream create() {
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                // El ByteArrayOutputStream no necesita cerrarse; se traga y se guarda.
                escrito = this.toString();
            }
        };
    }

    /** Ultimo contenido escrito, para comprobar que un guardado llego al almacen. */
    String escrito() {
        return escrito;
    }
}