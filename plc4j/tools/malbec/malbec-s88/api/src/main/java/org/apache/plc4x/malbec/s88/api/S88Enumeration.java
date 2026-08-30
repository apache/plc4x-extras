package org.apache.plc4x.malbec.s88.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global enumeration definition for the plant.
 * A named set of label -> numeric value pairs. Enumerations are global: they can be
 * referenced from any level of the equipment hierarchy.
 */
public class S88Enumeration {
    private String name;
    private final Map<String, Integer> values = new LinkedHashMap<>();

    public S88Enumeration() {
    }

    public S88Enumeration(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String label, Integer index) {
        if (label == null || label.isBlank()) {
            return;
        }
        if (index == null) {
            values.remove(label);
        } else {
            values.put(label, index);
        }
    }

    public Map<String, Integer> getValues() {
        return values;
    }

    public Integer getIndex(String label) {
        return values.get(label);
    }
}
