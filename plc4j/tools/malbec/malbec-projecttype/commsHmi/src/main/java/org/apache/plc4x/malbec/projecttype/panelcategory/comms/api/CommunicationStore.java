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

import java.util.List;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

public interface CommunicationStore {

    List<DeviceConfigData> getDevices();

    DeviceConfigData findByUuid(String uuid);

    CommConfigData getComms(String deviceUuid);

    void upsertDevice(DeviceConfigData device);

    void upsertComms(String deviceUuid, CommConfigData config);

    boolean removeDevice(String uuid);

    SaveResult save();

    void addChangeListener(ChangeListener listener);

    void removeChangeListener(ChangeListener listener);
}
