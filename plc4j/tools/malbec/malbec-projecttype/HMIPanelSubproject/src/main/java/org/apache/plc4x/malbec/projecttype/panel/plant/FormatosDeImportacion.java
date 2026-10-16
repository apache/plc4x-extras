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
import java.util.concurrent.atomic.AtomicBoolean;
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

    public static String extensionOf(File file) {
        String name = file.getName();
        int punto = name.lastIndexOf('.');
        return punto < 0 || punto == name.length() - 1
                ? "" : name.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean extensionAdmitida(File file) {
        return FORMATOS.containsKey(extensionOf(file));
    }

    public static String allowedFormats() {
        return String.join(" o ", FORMATOS.values());
    }

    public static FilenameFilter fileFilter() {
        return (directorio, name) -> new File(directorio, name).isDirectory()
                || FORMATOS.containsKey(extensionOf(new File(name)));
    }

    public static Set<String> formatsWithoutProvider(
            Collection<? extends S88RepositoryProvider> providers, Collection<String> declarados) {
        Set<String> sinProvider = new TreeSet<>(declarados);
        for (S88RepositoryProvider provider : providers) {
            sinProvider.removeIf(provider::accepts);
        }
        return sinProvider;
    }

    private static final AtomicBoolean AVISO_DADO = new AtomicBoolean();

    public static boolean warnMisalignedFormats() {
        if (!AVISO_DADO.compareAndSet(false, true)) {
            return false;
        }
        Set<String> sinProvider = formatsWithoutProvider(
                Lookup.getDefault().lookupAll(S88RepositoryProvider.class),
                FORMATOS.keySet());
        if (!sinProvider.isEmpty()) {
            
LOGGER.log(Level.WARNING,
                    "Se declaran formatos de modelo de planta que ningun S88RepositoryProvider"
                            + " acepta: {0}",
                    sinProvider);
        }
        return true;
    }
}