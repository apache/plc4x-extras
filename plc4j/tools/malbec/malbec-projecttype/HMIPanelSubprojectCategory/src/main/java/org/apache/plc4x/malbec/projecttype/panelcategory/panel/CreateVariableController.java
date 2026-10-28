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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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

        public Row withRowNumber(int newRowNumber) {
            return new Row(newRowNumber, uuid, name, deviceName, dataType, itemTag,
                    descriptor, scanTime, scanEnable, writeEnable, displayLimitLow,
                    displayLimitHigh, displayDescription, displayFormat, displayUnits,
                    controlLimitLow, controlLimitHigh, controlMinStep);
        }

        public Row withUuid(String newUuid) {
            return new Row(rowNumber, newUuid, name, deviceName, dataType, itemTag,
                    descriptor, scanTime, scanEnable, writeEnable, displayLimitLow,
                    displayLimitHigh, displayDescription, displayFormat, displayUnits,
                    controlLimitLow, controlLimitHigh, controlMinStep);
        }

        public Row withName(String newName) {
            return new Row(rowNumber, uuid, newName, deviceName, dataType, itemTag,
                    descriptor, scanTime, scanEnable, writeEnable, displayLimitLow,
                    displayLimitHigh, displayDescription, displayFormat, displayUnits,
                    controlLimitLow, controlLimitHigh, controlMinStep);
        }

        public Row withItemTag(String newItemTag) {
            return new Row(rowNumber, uuid, name, deviceName, dataType, newItemTag,
                    descriptor, scanTime, scanEnable, writeEnable, displayLimitLow,
                    displayLimitHigh, displayDescription, displayFormat, displayUnits,
                    controlLimitLow, controlLimitHigh, controlMinStep);
        }

        public Row withDeviceName(String newDeviceName) {
            return new Row(rowNumber, uuid, name, newDeviceName, dataType, itemTag,
                    descriptor, scanTime, scanEnable, writeEnable, displayLimitLow,
                    displayLimitHigh, displayDescription, displayFormat, displayUnits,
                    controlLimitLow, controlLimitHigh, controlMinStep);
        }
    }

    public record Result(boolean success, List<String> errors) {

        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result fail(List<String> errors) {
            return new Result(false, List.copyOf(errors));
        }
    }

    public enum Field {
        NAME,
        DEVICE,
        TYPE,
        TAG
    }

    public record RowIssue(int rowIndex, int rowNumber, Field field, String message) {

    }

    private record FieldIssue(Field field, String message) {

    }

    private record TagUse(int rowNumber, String deviceName, DynamicTag tag) {

    }

    private record NameKey(String deviceName, String name) {

    }

    private record RowEntry(int index, int rowNumber, Row row) {

    }

    public static String validate(Row row) {
        FieldIssue issue = validateFields(row);
        return issue == null ? null : issue.message();
    }

    public static List<RowIssue> validateRows(List<Row> rows) {
        return validateRows(rows, false);
    }

    private static List<RowIssue> validateRows(List<Row> rows, boolean includeEmptyRows) {
        List<Row> source = rows == null ? List.of() : rows;
        List<RowEntry> entries = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            Row row = source.get(index);
            if (!includeEmptyRows && isPlaceholder(row)) {
                continue;
            }
            entries.add(new RowEntry(index, rowNumber(row, index), row));
        }

        List<RowIssue> issues = new ArrayList<>();
        List<TagUse> usedTags = new ArrayList<>();
        Map<NameKey, List<RowEntry>> names = new LinkedHashMap<>();

        for (RowEntry entry : entries) {
            Row row = entry.row();
            FieldIssue fieldIssue = validateFields(row);
            if (fieldIssue != null) {
                issues.add(new RowIssue(entry.index(), entry.rowNumber(),
                        fieldIssue.field(), fieldIssue.message()));
            }

            if (row != null && !isBlank(row.name()) && !isBlank(row.deviceName())) {
                names.computeIfAbsent(new NameKey(row.deviceName(), row.name()),
                        key -> new ArrayList<>()).add(entry);
            }

            if (fieldIssue != null) {
                continue;
            }

            DataType type = DataType.findIgnoreCase(row.dataType());
            DynamicTag tag = DynamicTag.parse(row.itemTag(), type);
            TagUse repeated = findTagUse(usedTags, row.deviceName(), tag);
            if (repeated != null) {
                String detail = repeated.tag().address().equals(tag.address())
                        ? "is already used by" : "overlaps with";
                issues.add(new RowIssue(entry.index(), entry.rowNumber(), Field.TAG,
                        "tag " + tag.address() + " " + detail
                        + " the tag in row " + repeated.rowNumber()
                        + " of device '" + row.deviceName() + "'."));
            } else {
                usedTags.add(new TagUse(entry.rowNumber(), row.deviceName(), tag));
            }
        }

        for (Map.Entry<NameKey, List<RowEntry>> group : names.entrySet()) {
            List<RowEntry> duplicateRows = group.getValue();
            if (duplicateRows.size() < 2) {
                continue;
            }
            String message = "The variable name '" + group.getKey().name()
                    + "' is used more than once in device '" + group.getKey().deviceName()
                    + "' (rows " + joinRowNumbers(duplicateRows) + ").";
            for (RowEntry entry : duplicateRows) {
                issues.add(new RowIssue(entry.index(), entry.rowNumber(), Field.NAME, message));
            }
        }

        return issues;
    }

    private static FieldIssue validateFields(Row row) {
        if (row == null) {
            return new FieldIssue(Field.NAME, "There is no data to validate.");
        }
        if (isBlank(row.name())) {
            return new FieldIssue(Field.NAME, "The variable name is missing.");
        }
        if (isBlank(row.deviceName())) {
            return new FieldIssue(Field.DEVICE, "The connection/device needs to be selected.");
        }

        DataType type = DataType.findIgnoreCase(row.dataType());
        if (type == null || !type.isEnabled()) {
            return new FieldIssue(Field.TYPE, "Invalid data type: " + row.dataType());
        }

        try {
            DynamicTag tag = DynamicTag.parse(row.itemTag(), type);
            if (tag == null) {
                return new FieldIssue(Field.TAG, "Invalid tag: " + row.itemTag());
            }
        } catch (IllegalArgumentException ex) {
            return new FieldIssue(Field.TAG, ex.getMessage());
        }

        return null;
    }

    private static int rowNumber(Row row, int index) {
        return row != null && row.rowNumber() > 0 ? row.rowNumber() : index + 1;
    }

    private static String joinRowNumbers(List<RowEntry> rows) {
        List<String> numbers = new ArrayList<>(rows.size());
        for (RowEntry row : rows) {
            numbers.add(String.valueOf(row.rowNumber()));
        }
        if (numbers.size() == 2) {
            return numbers.get(0) + " and " + numbers.get(1);
        }
        return String.join(", ", numbers.subList(0, numbers.size() - 1))
                + " and " + numbers.get(numbers.size() - 1);
    }

    private static boolean isPlaceholder(Row row) {
        return row == null || (isBlank(row.name()) && isBlank(row.itemTag()));
    }

    public static Row defaultRow(String name, String tag, String deviceName) {
        return new Row(
                0,
                UUID.randomUUID().toString(),
                safe(name),
                safe(deviceName),
                "int",
                safe(tag),
                "",
                "1000ms",
                true,
                false,
                "1",
                "1",
                "",
                "###.##",
                "",
                "1",
                "1",
                "1");
    }

    public static Row fromPv(PvConfigData pv) {
        return new Row(
                0,
                safe(pv.getUuid()),
                safe(pv.getName()),
                safe(pv.getDeviceName()),
                safe(pv.getType()),
                safe(pv.getItemTag()),
                safe(pv.getDescriptorText()),
                safe(pv.getScanTime()),
                pv.isScanEnable(),
                pv.isWriteEnable(),
                safe(pv.getDisplayLimitLow()),
                safe(pv.getDisplayLimitHigh()),
                safe(pv.getDisplayDescription()),
                safe(pv.getDisplayFormat()),
                safe(pv.getDisplayUnits()),
                safe(pv.getControlLimitLow()),
                safe(pv.getControlLimitHigh()),
                safe(pv.getControlMinStep()));
    }

    public static List<Row> loadRows(File folder) throws Exception {
        if (folder == null) {
            return List.of();
        }
        List<PvConfigData> pvs = DynamicVariableXmlMapper.read(folder).pvs();
        List<Row> rows = new ArrayList<>(pvs.size());
        for (PvConfigData pv : pvs) {
            rows.add(fromPv(pv).withRowNumber(rows.size() + 1));
        }
        return rows;
    }

    public static List<Row> duplicateRows(List<Row> rows, int index, int copies) {
        List<Row> source = rows == null ? List.of() : rows;
        if (index < 0 || index >= source.size()) {
            throw new IllegalArgumentException("The selected row is not valid.");
        }
        if (copies < 1) {
            throw new IllegalArgumentException("The number of copies must be at least 1.");
        }

        Row base = source.get(index);
        String baseName = base.name() == null ? "" : base.name().trim();
        if (baseName.isBlank()) {
            throw new IllegalArgumentException("The variable name is missing.");
        }

        String baseTag = base.itemTag() == null ? "" : base.itemTag().trim();
        String baseDevice = base.deviceName() == null ? "" : base.deviceName().trim();
        List<String> tags = TagAllocator.allocate(
                baseTag, base.dataType(), copies, baseDevice, source);

        Set<String> usedNames = new LinkedHashSet<>();
        for (Row row : source) {
            String name = row.name() == null ? "" : row.name().trim();
            if (!name.isBlank()) {
                usedNames.add(name);
            }
        }

        List<Row> result = new ArrayList<>(copies);
        int suffix = 1;
        for (int i = 0; i < copies; i++) {
            String name;
            do {
                name = baseName + "_" + suffix++;
            } while (usedNames.contains(name));
            usedNames.add(name);
            result.add(base
                    .withRowNumber(0)
                    .withUuid(UUID.randomUUID().toString())
                    .withName(name)
                    .withItemTag(tags.get(i)));
        }
        return result;
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

    public static RowFilter<TableModel, Integer> buildExactRowFilter(String text, int... columns) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String expression = "(?i)^" + Pattern.quote(text) + "$";
        if (columns == null || columns.length == 0) {
            return RowFilter.regexFilter(expression);
        }
        return RowFilter.regexFilter(expression, columns);
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
        for (RowIssue issue : validateRows(rowsToSave, true)) {
            errors.add("Row " + issue.rowNumber() + ": " + issue.message());
        }

        if (!errors.isEmpty()) {
            return Result.fail(errors);
        }

        List<PvConfigData> pvs = new ArrayList<>(rowsToSave.size());
        for (Row row : rowsToSave) {
            pvs.add(toPv(row));
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
