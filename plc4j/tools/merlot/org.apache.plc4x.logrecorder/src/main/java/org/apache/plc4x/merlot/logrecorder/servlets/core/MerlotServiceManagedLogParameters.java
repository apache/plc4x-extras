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
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

@Getter
public class MerlotServiceManagedLogParameters implements ManagedService {

    private List<String> levels;
    private List<Tag> tags;
    private List<LogBook> logbooks;
    private List<Property> properties;
    private List<String> templates;

    public MerlotServiceManagedLogParameters() {
        this.levels = new ArrayList<>();
        this.tags = new ArrayList<>();;
        this.logbooks = new ArrayList<>();;
        this.properties = new ArrayList<>();;
        this.templates = new ArrayList<>();;
    }

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {

        //TODO: Se lee la informacion desde el archivo cfg
        levels.addAll(Arrays.asList(converterPropertyList((String) properties.get("levels"))));

        tags.add((Tag) converterProperty((String) properties.get("tags"), false));

        logbooks.add((LogBook) converterProperty((String) properties.get("logbooks"), true));
    }

    private List<Object> converterProperty(String propertyTag, boolean idType) {
        //TODO:
        //Doble separacion: Usar regex
        // Primero los ;
        // Segundo las ,
        // devolver la lista
        //si es false devuelve lista de tags
        // si es true devuelve lista de logbooks
        return null;

    }

    private String[] converterPropertyList(String propertyLevel) {
        if (!propertyLevel.isBlank() && !propertyLevel.isEmpty() && propertyLevel != null) {
            return propertyLevel.split(";");
        }
        return null;
    }

    @Getter
    @Setter
    @ToString
    public class LogBook {

        private String name;
        private String owner;
        private String state;
    }

    @Getter
    @Setter
    @ToString
    public class Tag {

        private String name;
        private String state;
    }

    @Getter
    @Setter
    @ToString
    public class Property {

        private String name;
        private String owner;
        private String state;
        private String[] attributes;
    }

}
