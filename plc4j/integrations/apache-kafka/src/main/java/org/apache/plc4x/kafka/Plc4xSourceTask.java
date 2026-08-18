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
package org.apache.plc4x.kafka;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.data.*;
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.tools.eventpump.EventPump;
import org.apache.plc4x.java.tools.eventpump.TagBatch;
import org.apache.plc4x.java.tools.eventpump.triggers.TimerTrigger;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;
import org.apache.plc4x.kafka.config.Constants;
import org.apache.plc4x.kafka.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

/**
 * Source Connector Task polling the data source at a given rate.
 * A timer thread is scheduled which sets the fetch flag to true every rate milliseconds.
 * When poll() is invoked, the calling thread waits until the fetch flag is set for WAIT_LIMIT_MILLIS.
 * If the flag does not become true, the method returns null, otherwise a fetch is performed.
 */
public class Plc4xSourceTask extends SourceTask {

    private static final Logger log = LoggerFactory.getLogger(Plc4xSourceTask.class);

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
        .define(Constants.CONNECTION_NAME_CONFIG,
            ConfigDef.Type.STRING,
            ConfigDef.Importance.HIGH,
            Constants.CONNECTION_NAME_STRING_DOC)
        .define(Constants.CONNECTION_STRING_CONFIG,
            ConfigDef.Type.STRING,
            ConfigDef.Importance.HIGH,
            Constants.CONNECTION_STRING_DOC)
        .define(Constants.KAFKA_POLL_RETURN_CONFIG,
            ConfigDef.Type.INT,
            Constants.KAFKA_POLL_RETURN_DEFAULT,
            ConfigDef.Importance.HIGH,
            Constants.KAFKA_POLL_RETURN_DOC)
        .define(Constants.BUFFER_SIZE_CONFIG,
            ConfigDef.Type.INT,
            Constants.BUFFER_SIZE_DEFAULT,
            ConfigDef.Importance.HIGH,
            Constants.BUFFER_SIZE_DOC)
        .define(Constants.QUERIES_CONFIG,
            ConfigDef.Type.LIST,
            ConfigDef.Importance.HIGH,
            Constants.QUERIES_DOC);


    private static final Schema KEY_SCHEMA =
        new SchemaBuilder(Schema.Type.STRUCT)
            .field(Constants.SOURCE_NAME_FIELD, Schema.STRING_SCHEMA)
            .field(Constants.JOB_NAME_FIELD, Schema.STRING_SCHEMA)
            .build();

    // Internal buffer into which all incoming responses are written to.
    private ArrayBlockingQueue<SourceRecord> buffer;
    private Integer pollReturnInterval;
    private EventPump eventPump;
    private PlcConnectionManager connectionManager;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String version() {
        return VersionUtil.getVersion();
    }

