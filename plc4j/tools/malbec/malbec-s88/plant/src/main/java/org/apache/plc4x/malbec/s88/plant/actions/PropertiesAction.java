package org.apache.plc4x.malbec.s88.plant.actions;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.PropertiesDialog;
import org.apache.plc4x.malbec.s88.plant.panels.PropertiesFactory;
import org.netbeans.api.project.Project;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class PropertiesAction  extends AbstractAction implements ContextAwareAction {
    private final Lookup context;
    public PropertiesAction() {
        this(Lookup.EMPTY);
    }

    private PropertiesAction(Lookup context) {
        super(Bundle.BTN_Props());
        this.context = context;
    }


    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new PropertiesAction(actionContext);
    }
    @NbBundle.Messages({
            "BTN_Props=Properties"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        S88Element targetEq = context.lookup(S88Element.class);

        if (project == null || targetEq == null) return;
        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null || plantModel.getModel() == null) return;



        PropertiesDialog props = PropertiesFactory.createDialog( targetEq.getId(), targetEq.getLevel(), targetEq.getElementClass());
        props.setVisible(true);
    }
}
