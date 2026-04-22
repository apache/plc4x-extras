/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.archiver.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.iotdb.isession.pool.SessionDataSetWrapper;
import org.apache.iotdb.pipe.api.type.Type;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.apache.plc4x.merlot.archiver.core.MerlotIoTDBMapping;
import org.apache.tsfile.read.common.Field;
import org.apache.tsfile.read.common.RowRecord;
import org.epics.vtype.Alarm;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VByte;
import org.epics.vtype.VDouble;
import org.epics.vtype.VFloat;
import org.epics.vtype.VInt;
import org.epics.vtype.VString;
import org.epics.vtype.VType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class MerlotHtcIoTDBImpl implements MerlotHtc, ManagedService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotHtcIoTDBImpl.class);

    private static final String strID = "iotdb";
    private SessionPool sp;
    private List<String> urls = new ArrayList<>();
    private String username;
    private String password;
    private int maxThreadPool;
    private boolean enableAutoFetch;

    public MerlotHtcIoTDBImpl() {

    }

    @Override
    public void init() {

    }

    public synchronized SessionPool getIoTDBConnection() throws IoTDBConnectionException, StatementExecutionException {
        if (sp != null) {
            return sp;
        } else {
            try {
                sp = new SessionPool.Builder()
                        .nodeUrls((new ArrayList<>(this.urls)))
                        .user(this.username)
                        .password(this.password)
                        .maxSize(this.maxThreadPool)
                        .enableAutoFetch(this.enableAutoFetch)
                        .build();

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void destroy() {
        if (sp != null) {
            sp.close();
        }
    }

    @Override
    public String getID() {
        return strID;
    }

    @Override
    public void addPV(String strPV, Double interval) {
        //
    }

    @Override
    public void removePV(String strPV
    ) {
        //
    }

    @Override
    public Set<String> getPVs() {
        Set<String> pvs = new HashSet<>();

        String pvNameQuery = "SHOW TIMESERIES root.**";

        try (SessionDataSetWrapper ds = getIoTDBConnection().executeQueryStatement(pvNameQuery)) {
            while (ds.hasNext()) {

                String fullPath = ds.next().getFields().get(0).getStringValue();

                pvs.add(fullPath.replaceFirst("^[^.]+\\.(.*)", "$1"));
            }
        } catch (Exception ex) {
            LOGGER.error("Error retrieving PVs from IoTDB: {}", ex.getMessage());
        }

        return pvs;
    }

    @Override
    public List<VType> getPVs(String strPV, String init, String end) {
        List<VType> listResult = new ArrayList<>();
        try {
            String device = getBasePath(strPV);
            String measurement = getTimeserieNameSimple(strPV);

            long startT = Instant.parse(init).toEpochMilli();
            long endT = Instant.parse(end).toEpochMilli();

            String sql = String.format("SELECT %s FROM root.%s WHERE time >= %d AND time <= %d",
                    measurement, device, startT, endT);
            
            SessionPool pool = getIoTDBConnection();
            if (pool == null) {
                return listResult;
            }
            try (SessionDataSetWrapper dataSet = pool.executeQueryStatement(sql)) {

                String typeStr = dataSet.getColumnTypes().get(1);
                Type iotdbType = Type.valueOf(typeStr);
                MerlotIoTDBMapping mapper = MerlotIoTDBMapping.fromIotdb(iotdbType);

                while (dataSet.hasNext()) {
                    RowRecord record = dataSet.next();
                    long timestamp = record.getTimestamp();
                    Field field = record.getFields().get(0);

                    if (field.getDataType() != null) {
                        Instant inst = Instant.ofEpochMilli(timestamp);

                        MerlotIoTDBMapping.EpicsMetadata meta
                                = new MerlotIoTDBMapping.EpicsMetadata((int) inst.getEpochSecond(), inst.getNano(), 0, 0);

                        Object epicsEvent = mapper.convert(field.getObjectValue(field.getDataType()), meta);
                        listResult.addAll(translateToScalarType(inst, epicsEvent));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error retrieving PVs from IoTDB", e);
        }

        return listResult;
    }

    private static List<VType> translateToScalarType(Instant inst, Object epicsEvent) {
        List<VType> listEvents = new ArrayList<>();

        // --- CONVERSION BASED STRICTLY ON MerlotIoTDBMapping ---
        if (epicsEvent instanceof VType) {
            listEvents.add((VType) epicsEvent);
        } else if (epicsEvent instanceof org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble) {
            var pbEvent = (org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarDouble) epicsEvent;
            listEvents.add(VDouble.of(pbEvent.getVal(), Alarm.none(), Time.of(inst), Display.none()));
        } else if (epicsEvent instanceof org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarInt) {
            var pbEvent = (org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarInt) epicsEvent;
            listEvents.add(VInt.of(pbEvent.getVal(), Alarm.none(), Time.of(inst), Display.none()));
        } else if (epicsEvent instanceof org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarFloat) {
            var pb = (org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarFloat) epicsEvent;
            listEvents.add(VFloat.of(pb.getVal(), Alarm.none(), Time.of(inst), Display.none()));
        } else if (epicsEvent instanceof org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarString) {
            var pb = (org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarString) epicsEvent;
            listEvents.add(VString.of(pb.getVal(), Alarm.none(), Time.of(inst)));
        } else if (epicsEvent instanceof org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarByte) {
            var pb = (org.apache.plc4x.merlot.api.PB.EPICSEvent.ScalarByte) epicsEvent;

            byte byteVal = pb.getVal().isEmpty() ? 0 : pb.getVal().byteAt(0);

            listEvents.add(VByte.of(byteVal, Alarm.none(), Time.of(inst), Display.none()));
        } else {
            LOGGER.warn("PB event type not supported in the final conversion: {}", epicsEvent.getClass().getName());
        }
        return listEvents;
    }

    @Override
    public int countPVs(String strPV, String init,
            String end
    ) {

        return 0;
    }

    /*
    Returns the variable stored in the IoTDB device (pvName)
     */
    private static String getTimeserieNameSimple(String pvName) {
        Pattern pattern = Pattern.compile("[^.]+$");
        Matcher matcher = pattern.matcher(pvName);

        if (matcher.find()) {
            return matcher.group();
        }

        return "";
    }

    /*
    Returns the base path contained in the IoTDB device
     */
    public static String getBasePath(String pvName) {
        if (pvName == null || pvName.isEmpty()) {
            return "";
        }
        return pvName.replaceFirst("^(?:root\\.)?(.*)\\.[^.]+$", "$1");
    }

    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        if (properties == null) {
            return;
        }

        Object urlsObj = properties.get("urls");
        if (urlsObj == null || urlsObj.toString().trim().isEmpty()) {
            throw new ConfigurationException("urls", "The list of URLs cannot be empty in the /etc/org.apache.plc4x.merlot.iotdb.cfg");
        }

        synchronized (this) {
            this.urls.clear();
            String[] splitUrls = urlsObj.toString().split("\\s*,\\s*");
            for (String url : splitUrls) {
                String cleanUrl = url.trim();
                if (!cleanUrl.isEmpty()) {
                    this.urls.add(cleanUrl);
                }
            }

            this.username = (String) properties.get("username");
            this.password = (String) properties.get("password");

            this.maxThreadPool = Integer.parseInt(properties.get("max_thread_pool").toString());
            this.enableAutoFetch = Boolean.parseBoolean(properties.get("enable_auto_fetch").toString());

            if (sp != null) {
                sp.close();
                sp = null;
            }
        }
        LOGGER.info("IoTDB configuration successfully updated. URLs: {}", this.urls);
    }

}