    @Override
    public void start(Map<String, String> props) {
        AbstractConfig config = new AbstractConfig(CONFIG_DEF, props);
        String connectionName = config.getString(Constants.CONNECTION_NAME_CONFIG);
        String plc4xConnectionString = config.getString(Constants.CONNECTION_STRING_CONFIG);
        pollReturnInterval = config.getInt(Constants.KAFKA_POLL_RETURN_CONFIG);
        Integer bufferSize = config.getInt(Constants.BUFFER_SIZE_CONFIG);

        // Create a buffer with a capacity of BUFFER_SIZE_CONFIG elements which schedules access in a fair way.
        buffer = new ArrayBlockingQueue<>(bufferSize, true);

        connectionManager = CachedPlcConnectionManager.getBuilder()
            .withConnectionFactory(new DefaultPlcDriverManager())
            .build();

        eventPump = new EventPump();

        List<String> jobConfigs = config.getList(Constants.QUERIES_CONFIG);
        for (String jobConfig : jobConfigs) {
            String[] jobConfigSegments = jobConfig.split("\\|");
            if (jobConfigSegments.length < 4) {
                log.warn("Error in job configuration '{}'. " +
                    "The configuration expects at least 4 segments: " +
                    "{job-name}|{topic}|{rate}(|{tag-alias}#{tag-address})+", jobConfig);
                continue;
            }

            final String jobName = jobConfigSegments[0];
            final String topic = jobConfigSegments[1];
            final int rate = Integer.parseInt(jobConfigSegments[2]);

            Map<String, String> tags = new LinkedHashMap<>();
            for (int i = 3; i < jobConfigSegments.length; i++) {
                String[] tagSegments = jobConfigSegments[i].split("#");
                if (tagSegments.length != 2) {
                    log.warn("Error in job configuration '{}'. " +
                            "The tag segment expects a format {tag-alias}#{tag-address}, but got '{}'",
                        jobName, jobConfigSegments[i]);
                    continue;
                }
                tags.put(tagSegments[0], tagSegments[1]);
            }
            if (tags.isEmpty()) {
                log.warn("Job configuration '{}' doesn't contain any valid tags ... skipping it.", jobName);
                continue;
            }

            // One batch per job: all tags of a job are read together, at the job's rate.
            TagBatch batch = TagBatch.builder()
                .withBatchId(jobName)
                .withConnectionFactory(connectionManager)
                .withConnectionString(plc4xConnectionString)
                .addTagAddresses(tags)
                .withTrigger(new TimerTrigger(rate, TimeUnit.MILLISECONDS))
                .withListener(new TagBatch.TagBatchListener() {
                    @Override
                    public void onTagsFetched(TagBatch tagBatch, PlcReadResponse response) {
                        handleResponse(tagBatch.getBatchId(), connectionName, topic, response);
                    }

                    @Override
                    public void onError(TagBatch tagBatch, Throwable error) {
                        log.error("Error reading tags for job '{}': {}", tagBatch.getBatchId(), error.getMessage());
                    }

                    @Override
                    public void onFetchSkipped(TagBatch tagBatch, long lastFetchDurationMs, long consecutiveSkips) {
                        log.warn("Job '{}' is configured to be read every {}ms, but the last read took {}ms " +
                                "({} consecutive reads skipped).",
                            tagBatch.getBatchId(), rate, lastFetchDurationMs, consecutiveSkips);
                    }
                })
                .build();
            eventPump.addBatch(batch);
        }

        eventPump.startAll();
    }

    /**
     * Turns one response into a Kafka {@link SourceRecord} and adds it to the buffer that
     * {@link #poll()} drains.
     */
    private void handleResponse(String jobName, String sourceName, String topic, PlcReadResponse response) {
        Map<String, Object> results = response.getTagNames().stream()
            .collect(HashMap::new, (map, name) -> map.put(name, response.getObject(name)), HashMap::putAll);
        try {
            Long timestamp = System.currentTimeMillis();

            Map<String, String> sourcePartition = new HashMap<>();
            sourcePartition.put("sourceName", sourceName);
            sourcePartition.put("jobName", jobName);

            Map<String, Long> sourceOffset = Collections.singletonMap("offset", timestamp);

            // Prepare the key structure.
            Struct key = new Struct(KEY_SCHEMA)
                .put(Constants.SOURCE_NAME_FIELD, sourceName)
                .put(Constants.JOB_NAME_FIELD, jobName);

            // Build the Schema for the result struct.
            SchemaBuilder tagSchemaBuilder = SchemaBuilder.struct()
                .name("org.apache.plc4x.kafka.schema.Tag");


            for (Map.Entry<String, Object> result : results.entrySet()) {
                // Get tag-name and -value from the results.
                String tagName = result.getKey();
                Object tagValue = result.getValue();

                // Get the schema for the given value type.
                Schema valueSchema = getSchema(tagValue);

                // Add the schema description for the current tag.
                tagSchemaBuilder.field(tagName, valueSchema);
            }
            Schema tagSchema = tagSchemaBuilder.build();

            Schema recordSchema = SchemaBuilder.struct()
                .name("org.apache.plc4x.kafka.schema.JobResult")
                .doc("PLC Job result. This contains all of the received PLCValues as well as a received timestamp")
                .field(Constants.TAGS_CONFIG, tagSchema)
                .field(Constants.TIMESTAMP_CONFIG, Schema.INT64_SCHEMA)
                .field(Constants.EXPIRES_CONFIG, Schema.OPTIONAL_INT64_SCHEMA)
                .build();

            // Build the struct itself.
            Struct tagStruct = new Struct(tagSchema);
            for (Map.Entry<String, Object> result : results.entrySet()) {
                // Get tag-name and -value from the results.
                String tagName = result.getKey();
                Object tagValue = result.getValue();

                if (tagSchema.field(tagName).schema().type() == Schema.Type.ARRAY) {
                    tagStruct.put(tagName, ((List) tagValue).stream().map(p -> ((PlcValue) p).getObject()).collect(Collectors.toList()));
                } else {
                    tagStruct.put(tagName, tagValue);
                }
            }

            Struct recordStruct = new Struct(recordSchema)
                .put(Constants.TAGS_CONFIG, tagStruct)
                .put(Constants.TIMESTAMP_CONFIG, timestamp);

            // Prepare the source-record element.
            SourceRecord sourceRecord = new SourceRecord(
                sourcePartition, sourceOffset,
                topic,
                KEY_SCHEMA, key,
                recordSchema, recordStruct
            );

            // Add the new source-record to the buffer.
            buffer.add(sourceRecord);
        } catch (Exception e) {
            log.error("Error while parsing returned values", e);
        }
    }

