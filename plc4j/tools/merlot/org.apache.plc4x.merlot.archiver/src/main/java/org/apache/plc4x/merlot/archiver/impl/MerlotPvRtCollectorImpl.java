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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVEvent;
import org.epics.gpclient.PVEventRecorder;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.PVReaderListener;
import org.epics.vtype.*;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.LoggerFactory;

public class MerlotPvRtCollectorImpl implements MerlotCollector, ManagedServiceFactory, PVReaderListener {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotPvRtCollectorImpl.class);

    private static final Pattern GROUP_INDEX_PATTERN
            = Pattern.compile("^RG(?<groupIndex>\\d{4})");
    private static final Pattern PV_INDEX_PATTERN
            = Pattern.compile("^PV(?<groupIndex>\\d{4})");

    protected static final String GROUP_INDEX = "groupIndex";

    private final Scheduler scheduler;
    private final GPClientInstance gpClient;
    private final Map<String, SchedulerGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, MutablePair<SchedulerGroup, PVReader<VType>>> pvs = new ConcurrentHashMap<>();

    /*
    Parameters Grafana Live post
     */
    private static final HttpClient client = HttpClient.newHttpClient();
    private URI uri;
    private HttpRequest request;

    //Properties cfg
    private Map<String, String> configurationChannel = new HashMap<>();

    ///////
   
    public MerlotPvRtCollectorImpl(Scheduler scheduler, MerlotGPClient gpMerlotClient) {
        this.scheduler = scheduler;
        this.gpClient = gpMerlotClient.gpClientFactory("MerlotPvRt");
    }

    @Override
    public void init() {
        LOGGER.info("Starting the module for sending data to Grafana Live");
    }

    @Override
    public void destroy() {
        try {
            this.gpClient.close();
            client.shutdownNow();
        } catch (Exception e) {
            LOGGER.info("Error closing the reading channel {}", e.getMessage());
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
            try {
                scheduler.schedule(o, o.getScheduleOptions());
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage());
            }
        });
    }

    private void fillMap(String key, String value) {
        LOGGER.info("Loading property: {} with value {}", key, value);
        configurationChannel.put(key, value);
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        Matcher matcher;
        String strObject;
        String strKey;
        String strValue;

        stop();
        groups.clear();
        configurationChannel.clear();

        if (null == properties) {
            return;
        }
        /*
        Reads parameters grafana live
         */
        fillMap("url", (String) properties.get("url"));
        fillMap("stream", (String) properties.get("stream"));
        fillMap("apitoken", (String) properties.get("apitoken"));
        fillMap("measurement", (String) properties.get("measurement"));
        fillMap("tag", (String) properties.get("tag"));

        LOGGER.info("Url: {}\nStream: {}\nApiToken: {}\nMeasurement: {}\nTag: {}",
                configurationChannel.get("url"),
                configurationChannel.get("stream"),
                configurationChannel.get("apitoken"),
                configurationChannel.get("measurement"),
                configurationChannel.get("tag"));

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
                    pvInfo.strTag = fields[3];

                    
                    String channel = getChannel(pvInfo.strPv);
                    String fieldQuery = getField(pvInfo.strPv);


                    PVReader<VType> pvr = gpClient.read(String.format("pva://%s?request=field(%s)", channel, fieldQuery))
                            .addReadListener((event, pv) -> {
                            })
                            .start();
                    LOGGER.info("Registered RT Pv: " + pvInfo.strPv);

                    pvInfo.pvr = pvr;
                    pvInfo.lastValue = null;
                    group.addPvReader(strKey, pvInfo);
                }
            };
        }
    }

    private String getChannel(String pvName) {
        Matcher matcher = Pattern.compile("//([^/]+)/").matcher(pvName);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getField(String pvName) {
        Matcher matcher = Pattern.compile("([^/]+)$").matcher(pvName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public String getName() {
        return "Merlot-PV-Rt";
    }

    @Override
    public void deleted(String pid) {
        //
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
        public String strTag;
    }

    private class SchedulerGroup implements Job {

        private final ScheduleOptions schOptions;

        private final Map<String, PVInfo> pvs = new ConcurrentHashMap<>();
        private Map<String, String> properties = new Hashtable();
        private VNumber value;

        public SchedulerGroup(ScheduleOptions schOptions) {
            this.schOptions = schOptions;
        }

        @Override
        public void execute(JobContext context) {
            pvs.forEach(new BiConsumer<String, PVInfo>() {
                @Override
                public void accept(String s, PVInfo pv) {

                    if ((pv.pvr.isConnected()) && (!pv.pvr.isPaused())) {
                        
                        value = (VNumber) pv.pvr.getValue();
                        if ((null == pv.lastValue) || !value.equals(pv.lastValue)) {

                            Double actualValue = ((VNumber) value).getValue().doubleValue();
                            Double lastValue = (null == pv.lastValue) ? 0 : pv.lastValue.getValue().doubleValue();

                            if ((Math.abs(actualValue - lastValue)) > Math.abs(lastValue * (pv.delta / 100))) {
                                pv.lastValue = value;

                                sendDataToGrafanaLive(pv.strPv, value);
                            }

                        }
                    } else{
                        LOGGER.info("PVReader {} offline", pv.pvr);
                    }
                }

                private void sendDataToGrafanaLive(String strPv, VNumber value) { 
                    long nanoTime = value.getTime().getTimestamp().getEpochSecond() * 1_000_000_000L + Instant.now().getNano();

                   
                    String influxLine = String.format(java.util.Locale.US, "%s,%s=%s,%s=%s %s=%f %d",
                            configurationChannel.get("measurement"),
                            "area",
                            configurationChannel.get("tag"),
                            "pvname",
                            strPv,
                            "value",
                            value.getValue().doubleValue(),
                            nanoTime);

                    request = (HttpRequest) HttpRequest.newBuilder()
                            .uri(uri.create(
                                    String.format("%s%s%s",
                                            configurationChannel.get("url"),
                                            "/api/live/push/",
                                            configurationChannel.get("stream"))))
                            .timeout(Duration.ofSeconds(3))
                            .header("Authorization", "Bearer " + configurationChannel.get("apitoken"))
                            .header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString(influxLine))
                            .build();

                    try {
                        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> {
                                    if (response.statusCode() >= 400) {
                                        LOGGER.warn("Grafana returned an error: {}", response.statusCode());
                                    }
                                    return response;
                                })
                                .exceptionally(ex -> {
                                    LOGGER.error("Critical error during transmission: {}", ex.getMessage());
                                    return null;
                                });
                    } catch (Exception e) {
                        LOGGER.error("Critical error when connecting to Grafana Live: {}", e.getMessage());
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
