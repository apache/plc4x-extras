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
package org.apache.plc4x;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.tools.eventpump.EventPump;
import org.apache.plc4x.java.tools.eventpump.TagBatch;
import org.apache.plc4x.java.tools.eventpump.config.BatchConfiguration;
import org.apache.plc4x.java.tools.eventpump.config.EventPumpConfiguration;
import org.apache.plc4x.java.tools.eventpump.config.EventPumpFactory;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

public class Plc4xSchema extends AbstractSchema {

    protected final EventPumpConfiguration configuration;
    protected final EventPump eventPump;
    protected final QueueHandler handler;
    protected final Map<String, BlockingQueue<Record>> queues;
    protected final Map<String, Table> tableMap;
    /** batch id -&gt; connection id, so a record can be attributed to the PLC it came from. */
    protected final Map<String, String> connectionIds;

    public Plc4xSchema(EventPumpConfiguration configuration, long tableCutoff) throws Exception {
        this.configuration = configuration;
        this.handler = new QueueHandler();
        this.connectionIds = configuration.getBatches().stream()
            .collect(Collectors.toMap(
                BatchConfiguration::getId,
                BatchConfiguration::getConnectionId
            ));
        this.queues = configuration.getBatches().stream()
            .collect(Collectors.toMap(
                BatchConfiguration::getId,
                conf -> new ArrayBlockingQueue<>(1000)
            ));
        // Create the tables - one per batch
        this.tableMap = configuration.getBatches().stream()
            .collect(Collectors.toMap(
                BatchConfiguration::getId,
                conf -> defineTable(queues.get(conf.getId()), conf, tableCutoff)
            ));
        // Every batch reports to the same handler, which routes by batch id
        this.eventPump = EventPumpFactory.create(configuration,
            CachedPlcConnectionManager.getBuilder()
                .withConnectionFactory(new DefaultPlcDriverManager())
                .build(),
            handler);
        this.eventPump.startAll();
    }

    Table defineTable(BlockingQueue<Record> queue, BatchConfiguration configuration, Long limit) {
        if (limit <= 0) {
            return new Plc4xStreamTable(queue, configuration);
        } else {
            return new Plc4xTable(queue, configuration, limit);
        }
    }

    @Override
    protected Map<String, Table> getTableMap() {
        // Return a map of all jobs
        return this.tableMap;
    }

    public static class Record {

        public final Instant timestamp;
        public final String source;
        public final Map<String, Object> values;

        public Record(Instant timestamp, String source, Map<String, Object> values) {
            this.timestamp = timestamp;
            this.source = source;
            this.values = values;
        }
    }

    class QueueHandler implements TagBatch.TagBatchListener {

        @Override
        public void onTagsFetched(TagBatch batch, PlcReadResponse response) {
            String batchId = batch.getBatchId();
            Map<String, Object> results = response.getTagNames().stream()
                .collect(Collectors.toMap(name -> name, response::getObject));
            try {
                Record record = new Record(Instant.now(), connectionIds.get(batchId), results);
                queues.get(batchId).put(record);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PlcRuntimeException("Handling got interrupted", e);
            }
        }

    }
}
