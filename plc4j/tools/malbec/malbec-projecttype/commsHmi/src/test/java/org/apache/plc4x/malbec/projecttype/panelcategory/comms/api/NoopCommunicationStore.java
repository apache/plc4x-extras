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

import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

public class NoopCommunicationStore implements CommunicationStore {

    @Override
    public List<DeviceConfigData> getDevices() {
        return Collections.emptyList();
    }

    @Override
    public DeviceConfigData findByUuid(String uuid) {
        return null;
    }

    @Override
    public CommConfigData getComms(String deviceUuid) {
        return null;
    }

    @Override
    public void upsertDevice(DeviceConfigData device) {
    }

    @Override
    public void upsertComms(String deviceUuid, CommConfigData config) {
    }

    @Override
    public boolean removeDevice(String uuid) {
        return false;
    }

    @Override
    public SaveResult save() {
        return SaveResult.exito();
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
    }
}
