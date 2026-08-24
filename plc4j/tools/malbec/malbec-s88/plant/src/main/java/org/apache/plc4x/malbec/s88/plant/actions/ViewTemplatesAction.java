package org.apache.plc4x.malbec.s88.plant.actions;

import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.plant.panels.TemplateDialogBuilder;
import org.apache.plc4x.malbec.s88.plant.panels.TemplateFactory;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


@NbBundle.Messages({
        "CTL_ViewTemplatesAction=View templates",
})
public class ViewTemplatesAction extends AbstractAction implements ContextAwareAction {

    private final Lookup context;

    public ViewTemplatesAction() {
        this(Lookup.EMPTY);
    }

    private ViewTemplatesAction(Lookup context) {
        super(Bundle.CTL_ViewTemplatesAction());
        this.context = context;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ViewTemplatesAction(actionContext);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        S88Element equipment = context.lookup(S88Element.class);

        DefaultListModel<S88ElementClass> classListModel = new DefaultListModel<>();;

        for(S88ElementClass ec : equipment.getElementClasses()){
            classListModel.addElement(ec);
        }

        JList<S88ElementClass> list = new JList<>(classListModel);

        TemplateDialogBuilder builder = new TemplateDialogBuilder("Select Template", false);

        builder.withReadOnlyNameField(equipment.getId());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof S88ElementClass) {
                    setText(((S88ElementClass) value).getName());
                }
                return this;
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                        S88ElementClass ec = list.getModel().getElementAt(index);
                        TemplateFactory.showTemplate(ec);
                    }
                }
            }
        });

        builder.addComponentRow(new JScrollPane(list));

        JDialog dlg = builder.build();
        dlg.setVisible(true);
    }
}
