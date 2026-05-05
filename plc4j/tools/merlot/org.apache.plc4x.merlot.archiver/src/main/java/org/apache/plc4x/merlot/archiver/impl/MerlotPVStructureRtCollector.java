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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.collections4.IterableMap;
import org.apache.commons.collections4.map.HashedMap;
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
import org.epics.pvaccess.ClientFactory;
import org.epics.pvaccess.client.Channel;
import org.epics.pvaccess.client.ChannelGet;
import org.epics.pvaccess.client.ChannelGetRequester;
import org.epics.pvaccess.client.ChannelProvider;
import org.epics.pvaccess.client.ChannelProviderRegistryFactory;
import org.epics.pvaccess.client.ChannelRequester;
import org.epics.pvdata.copy.CreateRequest;
import org.epics.pvdata.factory.ConvertFactory;
import org.epics.pvdata.misc.BitSet;
import org.epics.pvdata.pv.Convert;
import org.epics.pvdata.pv.MessageType;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVByte;
import org.epics.pvdata.pv.PVDouble;
import org.epics.pvdata.pv.PVFloat;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVLong;
import org.epics.pvdata.pv.PVScalar;
import org.epics.pvdata.pv.PVShort;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;
import org.epics.pvdata.pv.Status;
import org.epics.pvdata.pv.Structure;
import static org.epics.vtype.VImageDataType.pvBoolean;
import static org.epics.vtype.VImageDataType.pvByte;
import static org.epics.vtype.VImageDataType.pvDouble;
import static org.epics.vtype.VImageDataType.pvFloat;
import static org.epics.vtype.VImageDataType.pvInt;
import static org.epics.vtype.VImageDataType.pvLong;
import static org.epics.vtype.VImageDataType.pvShort;
import static org.epics.vtype.VImageDataType.pvString;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.LoggerFactory;

