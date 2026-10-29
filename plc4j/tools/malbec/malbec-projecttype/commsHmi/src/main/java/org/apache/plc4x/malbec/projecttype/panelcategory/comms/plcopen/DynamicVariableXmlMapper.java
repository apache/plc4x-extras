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

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import javax.xml.namespace.QName;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.PvConfigData;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.plcopen.xml.tc60201.AddData;
import org.plcopen.xml.tc60201.ProjectDocument;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations.Configuration;

/**
 * Persists the communication XML and the dynamic variables shared with the boot
 * database.
 *
 * <p>The XML keeps the PlcOpen envelope and stores one configuration per
 * device. Device fields use the new {@code Devices} table names as lowerCamel
 * attributes; {@code brand}, {@code model} and the S88 fields remain as legacy
 * XML-only elements. PV records are nested inside each device so the database
 * can be rebuilt later from the XML.</p>
 */
public final class DynamicVariableXmlMapper {

    public static final String FILE_NAME = "comunicacion.xml";

    private record Field<T>(String name, Function<T, String> read) {
    }

    private static final List<Field<PvConfigData>> PV_FIELDS = List.of(
            new Field<>("uuid", PvConfigData::getUuid),
            new Field<>("name", PvConfigData::getName),
            new Field<>("type", PvConfigData::getType),
            new Field<>("deviceName", PvConfigData::getDeviceName),
            new Field<>("tag", PvConfigData::getTag),
            new Field<>("descriptor", PvConfigData::getDescriptorText),
            new Field<>("scanTime", PvConfigData::getScanTime),
            new Field<>("scanEnable", p -> Boolean.toString(p.isScanEnable())),
            new Field<>("writeEnable", p -> Boolean.toString(p.isWriteEnable())),
            new Field<>("displayLimitLow", PvConfigData::getDisplayLimitLow),
            new Field<>("displayLimitHigh", PvConfigData::getDisplayLimitHigh),
            new Field<>("displayDescription", PvConfigData::getDisplayDescription),
            new Field<>("displayFormat", PvConfigData::getDisplayFormat),
            new Field<>("displayUnits", PvConfigData::getDisplayUnits),
            new Field<>("controlLimitLow", PvConfigData::getControlLimitLow),
            new Field<>("controlLimitHigh", PvConfigData::getControlLimitHigh),
            new Field<>("controlMinStep", PvConfigData::getControlMinStep),
            new Field<>("md5", PvConfigData::getMd5));

    private static final List<Field<DeviceConfigData>> DEVICE_ATTRS = List.of(
            new Field<>("uuid", DeviceConfigData::getUuid),
            new Field<>("driverName", DeviceConfigData::getProtocol),
            new Field<>("deviceKey", DeviceConfigData::getDeviceKey),
            new Field<>("deviceUrl", DeviceConfigData::getSpecificParameters),
            new Field<>("deviceName", DeviceConfigData::getDeviceName),
            new Field<>("deviceDescription", DeviceConfigData::getDescription),
            new Field<>("deviceEnable", d -> Boolean.toString(d.isEnabled())),
            new Field<>("md5", DeviceConfigData::getMd5));

    private static final List<Field<DeviceConfigData>> DEVICE_ELEMENTS = List.of(
            new Field<>("brand", DeviceConfigData::getBrand),
            new Field<>("model", DeviceConfigData::getModel),
            new Field<>("s88Node", DeviceConfigData::getS88Node),
            new Field<>("s88Uuid", DeviceConfigData::getS88Uuid));

    private DynamicVariableXmlMapper() {
    }

    public record Content(List<DeviceConfigData> devices, List<PvConfigData> pvs) {

        public Content {
            devices = List.copyOf(devices);
            pvs = List.copyOf(pvs);
        }
    }

    public static Content read(File folder) throws Exception {
        if (folder == null) {
            return new Content(List.of(), List.of());
        }

        File file = new File(folder, FILE_NAME);
        if (!file.isFile() || file.length() == 0) {
            return new Content(List.of(), List.of());
        }

        return read(PlcOpenCommConfigRepositoryImpl.parseDocument(file));
    }

