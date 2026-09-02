package org.apache.plc4x.malbec.s88.plant.actions;


import org.apache.commons.io.FilenameUtils;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.plant.impl.Plc4xPlantSubProjectProviderImpl;
import org.apache.plc4x.malbec.s88.plant.services.S88ProjectServices;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;

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
        Project project = context.lookup(Project.class);
        if (project == null) return;

        File file = null;

        FileDialog chooser = new FileDialog((Frame)  null, "Select File", FileDialog.LOAD);


        chooser.setDirectory(System.getProperty("user.home"));
        chooser.setVisible(true);

        String dir  = chooser.getDirectory();
        String filename = chooser.getFile();

        if (dir != null && filename != null){
            file = new File(dir, filename);
        }
        
        if (file == null) return;

        try {

            S88Repository importer = S88ProjectServices.createRepository(FilenameUtils.getExtension(file.getName()), new FileStorage(file));
            S88PlantModel model = importer.loadPlant();

            String folderName = FilenameUtils.getBaseName(file.getName());

            FileObject projectDir = project.getProjectDirectory();
            FileObject existingFolder = projectDir.getFileObject(folderName);

            if (existingFolder != null && existingFolder.isFolder()) {

                JOptionPane.showMessageDialog(null,
                        "There is already a '" + folderName + "' project",
                        "Duplicate Project",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            FileObject folder = projectDir.createFolder(FilenameUtils.getBaseName(file.getName()));
            FileObject plantXML = folder.createData("plant.xml");
            S88Repository saver = S88ProjectServices.createRepository("xml", new FileObjectStorage(plantXML));
            saver.savePlant(model);

            Plc4xPlantSubProjectProviderImpl provider = project.getLookup().lookup(Plc4xPlantSubProjectProviderImpl.class);
            if (provider != null) {
                java.awt.EventQueue.invokeLater(provider::fireChange);
            }
        } catch (IllegalArgumentException ia){
            JOptionPane.showMessageDialog(null, ia.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    private record FileStorage(File file) implements S88Storage {
        @Override
        public InputStream openInput() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public OutputStream openOutput() throws IOException {
            return new FileOutputStream(file);
        }
        }

    private record FileObjectStorage(FileObject fo) implements S88Storage {
        @Override
        public InputStream openInput() throws IOException {
            return fo.getInputStream();
        }

        @Override
        public OutputStream openOutput() throws IOException {
            return fo.getOutputStream();
        }
        }
}
