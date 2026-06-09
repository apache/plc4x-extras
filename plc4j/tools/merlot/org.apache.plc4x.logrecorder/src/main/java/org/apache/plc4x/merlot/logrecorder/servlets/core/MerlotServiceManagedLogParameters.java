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
package org.apache.plc4x.merlot.logrecorder.servlets.core;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.LoggerFactory;

@Getter
public class MerlotServiceManagedLogParameters implements ManagedService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotServiceManagedLogParameters.class);
    private List<Level> levels;
    private List<Tag> tags;
    private List<LogBook> logbooks;
    private List<Property> properties;
    private List<String> templates;

    public MerlotServiceManagedLogParameters() {
        this.levels = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.logbooks = new ArrayList<>();
        this.properties = new ArrayList<>();
        this.templates = new ArrayList<>();
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        LOGGER.info("Reading properties");
        cleanList();
        converterPropertyLevels((String) properties.get("levels"));
        converterPropertyTagOrLogbook((String) properties.get("tags"), true);
        converterPropertyTagOrLogbook((String) properties.get("logbooks"), false);
    }

    private void converterPropertyLevels(String propertyLevel) {
        LOGGER.info("Reading levels");
        if ((!propertyLevel.isBlank()) && (!propertyLevel.isEmpty()) && (propertyLevel != null)) {

            for (String splitLevel : propertyLevel.split(";")) {
                this.levels.add(new Level("name", splitLevel));
            }

        }
    }

    private void converterPropertyTagOrLogbook(String property, boolean idType) {

        String regexTag = "([^,;]+),([^,;]+)(?=;|$)";
        String regexLogbook = "([^,;]+),([^,;]+),([^,;]+)(?=;|$)";
        Pattern pattern;
        Matcher matcher;

        if ((!property.isBlank()) && (!property.isEmpty()) && (property != null)) {

            if (idType) {
                LOGGER.info("Reading tags");
                pattern = Pattern.compile(regexTag);
                matcher = pattern.matcher(property.trim());

                while (matcher.find()) {
                    String key = matcher.group(1).trim();
                    String state = matcher.group(2).trim();
                    this.tags.add(new Tag(key, state));
                }
            } else {
                LOGGER.info("Reading books");
                pattern = Pattern.compile(regexLogbook);
                matcher = pattern.matcher(property.trim());

                while (matcher.find()) {
                    String name = matcher.group(1).trim();
                    String role = matcher.group(2).trim();
                    String state = matcher.group(3).trim();
                    this.logbooks.add(new LogBook(name, role, state));
                }
            }

        }

    }

    private void cleanList() {
        LOGGER.info("Clean lists");
        this.levels.clear();
        this.tags.clear();
        this.logbooks.clear();
        this.properties.clear();
        this.templates.clear();
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public class Level {

        private String key;
        private String description;
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public class LogBook {

        private String key;
        private String owner;
        private String state;
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public class Tag {

        private String key;
        private String state;
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public class Property {

        private String name;
        private String owner;
        private String state;
        private String[] attributes;
    }

}
