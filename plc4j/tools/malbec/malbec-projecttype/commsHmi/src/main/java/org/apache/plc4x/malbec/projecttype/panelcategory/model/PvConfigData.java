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
package org.apache.plc4x.malbec.projecttype.panelcategory.model;

public record PvConfigData(String uuid, String name, String type, String deviceName, String itemTag,
        String descriptorText, String scanTime, boolean scanEnable, boolean writeEnable,
        String displayLimitLow, String displayLimitHigh, String displayDescription,
        String displayFormat, String displayUnits, String controlLimitLow,
        String controlLimitHigh, String controlMinStep, String md5) {

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
    public String getDeviceName() {
        return deviceName;
    }

    public String getItemTag() {
        return itemTag;
    }

    public String getDescriptorText() {
        return descriptorText;
    }

    public String getScanTime() {
        return scanTime;
    }

    public String getScanTimeWithoutUnit() {
        String value = scanTime == null ? "" : scanTime.trim();
        if (value.length() >= 2
                && value.regionMatches(true, value.length() - 2, "ms", 0, 2)) {
            value = value.substring(0, value.length() - 2).trim();
        }
        return value;
    }

    public boolean isScanEnable() {
        return scanEnable;
    }

    public boolean isWriteEnable() {
        return writeEnable;
    }

    public String getDisplayLimitLow() {
        return displayLimitLow;
    }

    public String getDisplayLimitHigh() {
        return displayLimitHigh;
    }

    public String getDisplayDescription() {
        return displayDescription;
    }

    public String getDisplayFormat() {
        return displayFormat;
    }

    public String getDisplayUnits() {
        return displayUnits;
    }

    public String getControlLimitLow() {
        return controlLimitLow;
    }

    public String getControlLimitHigh() {
        return controlLimitHigh;
    }

    public String getControlMinStep() {
        return controlMinStep;
    }

    public String getMd5() {
        return md5;
    }

    public PvConfigData withUuid(String uuid) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withName(String name) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withType(String type) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDeviceName(String deviceName) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withItemTag(String itemTag) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDescriptorText(String descriptorText) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withScanTime(String scanTime) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withScanEnable(boolean scanEnable) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withWriteEnable(boolean writeEnable) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDisplayLimitLow(String displayLimitLow) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDisplayLimitHigh(String displayLimitHigh) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDisplayDescription(String displayDescription) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDisplayFormat(String displayFormat) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withDisplayUnits(String displayUnits) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withControlLimitLow(String controlLimitLow) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withControlLimitHigh(String controlLimitHigh) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withControlMinStep(String controlMinStep) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

    public PvConfigData withMd5(String md5) {
        return new PvConfigData(uuid, name, type, deviceName, itemTag, descriptorText, scanTime,
                scanEnable, writeEnable, displayLimitLow, displayLimitHigh, displayDescription,
                displayFormat, displayUnits, controlLimitLow, controlLimitHigh, controlMinStep,
                md5);
    }

}