    @Override
    public void stop() {
        synchronized (this) {
            if (eventPump != null) {
                eventPump.close();
            }
            // The pump is stopped first, so nothing asks for a connection while the cache
            // that holds them is being torn down.
            if (connectionManager != null) {
                try {
                    connectionManager.close();
                } catch (PlcConnectionException e) {
                    log.error("Error closing the connection manager", e);
                }
            }
            notifyAll(); // wake up thread waiting in awaitFetch
        }
    }

    @Override
    public List<SourceRecord> poll() {
        if (!buffer.isEmpty()) {
            int numElements = buffer.size();
            List<SourceRecord> result = new ArrayList<>(numElements);
            buffer.drainTo(result, numElements);
            return result;
        }
        try {
            List<SourceRecord> result = new ArrayList<>(1);
            SourceRecord temp = buffer.poll(pollReturnInterval + (long) random.nextInt((int) Math.round(pollReturnInterval * 0.05)), TimeUnit.MILLISECONDS);
            if (temp == null) {
                return Collections.emptyList();
            }
            result.add(temp);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    private Schema getSchema(Object value) {
        Objects.requireNonNull(value);

        if (value instanceof PlcValue) {
            value = ((PlcValue) value).getObject();
        }

        if (value instanceof List) {
            List list = (List) value;
            if (list.isEmpty()) {
                throw new ConnectException("Unsupported empty lists.");
            }
            // In PLC4X list elements all contain the same type.
            Object firstElement = list.get(0);
            Schema elementSchema = getSchema(firstElement);
            return SchemaBuilder.array(elementSchema).build();
        }
        if (value instanceof BigInteger) {
            // no support yet
        }
        if (value instanceof BigDecimal) {
            // no support yet
        }
        if (value instanceof Boolean) {
            return Schema.OPTIONAL_BOOLEAN_SCHEMA;
        }
        if (value instanceof byte[]) {
            return Schema.OPTIONAL_BYTES_SCHEMA;
        }
        if (value instanceof Byte) {
            return Schema.OPTIONAL_INT8_SCHEMA;
        }
        if (value instanceof Double) {
            return Schema.OPTIONAL_FLOAT64_SCHEMA;
        }
        if (value instanceof Float) {
            return Schema.OPTIONAL_FLOAT32_SCHEMA;
        }
        if (value instanceof Integer) {
            return Schema.OPTIONAL_INT32_SCHEMA;
        }
        if (value instanceof LocalDate) {
            return Date.builder().optional().build();
        }
        if (value instanceof LocalDateTime) {
            return Timestamp.builder().optional().build();
        }
        if (value instanceof LocalTime) {
            return Time.builder().optional().build();
        }
        if (value instanceof Long) {
            return Schema.OPTIONAL_INT64_SCHEMA;
        }
        if (value instanceof Short) {
            return Schema.OPTIONAL_INT16_SCHEMA;
        }
        if (value instanceof String) {
            return Schema.OPTIONAL_STRING_SCHEMA;
        }
        // TODO: add support for collective and complex types
        throw new ConnectException(String.format("Unsupported data type %s", value.getClass().getName()));
    }

}
