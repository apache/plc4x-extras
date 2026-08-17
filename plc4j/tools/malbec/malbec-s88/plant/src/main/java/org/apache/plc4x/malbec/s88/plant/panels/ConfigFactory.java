package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

import javax.swing.*;

public class ConfigFactory {
    public static JPanel createConfigPanel(Plc4xPlantModel model, S88Element parent) {
        return switch(parent.getLevel()){
            case PROCESSCELL -> new ConfigPanel(model, parent);
            case UNIT -> new UnitPanel(model, parent);
            case EQUIPMENTMODULE ->new ConfigPanel(model, parent);
            case CONTROLMODULE -> new ConfigPanel(model, parent);
            default -> new ConfigPanel(model, parent);
        };
    }
}
