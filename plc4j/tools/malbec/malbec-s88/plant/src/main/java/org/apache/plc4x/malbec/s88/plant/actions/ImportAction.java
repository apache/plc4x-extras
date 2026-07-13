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
 * Action to import a model from another environment
 */
@ActionID(category = "Project", id = "org.apache.plc4x.malbec.s88.plant.actions.ImportAction")
@ActionRegistration(displayName = "#CTL_ImportAction", lazy = false)
@ActionReference(path = "Projects/org-plc4x-s88-project/Actions", position = 250)
@NbBundle.Messages({
    "CTL_ImportAction=Import"
})
public class ImportAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;
//    private final CreateElementUseCase createElementUseCase = new CreateElementUseCase();

    public ImportAction() {
        this(Lookup.EMPTY);
    }

    private ImportAction(Lookup context) {
        super(Bundle.CTL_ImportAction());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ImportAction(actionContext);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }
}
