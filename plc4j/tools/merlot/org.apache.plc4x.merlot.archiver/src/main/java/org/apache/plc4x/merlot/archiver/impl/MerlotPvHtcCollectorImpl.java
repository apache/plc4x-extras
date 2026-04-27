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
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.plc4x.merlot.archiver.api.MerlotCollector;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.apache.plc4x.merlot.scheduler.api.ScheduleOptions;
import org.apache.plc4x.merlot.scheduler.api.Scheduler;
import static org.apache.tsfile.file.metadata.IDeviceID.LOGGER;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVEvent;
import org.epics.gpclient.PVEventRecorder;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.PVReaderListener;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventProperties;
import org.slf4j.LoggerFactory;

public class MerlotPvHtcCollectorImpl implements MerlotCollector, ManagedServiceFactory, PVReaderListener {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotPvHtcCollectorImpl.class);

    private static final Pattern GROUP_INDEX_PATTERN
            = Pattern.compile("^HG(?<groupIndex>\\d{4})");
    private static final Pattern PV_INDEX_PATTERN
            = Pattern.compile("^PV(?<groupIndex>\\d{4})");

    protected static final String GROUP_INDEX = "groupIndex";

    private final Scheduler scheduler;
    private final GPClientInstance gpClient;
    private final Map<String, SchedulerGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, MutablePair<SchedulerGroup, PVReader<VType>>> pvs = new ConcurrentHashMap<>();

    /*
    Parameter Broker MQTT IoTDB
     */
    private volatile MqttClient mqttClient = null;
    //

    public MerlotPvHtcCollectorImpl(Scheduler scheduler, MerlotGPClient gpMerlotClient) {
        this.scheduler = scheduler;
        this.gpClient = gpMerlotClient.gpClientFactory("MerlotPvHtc");
    }

    @Override
    public void init() {
        LOGGER.info("Starting the PV Collector");
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    private void createConnection(String mqttUrl, String username, String password) {

        try {
            mqttClient = new MqttClient(mqttUrl, "Merlot-HTC-IoTDB" + System.currentTimeMillis(), new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(username);
            connOpts.setPassword(password.toCharArray());
            connOpts.setCleanSession(true);
            connOpts.setAutomaticReconnect(true);

            mqttClient.connect(connOpts);
            LOGGER.info("Connection made successfully");
        } catch (MqttException e) {
            LOGGER.error("Error connecting to MQTT broker: " + e.getMessage());
        }

    }

    @Override
    public void destroy() {
        try {
            this.mqttClient.close();
            this.gpClient.close();
        } catch (MqttException ex) {
            LOGGER.info("Close connection MQTT");
        }
    }

    @Override
    public void stop() {
        groups.forEach((g, o) -> {
            scheduler.unschedule(g);
        });
    }

    @Override
    public void start() {
        groups.forEach((g, o) -> {
            scheduler.unschedule(g);

            o.pvs.values().forEach(pvInfo -> {
                if (pvInfo.pvr != null) {
                    pvInfo.pvr.close();
                }
            });
        });
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        Matcher matcher;
        String strObject;
        String strKey;
        String strValue;

        stop();
        groups.clear();

        if (this.mqttClient != null) {
            try {

                if (this.mqttClient.isConnected()) {
                    this.mqttClient.disconnectForcibly();
                }
                this.mqttClient.close();
                LOGGER.info("MQTT connection released.");
            } catch (MqttException ex) {
                LOGGER.warn("Error closing previous MQTT connection: {}", ex.getMessage());
            }
        }
        createConnection((String) properties.get("broker"), (String) properties.get("useriotdb"), (String) properties.get("passwordiotdb"));

        if (null == properties) {
            return;
        }
        //Group Section
        Enumeration<String> enumKeys = properties.keys();
        while (enumKeys.hasMoreElements()) {
            strKey = enumKeys.nextElement();
            strValue = (String) properties.get(strKey);
            if ((matcher = GROUP_INDEX_PATTERN.matcher(strKey)).matches()) {
                addGroup(strKey, strValue);
            }
        }

        //PV Section
        enumKeys = properties.keys();
        while (enumKeys.hasMoreElements()) {
            strKey = enumKeys.nextElement();
            strValue = (String) properties.get(strKey);
            if ((matcher = PV_INDEX_PATTERN.matcher(strKey)).matches()) {
                String[] fields = strValue.split(";");

                final SchedulerGroup group = groups.get(fields[1]);

                if (null != group) {
                    PVInfo pvInfo = new PVInfo();
                    pvInfo.strPv = fields[0];
                    pvInfo.strGroup = fields[1];
                    pvInfo.delta = Double.parseDouble(fields[2]);
                    pvInfo.strDevice = fields[3];
                    pvInfo.strTag = fields[4];

                    PVEventRecorder recorder = new PVEventRecorder();
                    PVReader<VType> pvr = gpClient.read(pvInfo.strPv).
                            addListener(recorder).
                            addReadListener(this).
                            start();
                    LOGGER.info("Registered HTC Pv: " + pvInfo.strPv);
                    pvInfo.pvr = pvr;
                    pvInfo.lastValue = null;
                    group.addPvReader(strKey, pvInfo);
                }
            };
        }
    }

    @Override
    public String getName() {
        return "Merlot - htc";
    }

    @Override
    public void deleted(String pid) {
        LOGGER.info("Remove config: " + pid);
    }

    @Override
    public void addGroup(String strGroup, String... args) {
        if (null != args) {
            if (null != args[0]) {
                Integer period = Integer.parseInt(args[0]) * 1000;
                ScheduleOptions schOptions = scheduler.AT(Date.from(Instant.now()), -1, period);
                schOptions.name(strGroup);

                SchedulerGroup group = new SchedulerGroup(schOptions);
                groups.put(strGroup, group);

                try {
                    scheduler.schedule(group, schOptions);
                } catch (Exception ex) {
                    LOGGER.error(ex.getMessage());
                }

            }
        }
    }

    @Override
    public void removeGroup(String strGroup) {
        scheduler.unschedule(strGroup);
        groups.remove(strGroup);
    }

    @Override
    public void schedulerGroup(String strGroup, int scanTime) {

    }

    @Override
    public void putPvRecord(String strPvName, String... args) {
        if (null != args) {
            if (null != args[0]) {

            }
        }
    }

    @Override
    public void removePvRecord(String strPvName) {

    }

    @Override
    public void pvChanged(PVEvent event, PVReader pvReader) {
        if (event.isType(PVEvent.Type.EXCEPTION)) {
            LOGGER.info("EVENT: " + event.toString());
        }

    }

    private class PVInfo {

        public PVReader<VType> pvr;
        public VNumber lastValue;
        public String strPv;
        public String strGroup;
        public Double delta;
        public String strDevice;
        public String strTag;
    }

    private class SchedulerGroup implements Job {

        private final ScheduleOptions schOptions;

        private final Map<String, PVInfo> pvs = new ConcurrentHashMap<>();
        private Map<String, String> properties = new Hashtable();
        private VNumber value;

        private Map<String, Boolean> config = new HashMap<String, Boolean>();

        public SchedulerGroup(ScheduleOptions schOptions) {
            this.schOptions = schOptions;
        }

        @Override
        public void execute(JobContext context) {
            pvs.forEach(new BiConsumer<String, PVInfo>() {
                @Override
                public void accept(String s, PVInfo pv) {

                    if ((pv.pvr.isConnected()) && (!pv.pvr.isPaused()
                            && mqttClient != null && mqttClient.isConnected())) {

                        value = (VNumber) pv.pvr.getValue();
                        if ((null == pv.lastValue) || !value.equals(pv.lastValue)) {

                            Double actualValue = value.getValue().doubleValue();
                            Double lastValue = (null == pv.lastValue) ? 0 : pv.lastValue.getValue().doubleValue();

                            if ((Math.abs(actualValue - lastValue)) > Math.abs(lastValue * (pv.delta / 100))) {
                                pv.lastValue = value;
                                long timeEpoch = value.getTime().getTimestamp().toEpochMilli();

                                String strValue = String.format(java.util.Locale.US, "{\n"
                                        + "\"device\":\"%s\",\n"
                                        + "\"timestamp\":%d,\n"
                                        + "\"measurements\":[\"%s\"],\n"
                                        + "\"values\":[%f]\n"
                                        + "}",
                                        pv.strDevice,
                                        timeEpoch,
                                        pv.strTag,
                                        value.getValue().doubleValue());
                                properties.clear();
                                properties.put("tag", pv.strDevice);
                                properties.put("value", strValue);

                                try {

                                    MqttMessage message = new MqttMessage(strValue.getBytes());
                                    message.setQos(0);

                                    getMqttClient().publish(pv.strDevice, message);

                                } catch (MqttException e) {
                                    LOGGER.error("Error posting to MQTT: " + e.getMessage());
                                }
                            }

                        }
                    } else {
                        LOGGER.info("PVReader not connected or MQTT connection not established");
                    }
                }
            });

        }

        public void addPvReader(final String strPVIndex, final PVInfo pvInfo) {
            pvs.put(strPVIndex, pvInfo);
        }

        public ScheduleOptions getScheduleOptions() {
            return this.schOptions;
        }

    }

}
