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
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.plc4x.malbec.s88.api.S88RepositoryProvider;
import org.openide.util.Lookup;

public final class FormatosDeImportacion {

    private static final Logger LOGGER = Logger.getLogger(FormatosDeImportacion.class.getName());
    public static final Map<String, String> FORMATOS =
            Map.of("xml", "Modelo de planta (*.xml)");

    private FormatosDeImportacion() {
    }

    public static String extensionDe(File archivo) {
        String nombre = archivo.getName();
        int punto = nombre.lastIndexOf('.');
        return punto < 0 || punto == nombre.length() - 1
                ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean extensionAdmitida(File archivo) {
        return FORMATOS.containsKey(extensionDe(archivo));
    }

    public static String formatosAdmitidos() {
        return String.join(" o ", FORMATOS.values());
    }

    public static FilenameFilter filtroDeArchivo() {
        return (directorio, nombre) -> new File(directorio, nombre).isDirectory()
                || FORMATOS.containsKey(extensionDe(new File(nombre)));
    }

    public static Set<String> formatosSinProvider(
            Collection<? extends S88RepositoryProvider> providers, Collection<String> declarados) {
        Set<String> sinProvider = new TreeSet<>(declarados);
        for (S88RepositoryProvider provider : providers) {
            sinProvider.removeIf(provider::accepts);
        }
        return sinProvider;
    }

    public static void avisarFormatosDesalineados() {
        Set<String> sinProvider = formatosSinProvider(
                Lookup.getDefault().lookupAll(S88RepositoryProvider.class),
                FORMATOS.keySet());
        if (!sinProvider.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "Se declaran formatos de modelo de planta que ningun S88RepositoryProvider"
                            + " acepta: {0}",
                    sinProvider);
        }
    }
}