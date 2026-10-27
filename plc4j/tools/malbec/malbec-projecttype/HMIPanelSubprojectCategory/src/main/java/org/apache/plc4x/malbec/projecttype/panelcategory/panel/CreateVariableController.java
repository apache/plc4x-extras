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

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;
import javax.swing.RowFilter;
import javax.swing.table.TableModel;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.BootDbTagDynamicRepository;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.persistence.RebuildResults;
import org.apache.plc4x.malbec.projecttype.panelcategory.comms.plcopen.DynamicVariableXmlMapper;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DataType;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DynamicTag;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public final class CreateVariableController {

    private CreateVariableController() {
    }

    public record Row(
            int rowNumber,
            String uuid,
            String name,
            String deviceName,
            String dataType,
            String itemTag,
            String descriptor,
            String scanTime,
            boolean scanEnable,
            boolean writeEnable,
            String displayLimitLow,
            String displayLimitHigh,
            String displayDescription,
            String displayFormat,
            String displayUnits,
            String controlLimitLow,
            String controlLimitHigh,
            String controlMinStep) {

    }

    public record Result(boolean success, List<String> errors) {

        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result fail(List<String> errors) {
            return new Result(false, List.copyOf(errors));
        }
    }

    private record TagUse(int rowNumber, String deviceName, DynamicTag tag) {

    }

    public static String validate(Row row) {
        if (row == null) {
            return "There is no data to validate.";
        }
        if (isBlank(row.name())) {
            return "The variable name is missing.";
        }
        if (isBlank(row.deviceName())) {
            return "The connection/device needs to be selected.";
        }

        DataType type = DataType.findIgnoreCase(row.dataType());
        if (type == null || !type.isEnabled()) {
            return "Invalid data type: " + row.dataType();
        }

        try {
            DynamicTag tag = DynamicTag.parse(row.itemTag(), type);
            if (tag == null) {
                return "Invalid tag: " + row.itemTag();
            }
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }

        return null;
    }

    public static RowFilter<TableModel, Integer> buildRowFilter(String text, int... columns) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            if (columns == null || columns.length == 0) {
                return RowFilter.regexFilter("(?i)" + text);
            }
            return RowFilter.regexFilter("(?i)" + text, columns);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }

    public static Result saveVariables(String folderPath,
            List<DeviceConfigData> devices,
            List<Row> rows) {
        return saveVariables(folderPath, devices, rows, List.of());
    }

    public static Result saveVariables(String folderPath,
            List<DeviceConfigData> devices,
            List<Row> rows,
            Collection<String> deletedPvUuids) {

        if (isBlank(folderPath)) {
            return Result.fail(List.of("The project folder was not specified."));
        }

        boolean hasRows = rows != null && !rows.isEmpty();
        boolean hasDeletions = deletedPvUuids != null && !deletedPvUuids.isEmpty();

        if (!hasRows && !hasDeletions) {
            return Result.fail(List.of("There are no variables to save."));
        }
        if (hasRows && (devices == null || devices.isEmpty())) {
            return Result.fail(List.of("No devices are configured."));
        }

        List<Row> rowsToSave = rows == null ? List.of() : rows;
        List<DeviceConfigData> devicesToSave = devices == null ? List.of() : devices;

        List<String> errors = new ArrayList<>();
        List<PvConfigData> pvs = new ArrayList<>();
        List<TagUse> usedTags = new ArrayList<>();

        for (int i = 0; i < rowsToSave.size(); i++) {
            Row row = rowsToSave.get(i);
            String label = label(row, i);
            String error = validate(row);

            if (error != null) {
                errors.add(label + ": " + error);
                continue;
            }

            DataType type = DataType.findIgnoreCase(row.dataType());
            DynamicTag tag = DynamicTag.parse(row.itemTag(), type);
            TagUse repeated = findTagUse(usedTags, row.deviceName(), tag);

            if (repeated != null) {
                String detail = repeated.tag().address().equals(tag.address())
                        ? "is already used by" : "overlaps with";
                errors.add(label + ": tag " + tag.address() + " " + detail
                        + " the tag in row " + repeated.rowNumber()
                        + " of device '" + row.deviceName() + "'.");
                continue;
            }

            usedTags.add(new TagUse(row.rowNumber(), row.deviceName(), tag));
            pvs.add(toPv(row));
        }

        if (!errors.isEmpty()) {
            return Result.fail(errors);
        }

        Map<String, DeviceConfigData> devicesByName = new LinkedHashMap<>();
        for (DeviceConfigData device : devicesToSave) {
            if (device == null || isBlank(device.getDeviceName())) {
                continue;
            }
            devicesByName.putIfAbsent(device.getDeviceName(), device);
        }

        Map<String, List<PvConfigData>> pvsByDevice = new LinkedHashMap<>();
        for (PvConfigData pv : pvs) {
            if (!devicesByName.containsKey(pv.getDeviceName())) {
                errors.add("The device does not exist '" + pv.getDeviceName() + "'.");
                continue;
            }
            pvsByDevice
                    .computeIfAbsent(pv.getDeviceName(), key -> new ArrayList<>())
                    .add(pv);
        }

        if (!errors.isEmpty()) {
            return Result.fail(errors);
        }

        List<DeviceConfigData> uniqueDevices = new ArrayList<>(devicesByName.values());
        File folder = new File(folderPath);
        try {
            writeXml(folder, uniqueDevices, pvs);
        } catch (Exception ex) {
            String detail = ex.getMessage() == null
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            return Result.fail(List.of(
                    "The variables could not be saved to comunicacion.xml: " + detail));
        }

        RebuildResults rebuild = BootDbTagDynamicRepository.rebuildWithReason(
                new File(folderPath), uniqueDevices, pvs);

        if (!rebuild.success()) {
            return Result.fail(List.of(
                    "The variables could not be saved to boot.db: " + rebuild.reason()));
        }

        return Result.ok();
    }

    private static void writeXml(File folder, List<DeviceConfigData> devices,
            List<PvConfigData> pvs) throws Exception {
        FileObject folderObject = FileUtil.toFileObject(folder);
        if (folderObject == null) {
            DynamicVariableXmlMapper.write(folder, devices, pvs);
            return;
        }

        FileObject xmlFile = folderObject.getFileObject(DynamicVariableXmlMapper.FILE_NAME);
        if (xmlFile == null) {
            xmlFile = FileUtil.createData(folderObject, DynamicVariableXmlMapper.FILE_NAME);
        }
        try (FileLock lock = xmlFile.lock();
                OutputStream out = xmlFile.getOutputStream(lock)) {
            DynamicVariableXmlMapper.write(out, devices, pvs);
        }
    }

    private static TagUse findTagUse(List<TagUse> usedTags, String deviceName, DynamicTag tag) {
        for (TagUse used : usedTags) {
            if (used.deviceName().equals(deviceName) && used.tag().overlaps(tag)) {
                return used;
            }
        }
        return null;
    }

    private static PvConfigData toPv(Row row) {
        DataType type = DataType.findIgnoreCase(row.dataType());

        String uuid = isBlank(row.uuid())
                ? UUID.randomUUID().toString()
                : row.uuid();

        return new PvConfigData(
                uuid,
                row.name(),
                type.label(),
                row.deviceName(),
                row.itemTag(),
                row.descriptor(),
                row.scanTime(),
                row.scanEnable(),
                row.writeEnable(),
                row.displayLimitLow(),
                row.displayLimitHigh(),
                row.displayDescription(),
                row.displayFormat(),
                row.displayUnits(),
                row.controlLimitLow(),
                row.controlLimitHigh(),
                row.controlMinStep(),
                "");
    }

    private static String label(Row row, int index) {
        int number = row != null && row.rowNumber() > 0
                ? row.rowNumber()
                : index + 1;
        return "Row " + number;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
