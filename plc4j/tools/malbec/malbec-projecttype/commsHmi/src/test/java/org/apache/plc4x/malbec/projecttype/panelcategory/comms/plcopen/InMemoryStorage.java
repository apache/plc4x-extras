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
package org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.api.CommConfigStorage;

final class InMemoryStorage implements CommConfigStorage {

    private String text = "";

    String text() {
        return text;
    }

    @Override
    public String open() {
        return text;
    }

    @Override
    public OutputStream create() {
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                text = new String(toByteArray(), StandardCharsets.UTF_8);
            }
        };
    }
}