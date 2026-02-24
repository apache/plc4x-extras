/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.services.core;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.awt.event.ActionEvent;
import java.io.PrintWriter;
import javax.swing.AbstractAction;
import org.apache.plc4x.malbec.api.MasterDB;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author cgarcia
 */
public class Plc4xRootNodeSaveAction  extends AbstractAction  {

    private MasterDB db =  null;   
    private final XmlMapper mapper = new XmlMapper();    

    public Plc4xRootNodeSaveAction() {
        this.putValue(AbstractAction.NAME, "Save config...");
    }
    
    @Override
    public void actionPerformed(ActionEvent ae) {
        db = Lookup.getDefault().lookup(MasterDB.class); 
        if (db != null) {
            try {
                FileObject fo = FileUtil.getConfigRoot();
                String dir = FileUtil.getFileDisplayName(fo);
                FileObject fxml = FileUtil.createData(fo, "db.plc4x.xml");
                if (fxml.isLocked()) System.out.println("Archivo bloqueado!");
                System.out.println("Dir: " + dir);
                
                String result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(db);
                System.out.println(result);
                if (fxml.canWrite()) {
                    PrintWriter output = new PrintWriter(fxml.getOutputStream());
                    output.println(result);
                    output.close();
                }
           
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
    }
    
}
