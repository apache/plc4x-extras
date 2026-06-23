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
package org.apache.plc4x.merlot.kafka.impl;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.apache.plc4x.merlot.kafka.api.MerlotDecanterCollector;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MerlotKafkaDecanterCollectorImpl implements MerlotDecanterCollector, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotKafkaDecanterCollectorImpl.class);

    private String topic;
    private String eventAdminTopic;
    private volatile boolean consuming = false;
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private String messageType;

    private Dictionary<String, Object> properties;
    private KafkaConsumer<String, String> consumer;

    private final EventAdmin dispatcher;
    private  Unmarshaller unmarshaller;
    private ExecutorService executor;

    public MerlotKafkaDecanterCollectorImpl(EventAdmin dispatcher, Unmarshaller unmarshaller) {
        this.dispatcher = dispatcher;
        this.unmarshaller = unmarshaller;
    }

    @Override
    public void init() {
        consuming = true;
       this.executor = Executors.newSingleThreadExecutor();
       this.executor.execute(this);
    }

    @Override
    public void destroy() {
        consuming = false;
        shutdownInitiated.set(true);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void activate(String pid, Dictionary<String, Object> properties) {
        this.properties = properties;
        topic = getValue(properties, "topic", "decanter");
        eventAdminTopic = getValue(properties, EventConstants.EVENT_TOPIC, "decanter/collect/kafka/decanter");
        messageType = getValue(properties, "message.type", "text");

        Properties config = new Properties();

        String bootstrapServers = getValue(properties, "bootstrap.servers", "localhost:9092");
        config.put("bootstrap.servers", bootstrapServers);

        String groupId = getValue(properties, "group.id", "decanter");
        config.put("group.id", groupId);

        String enableAutoCommit = getValue(properties, "enable.auto.commit", "true");
        config.put("enable.auto.commit", enableAutoCommit);

        String autoCommitIntervalMs = getValue(properties, "auto.commit.interval.ms", "1000");
        config.put("auto.commit.interval.ms", autoCommitIntervalMs);

        String sessionTimeoutMs = getValue(properties, "session.timeout.ms", "10000");
        config.put("session.timeout.ms", sessionTimeoutMs);

        String keyDeserializer = getValue(properties, "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("key.deserializer", keyDeserializer);

        String valueDeserializer = getValue(properties, "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", valueDeserializer);

        String securityProtocol = getValue(properties, "security.protocol", null);
        if (securityProtocol != null)
            config.put("security.protocol", securityProtocol);

        String sslTruststoreLocation = getValue(properties, "ssl.truststore.location", null);
        if (sslTruststoreLocation != null)
            config.put("ssl.truststore.location", sslTruststoreLocation);

        String sslTruststorePassword = getValue(properties, "ssl.truststore.password", null);
        if (sslTruststorePassword != null)
            config.put("ssl.truststore.password", sslTruststorePassword);

        String sslKeystoreLocation = getValue(properties, "ssl.keystore.location", null);
        if (sslKeystoreLocation != null)
            config.put("ssl.keystore.location", sslKeystoreLocation);

        String sslKeystorePassword = getValue(properties, "ssl.keystore.password", null);
        if (sslKeystorePassword != null)
            config.put("ssl.keystore.password", sslKeystorePassword);

        String sslKeyPassword = getValue(properties, "ssl.key.password", null);
        if (sslKeyPassword != null)
            config.put("ssl.key.password", sslKeyPassword);

        String sslProvider = getValue(properties, "ssl.provider", null);
        if (sslProvider != null)
            config.put("ssl.provider", sslProvider);

        String sslCipherSuites = getValue(properties, "ssl.cipher.suites", null);
        if (sslCipherSuites != null)
            config.put("ssl.cipher.suites", sslCipherSuites);

        String sslEnabledProtocols = getValue(properties, "ssl.enabled.protocols", null);
        if (sslEnabledProtocols != null)
            config.put("ssl.enabled.protocols", sslEnabledProtocols);

        String sslTruststoreType = getValue(properties, "ssl.truststore.type", null);
        if (sslTruststoreType != null)
            config.put("ssl.truststore.type", sslTruststoreType);

        String sslKeystoreType = getValue(properties, "ssl.keystore.type", null);
        if (sslKeystoreType != null)
            config.put("ssl.keystore.type", sslKeystoreType);

        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            consumer = new KafkaConsumer<String, String>(config);
            String[] topics = topic.split(",");
            for (int i = 0; i < topics.length; i++) {
                topics[i] = topics[i].replaceAll("\\s+", "");
            }
            consumer.subscribe(Arrays.asList(topics));
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
        }
    }

    @Override
    public void run() {
        try {
            while (consuming && !shutdownInitiated.get()) {
                try {
                    consume();
                } catch (WakeupException e) {
                } catch (Exception e) {
                    LOGGER.info(e.getMessage(), e);
                }
            }
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    LOGGER.warn("Error closing Kafka consumer", e);
                }
            }
        }
    }

    private void consume() {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        if (records.isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("loki.label.job", "MerlotAlarmCollector");

        for (ConsumerRecord<String, String> record : records) {
            if (!consuming) {
                return;
            }

            //Data headers
            String key = record.key();

            //Alarm values
            String value = record.value();

            //LOGGER.info("Key: {} Value: {}", key, value);

            String pathPV = getPathPV(key);

            //Loki paramaters
            data.put("loki.label.topicalarm", getTopicAlarm(key));
            data.put("alarm.pathpvname", pathPV);
            data.put("loki.label.pvname", pathPV.substring(pathPV.indexOf("//") + 2));
            data.put("loki.label.component", getComponent(key));
            data.put("loki.label.severity", getSeverity(value));
            data.put("alarm.value", getValueAlarm(value));


            //Send event bus karaf
            Event event = new Event(eventAdminTopic, data);
            dispatcher.postEvent(event);
        }
    }


    //Initial parameters
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }


    //Kafka message parameters
    public static String getTopicAlarm(String keyText) {
        if (keyText == null) return null;
        String regex = ":/([^/]+)/";
        Matcher matcher = Pattern.compile(regex).matcher(keyText);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
    public static String getPathPV(String keyText) {
        if (keyText == null) return null;

        int indexEndProtocol = keyText.indexOf(":\\/\\/");
        if (indexEndProtocol == -1) {
            indexEndProtocol = keyText.indexOf("://");
        }

        if (indexEndProtocol != -1) {
            int indexLastSlash = keyText.lastIndexOf("/", indexEndProtocol);

            if (indexLastSlash != -1) {
                return keyText.substring(indexLastSlash + 1).replace("\\/\\/", "//");
            }
        }
        return null;
    }
    public static String getComponent(String keyText) {
        if (keyText == null) return null;
        String regex = "^[^:/]+:/[^/]+/(.+)/[a-zA-Z0-9]+:[\\\\/]{2}";

        Matcher matcher = Pattern.compile(regex).matcher(keyText);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
    public static String getSeverity(String valueText) {
        if (valueText == null) return null;

        String regex = "\"severity\"\\s*:\\s*\"([^\"]+)\"";

        Matcher matcher = Pattern.compile(regex).matcher(valueText);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    public static String getValueAlarm(String valueText) {
        if (valueText == null) return null;
        String regex = "\"value\"\\s*:\\s*\"([^\"]+)\"";

        Matcher matcher = Pattern.compile(regex).matcher(valueText);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
