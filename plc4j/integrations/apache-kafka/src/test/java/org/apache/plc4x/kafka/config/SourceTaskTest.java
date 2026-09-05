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
package org.apache.plc4x.kafka.config;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.plc4x.kafka.Plc4xSourceConnector;
import org.apache.plc4x.kafka.Plc4xSourceTask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.StringReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceTaskTest {

    private static final Logger log = LoggerFactory.getLogger(SourceTaskTest.class);
    private Plc4xSourceConnector sourceConnector;

    @BeforeEach
    public void setUp() throws Exception{
        log.info("-------------Setting Up SourceTaskTest----------------");
        Properties properties = new Properties();
        Path path = FileSystems.getDefault().getPath(
            "src/test/java/org/apache/plc4x/kafka/properties/",
            "source_task_no_error.properties");
        properties.load((new StringReader(new String(Files.readAllBytes(path)))));

        Map<String, String> map = new HashMap<>();
        for (final String name: properties.stringPropertyNames())
            map.put(name, properties.getProperty(name));

        sourceConnector = new Plc4xSourceConnector();
        sourceConnector.start(toStringMap(properties));
    }

    @Test
    public void parseConfig() throws Exception {
        log.info("-----------------SourceTaskTest no Errors----------------");
        log.info(sourceConnector.toString());
        List<Map<String, String>> config = sourceConnector.taskConfigs(2);
        assertEquals(1, config.size());
        assertEquals("machineA", config.get(0).get(Constants.CONNECTION_NAME_CONFIG));
        assertEquals("simulated://127.0.0.1", config.get(0).get(Constants.CONNECTION_STRING_CONFIG));
        assertEquals("1000", config.get(0).get(Constants.BUFFER_SIZE_CONFIG));
        assertEquals("5000", config.get(0).get(Constants.KAFKA_POLL_RETURN_CONFIG));
        assertEquals("simulateddashboard|machineData|1000|running#RANDOM/Temporary:BOOL|conveyorEntry#RANDOM/Temporary:BOOL|load#RANDOM/Temporary:BOOL|unload#RANDOM/Temporary:BOOL|transferLeft#RANDOM/Temporary:BOOL|transferRight#RANDOM/Temporary:BOOL|conveyorLeft#RANDOM/Temporary:BOOL|conveyorRight#RANDOM/Temporary:BOOL|numLargeBoxes#RANDOM/Temporary:DINT|numSmallBoxes#RANDOM/Temporary[0..1]:DINT,simulatedheartbeat|simulatedheartbeat|500|active#RANDOM/Temporary:DINT", config.get(0).get(Constants.QUERIES_CONFIG));
    }

    @Test
    public void startTasks() throws Exception {
        log.info("-----------------SourceTaskTest----------------");
        log.info(sourceConnector.toString());
        List<Map<String, String>> config = sourceConnector.taskConfigs(2);
        List<Plc4xSourceTask> sourceList = new ArrayList<>(config.size());
        for (Map<String, String> taskConfig : config) {
            log.info("Starting Source Task");
            Plc4xSourceTask sourceTask = new Plc4xSourceTask();
            sourceList.add(sourceTask);
            sourceTask.start(taskConfig);
        }
        Thread.sleep(5000);
        try {
            for (Plc4xSourceTask sourceTask : sourceList) {
                List<SourceRecord> records = sourceTask.poll();
                assertNotNull(records);
                // The jobs are configured to be read every 1000ms/500ms, so after 5s there
                // has to be data in the buffer - an empty result means nothing was ever read.
                assertFalse(records.isEmpty(), "expected the source task to have collected records");
            }
        } finally {
            for (Plc4xSourceTask sourceTask : sourceList) {
                sourceTask.stop();
            }
        }
    }

    /**
     * The task builds a PlcConnectionCache, which holds on to the connections it hands
     * out, so stopping the task has to release them - and stopping an already stopped task must
     * stay harmless, because closing a closed manager does nothing.
     */
    @Test
    public void stopClosesTheConnectionManager() throws Exception {
        log.info("-----------------SourceTaskTest.Stop----------------");
        Map<String, String> taskConfig = sourceConnector.taskConfigs(2).get(0);
        Plc4xSourceTask sourceTask = new Plc4xSourceTask();
        sourceTask.start(taskConfig);

        assertDoesNotThrow(sourceTask::stop);
        assertDoesNotThrow(sourceTask::stop);
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> map = new HashMap<>();
        for (String stringPropertyName : properties.stringPropertyNames()) {
            map.put(stringPropertyName, properties.getProperty(stringPropertyName));
        }
        return map;
    }

}
