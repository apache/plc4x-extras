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
package org.apache.plc4x.malbec.projecttype.panelcategory;

import java.util.ArrayList;
import java.util.List;
import org.openide.filesystems.FileObject;

final class FileObjectTree implements ProjectTree {

    private final FileObject file;

    FileObjectTree(FileObject file) {
        this.file = file;
    }

    static ProjectTree fromFileObject(FileObject file) {
        return file == null ? null : new FileObjectTree(file);
    }

    @Override
    public String name() {
        return file.getNameExt();
    }

    @Override
    public boolean isFolder() {
        return file.isFolder();
    }

    @Override
    public List<ProjectTree> children() {
        if (!file.isFolder()) {
            return List.of();
        }
        List<ProjectTree> children = new ArrayList<>();
        for (FileObject hijo : file.getChildren()) {
            if (hijo != null) {
                children.add(new FileObjectTree(hijo));
            }
        }
        return children;
    }

    @Override
    public String toString() {
        return file.getPath();
    }
}
