package org.apache.plc4x.malbec.s88.impl;

import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.modules.OnStart;

import java.io.File;

@OnStart
public class FolderInstaller implements Runnable {
    @Override
    public void run() {
        File dir = new File(System.getProperty("user.home"), "MalbecProjects");

        if(!dir.exists()) {
            dir.mkdirs();
        }

        ProjectChooser.setProjectsFolder(dir);
    }
}
