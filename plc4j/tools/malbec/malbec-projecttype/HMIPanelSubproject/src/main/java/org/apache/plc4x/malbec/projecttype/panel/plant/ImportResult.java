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
package org.apache.plc4x.malbec.projecttype.panel.plant;

import java.io.File;

/**
 * Como termino una importacion de planta.
 *
 * <p>Es un tipo, no un booleano con un mensaje al lado, porque los dos casos llevan
 * informacion distinta y quien lo recibe decide el texto: el exito dice que area se
 * importo, el fallo dice por que no se pudo.
 */
public sealed interface ImportResult {

    File archivo();

    /** El modelo se importo y quedo guardado con este identificador de area. */
    record Exito(File archivo, String areaId) implements ImportResult {
    }

    /** No se importo nada, y {@code motivo} esta escrito para que lo lea el operador. */
    record Fallo(File archivo, String motivo) implements ImportResult {
    }
}