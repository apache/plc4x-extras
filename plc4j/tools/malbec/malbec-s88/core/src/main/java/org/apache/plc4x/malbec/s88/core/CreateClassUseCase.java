package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

public class CreateClassUseCase {

    public void execute(S88PlantModel model, S88Element parent, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        if (model.getClasses().containsKey(name)) {
            throw new IllegalStateException("Template with ID '" + name + "' already exists.");
        }

        S88Element targetParent = parent != null ? parent : model.getRoot();

        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName(name);
        elementClass.setTargetLevel(parent.getLevel().getChildLevel());


        targetParent.addElementClass(elementClass);
        model.registerClass(elementClass);
    }
    
}
