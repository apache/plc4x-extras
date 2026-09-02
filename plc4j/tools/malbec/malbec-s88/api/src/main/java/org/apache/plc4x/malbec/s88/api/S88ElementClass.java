package org.apache.plc4x.malbec.s88.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EquipmentClass in B2MML. Template for S88Element grouping.
 */

public class S88ElementClass {
    private String name;
    private S88Level targetLevel;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public  S88ElementClass() {}

    public void setName(String name){
        this.name = name;
    }

    public String getName(){
        return this.name;
    }

    public void setProperty(String key, Object value){
        this.properties.put(key, value);
    }

    public Object getProperty(String key){
        return this.properties.get(key);
    }

    public Map<String,Object> getProperties(){
        return this.properties;
    }

    public void setTargetLevel(S88Level targetLevel) { this.targetLevel = targetLevel; }

    public S88Level getTargetLevel() { return this.targetLevel; }

}
