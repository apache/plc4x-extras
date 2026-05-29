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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.Session;
import org.apache.plc4x.merlot.archiver.api.MerlotCollector;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.apache.plc4x.merlot.scheduler.api.ScheduleOptions;
import org.apache.plc4x.merlot.scheduler.api.Scheduler;

import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.write.WriteProcessException;
import org.apache.tsfile.file.metadata.IDeviceID;
import static org.apache.tsfile.file.metadata.IDeviceID.LOGGER;
import org.apache.tsfile.file.metadata.enums.CompressionType;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.read.TsFileReader;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.read.common.Field;
import org.apache.tsfile.read.common.RowRecord;
import org.apache.tsfile.read.expression.QueryExpression;
import org.apache.tsfile.read.query.dataset.QueryDataSet;
import org.apache.tsfile.write.TsFileWriter;
import org.apache.tsfile.write.record.TSRecord;
import org.apache.tsfile.write.record.datapoint.DataPoint;
import org.apache.tsfile.write.record.datapoint.DoubleDataPoint;
import org.apache.tsfile.write.record.datapoint.LongDataPoint;
import org.apache.tsfile.write.schema.MeasurementSchema;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVEvent;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.PVReaderListener;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.LoggerFactory;

public class MerlotPvHtcCollectorImpl implements MerlotCollector, ManagedServiceFactory, PVReaderListener, MqttCallbackExtended {

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

    //Buffer IoTDB fail connection (Local Buffer)
    private volatile TsFileWriter tsFileWriter;
    private final BundleContext ctx;
    private File file;
    private final String MERLOT_DATA_DIRECTORY = "karaf.data";
    private static final Set<String> timeSeries = java.util.concurrent.ConcurrentHashMap.newKeySet();

    //Parameter Broker MQTT IoTDB
    private volatile MqttAsyncClient mqttClient = null;
    
    public MerlotPvHtcCollectorImpl(Scheduler scheduler, MerlotGPClient gpMerlotClient, BundleContext ctx) {
        this.scheduler = scheduler;
        this.gpClient = gpMerlotClient.gpClientFactory("MerlotPvHtc");
        this.ctx = ctx;
    }

    @Override
    public void init() {
        LOGGER.info("Starting the PV Collector");
    }

    public MqttAsyncClient getMqttClient() {
        return mqttClient;
    }

