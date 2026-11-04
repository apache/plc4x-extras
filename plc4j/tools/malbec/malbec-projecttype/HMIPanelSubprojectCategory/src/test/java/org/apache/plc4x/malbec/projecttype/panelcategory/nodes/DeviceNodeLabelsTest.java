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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Properties;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.junit.jupiter.api.Test;

class DeviceNodeLabelsTest {

    @Test
    void withoutNameReturnsTheSubstituteText() {
        assertEquals("(unnamed)", DeviceNodeLabels.nameOf(null));
        assertEquals("(unnamed)", DeviceNodeLabels.nameOf(device("")));
        assertEquals("(unnamed)", DeviceNodeLabels.nameOf(device("   ")));
    }

    @Test
    void withNameReturnsTheName() {
        assertEquals("Compresor 1", DeviceNodeLabels.nameOf(device("Compresor 1")));
    }

    @Test
    void tooltipIdentifiesBrandModelAndProtocol() {
        DeviceConfigData d = propertiesDevice("Compresor 1", "Siemens", "S7-1500", "S7");

        assertEquals("Siemens / S7-1500 / S7", DeviceNodeLabels.tooltipOf(d));
    }

    @Test
    void tooltipDoesNotBreakIfAFieldIsMissing() {
        DeviceConfigData d = propertiesDevice("PLC", null, "S7-1200", null);

        assertEquals("null / S7-1200 / null",
                DeviceNodeLabels.tooltipOf(d),
                "previously it was concatenated as-is; these tests lock that behavior");
    }

    @Test
    void missingTextPropertyComesOutEmptyNotNull() {
        DeviceConfigData d = device("PLC");

        assertEquals("", DeviceNodeLabels.textOf(null, x -> "something"),
                "without a device the property is shown empty, not as null");
        assertEquals("", DeviceNodeLabels.textOf(d, x -> null));
        assertEquals("PLC", DeviceNodeLabels.textOf(d, DeviceConfigData::getDeviceName));
    }

    @Test
    void booleanPropertyWithoutDeviceIsFalse() {
        assertFalse(DeviceNodeLabels.booleanOf(null, x -> true));
        assertTrue(DeviceNodeLabels.booleanOf(device("PLC"), x -> true));
        assertFalse(DeviceNodeLabels.booleanOf(device("PLC"), x -> null));
    }

    @Test
    void deletionMessagePicksSingularOrPluralByCount() {
        CommConfigData oneGroup = communication(1, 1, 1);
        String text = DeviceNodeLabels.deletionMessage("Compresor 1", oneGroup);

        assertTrue(text.contains("1 group\n"), "one group is singular: " + text);
        assertFalse(text.contains("1 groups"), "it cannot say '1 groups': " + text);
        assertTrue(text.contains("1 area"), "one area is singular: " + text);
        assertFalse(text.contains("1 areas"), "it cannot say '1 areas': " + text);
        assertTrue(text.contains("1 variable\n"), "one variable is singular: " + text);
        assertFalse(text.contains("1 variables"), "it cannot say '1 variables': " + text);
    }

    @Test
    void withSeveralItemsItIsPlural() {
        String text = DeviceNodeLabels.deletionMessage("Compresor 1", communication(3, 2, 7));

        assertTrue(text.contains("3 groups"), text);
        assertTrue(text.contains("2 areas"), text);
        assertTrue(text.contains("7 variables"), text);
    }

    @Test
    void deviceWithNothingToLoseDoesNotListZeros() {
        String text = DeviceNodeLabels.deletionMessage("PLC", communication(0, 0, 0));

        assertFalse(text.contains("  \u2022 0 groups"), "listing zeros makes no sense: " + text);
        assertTrue(text.contains("You are about to delete the device 'PLC'."), text);
        assertTrue(text.contains("Do you want to continue?"), text);
    }

    @Test
    void deletionMessageWarnsThatBootDbIsAlsoTouched() {
        String text = DeviceNodeLabels.deletionMessage("PLC", communication(1, 0, 0));

        assertTrue(text.contains("comunicacion.xml"), text);
        assertTrue(text.contains("boot.db"), text);
        assertTrue(text.contains("Deleting it will discard all its settings."), text);
    }

    @Test
    void deletionMessageHandlesNullComms() {
        String text = DeviceNodeLabels.deletionMessage("PLC", null);

        assertTrue(text.contains("You are about to delete the device 'PLC'."), text);
        assertFalse(text.contains("\u2022"), "without communication there is nothing to list: " + text);
    }

