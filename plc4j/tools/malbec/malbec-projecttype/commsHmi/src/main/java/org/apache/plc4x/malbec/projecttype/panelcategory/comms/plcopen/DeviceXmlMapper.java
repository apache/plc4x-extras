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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import javax.xml.namespace.QName;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.CommConfigData;
import org.apache.plc4x.malbec.projecttype.panelcategory.model.DeviceConfigData;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.plcopen.xml.tc60201.AddData;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations;
import org.plcopen.xml.tc60201.ProjectDocument.Project.Instances.Configurations.Configuration;

final class DeviceXmlMapper {

    private record Field<T>(String name, Function<T, String> read) {
    }

    private static final List<Field<DeviceConfigData>> DEVICE_ATTRS = List.of(
            new Field<>("uuid", DeviceConfigData::getUuid),
            new Field<>("enabled", d -> Boolean.toString(d.isEnabled())),
            new Field<>("s88Uuid", DeviceConfigData::getS88Uuid));

    private static final List<Field<DeviceConfigData>> DEVICE_ELEMENTS = List.of(
            new Field<>("brand", DeviceConfigData::getBrand),
            new Field<>("model", DeviceConfigData::getModel),
            new Field<>("protocol", DeviceConfigData::getProtocol),
            new Field<>("deviceKey", DeviceConfigData::getDeviceKey),
            new Field<>("description", DeviceConfigData::getDescription),
            new Field<>("s88Node", DeviceConfigData::getS88Node),
            new Field<>("specificParameters", DeviceConfigData::getSpecificParameters));

    private static final List<Field<CommConfigData.GroupConfig>> GROUP_FIELDS = List.of(
            new Field<>("uuid", CommConfigData.GroupConfig::getUuid),
            new Field<>("name", CommConfigData.GroupConfig::getName),
            new Field<>("description", CommConfigData.GroupConfig::getDescription),
            new Field<>("scantime", CommConfigData.GroupConfig::getScantime),
            new Field<>("enable", g -> Boolean.toString(g.isEnable())),
            new Field<>("md5", CommConfigData.GroupConfig::getMd5));

    private static final List<Field<CommConfigData.ItemConfig>> ITEM_FIELDS = List.of(
            new Field<>("uuid", CommConfigData.ItemConfig::getUuid),
            new Field<>("name", CommConfigData.ItemConfig::getName),
            new Field<>("description", CommConfigData.ItemConfig::getDescription),
            new Field<>("tag", CommConfigData.ItemConfig::getTag),
            new Field<>("enable", i -> Boolean.toString(i.isEnable())),
            new Field<>("group", CommConfigData.ItemConfig::getGroupUuid),
            new Field<>("md5", CommConfigData.ItemConfig::getMd5));

    private static final List<Field<CommConfigData.PvConfig>> PV_FIELDS = List.of(
            new Field<>("uuid", CommConfigData.PvConfig::getUuid),
            new Field<>("name", CommConfigData.PvConfig::getName),
            new Field<>("type", CommConfigData.PvConfig::getType),
            new Field<>("id", CommConfigData.PvConfig::getAreaName),
            new Field<>("offset", CommConfigData.PvConfig::getOffset),
            new Field<>("descriptor", CommConfigData.PvConfig::getDescriptorText),
            new Field<>("scanTime", CommConfigData.PvConfig::getScanTime),
            new Field<>("scanEnable", p -> Boolean.toString(p.isScanEnable())),
            new Field<>("writeEnable", p -> Boolean.toString(p.isWriteEnable())),
            new Field<>("displayLimitLow", CommConfigData.PvConfig::getDisplayLimitLow),
            new Field<>("displayLimitHigh", CommConfigData.PvConfig::getDisplayLimitHigh),
            new Field<>("displayDescription", CommConfigData.PvConfig::getDisplayDescription),
            new Field<>("displayFormat", CommConfigData.PvConfig::getDisplayFormat),
            new Field<>("displayUnits", CommConfigData.PvConfig::getDisplayUnits),
            new Field<>("controlLimitLow", CommConfigData.PvConfig::getControlLimitLow),
            new Field<>("controlLimitHigh", CommConfigData.PvConfig::getControlLimitHigh),
            new Field<>("controlMinStep", CommConfigData.PvConfig::getControlMinStep),
            new Field<>("s88Path", CommConfigData.PvConfig::getS88Path),
            new Field<>("md5", CommConfigData.PvConfig::getMd5));

