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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;

public record ProjectFileStorage(FileObject fileObject) implements CommConfigStorage {

    @Override
    public String open() throws IOException {
        if (!fileObject.isValid()) {
            return "";
        }
        try (InputStream in = fileObject.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public OutputStream create() throws IOException {
        return fileObject.getOutputStream();
    }

    @Override
    public void write(byte[] contenido) throws IOException {
        try (FileLock lock = fileObject.lock();
                OutputStream out = fileObject.getOutputStream(lock)) {
            out.write(contenido);
        }
    }
}