    private void createConnection(String mqttUrl, String username, String password) {

        try {
            mqttClient = new MqttAsyncClient(mqttUrl, "Merlot-HTC-IoTDB" + System.currentTimeMillis(), new MemoryPersistence());
            mqttClient.setCallback(this);

            /*Note: Set a property in IoTDB (Server): File /config/iotdb-system.properties: 
            dn_session_timeout_threshold=0; To prevent information from being deleted from open channels
             */
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName(username);
            connOpts.setPassword(password.toCharArray());

            connOpts.setCleanSession(false);
            connOpts.setKeepAliveInterval(30);
            connOpts.setConnectionTimeout(30);
            connOpts.setMaxInflight(1000);
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

            o.pvs.values().forEach(pvInfo -> {
                if (pvInfo.pvr != null) {
                    pvInfo.pvr.close();
                }
            });
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

            while (this.mqttClient != null) {
                try {
                    this.mqttClient.disconnectForcibly(0, 0, true);
                    if (!this.mqttClient.isConnected()) {
                        this.mqttClient.close(true);
                        this.mqttClient = null;
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    LOGGER.info(ex.getMessage());
                } catch (MqttException ex) {
                    LOGGER.info(ex.getMessage());
                }
            }
        }

        //Make sure that too many MQTT connections aren't created. There should only be one.
        if (this.mqttClient == null) {
            createConnection((String) properties.get("broker"), (String) properties.get("useriotdb"), (String) properties.get("passwordiotdb"));
        }

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

                    String channel = getChannel(pvInfo.strPv);
                    String fieldQuery = getField(pvInfo.strPv);

                    String pathPV = "";
                    if (fieldQuery == null) {
                        pathPV = String.format("pva://%s", channel);
                    } else {

                        pathPV = String.format("pva://%s?request=field(%s)", channel, fieldQuery);
                    }
                    PVReader<VType> pvr = gpClient.read(pathPV)
                            .addReadListener((event, pv) -> {
                            })
                            .start();
                    LOGGER.info("Registered HTC Pv: " + pvInfo.strPv);
                    pvInfo.pvr = pvr;
                    pvInfo.lastValue = null;
                    group.addPvReader(strKey, pvInfo);
                }
            };
        }
    }

    private String getChannel(String pvName) {
        if (pvName == null || pvName.isEmpty()) {
            return null;
        }

        int startIndex = pvName.indexOf("://");
        if (startIndex == -1) {
            return null;
        }

        startIndex += 3;

        int endIndex = pvName.indexOf("/", (startIndex));

        if (endIndex != -1) {
            return pvName.substring(startIndex, endIndex);
        } else {
            return pvName.substring(startIndex);
        }
    }

    private String getField(String pvName) {
        if (pvName == null || pvName.isEmpty()) {
            return null;
        }

        int protocolIndex = pvName.indexOf("://");
        if (protocolIndex == -1) {
            return null;
        }

        String withoutProtocol = pvName.substring(protocolIndex + 3);

        int lastSlashIndex = withoutProtocol.lastIndexOf("/");

        if (lastSlashIndex != -1) {
            return withoutProtocol.substring(lastSlashIndex + 1);
        } else {
            return null;
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

    @Override
    public void connectionLost(Throwable cause) {

        LOGGER.info("The connection to the IoTDB broker has been lost: {}", cause.getMessage());
        String karafDataDir = ctx.getProperty(MERLOT_DATA_DIRECTORY);

        if (file == null) {
            file = new File(karafDataDir, "buffer.tsfile");
        }

        if (!file.exists()) {
            try {

                LOGGER.info("The buffer file did not exist; it is being created");
                file.createNewFile();
            } catch (IOException ex) {
                LOGGER.info("The file could not be created in the /data directory of Merlot");
            }
        }

        if (file.exists() && tsFileWriter == null) {
            try {
                LOGGER.info("The file already exists; create the writer and assign the file");

                tsFileWriter = new TsFileWriter(file);
            } catch (Exception ex) {
                LOGGER.error("Error assigning tsfilewriter: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        //Not in use
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        //Not in use
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            LOGGER.info("Successful reconnection detected automatically");


            new Thread(() -> {
                try {
                    if (this.tsFileWriter != null) {
                        LOGGER.info("Closing the file writer");
                        this.tsFileWriter.close();

                        LOGGER.info("Sending a buffer to the IoTDB broker");
                        sendBufferToIoTDB();

                        LOGGER.info("Removing the reference to the current writer");
                        this.tsFileWriter = null;
                    }
                    this.timeSeries.clear();
                } catch (IOException e) {
                    LOGGER.error("Error processing the buffer after reconnection: " + e.getMessage(), e);
                }

            }).start();

        } else {
            LOGGER.info("Initial connection established with the broker: " + serverURI);
        }

    }

    public void sendBufferToIoTDB() {

        List<String> measurements = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        String device = "";

        try {
            String filePath = this.tsFileWriter.getIOWriter().getFile().toString();

            try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
                List<org.apache.tsfile.read.common.Path> paths = reader.getAllPaths();

                try (TsFileReader tsReader = new TsFileReader(reader)) {
                    QueryExpression expr = QueryExpression.create(paths, null);
                    QueryDataSet dataSet = tsReader.query(expr);

                    while (dataSet.hasNext()) {
                        RowRecord row = dataSet.next();
                        long timestamp = row.getTimestamp();

                        List<Field> fields = row.getFields();

                        for (int i = 0; i < fields.size(); i++) {
                            Field field = fields.get(i);

                            //Device
                            device = dataSet.getPaths().get(i).getDeviceString();

                            //Measurements and Values
                            if (field != null) {
                                measurements.add(dataSet.getPaths().get(i).getMeasurement());
                                values.add(field.getDoubleV());
                            }

                        }

                        //Constructing JSON messages for the batch to IoTDB
                        String jsonMeasurements = measurements.toString().replace("[", "[\"").replace("]", "\"]").replace(", ", "\",\"");
                        String jsonValues = values.toString();

                        //Added to the JSON message list
                        messages.add(
                                String.format(java.util.Locale.US, "{\n"
                                        + "  \"device\":\"%s\",\n"
                                        + "  \"timestamp\":%d,\n"
                                        + "  \"measurements\":%s,\n"
                                        + "  \"values\":%s\n"
                                        + "}",
                                        device,
                                        timestamp,
                                        jsonMeasurements,
                                        jsonValues)
                        );

                        measurements.clear();
                        values.clear();

                    }

                    try {
                        //Converts a JSON message list into a string
                        String convertedMessage = messages.toString();
                        //Serialize the string. Then the MQTT message is constructed.
                        MqttMessage message = new MqttMessage(convertedMessage.getBytes());
                        message.setQos(0);

                        //A message is sent to the IoTDB MQTT broker
                        getMqttClient().publish("iotdb/insert", message);

                    } catch (MqttException e) {
                        LOGGER.error("Error posting to MQTT: " + e.getMessage());
                    }
                }

            }
        } catch (Exception e) {
            System.err.println("Error reading the buffer.tsfile file " + e.getMessage());
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
                        if (value != null && ((null == pv.lastValue) || !value.equals(pv.lastValue))) {

                            Double actualValue = value.getValue().doubleValue();
                            Double lastValue = (null == pv.lastValue) ? 0 : pv.lastValue.getValue().doubleValue();

                            if ((Math.abs(actualValue - lastValue)) > Math.abs(lastValue * (pv.delta / 100))) {
                                pv.lastValue = value;
                                long timeEpoch = Instant.now().toEpochMilli();

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

                                    getMqttClient().publish("iotdb/insert", message);

                                } catch (MqttException e) {
                                    LOGGER.error("Error posting to MQTT: " + e.getMessage());
                                }
                            }
                        }
                    } else if (!mqttClient.isConnected() && pv.pvr.isConnected()) {

                        VNumber valueT = (VNumber) pv.pvr.getValue();
                        if (valueT != null && ((null == pv.lastValue) || !valueT.equals(pv.lastValue))) {

                            Double actualValue = valueT.getValue().doubleValue();
                            Double lastValue = (null == pv.lastValue) ? 0 : pv.lastValue.getValue().doubleValue();

                            if ((Math.abs(actualValue - lastValue)) > Math.abs(lastValue * (pv.delta / 100))) {
                                pv.lastValue = valueT;
                                long timeEpoch = Instant.now().toEpochMilli();
//                                LOGGER.info("PV: {} Value: {} Timestamp: {}",
//                                        pv.strTag, valueT.getValue().doubleValue(), timeEpoch);

                                try {
                                    if (timeSeries != null && !timeSeries.contains(pv.strDevice + "." + pv.strTag)) {
                                        tsFileWriter.registerTimeseries(pv.strDevice,
                                                new MeasurementSchema(
                                                        pv.strTag,
                                                        TSDataType.DOUBLE,
                                                        TSEncoding.GORILLA,
                                                        CompressionType.LZMA2));

                                        timeSeries.add(pv.strDevice + "." + pv.strTag);

                                    }
                                    TSRecord tsRecord = new TSRecord(pv.strDevice, timeEpoch);

                                    DataPoint dataPoint = new DoubleDataPoint(pv.strTag, valueT.getValue().doubleValue());
                                    tsRecord.addTuple(dataPoint);

                                    if (tsFileWriter.writeRecord(tsRecord)) {
                                        LOGGER.debug("A write operation was performed to the buffer file");
                                    }

                                } catch (Exception e) {

                                    LOGGER.info("Error processing device: {} ", pv.strDevice);

                                }

                            }

                        }

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
