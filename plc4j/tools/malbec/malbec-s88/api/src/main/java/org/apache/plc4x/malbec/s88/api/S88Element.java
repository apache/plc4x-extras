package org.apache.plc4x.malbec.s88.api;

import java.util.*;

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
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private S88ElementClass elementClass;
    private final List<S88ElementClass> elementClasses = new ArrayList<>();


    public S88Element(){

    }
    public void setClass(S88ElementClass elementClass){
        this.elementClass = elementClass;
    }

    public S88ElementClass getElementClass(){
        return this.elementClass;
    }

    public void addElementClass(S88ElementClass elementClass){
        this.elementClasses.add(elementClass);
    }

    public List<S88ElementClass> getElementClasses(){
        return this.elementClasses;
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

    public void setProperty(String k, Object v){
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

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Map<String, Map<String, Object>> getStructuredProperties(Map<String, Object> properties) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        if (properties == null)  properties = this.getProperties();

        for (var entry : properties.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                result.put(entry.getKey(), typed);
            }
        }

        return result;
    }

    public Object getProperty(String k){
        return this.properties.getOrDefault(k, "");
    }

    public Map<String, Object> getStructuredProperty(String k){
        return this.getStructuredProperties(null).get(k);
    }

}