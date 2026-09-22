/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.plc4x.malbec.s88.plant.panels;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.io.IOException;

public class PropertiesFactory {

    public static JDialog createDialog(S88Element element, Plc4xPlantModel model) {
        String levelName = element.getLevel().name();
        String className = element.getElementClass() != null ? element.getElementClass().getName() : "";
        String id = element.getId();

        return switch (element.getLevel()) {
            case PROCESSCELL -> buildProcessCellDialog(id, levelName, className, element, model);
            case UNIT -> buildUnitDialog(id, levelName, className, element, model);
            case EQUIPMENTMODULE -> buildEquipmentModuleDialog(id, levelName, className, element, model);
            default -> throw new IllegalArgumentException("No dialog implemented for level: " + levelName);
        };
    }

    private static JCheckBox AddCheck(S88Element element){
        JCheckBox check = new JCheckBox();
        check.setSelected(element.isCheck());

        check.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                element.setCheck(true);
            } else {
                element.setCheck(false);
            }
        });

        return check;
    }

    private static JDialog buildProcessCellDialog(String id, String level, String elementClass, S88Element element, Plc4xPlantModel model) {
        return createBaseBuilder("Edit Process Cell " + id, id, level, elementClass, element)
                .onOk(()->{
                    try {
                        model.save();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onApply(()->{
                    try {
                        model.save();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }

    private static JDialog buildEquipmentModuleDialog(String id, String level, String elementClass, S88Element element, Plc4xPlantModel model) {
        return createBaseBuilder("Edit Equipment Module " + id, id, level, elementClass, element)
                .beginTab("Parameters and Reports")
                .endTab()
                .addEmptyTab("Arbitration")
                .addEmptyTab("Cross Invocation")
                .addEmptyTab("External sources")
                .onOk(()->{
                    try {
                        model.save();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onApply(()->{
                    try {
                        model.save();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }

    private static JDialog buildUnitDialog(String id, String level, String elementClass, S88Element element,  Plc4xPlantModel model) {
        return createBaseBuilder("Edit Unit: " + id, id, level, elementClass, element)
                .beginTab("Attribute Tags")
                .endTab()
                .addEmptyTab("Arbitration")
                .addEmptyTab("Cross Invocation")
                .addEmptyTab("External sources")
                .onOk(() -> {
                    try {
                        model.save();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onApply(()->{
                    try {
                        model.save();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }

    private static PropertiesDialogBuilder createBaseBuilder(String dialogTitle, String id, String level, String elementClass, S88Element element) {
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
                .addPropertyRow("Enable display creation", AddCheck(element))
                .endTab();

        return builder;
    }
}