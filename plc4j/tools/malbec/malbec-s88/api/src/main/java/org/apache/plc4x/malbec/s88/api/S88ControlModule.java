package org.apache.plc4x.malbec.s88.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Control Module is a physical control element (such as a valve, motor or sensor).
 * Malbec represents it as an {@code S88Element} with a fixed, predefined set of properties
 * (its "property table"). Data can be written into these properties, but the
 * module itself does not dynamically add children, element classes or attributes over time.
 */
public abstract class S88ControlModule extends S88Element {

    protected final Map<String, Object> propertyTable = new LinkedHashMap<>();

    public S88ControlModule() {
        super();
        super.setLevel(S88Level.CONTROLMODULE);
        initPropertyTable();
        propertyTable.forEach(super::setProperty);
    }

    protected void initPropertyTable() {

    }

    @Override
    public void setLevel(S88Level level) {
        // Control modules always stay at CONTROLMODULE level.
    }

    @Override
    public void setProperty(String k, Object v) {
        if (propertyTable.containsKey(k) && v != null) {
            super.setProperty(k, v);
        }
    }

    @Override
    public Object getProperty(String k) {
        if (!propertyTable.containsKey(k)) {
            return null;
        }
        return super.getProperty(k);
    }

    @Override
    public void addChild(S88Element child) {
        throw new UnsupportedOperationException("Control modules cannot have children.");
    }

    @Override
    public void setClass(S88ElementClass elementClass) {
        throw new UnsupportedOperationException("Control modules do not have element classes.");
    }

    @Override
    public void addElementClass(S88ElementClass elementClass) {
        throw new UnsupportedOperationException("Control modules do not have element classes.");
    }

    public Set<String> getPropertyNames() {
        return propertyTable.keySet();
    }

    /**
     * Restores persisted data into the module. Only keys present in the fixed
     * property table are written; everything else is ignored.
     */
    public void restoreProperties(Map<String, Object> properties) {
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            setProperty(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Creates the concrete control module matching a persisted type name
     * (typically {@link #getTypeName()}), or {@code null} when the type name is
     * not recognized.
     */
    public static S88ControlModule fromTypeName(Object typeName) {
        if (typeName == null) {
            return null;
        }
        ControlModules catalog = ControlModules.fromTypeName(String.valueOf(typeName));
        return catalog != null ? catalog.create() : null;
    }
}