    public static Content read(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            return new Content(List.of(), List.of());
        }
        return read(PlcOpenCommConfigRepositoryImpl.parseDocument(xml));
    }

    private static Content read(ProjectDocument document) {
        List<DeviceConfigData> devices = new ArrayList<>();
        List<PvConfigData> pvs = new ArrayList<>();

        for (Configuration configuration
                : document.getProject().getInstances().getConfigurations().getConfigurationList()) {
            DeviceConfigData device = readDevice(configuration);
            if (device != null) {
                devices.add(device);
            }
            pvs.addAll(readPvs(configuration, device));
        }

        return new Content(devices, pvs);
    }

    public static void write(File folder, List<DeviceConfigData> devices,
            List<PvConfigData> pvs) throws Exception {
        if (folder == null) {
            throw new IllegalArgumentException("The project folder was not specified.");
        }

        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException(
                    "The project folder could not be created: " + folder);
        }
        PlcOpenCommConfigRepositoryImpl.saveDocument(
                buildDocument(devices, pvs), new File(folder, FILE_NAME));
    }

    public static void write(OutputStream out, List<DeviceConfigData> devices,
            List<PvConfigData> pvs) throws Exception {
        if (out == null) {
            throw new IllegalArgumentException("The output stream was not specified.");
        }
        PlcOpenCommConfigRepositoryImpl.saveDocument(buildDocument(devices, pvs), out);
    }

    private static ProjectDocument buildDocument(List<DeviceConfigData> devices,
            List<PvConfigData> pvs) throws Exception {
        ProjectDocument document = ProjectDocument.Factory.parse(
                PlcOpenCommConfigRepositoryImpl.TEMPLATE);
        Configurations configurations = document.getProject()
                .getInstances().getConfigurations();

        Map<String, List<PvConfigData>> pvsByDevice = new LinkedHashMap<>();
        if (pvs != null) {
            for (PvConfigData pv : pvs) {
                if (pv == null || isBlank(pv.getDeviceName())) {
                    continue;
                }
                pvsByDevice.computeIfAbsent(pv.getDeviceName(), key -> new ArrayList<>()).add(pv);
            }
        }

        if (devices != null) {
            for (DeviceConfigData device : devices) {
                if (device == null || isBlank(device.getDeviceName())) {
                    continue;
                }
                Configuration configuration = writeDevice(device, configurations);
                XmlObject deviceElement = deviceElement(configuration);
                if (deviceElement != null) {
                    writePvs(deviceElement,
                            pvsByDevice.getOrDefault(device.getDeviceName(), List.of()));
                }
            }
        }

        return document;
    }

    static Configuration writeDevice(DeviceConfigData device, Configurations configurations) {
        Configuration configuration = configurations.addNewConfiguration();
        configuration.setName(device.getDeviceName());
        AddData addData = configuration.addNewAddData();
        AddData.Data data = addData.addNewData();
        data.setName(CommsXmlIdentifiers.DEVICE_DATA_NAME);
        data.setHandleUnknown(AddData.Data.HandleUnknown.PRESERVE);

        XmlObject root = parse(skeleton("device",
                CommsXmlIdentifiers.DEVICE_NS, DEVICE_ELEMENTS));
        XmlCursor cursor = root.newCursor();
        try {
            cursor.toFirstChild();
            for (Field<DeviceConfigData> field : DEVICE_ATTRS) {
                cursor.setAttributeText(new QName(field.name()), value(field, device));
            }
        } finally {
            cursor.dispose();
        }
        fillChildText(root, DEVICE_ELEMENTS, device);
        appendAnyContent(data, root);
        return configuration;
    }

    static DeviceConfigData readDevice(Configuration configuration) {
        XmlObject device = deviceElement(configuration);
        if (device == null) {
            return null;
        }
        Map<String, String> attrs = readFields(DEVICE_ATTRS, device);
        Properties properties = new Properties();
        properties.put("uuid", firstNonBlank(attrs.get("uuid"), attr(device, "uuid")));
        properties.put("protocol", firstNonBlank(attrs.get("driverName"),
                text(device, "protocol")));
        properties.put("deviceKey", firstNonBlank(attrs.get("deviceKey"),
                text(device, "deviceKey")));
        properties.put("specificParameters", firstNonBlank(attrs.get("deviceUrl"),
                text(device, "specificParameters")));
        properties.put("deviceName", firstNonBlank(attrs.get("deviceName"),
                configuration.getName()));
        properties.put("description", firstNonBlank(attrs.get("deviceDescription"),
                text(device, "description")));
        properties.put("enable", Boolean.valueOf(
                firstNonBlank(attrs.get("deviceEnable"), attr(device, "enabled"))));
        properties.put("md5", attrs.get("md5"));
        properties.put("brand", text(device, "brand"));
        properties.put("model", text(device, "model"));
        properties.put("s88Node", text(device, "s88Node"));
        properties.put("s88Uuid", firstNonBlank(text(device, "s88Uuid"),
                attr(device, "s88Uuid")));
        return new DeviceConfigData(properties);
    }

    private static void writePvs(XmlObject deviceElement, List<PvConfigData> pvs) {
        appendAnyContent(deviceElement, parse(pvRecordsXml(pvs)));
    }

    static void writePvs(Configuration configuration, List<PvConfigData> pvs) {
        XmlObject deviceElement = deviceElement(configuration);
        if (deviceElement != null) {
            writePvs(deviceElement, pvs);
        }
    }

    static List<PvConfigData> readNestedPvs(Configuration configuration,
            DeviceConfigData device) {
        XmlObject deviceElement = deviceElement(configuration);
        if (deviceElement == null) {
            return List.of();
        }
        XmlObject pvRecords = firstChild(deviceElement, "pvrecords");
        return pvRecords == null ? List.of()
                : readPvElements(
                        childElements(pvRecords, "pvrecord"), configuration, device);
    }

    private static String pvRecordsXml(List<PvConfigData> pvs) {
        StringBuilder xml = new StringBuilder("<pvrecords xmlns=\"")
                .append(CommsXmlIdentifiers.PVRECORDS_NS).append("\">");
        if (pvs != null) {
            for (PvConfigData pv : pvs) {
                if (pv == null) {
                    continue;
                }
                xml.append("<pvrecord");
                for (Field<PvConfigData> field : PV_FIELDS) {
                    xml.append(' ')
                            .append(field.name())
                            .append("=\"")
                            .append(xmlEscape(value(field, pv)))
                            .append('"');
                }
                xml.append("/>");
            }
        }
        return xml.append("</pvrecords>").toString();
    }

    static List<PvConfigData> readPvs(Configuration configuration,
            DeviceConfigData device) {
        return readNestedPvs(configuration, device);
    }

    private static List<PvConfigData> readPvElements(List<XmlObject> elements,
            Configuration configuration, DeviceConfigData device) {
        List<PvConfigData> pvs = new ArrayList<>();
        for (XmlObject element : elements) {
            Map<String, String> values = readFields(PV_FIELDS, element);
            String deviceName = values.get("deviceName");
            if (isBlank(deviceName)) {
                deviceName = device == null
                        ? configuration.getName()
                        : device.getDeviceName();
            }

            String tag = firstNonBlank(values.get("tag"), attr(element, "id"));

            pvs.add(new PvConfigData(
                    values.get("uuid"),
                    values.get("name"),
                    values.get("type"),
                    deviceName,
                    tag,
                    values.get("descriptor"),
                    values.get("scanTime"),
                    Boolean.parseBoolean(values.get("scanEnable")),
                    Boolean.parseBoolean(values.get("writeEnable")),
                    values.get("displayLimitLow"),
                    values.get("displayLimitHigh"),
                    values.get("displayDescription"),
                    values.get("displayFormat"),
                    values.get("displayUnits"),
                    values.get("controlLimitLow"),
                    values.get("controlLimitHigh"),
                    values.get("controlMinStep"),
                    values.get("md5")));
        }
        return pvs;
    }

    private static <T> void fillChildText(XmlObject root, List<Field<T>> fields, T value) {
        for (int i = 0; i < fields.size(); i++) {
            XmlCursor cursor = root.newCursor();
            try {
                cursor.toFirstChild();
                cursor.toFirstChild();
                for (int k = 0; k < i; k++) {
                    cursor.toNextSibling();
                }
                cursor.toFirstContentToken();
                cursor.insertChars(value(fields.get(i), value));
            } finally {
                cursor.dispose();
            }
        }
    }

    private static String skeleton(String element, String namespace,
            List<? extends Field<?>> children) {
        StringBuilder xml = new StringBuilder("<").append(element)
                .append(" xmlns=\"").append(namespace).append("\">");
        for (Field<?> child : children) {
            xml.append('<').append(child.name()).append("/>");
        }
        return xml.append("</").append(element).append('>').toString();
    }

    private static String text(XmlObject parent, String name) {
        XmlCursor cursor = parent.newCursor();
        try {
            if (cursor.toFirstChild()) {
                do {
                    QName current = cursor.getName();
                    if (current != null && name.equals(current.getLocalPart())) {
                        String value = cursor.getTextValue();
                        return value == null ? "" : value;
                    }
                } while (cursor.toNextSibling());
            }
            return "";
        } finally {
            cursor.dispose();
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? (fallback == null ? "" : fallback) : preferred;
    }

    private static XmlObject deviceElement(Configuration configuration) {
        AddData addData = configuration.getAddData();
        if (addData == null) {
            return null;
        }
        for (AddData.Data data : addData.getDataArray()) {
            if (CommsXmlIdentifiers.DEVICE_DATA_NAME.equals(data.getName())) {
                return firstChild(data, "device");
            }
        }
        return null;
    }

    private static void appendAnyContent(XmlObject parent, XmlObject content) {
        XmlCursor cursor = parent.newCursor();
        try {
            cursor.toEndToken();
            XmlCursor source = content.newCursor();
            try {
                source.toFirstChild();
                source.copyXml(cursor);
            } finally {
                source.dispose();
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "The dynamic variable content could not be serialized.", ex);
        } finally {
            cursor.dispose();
        }
    }

    private static List<XmlObject> childElements(XmlObject parent, String localName) {
        List<XmlObject> result = new ArrayList<>();
        XmlCursor cursor = parent.newCursor();
        try {
            if (cursor.toFirstChild()) {
                do {
                    QName name = cursor.getName();
                    if (name != null && localName.equals(name.getLocalPart())) {
                        result.add(cursor.getObject());
                    }
                } while (cursor.toNextSibling());
            }
        } finally {
            cursor.dispose();
        }
        return result;
    }

    private static XmlObject firstChild(XmlObject parent, String localName) {
        XmlCursor cursor = parent.newCursor();
        try {
            if (cursor.toFirstChild()) {
                do {
                    QName name = cursor.getName();
                    if (name != null && localName.equals(name.getLocalPart())) {
                        return cursor.getObject();
                    }
                } while (cursor.toNextSibling());
            }
        } finally {
            cursor.dispose();
        }
        return null;
    }

    private static <T> Map<String, String> readFields(List<Field<T>> fields,
            XmlObject element) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field<T> field : fields) {
            values.put(field.name(), attr(element, field.name()));
        }
        return values;
    }

    private static String attr(XmlObject element, String name) {
        XmlCursor cursor = element.newCursor();
        try {
            String value = cursor.getAttributeText(new QName(name));
            return value == null ? "" : value;
        } finally {
            cursor.dispose();
        }
    }

    private static <T> String value(Field<T> field, T target) {
        String raw = field.read().apply(target);
        return raw == null ? "" : raw;
    }

    private static String xmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static XmlObject parse(String xml) {
        try {
            return XmlObject.Factory.parse(xml);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "The block structure for dynamic variables could not be prepared.", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
