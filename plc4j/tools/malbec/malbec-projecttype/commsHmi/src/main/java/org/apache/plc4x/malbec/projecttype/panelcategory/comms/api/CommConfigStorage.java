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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.api;

import java.io.OutputStream;

public interface CommConfigStorage {

    String open() throws Exception;

    OutputStream create() throws Exception;

    /**
     * Reemplaza el contenido guardado por {@code contenido}, ya completo.
     *
     * <p>Existe para que quien guarda pueda serializar entero antes de tocar el destino.
     * Con {@link #create()} el fichero se vacia en cuanto se pide el flujo, asi que un
     * fallo durante la serializacion deja el archivo vacio y se pierde lo que habia.
     *
     * <p>Quien implemente esta interfaz deberia reemplazar el contenido de una vez: un
     * fallo a mitad de escritura no deberia dejar el archivo a medias.
     *
     * <p>La implementacion por defecto mantiene el comportamiento de siempre para no
     * romper a los implementadores existentes; los que puedan conviene sobrescribirla.
     *
     * @param contenido los bytes completos a guardar
     */
    default void write(byte[] contenido) throws Exception {
        try (OutputStream out = create()) {
            out.write(contenido);
        }
    }
}