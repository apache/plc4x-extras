package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;

import javax.swing.*;

public class PropertiesFactory {

    public static JDialog createDialog(String id, S88Level level, S88ElementClass elementClass) {
        String levelName = level.name();
        String className = elementClass.getName();

        return switch (level) {
            case PROCESSCELL -> buildProcessCellDialog(id, levelName, className);
            case UNIT -> buildUnitDialog(id, levelName, className);
            case EQUIPMENTMODULE -> buildEquipmentModuleDialog(id, levelName, className);
            case CONTROLMODULE -> buildControlModuleDialog(id, levelName, className);
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + level);
        };
    }

    private static JDialog buildProcessCellDialog(String id, String level, String elementClass) {
        return createBaseBuilder("Edit Process Cell " + id, id, level, elementClass)
                .build();
    }

    private static JDialog buildEquipmentModuleDialog(String id, String level, String elementClass) {
        return createBaseBuilder("Edit Equipment Module " + id, id, level, elementClass)
                .build();
    }

    private static JDialog buildUnitDialog(String id, String level, String elementClass) {
        return createBaseBuilder("Edit Unit: " + id, id, level, elementClass)
                .beginTab("Attribute Tags")
                .addPropertyRow("Initial Tag", new JTextField("Value 1"))
                .addPropertyRow("Secondary Tag", new JTextField("Value 2"))
                .endTab()
                .addEmptyTab("Arbitration")
                .addEmptyTab("Cross Invocation")
                .addEmptyTab("External sources")
                .onOk(() -> {
                    // todo: save modified properties
                })
                .build();
    }

    private static JDialog buildControlModuleDialog(String id, String level, String elementClass) {
        return createBaseBuilder("Edit Control Module " + id, id, level, elementClass)
                .beginTab("Attribute Tags")
                .addPropertyRow("Initial Tag", new JTextField("Value 1"))
                .addPropertyRow("Secondary Tag", new JTextField("Value 2"))
                .endTab()
                .addEmptyTab("Arbitration")
                .addEmptyTab("Cross Invocation")
                .addEmptyTab("External sources")
                .build();
    }

    private static PropertiesDialogBuilder createBaseBuilder(String dialogTitle, String id, String level, String elementClass) {
        JTextField nameField = new JTextField(id);
        nameField.setEditable(false);

        JTextField levelField = new JTextField(level);
        levelField.setEditable(false);

        JTextField classField = new JTextField(elementClass);
        classField.setEditable(false);

        PropertiesDialogBuilder builder = new PropertiesDialogBuilder(dialogTitle);

        builder.beginTab("General")
                .addPropertyRow("Name", nameField)
                .addPropertyRow("Level", levelField)
                .addPropertyRow("Template/Class", classField)
                .endTab();

        return builder;
    }
}