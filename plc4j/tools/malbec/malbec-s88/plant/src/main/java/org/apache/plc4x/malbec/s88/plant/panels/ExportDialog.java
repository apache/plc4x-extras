package org.apache.plc4x.malbec.s88.plant.panels;

import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantModel;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileUtil;

import javax.swing.*;
import java.io.File;

public class ExportDialog {
    public ExportDialog(Project[] projects) {
        JRadioButton[] radios = new JRadioButton[projects.length];

        ButtonGroup group = new ButtonGroup();
        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        for (int i = 0; i < projects.length; i++) {
            radios[i] = new JRadioButton(projects[i].getProjectDirectory().getName(), i == 0);
            group.add(radios[i]);
            radioPanel.add(radios[i]);
        }


        int result = JOptionPane.showConfirmDialog(
                null,
                radioPanel,
                "Select a project",
                JOptionPane.OK_CANCEL_OPTION
        );

        if(result == JOptionPane.OK_OPTION){
            for(int i = 0; i < radios.length; i++){
                if(radios[i].isSelected()) {
                    Project selected = projects[i];
                    File projDir = FileUtil.toFile(selected.getProjectDirectory());
                    JFileChooser chooser = new JFileChooser(projDir);

                    chooser.setSelectedFile(new File(selected.getProjectDirectory().getName() + ".axml"));
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        String exportPath = chooser.getSelectedFile().getAbsolutePath();
                        Plc4xPlantModel pmodel = selected.getLookup().lookup(Plc4xPlantModel.class);
                        pmodel.export(exportPath);
                    }

                    break;
                }
            }


        }
    }

//    private JList<Project> getProjectJList(Project[] projects) {
//        JList<Project> list = new JList<>(projects);
//        list.setCellRenderer(new DefaultListCellRenderer() {
//            @Override
//            public Component getListCellRendererComponent(JList<?> list, Object value,
//                                                          int index, boolean isSelected, boolean CellHasFocus) {
//                if (value instanceof Project) {
//                    setText(((Project) value).getProjectDirectory().getName());
//                }
//                return this;
//            }
//        });
//        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        return list;
//    }
}
