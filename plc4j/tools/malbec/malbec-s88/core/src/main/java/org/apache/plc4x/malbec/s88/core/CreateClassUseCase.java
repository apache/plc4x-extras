package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.Map;

public class CreateClassUseCase {
    private CreateClassUseCase() {
        /* This utility class should not be instantiated */
    }

    public static void execute(S88PlantModel model, S88Element parent, String name, Map<String, Object> properties) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        if (name.startsWith(S88PlantModel.ENUM_CLASS_PREFIX)) {
            throw new IllegalArgumentException("'" + S88PlantModel.ENUM_CLASS_PREFIX
                    + "' is a reserved prefix for global enumerations.");
        }

        if (model.getClasses().containsKey(name)) {
            throw new IllegalStateException("Template with ID '" + name + "' already exists.");
        }

        S88Element targetParent = parent != null ? parent : model.getRoot();

        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName(name);
        if (parent != null) {
            elementClass.setTargetLevel(parent.getLevel().getChildLevel());
        }


        if (properties != null) {
            for (String key : properties.keySet()) {
                elementClass.setProperty(key, properties.get(key));
            }
        }

        targetParent.addElementClass(elementClass);
        model.registerClass(elementClass);
    }
    
}