    @Test
    void warningMessagesNameTheDevice() {
        assertTrue(DeviceNodeLabels.unknownDeviceMessage("PLC")
                .contains("'PLC'"));
        assertTrue(DeviceNodeLabels.partialSaveMessage("PLC").contains("boot.db"));
        assertTrue(DeviceNodeLabels.correctDeletionMessage("PLC")
                .contains("'PLC' was deleted successfully"));
        assertTrue(DeviceNodeLabels.nothingToDeleteMessage("PLC")
                .contains("'PLC' was not found"));
    }

    @Test
    void deviceWithoutCommunicationHasNoVariables() {
        assertEquals(0, DeviceNodeLabels.variableCount((CommConfigData) null));
        assertFalse(DeviceNodeLabels.hasVariables((CommConfigData) null));
    }

    @Test
    void communicationWithoutVariablesDoesNotShowTheVariableNode() {
        CommConfigData empty = communication(0, 0, 0);

        assertEquals(0, DeviceNodeLabels.variableCount(empty));
        assertFalse(DeviceNodeLabels.hasVariables(empty));
    }

    @Test
    void communicationWithVariablesShowsTheVariableNode() {
        CommConfigData one = communication(0, 0, 1);
        CommConfigData several = communication(1, 2, 7);

        assertEquals(1, DeviceNodeLabels.variableCount(one));
        assertTrue(DeviceNodeLabels.hasVariables(one));
        assertEquals(7, DeviceNodeLabels.variableCount(several));
        assertTrue(DeviceNodeLabels.hasVariables(several));
    }

    @Test
    void variableTooltipUsesSingularAndPlural() {
        assertEquals("1 variable", DeviceNodeLabels.variablesTooltip(communication(0, 0, 1)));
        assertEquals("7 variables", DeviceNodeLabels.variablesTooltip(communication(0, 0, 7)));
    }

    @Test
    void xmlVariablesDriveTheVariableNode() {
        assertFalse(DeviceNodeLabels.hasVariables(List.<PvConfigData>of()));
        assertTrue(DeviceNodeLabels.hasVariables(variableList(1)));
        assertEquals(3, DeviceNodeLabels.variableCount(variableList(3)));
        assertEquals("3 variables", DeviceNodeLabels.variablesTooltip(variableList(3)));
    }

    private static DeviceConfigData device(String name) {
        return propertiesDevice(name, null, null, null);
    }

    private static DeviceConfigData propertiesDevice(String name, String brand, String model,
            String protocol) {
        Properties p = new Properties();
        put(p, "deviceName", name);
        put(p, "brand", brand);
        put(p, "model", model);
        put(p, "protocol", protocol);
        return new DeviceConfigData(p);
    }

    private static void put(Properties p, String key, String value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    private static List<PvConfigData> variableList(int variables) {
        List<PvConfigData> pvs = new java.util.ArrayList<>();
        for (int i = 0; i < variables; i++) {
            pvs.add(new PvConfigData("p" + i, "P" + i, "INT", "PLC", "%DB1.DBB0:INT",
                    "d", "500", true, true, "0", "1", "d", "%.2f", "u", "0", "1", "1", "m"));
        }
        return pvs;
    }

    private static CommConfigData communication(int groups, int areas, int variables) {
        List<CommConfigData.GroupConfig> groupList = new java.util.ArrayList<>();
        for (int i = 0; i < groups; i++) {
            groupList.add(new CommConfigData.GroupConfig("g" + i, "G" + i, "d", "500", true, "m"));
        }
        List<CommConfigData.ItemConfig> areaList = new java.util.ArrayList<>();
        for (int i = 0; i < areas; i++) {
            areaList.add(new CommConfigData.ItemConfig("i" + i, "A" + i, "d", "t" + i, true, "m", "g0"));
        }
        List<CommConfigData.PvConfig> pvList = new java.util.ArrayList<>();
        for (int i = 0; i < variables; i++) {
            pvList.add(new CommConfigData.PvConfig("p" + i, "P" + i, "INT", "A0", "0", "d",
                    "500", true, true, "0", "1", "d", "%.2f", "u", "0", "1", "1", "m", ""));
        }
        return new CommConfigData("PLC", groupList, areaList, pvList);
    }
}