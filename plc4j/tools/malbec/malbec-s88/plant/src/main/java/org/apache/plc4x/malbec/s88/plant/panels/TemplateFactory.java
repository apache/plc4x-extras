package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

import javax.swing.*;

public class TemplateFactory {
    /**
     *
     */

    public static JDialog createDialog(S88Element parent, Plc4xPlantModel model) {
        return switch (parent.getLevel()) {
            case AREA -> new SimpleTemplateDialog(parent, model);
            case PROCESSCELL -> new UnitTemplateDialog(parent, model);
            case UNIT -> null /*Equipment module template dialog*/;
            case EQUIPMENTMODULE -> null /*Control module template dialog. Not sure about adding templates for control modules*/;
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + parent.getLevel().name());
        };
    }
}
