/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.merlot.archiver.command;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.osgi.framework.BundleContext;

@Service
@Command(scope = "plc4x", name = "htc", description = "Historical collector admin.")
public class MerlotHtcCommand   implements Action {

    @Reference
    BundleContext bc;
    
    @Reference
    volatile List<MerlotHtc> htcs;      
    
    @Option(name = "-l", aliases = "--list", description = "List collector PVs.", required = false, multiValued = false)
    String strHtc;     
    
    @Override
    public Object execute() throws Exception {
        if (null == strHtc){
            ListCollectorsServices(null);
        } else {
            ListCollectorsServices(strHtc);
        }
        return null;
    }
    
    private void ListCollectorsServices(String id){
        if (null == id) {
            ShellTable table = new ShellTable();
            table.column("Uid");
            table.column("Key");
            htcs.forEach(htc -> {
                    table.addRow().addContent("uno",htc.getPVs().length);
            });
            
        } else {
            ShellTable table = new ShellTable();
            table.column("Item");
            table.column("PV");             
        }
    }
    
 
    
}