    private DeviceXmlMapper() {
    }

    public static Configuration writeDevice(DeviceConfigData device, Configurations configurations) {
        Configuration configuration = configurations.addNewConfiguration();
        configuration.setName(device.getDeviceName());
        AddData addData = configuration.addNewAddData();
        AddData.Data data = addData.addNewData();
        data.setName(CommsXmlIdentifiers.DEVICE_DATA_NAME);
        data.setHandleUnknown(AddData.Data.HandleUnknown.PRESERVE);

        XmlObject root = parse(skeleton("device", CommsXmlIdentifiers.DEVICE_NS, DEVICE_ELEMENTS));
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

    public static void writeComms(CommConfigData comms, Configuration configuration) {
        if (comms == null) {
            return;
        }
        AddData addData = configuration.getAddData();
        if (addData == null) {
            addData = configuration.addNewAddData();
        }
        AddData.Data data = addData.addNewData();
        data.setName(CommsXmlIdentifiers.COMMS_DATA_NAME);
        data.setHandleUnknown(AddData.Data.HandleUnknown.PRESERVE);

        XmlObject root = parse("<comm xmlns=\"" + CommsXmlIdentifiers.COMMS_NS + "\">"
                + "<groups/><items/><pvs/></comm>");
        appendRows(root, "groups", "group", GROUP_FIELDS, comms.getGroups());
        appendRows(root, "items", "item", ITEM_FIELDS, comms.getItems());
        appendRows(root, "pvs", "pv", PV_FIELDS, comms.getPvs());
        appendAnyContent(data, root);
    }

    public static DeviceConfigData readDevice(Configuration configuration) {
        AddData addData = configuration.getAddData();
        if (addData == null) {
            return null;
        }
        for (AddData.Data data : addData.getDataArray()) {
            if (CommsXmlIdentifiers.DEVICE_DATA_NAME.equals(data.getName())) {
                XmlObject device = firstChild(data, "device");
                if (device == null) {
                    return null;
                }
                Properties pDevice = new Properties();
                Map<String, String> attrs = readFields(DEVICE_ATTRS, device);
                for (Field<DeviceConfigData> field : DEVICE_ELEMENTS) {
                    pDevice.put(field.name(), text(device, field.name()));
                }
                pDevice.put("deviceName", configuration.getName());
                pDevice.put("enable", Boolean.valueOf(attrs.get("enabled")));
                pDevice.put("uuid", attrs.get("uuid"));
                pDevice.put("s88Uuid", attrs.get("s88Uuid"));
                return new DeviceConfigData(pDevice);
            }
        }
        return null;
    }

    public static CommConfigData readComms(Configuration configuration) {
        AddData addData = configuration.getAddData();
        if (addData == null) {
            return null;
        }
        for (AddData.Data data : addData.getDataArray()) {
            if (CommsXmlIdentifiers.COMMS_DATA_NAME.equals(data.getName())) {
                XmlObject comm = firstChild(data, "comm");
                if (comm == null) {
                    return null;
                }
                List<CommConfigData.GroupConfig> groups = new ArrayList<>();
                for (XmlObject g : children(comm, "groups", "group")) {
                    Map<String, String> m = readFields(GROUP_FIELDS, g);
                    groups.add(new CommConfigData.GroupConfig(
                            m.get("uuid"), m.get("name"), m.get("description"),
                            m.get("scantime"), Boolean.parseBoolean(m.get("enable")),
                            m.get("md5")));
                }
                List<CommConfigData.ItemConfig> items = new ArrayList<>();
                for (XmlObject i : children(comm, "items", "item")) {
                    Map<String, String> m = readFields(ITEM_FIELDS, i);
                    items.add(new CommConfigData.ItemConfig(
                            m.get("uuid"), m.get("name"), m.get("description"),
                            m.get("tag"), Boolean.parseBoolean(m.get("enable")),
                            m.get("md5"), m.get("group")));
                }
                List<CommConfigData.PvConfig> pvs = new ArrayList<>();
                for (XmlObject p : children(comm, "pvs", "pv")) {
                    Map<String, String> m = readFields(PV_FIELDS, p);
                    pvs.add(new CommConfigData.PvConfig(
                            m.get("uuid"), m.get("name"), m.get("type"),
                            areaNameOf(m.get("id"), items),
                            m.get("offset"), m.get("descriptor"), m.get("scanTime"),
                            Boolean.parseBoolean(m.get("scanEnable")),
                            Boolean.parseBoolean(m.get("writeEnable")), m.get("displayLimitLow"),
                            m.get("displayLimitHigh"), m.get("displayDescription"),
                            m.get("displayFormat"), m.get("displayUnits"), m.get("controlLimitLow"),
                            m.get("controlLimitHigh"), m.get("controlMinStep"), m.get("md5"),
                            m.get("s88Path")));
                }
                return new CommConfigData(configuration.getName(), groups, items, pvs);
            }
        }
        return null;
    }

    private static String areaNameOf(String ref, List<CommConfigData.ItemConfig> items) {
        if (ref == null || ref.isEmpty()) {
            return ref;
        }
        for (CommConfigData.ItemConfig item : items) {
            if (ref.equals(item.getUuid())) {
                return item.getName();
            }
        }
        return ref;
    }

    private static <T> void appendRows(XmlObject root, String container, String element,
            List<Field<T>> fields, List<T> rows) throws RuntimeException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String ns = CommsXmlIdentifiers.COMMS_NS;
        for (T row : rows) {
            XmlObject child = parse("<" + element + " xmlns=\"" + ns + "\"/>");
            XmlCursor source = child.newCursor();
            try {
                source.toFirstChild();
                for (Field<T> field : fields) {
                    source.setAttributeText(new QName(field.name()), value(field, row));
                }
                source.toFirstChild();
                XmlCursor target = root.newCursor();
                try {
                    target.toFirstChild();
                    target.toFirstChild();
                    while (!container.equals(localName(target))) {
                        if (!target.toNextSibling()) {
                            throw new IllegalStateException("No se encontro el contenedor " + container);
                        }
                    }
                    target.push();
                    target.toEndToken();
                    source.copyXml(target);
                    target.pop();
                } finally {
                    target.dispose();
                }
            } finally {
                source.dispose();
            }
        }
    }

