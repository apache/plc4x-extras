package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

public class TemplateFactory {
    /**
     *
     */

    public static TemplateDialog createDialog(S88Element parent, Plc4xPlantModel model) {
        return switch (parent.getLevel()) {
            case AREA, PROCESSCELL -> new SimpleTemplateDialog(parent, model);
            case UNIT -> new UnitTemplateDialog(parent, model);
//            case EQUIPMENTMODULE -> new EquipmentModulePropertiesDialog(level.name());
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + parent.getLevel().name());
        };
    }
}
