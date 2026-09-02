package org.apache.plc4x.malbec.s88.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public abstract class S88ControlModule {

    protected final Map<String, Object> propertyTable = new LinkedHashMap<>();
    private final S88Element parent;

    public S88ControlModule(S88Element parent) {
        this.parent = parent;
        initPropertyTable();
    }

    protected void initPropertyTable() {

    }

    public void setProperty(String k, Object v){
        if(propertyTable.containsKey(k)) {
            if (v != null) {
                propertyTable.put(k, v);
            }
        }
    }

    public Object getProperty(String k){
        return propertyTable.getOrDefault(k, null);
    }

    public Map<String, Object> getProperties(){
        return propertyTable;
    }

    public S88Element getParent() {
        return parent;
    }

    public Set<String> getPropertyNames(){
        return propertyTable.keySet();
    }
}
