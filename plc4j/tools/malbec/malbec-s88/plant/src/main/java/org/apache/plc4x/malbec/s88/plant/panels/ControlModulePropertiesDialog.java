package org.apache.plc4x.malbec.s88.plant.panels;

import javax.swing.*;

public class ControlModulePropertiesDialog extends PropertiesDialog{

    public ControlModulePropertiesDialog(String id, String level, String elementClass) {
        super("Edit Control Module " + id);
        FormTab generalTab = new FormTab("General");

        JTextField nameField = new JTextField(id);
        nameField.setEditable(false);

        JTextField levelField = new JTextField(level);
        levelField.setEditable(false);

        JTextField classField = new JTextField(elementClass);
        classField.setEditable(false);


        generalTab.addPropertyRow("Name", nameField);
        generalTab.addPropertyRow("Class", levelField);
        generalTab.addPropertyRow("Template/Class", classField);

        FormTab attributesTab = new FormTab("Attribute Tags");

        JTextField tag1Field = new JTextField("Value 1");
        JTextField tag2Field = new JTextField("Value 2");

        attributesTab.addPropertyRow("Initial Tag", tag1Field);
        attributesTab.addPropertyRow("Secondary Tag", tag2Field);


        new FormTab("Arbitration");
        new FormTab("Cross Invocation");
        new FormTab("External sources");

    }
}
