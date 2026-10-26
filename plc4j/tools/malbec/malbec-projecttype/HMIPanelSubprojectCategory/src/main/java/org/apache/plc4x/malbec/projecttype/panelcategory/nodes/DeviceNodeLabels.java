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
package org.apache.plc4x.malbec.projecttype.panelcategory.nodes;

import java.util.function.Function;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;

final class DeviceNodeLabels {

    static final String UNNAMED = "(unnamed)";

    private DeviceNodeLabels() {
    }

    static String nameOf(DeviceConfigData device) {
        if (device == null) {
            return UNNAMED;
        }
        String name = device.getDeviceName();
        return name == null || name.isBlank() ? UNNAMED : name;
    }

    static String tooltipOf(DeviceConfigData device) {
        return device.getBrand() + " / " + device.getModel()
                + " / " + device.getProtocol();
    }

    static String textOf(DeviceConfigData device, Function<DeviceConfigData, String> getter) {
        String value = device == null ? null : getter.apply(device);
        return value == null ? "" : value;
    }

    static boolean booleanOf(DeviceConfigData device,
            Function<DeviceConfigData, Boolean> getter) {
        return device != null && Boolean.TRUE.equals(getter.apply(device));
    }

    /**
     * @param name device name, already resolved by {@link #nameOf}
     * @param communication its communication, or null if it has none
     */
    static String deletionMessage(String name, CommConfigData communication) {
        StringBuilder text = new StringBuilder("You are about to delete the device '")
                .append(name).append("'.")
                .append("\n\nDeleting it will discard all its settings.");
        if (communication != null) {
            int groups = communication.getGroups().size();
            int areas = communication.getItems().size();
            int variables = communication.getPvs().size();
            if (groups > 0 || areas > 0 || variables > 0) {
                text.append("\n\n  \u2022 ").append(groups)
                        .append(groups == 1 ? " group" : " groups")
                        .append("\n  \u2022 ").append(areas)
                        .append(areas == 1 ? " area" : " areas")
                        .append("\n  \u2022 ").append(variables)
                        .append(variables == 1 ? " variable" : " variables");
            }
        }
        return text.append("\n\nIt will also be removed from comunicacion.xml and boot.db.")
                .append("\n\nDo you want to continue?").toString();
    }

    static String unknownDeviceMessage(String name) {
        return "The device '" + name + "' is no longer in the configuration.\n"
                + "It may have been deleted from another view.";
    }

    static String partialSaveMessage(String name) {
        return "The device '" + name + "' was removed from comunicacion.xml, but boot.db\n"
                + "could not be updated. Check the console.";
    }

    static String correctDeletionMessage(String name) {
        return "The device '" + name + "' was deleted successfully.";
    }

    static String nothingToDeleteMessage(String name) {
        return "The device '" + name + "' was not found.";
    }
}
