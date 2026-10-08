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

/**
 * Una copia del modelo de planta, independiente de Swing y del resto de la aplicacion.
 *
 * <p>Esto sustituye al {@code LinkedHashMap<String, Object>} que se usaba antes para
 * representar un nodo. El mapa obligaba a leer claves de texto por todas partes
 * ({@code element.get("variables")}) y a escribir codigo con
 * {@code @SuppressWarnings("unchecked")} al ir bajando por los hijos. Con records,
 * {@code variables()} es un campo, y una llamada mal escrita no compila.
 */
public final class PlantSnapshot {

    /** Un atributo del modelo, tal y como se escribe en {@code plant-model.xml}. */
    public record Variable(String name, String type) {
    }

    /**
     * Un nodo del modelo y su contenido.
     *
     * <p>El nombre es {@code AreaNode} por el del primer elemento, aunque sirve para
     * cualquier nivel: el modelo entero cuelga del area y por debajo solo hay hijos.
     */
    public record AreaNode(String id,
                           String tag,
                           List<Variable> variables,
                           List<Variable> parameters,
                           List<Variable> report,
                           List<AreaNode> children) {

        /** Copia defensiva: el llamante no debe poder mutar la lista de hijos. */
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

    /**
     * Nombre del elemento XML con el que se escribe un nodo de {@code plant-model.xml}.
     *
     * <p>Solo hay dos nombres propios: {@code area} y {@code processcell}. Los otros dos
     * niveles del enum ({@code UNIT} y {@code EQUIPMENTMODULE}) caen en {@code element}, y
     * eso es a proposito, no un olvido.
     *
     * <p>El motivo es que <strong>ningun lector distingue esos nombres</strong>. Los tres
     * sitios que parsean {@code plant-model.xml} - {@code PlantModelReader},
     * {@code PlantNodeLoader} y el borrado de areas de la propia importacion - buscan
     * exclusivamente {@code <area>}, y el resto del arbol lo recorren sin mirar la etiqueta.
     * Darle a cada nivel un nombre propio solo cambiaria el aspecto del fichero generado,
     * sin que nadaiese mas rapido ni mas correcto.
     *
     * <p>Si algun dia hiciera falta distinguirlos, el sitio unico a tocar es este metodo, y
     * habria que acordarlo antes con quien lea esos ficheros, porque {@code plant-model.xml}
     * esta en proyectos reales y no solo en el del repositorio.
     *
     * @param node el nodo de origen
     * @return el nombre del elemento a crear
     */
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