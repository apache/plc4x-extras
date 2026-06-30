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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MerlotKafkaDecanterCollectorImplTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotKafkaDecanterCollectorImplTest.class);
    private final static String MERLOT_KAFKA_TOPIC = "merlot_test";
    private MerlotKafkaDecanterCollectorImpl consumer;

    /**
     * Allows JUnit 5 to automatically detect and manage extensions declared as
     * instance fields, rather than having to register them using @ExtendWith at
     * the class level.
     */
    @RegisterExtension
    static final SharedKafkaTestResource server
            = new SharedKafkaTestResource()
                    .withBrokerProperty("num.partitions", "3")
                    .withBrokerProperty("auto.create.topics.enable", "false");

    /**
     * Create the topic {MERLOT_KAFKA_TOPIC} within the Kafka broker
     */
    @Test
    @Order(1)
    public void createTopic() {
        server.getKafkaTestUtils().createTopic(MERLOT_KAFKA_TOPIC, 3, (short) 1);
        LOGGER.info("Kafka Server Test: {}", server.getKafkaConnectString());
        LOGGER.info("Topic created: {}", server.getKafkaTestUtils().getTopicNames());
    }

    /**
     * Initializes a consumer group and the data ingestion process.
     * Additionally, it injects a record into the Kafka topic.
     *
     * @throws JsonProcessingException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    @Order(2)
    public void insertDataOnTopicAndConsume() throws JsonProcessingException, InterruptedException, ExecutionException {

        //Class to be tested
        consumer = new MerlotKafkaDecanterCollectorImpl(
                new EventAdmin() {
            @Override
            public void postEvent(Event event) {
                assertEquals("MerlotAlarmCollector", event.getProperty("loki.label.job"));
                assertEquals("demo.temperature", event.getProperty("loki.label.pvname"));
                assertEquals("merlot_test", event.getProperty("loki.label.topicalarm"));
                assertEquals("MAJOR", event.getProperty("loki.label.severity"));
                assertEquals(19.5, event.getProperty("alarm.value"));
            }

            @Override
            public void sendEvent(Event event) {
                LOGGER.info("Event sent: {}", event);
            }
        },
                new Unmarshaller() {
            @Override
            public Map<String, Object> unmarshal(InputStream in) {
                // Return empty map for testing
                return new java.util.HashMap<>();
            }
        }
        );

        //Startup Order of the KafkaConsumer Group. "test-pid" ->It is the name of the cfg configuration file
        consumer.activate("test-pid", setUpConsumer());
        //Topic data ingestion initialization {MERLOT_KAFKA_TOPIC}
        consumer.init();

        //Startup time required and KafkaConsumer group assignment (Wait 5 seconds until all consumers have been assigned a share of the topic)
        Thread.sleep(5000);

        //Message Alarm (Phoebus Format)
        String keyMessage = "state:/merlot_test/Area 2/pva:\\/\\/demo.temperature";
        String valueMessage = valueMessage();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(setUpProducer())) {
            ProducerRecord<String, String> record
                    = new ProducerRecord<>(MERLOT_KAFKA_TOPIC,
                            keyMessage,
                            valueMessage);

            var result = producer.send(record).get();

            assertNotNull(result);
            assertEquals(MERLOT_KAFKA_TOPIC, result.topic());
            assertEquals(0, result.offset());
        }

        //Data ingestion from topic {MERLOT_KAFKA_TOPIC} is stopped
        consumer.destroy();

    }

    /**
     *
     * @return The content of the alarm message is in JSON format (but as a text
     * string) and complies with the values defined by the ANSI/ISA-18.2
     * standard.
     * @throws JsonProcessingException
     */
    private String valueMessage() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("severity", "MAJOR");
        root.put("message", "Test message");
        root.put("value", "19.5");

        ObjectNode time = root.putObject("time");
        time.put("seconds", 1781972956);
        time.put("nano", 529877867);

        root.put("current_severity", "MAJOR");
        root.put("current_message", "This is a test message");

        return mapper.writeValueAsString(root);
    }

    /**
     *
     * @return The Kafka producer configuration
     */
    private Properties setUpProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", server.getKafkaConnectString());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        return props;
    }

    /**
     *
     * @return A dictionary with the Kafka consumer configuration that will be
     * used for the test class.
     */
    private Dictionary<String, Object> setUpConsumer() {
        Dictionary<String, Object> props = new java.util.Hashtable<>();
        props.put("bootstrap.servers", server.getKafkaConnectString());
        props.put("topic", MERLOT_KAFKA_TOPIC);
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        return props;
    }

}
