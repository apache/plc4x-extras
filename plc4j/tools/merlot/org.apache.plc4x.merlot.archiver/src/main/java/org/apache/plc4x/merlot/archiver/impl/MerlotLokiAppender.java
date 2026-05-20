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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLokiAppender implements EventHandler, ManagedService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotLokiAppender.class);

    private String url;
    private String username;
    private String password;
    private final Set<String> allowedTopics = new HashSet<>();
    private final Map<String, String> labels = new HashMap<>();

    private HttpClient httpClient;

    public MerlotLokiAppender() {
    }

    public void init() {
        LOGGER.info("Starting the Merlot-Loki appender");
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void destroy() {
        LOGGER.info("Deleting the Merlot-Loki appender module");
        httpClient.close();
        httpClient.shutdownNow();
    }

    //Inject HttpClient for use with WireMock
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    ////Inject Url for use with WireMock
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void handleEvent(Event event) {
        String topic = event.getTopic();

        //The topic must match those listed in the “loki.topics” property of the corresponding cfg file
        if (!allowedTopics.isEmpty() && !allowedTopics.contains(topic)) {
            return;
        }
        //For events without loki.label.<name> tags, general tags are assigned as the primary tags (cfg file)
        Map<String, String> eventLabels = new HashMap<>(this.labels);

        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("[").append(topic).append("] ");

        for (String prop : event.getPropertyNames()) {
            Object value = event.getProperty(prop);

            //Static tags will be sent as message content to Loki.
            if (prop.startsWith("loki.label.")) {
                String labelName = prop.substring("loki.label.".length());
                eventLabels.put(labelName, value.toString());
            } else if (!prop.equals("event.topics") && !prop.equals("service.id") && !prop.equals("subject")) {
                msgBuilder.append(prop).append("=").append(value).append(" ");
            }
        }

        sendLokiServer(msgBuilder.toString().trim(), eventLabels);
    }

    public void sendLokiServer(String message, Map<String, String> dynamicLabels) {
        if (this.url == null || this.url.isEmpty()) {
            return;
        }

        try {
            long timeNano = System.currentTimeMillis() * 1_000_000;

            //Label classifier
            StringBuilder labelsJson = new StringBuilder("{");

            StringJoiner sj = new StringJoiner(",");

            dynamicLabels.forEach((k, v) -> {
                sj.add("\"" + escapeJson(k) + "\":\"" + escapeJson(v) + "\"");
            });

            labelsJson.append(sj.toString()).append("}");

            //Payload to be sent to the Loki server, with the tags from the event as well as those saved in the cfg file.
            String payload = "{\"streams\": [{\"stream\": " + labelsJson + ", \"values\": [ [\"" + timeNano + "\", \"" + escapeJson(message) + "\"] ]}]}";

            //The message must be sent as JSON
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(this.url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

            //If the server requires authentication, it must be encrypted
            if (this.username != null && this.password != null) {
                String auth = this.username + ":" + this.password;
                String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encoded);
            }

            //Send the request asynchronously to avoid blocking the Karaf internal bus
            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 204 && response.statusCode() != 200) {
                            LOGGER.error("Loki rejected (Code {} ): {}", response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(e -> {
                        LOGGER.error("Loki Connection Error: {}", e.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            LOGGER.error("Critical Error in MerlotLokiAppender: {}", e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        //Escape character correction
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        //Connection parameters
        this.url = (String) properties.get("loki.url");
        this.username = (String) properties.get("loki.username");
        this.password = (String) properties.get("loki.password");

        // Topic configuration
        this.allowedTopics.clear();
        String strTopics = (String) properties.get("loki.topics");
        if (strTopics != null) {
            Arrays.stream(strTopics.split(",")).map(String::trim).forEach(this.allowedTopics::add);
        }

        // Configuration of dynamic, static labels
        this.labels.clear();
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.startsWith("loki.label.")) {
                String labelName = key.substring("loki.label.".length());
                this.labels.put(labelName, properties.get(key).toString());
            }
        }
    }
}
