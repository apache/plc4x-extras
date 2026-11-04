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

import java.util.Objects;

/**
 * @param xmlSaved         whether {@code comunicacion.xml} was written
 * @param databaseUpdated  whether {@code boot.db} was rebuilt
 * @param message          explaining why not, empty if everything went well
 */
public record SaveResult(boolean xmlSaved, boolean databaseUpdated, String message) {

    private static final SaveResult success = new SaveResult(true, true, "");

    public SaveResult {
        Objects.requireNonNull(message, "el mensaje no puede ser null");
        if (!isComplete2(xmlSaved, databaseUpdated) && message.isBlank()) {
            throw new IllegalArgumentException(
                    "un guardado que no fue completo necesita un motivo: sin el, el aviso "
                            + "no le diria nada al usuario");
        }
    }

    private static boolean isComplete2(boolean xml, boolean db) {
        return xml && db;
    }

    /** El guardado fue bien: XML y base de datos. */
    public static SaveResult success() {
        return success;
    }

    public static SaveResult partial(String message) {
        return new SaveResult(true, false, Objects.requireNonNull(message,
                "un guardado parcial necesita un motivo: si no, el aviso no dice nada"));
    }

    public static SaveResult notSaved(String message) {
        return new SaveResult(false, false, Objects.requireNonNull(message,
                "un guardado rechazado necesita un motivo: si no, el usuario no sabe que "
                        + "se ha perdido su trabajo"));
    }

    public boolean isComplete() {
        return xmlSaved && databaseUpdated;
    }

    public boolean isPartial() {
        return xmlSaved && !databaseUpdated;
    }
}