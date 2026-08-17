package org.apache.plc4x.malbec.s88.plant.actions;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.apache.plc4x.malbec.s88.plant.panels.TemplateFactory;
import org.netbeans.api.project.Project;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class CreateTemplateAction extends AbstractAction implements ContextAwareAction {
    private final Lookup context;

    public CreateTemplateAction() {
        this(Lookup.EMPTY);
    }

    private CreateTemplateAction(Lookup context) {
        super(Bundle.BTN_Template());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new CreateTemplateAction(actionContext);
    }

    @NbBundle.Messages({
            "BTN_Template=New Template"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = context.lookup(Project.class);
        S88Element parent = context.lookup(S88Element.class);

        if (project == null || parent == null) return;
        Plc4xPlantModel plantModel = project.getLookup().lookup(Plc4xPlantModel.class);
        if (plantModel == null || plantModel.getModel() == null) return;

        JDialog dialog = TemplateFactory.createDialog(parent, plantModel);
    }
}
