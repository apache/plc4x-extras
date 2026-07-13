package org.apache.plc4x.malbec.s88.plant.actions;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Action to export a plant model to another environment
 */
@ActionID(category = "Project", id = "org.apache.plc4x.malbec.s88.plant.actions.ExportAction")
@ActionRegistration(displayName = "#CTL_ExportAction", lazy = false)
@ActionReference(path = "Projects/org-plc4x-s88-project/Actions", position = 200)
@NbBundle.Messages({
    "CTL_ExportAction=Export"
})
public class ExportAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;
//    private final CreateElementUseCase createElementUseCase = new CreateElementUseCase();

    public ExportAction() {
        this(Lookup.EMPTY);
    }

    private ExportAction(Lookup context) {
        super(Bundle.CTL_ExportAction());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ExportAction(actionContext);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }
}
