package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.core.CreateClassUseCase;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

@NbBundle.Messages({
        "LBL_TemplateName=Template Name:",
        "LBL_CreateTemplate=Create {0} Template",
})
public class SimpleTemplateDialog implements TemplateDialog{
    private String templateName;
    private String level;
    private S88Element parent;
    private Plc4xPlantModel model;
    private CreateClassUseCase createClassUseCase = new CreateClassUseCase();

    public SimpleTemplateDialog(S88Element parent, Plc4xPlantModel model){
        this.level = parent.getLevel().getChildLevel().name();
        this.parent = parent;
        this.model = model;
    }

    @Override
    public boolean showDialog() {
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(
                Bundle.LBL_TemplateName(),
                Bundle.LBL_CreateTemplate(level)
        );
        input.setInputText("");

        if (DialogDisplayer.getDefault().notify(input) == NotifyDescriptor.OK_OPTION) {

            try {
                this.templateName = input.getInputText();
                createClassUseCase.execute(model.getModel(), parent, input.getInputText());
                model.save();
                return true;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return false;
    }

    @Override
    public String getTemplateName() {
        return this.templateName;
    }
}
