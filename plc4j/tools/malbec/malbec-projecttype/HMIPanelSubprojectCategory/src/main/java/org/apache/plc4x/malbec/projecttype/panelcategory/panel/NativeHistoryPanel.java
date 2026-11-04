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
package org.apache.plc4x.malbec.projecttype.panelcategory.panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.modules.ModuleInfo;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

final class NativeHistoryPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(NativeHistoryPanel.class.getName());
    private static final String HISTORY_COMPONENT_CLASS =
            "org.netbeans.modules.versioning.ui.history.HistoryComponent";
    private static final String[] HISTORY_MODULES = {
        "org.netbeans.modules.versioning.ui",
        "org.netbeans.modules.localhistory"
    };

    private final Object history;
    private final Method componentClosed;

    private NativeHistoryPanel(Object history, Method componentClosed) {
        super(new BorderLayout());
        this.history = history;
        this.componentClosed = componentClosed;

        JComponent toolbar = invoke(history, "getToolbarRepresentation", JComponent.class);
        if (toolbar != null) {
            add(toolbar, BorderLayout.NORTH);
        }
        add((Component) history, BorderLayout.CENTER);
    }

    static NativeHistoryPanel create(FileObject xmlFile) {
        if (xmlFile == null || !xmlFile.isValid()) {
            return null;
        }

        ClassLoader loader = findClassLoader();
        if (loader == null) {
            LOGGER.fine("The versioning UI module is not loaded");
            return null;
        }

        try {
            DataObject dataObject = DataObject.find(xmlFile);
            Class<?> type = Class.forName(HISTORY_COMPONENT_CLASS, true, loader);
            Object history = type.getConstructor(Lookup.class)
                    .newInstance(Lookups.singleton(dataObject));
            type.getMethod("componentOpened").invoke(history);
            Method close = type.getMethod("componentClosed");
            return new NativeHistoryPanel(history, close);
        } catch (IOException | ReflectiveOperationException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "The native history component is not available", ex);
            return null;
        }
    }

    void close() {
        try {
            componentClosed.invoke(history);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "The native history component could not be closed", ex);
        }
    }

    private static ClassLoader findClassLoader() {
        for (String module : HISTORY_MODULES) {
            for (ModuleInfo info : Lookup.getDefault().lookupAll(ModuleInfo.class)) {
                if (module.equals(info.getCodeNameBase())) {
                    try {
                        ClassLoader loader = info.getClassLoader();
                        if (loader != null) {
                            return loader;
                        }
                    } catch (RuntimeException ex) {
                        LOGGER.log(Level.FINE, "Could not obtain the class loader of " + module, ex);
                    }
                }
            }
        }
        return null;
    }

    private static <T> T invoke(Object target, String method, Class<T> type) {
        try {
            return type.cast(target.getClass().getMethod(method).invoke(target));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Could not call " + method, ex);
            return null;
        }
    }
}
