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
package org.apache.plc4x.malbec.projecttype.panel.plant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88Element;

public final class PlantSnapshot {

    public record Variable(String name, String type) {
    }

    public record AreaNode(String id,
                           String tag,
                           List<Variable> variables,
                           List<Variable> parameters,
                           List<Variable> report,
                           List<AreaNode> children) {

        public AreaNode {
            variables = List.copyOf(variables);
            parameters = List.copyOf(parameters);
            report = List.copyOf(report);
            children = List.copyOf(children);
        }
    }

    private PlantSnapshot() {
    }

    public static AreaNode collectElement(S88Element node) {
        List<Variable> variables = new ArrayList<>();
        List<Variable> parameters = new ArrayList<>();
        List<Variable> report = new ArrayList<>();

        for (Map.Entry<String, Object> entry : node.getProperties().entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> map)) {
                continue;
            }
            if (map.containsKey("Type")) {
                variables.add(variable(entry.getKey(), map.get("Type")));
            } else {
                List<Variable> target = switch (entry.getKey().toLowerCase(Locale.ROOT)) {
                    case "parameters" -> parameters;
                    case "reports" -> report;
                    default -> variables;
                };
                target.addAll(collectVariables(map));
            }
        }

        List<AreaNode> children = new ArrayList<>();
        if (node.getChildren() != null) {
            for (S88Element child : node.getChildren()) {
                children.add(collectElement(child));
            }
        }

        return new AreaNode(
                node.getId() == null ? "" : node.getId(),
                tagFor(node),
                variables,
                parameters,
                report,
                children);
    }

    public static Variable variable(String name, Object type) {
        return new Variable(name, type == null ? "" : String.valueOf(type));
    }

    public static List<Variable> collectVariables(Object value) {
        List<Variable> collected = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> nested) {
                    Object type = nested.get("Type");
                    if (type != null) {
                        collected.add(variable(String.valueOf(entry.getKey()), type));
                    } else {
                        collected.addAll(collectVariables(nested));
                    }
                }
            }
        }
        return collected;
    }

    public static String tagFor(S88Element node) {
        if (node.getLevel() == null) {
            return "element";
        }
        return switch (node.getLevel()) {
            case AREA -> "area";
            case PROCESSCELL -> "processcell";
            default -> "element";
        };
    }
}