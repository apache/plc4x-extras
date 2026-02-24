/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.plc4x.app.projecttype.impl;

import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author cgarcia
 */
public class Plc4xProject implements  Project {

    private final FileObject projectDir;
    private final ProjectState state;
    private Lookup lkp;    
    
   Plc4xProject(FileObject dir, ProjectState state) {
        this.projectDir = dir;
        this.state = state;
    }    
    
    @Override
    public FileObject getProjectDirectory() {
        return projectDir;
    }

    @Override
    public Lookup getLookup() {
        if (lkp == null) {
            lkp = Lookups.fixed(new Object[]{

            // register your features here

            });
        }
        return lkp;
    }
    
    private class Plc4xProjectInformation implements  ProjectInformation {

        @StaticResource()
        public static final String CUSTOMER_ICON = "org/apache/plc4x/app/projecttype/impl/Proyecto.png";    

        @Override
        public String getName() {
            return getProjectDirectory().getName();
        }

        @Override
        public String getDisplayName() {
            return getName();
        }

        @Override
        public Icon getIcon() {
            return new ImageIcon(ImageUtilities.loadImage(CUSTOMER_ICON));
        }

        @Override
        public Project getProject() {
            return Plc4xProject.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener pl) {
            //
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener pl) {
            //
        }

    }    
    
}
