package org.apache.plc4x.malbec.s88.api;

import org.apache.plc4x.malbec.s88.api.S88Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AREA
 * PROCESS CELL
 * UNIT
 * EQUIPMENT MODULE
 * CONTROL MODULE
 */
public class S88Element {


    private String id;
    private S88Level level;
    private S88Element parent;
    private final List<S88Element> children = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();


    public S88Element(){

    }


    public void setId(String id) {
        this.id = id;
    }

    public void setLevel(S88Level level) {
        this.level = level;
    }

    public void setParent(S88Element parent) {
        this.parent = parent;
    }

    public void setProperty(String k, String v){
        if(v == null){
            this.properties.remove(k);
        } else {
            this.properties.put(k, v);
        }
    }

    public String getId() {
        return id;
    }

    public S88Level getLevel() {
        return level;
    }

    public S88Element getParent() {
        return parent;
    }

    public List<S88Element> getChildren() {
        return children;
    }

    public void addChild(S88Element element){
        this.children.add(element);
        element.setParent(this);
    }

    public void removeChild(S88Element element){
        this.children.remove(element);
        element.setParent(null);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getProperty(String k){
        return this.properties.getOrDefault(k, "");
    }
}