    private static <T> void fillChildText(XmlObject root, List<Field<T>> fields, T value)
            throws RuntimeException {
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

    private static <T> String value(Field<T> field, T target) {
        String raw = field.read().apply(target);
        return raw == null ? "" : raw;
    }

    private static <T> Map<String, String> readFields(List<Field<T>> fields, XmlObject element) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field<T> field : fields) {
            values.put(field.name(), attr(element, field.name()));
        }
        return values;
    }

    /** Esqueleto con un hijo vacio por cada elemento de la tabla; los nombres no llevan datos. */
    private static String skeleton(String element, String ns, List<? extends Field<?>> children) {
        StringBuilder xml = new StringBuilder("<").append(element).append(" xmlns=\"").append(ns).append("\">");
        for (Field<?> child : children) {
            xml.append('<').append(child.name()).append("/>");
        }
        return xml.append("</").append(element).append('>').toString();
    }

    private static void appendAnyContent(AddData.Data data, XmlObject content) {
        XmlCursor cursor = data.newCursor();
        try {
            cursor.toEndToken();
            XmlCursor source = content.newCursor();
            try {
                source.toFirstChild();
                source.copyXml(cursor);
            } finally {
                source.dispose();
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el contenido dev/comms", e);
        } finally {
            cursor.dispose();
        }
    }

    private static List<XmlObject> children(XmlObject comm, String container, String localName) {
        List<XmlObject> result = new ArrayList<>();
        XmlObject parent = firstChild(comm, container);
        if (parent == null) {
            return result;
        }
        XmlCursor cursor = parent.newCursor();
        try {
            if (cursor.toFirstChild()) {
                do {
                    if (localName.equals(cursor.getName().getLocalPart())) {
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

    private static String localName(XmlCursor cursor) {
        QName name = cursor.getName();
        return name == null ? "" : name.getLocalPart();
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

    private static XmlObject parse(String xml) {
        try {
            return XmlObject.Factory.parse(xml);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el esqueleto del bloque dev/comms", e);
        }
    }
}