public class MerlotPVStructureRtCollector implements MerlotCollector, ManagedService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotPvRtCollectorImpl.class);
    private final Scheduler scheduler;
    private static final Pattern GROUP_INDEX_PATTERN
            = Pattern.compile("^RG(?<groupIndex>\\d{4})");
    private static final Pattern PV_INDEX_PATTERN
            = Pattern.compile("^PV(?<groupIndex>\\d{4})");

    protected static final String GROUP_INDEX = "groupIndex";

    private IterableMap<String, PVInfo> pvs = new HashedMap<>();
    private IterableMap<String, String> groups = new HashedMap<>();

    /*
    Parameters Grafana Live post
     */
    private static final HttpClient client = HttpClient.newHttpClient();
    private URI uri;
    private HttpRequest request;

    //Properties cfg
    private Map<String, String> configurationChannel = new HashMap<>();

    //PVA read
    private static ChannelProvider provider;

    public MerlotPVStructureRtCollector(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void init() {
        LOGGER.info("Launching the complex PVS reader");
        ClientFactory.start();

        provider = ChannelProviderRegistryFactory.getChannelProviderRegistry().getProvider("pva");

    }

    @Override
    public void destroy() {
        try {
            client.shutdownNow();
            ClientFactory.stop();

            provider.destroy();
        } catch (Exception e) {
            LOGGER.info("Error closing the reading channel {}", e.getMessage());
        }

    }

    @Override
    public void stop() {
    }

    @Override
    public void start() {

    }

    @Override
    public void addGroup(String strGroup, String... args) {
        if (null != args) {
            if (null != args[0]) {
                Integer period = Integer.parseInt(args[0]) * 1000;
                ScheduleOptions schOptions = scheduler.AT(Date.from(Instant.now()), -1, period);
                schOptions.name(strGroup);

                MerlotPVStructureRtCollector.SchedulerGroup group = new MerlotPVStructureRtCollector.SchedulerGroup(schOptions);

                try {
                    scheduler.schedule(group, schOptions);
                } catch (Exception ex) {
                    LOGGER.error(ex.getMessage());
                }

            }
        }
        LOGGER.info("Group: {} {}", strGroup, args[0]);
        if (strGroup != null && args.length == 1) {
            this.groups.put(strGroup, args[0]);
        }

    }

    @Override
    public void removeGroup(String strGroup) {

    }

    @Override
    public void schedulerGroup(String strGroup, int scanTime) {

    }

    @Override
    public void putPvRecord(String strPvName, String... args) {

        PVInfo pvInfo = new PVInfo();
        pvInfo.strPv = args[0];
        pvInfo.strGroup = args[1];
        pvInfo.delta = Double.parseDouble(args[2]);

        LOGGER.info("Data: {} {} {}", pvInfo.strPv, pvInfo.strGroup, pvInfo.delta);
        if (args != null && args.length == 3) {
            this.pvs.put(strPvName, pvInfo);
        }

    }

    @Override
    public void removePvRecord(String strPvName) {
        if (!strPvName.isEmpty() && !strPvName.equals(null)) {
            this.pvs.remove(strPvName);
        }

    }

    private void fillMap(String key, String value) {
        LOGGER.info("Loading property: {} with value {}", key, value);
        configurationChannel.put(key, value);
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        Matcher matcher;
        String strObject;
        String strKey;
        String strValue;

        System.out.println("Iniciando carga de propiedades");
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
                putPvRecord(strValue, fields);
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

    private class PVInfo {

        public String strPv;
        public String strGroup;
        public Double delta;
    }

    private class SchedulerGroup implements Job {

        private ScheduleOptions options;

        public SchedulerGroup(ScheduleOptions options) {
            this.options = options;
        }

        @Override
        public void execute(JobContext context) {
            pvs.forEach((s, pv) -> {
                // 1. Capturar canal y campo
                String channelPVA = getChannel(pv.strPv);
                String fieldQuery = getField(pv.strPv);

                if (channelPVA == null || fieldQuery == null) {
                    LOGGER.error("Formato de PV inválido: {}", pv.strPv);
                    return;
                }

                // 2. Latch LOCAL para cada iteración de PV
                CountDownLatch localLatch = new CountDownLatch(1);

                provider.createChannel(channelPVA, new ChannelRequester() {
                    @Override
                    public void channelCreated(Status status, Channel chnl) {
                        if (status.isSuccess()) {
                            PVStructure pvRequest = CreateRequest.create().createRequest(String.format("field(%s)", fieldQuery));

                            chnl.createChannelGet(new ChannelGetRequester() {
                                @Override
                                public void channelGetConnect(Status status, ChannelGet cg, Structure strctr) {
                                    if (status.isSuccess()) {
                                        cg.get();
                                    } else {
                                        LOGGER.warn("Fallo al conectar ChannelGet para {}", fieldQuery);
                                        localLatch.countDown();
                                    }
                                }

                                @Override
                                public void getDone(Status status, ChannelGet cg, PVStructure pvsStruct, BitSet bitset) {
                                    try {
                                        if (status.isSuccess()) {
                                            // Extraer el escalar de la estructura devuelta
                                            PVScalar value = pvsStruct.getSubField(PVScalar.class, fieldQuery);
                                            if (value != null) {
//                                                sendDataToGrafanaLive(fieldQuery, value);
                                            LOGGER.warn("El campo {} con valor: {}", fieldQuery, value); 
                                            } else {
                                                LOGGER.warn("El campo {} no se encontró en la estructura", fieldQuery);
                                            }
                                        }
                                    } finally {
                                        // 3. LIBERAR RECURSOS: IMPORTANTE para evitar fugas de memoria
                                        cg.destroy();
                                        chnl.destroy();
                                        localLatch.countDown();
                                    }
                                }

                                @Override
                                public String getRequesterName() {
                                    return "MerlotGetRequester";
                                }

                                @Override
                                public void message(String message, MessageType messageType) {
                                    LOGGER.debug(message);
                                }
                            }, pvRequest);
                        } else {
                            LOGGER.error("No se pudo crear el canal para: {}", channelPVA);
                            localLatch.countDown();
                        }
                    }

                    @Override
                    public void channelStateChange(Channel channel, Channel.ConnectionState connectionState) {
                        LOGGER.debug("Canal {} estado: {}", channel.getChannelName(), connectionState);
                    }

                    @Override
                    public String getRequesterName() {
                        return "MerlotChannelRequester";
                    }

                    @Override
                    public void message(String message, MessageType messageType) {
                        LOGGER.debug(message);
                    }

                }, ChannelProvider.PRIORITY_DEFAULT);

                try {
                    // Espera máxima por PV para no bloquear el scheduler infinitamente
                    if (!localLatch.await(2, TimeUnit.SECONDS)) {
                        LOGGER.warn("Timeout leyendo PV: {}", channelPVA);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        private void sendDataToGrafanaLive(String strPv, PVScalar value) {

            mapValue(value);
            long nanoTime = Instant.now().getEpochSecond() * 1_000_000_000L + Instant.now().getNano();
            String influxLine = String.format(java.util.Locale.US, "%s,%s=%s,%s=%s %s=%f %d",
                    configurationChannel.get("measurement"),
                    "area",
                    configurationChannel.get("tag"),
                    "pvname",
                    strPv,
                    "value",
                    mapValue(value),
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

        private Object mapValue(PVScalar pvScalar) {

            if (pvScalar == null) {
                return null;
            }

            ScalarType tipo = pvScalar.getScalar().getScalarType();

            return switch (tipo) {
                case pvDouble ->
                    ((PVDouble) pvScalar).get();
                case pvInt ->
                    ((PVInt) pvScalar).get();
                case pvBoolean ->
                    ((PVBoolean) pvScalar).get();
                case pvLong ->
                    ((PVLong) pvScalar).get();
                case pvFloat ->
                    ((PVFloat) pvScalar).get();
                case pvShort ->
                    ((PVShort) pvScalar).get();
                case pvByte ->
                    ((PVByte) pvScalar).get();
                case pvString ->
                    ((PVString) pvScalar).get();
                default ->
                    pvScalar.toString();

            };
        }
    }
}
