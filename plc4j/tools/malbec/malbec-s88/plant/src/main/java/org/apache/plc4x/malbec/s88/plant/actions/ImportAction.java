package org.apache.plc4x.malbec.s88.plant.actions;


import org.apache.commons.io.FilenameUtils;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88Storage;
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
        JFileChooser chooser = new JFileChooser("user.home");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
           file = chooser.getSelectedFile();
        }
        
        if (file == null) return;

        try {

            S88Repository importer = S88ProjectServices.createRepository(FilenameUtils.getExtension(file.getName()), new FileStorage(file));
            S88PlantModel model = importer.loadPlant();

            FileObject folder = project.getProjectDirectory().createFolder(FilenameUtils.getBaseName(file.getName()));
            FileObject plantXML = folder.createData("plant.xml");
            S88Repository saver = S88ProjectServices.createRepository("xml", new FileObjectStorage(plantXML));
            saver.savePlant(model);
        } catch (IllegalArgumentException ia){
            JOptionPane.showMessageDialog(null, ia.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    private static class FileStorage implements S88Storage {
        private final File file;
        FileStorage(File file) { this.file = file; }
        @Override public InputStream openInput() throws IOException { return new FileInputStream(file);}
        @Override public OutputStream openOutput() throws IOException { return new FileOutputStream(file);}
    }

    private static class FileObjectStorage implements S88Storage {
        private final FileObject fo;
        FileObjectStorage(FileObject fo) { this.fo = fo; }
        @Override public InputStream openInput() throws IOException { return fo.getInputStream(); }
        @Override public OutputStream openOutput() throws IOException { return fo.getOutputStream(); }
    }
}
