package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;

public class PropertiesFactory {

    /**
     *
     */
    public static PropertiesDialog createDialog(String id, S88Level level, S88ElementClass elementClass) {
        return switch (level) {
            case PROCESSCELL -> new ProcessCellPropertiesDialog(id, level.name(), elementClass.getName());
            case UNIT -> new UnitPropertiesDialog(id, level.name(), elementClass.getName());
            case EQUIPMENTMODULE -> new EquipmentModulePropertiesDialog(id, level.name(), elementClass.getName());
            case CONTROLMODULE -> new ControlModulePropertiesDialog(id, level.name(), elementClass.getName());
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + level);
        };
    }
}