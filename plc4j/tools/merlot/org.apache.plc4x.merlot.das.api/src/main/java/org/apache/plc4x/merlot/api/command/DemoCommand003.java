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
package org.apache.plc4x.merlot.api.command;

import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.osgi.framework.BundleContext;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.core.PlcItemClientService;
import org.apache.plc4x.merlot.api.impl.PlcGroupImpl;
import org.apache.plc4x.merlot.api.impl.PlcItemImpl;


@Service
@Command(scope = "plc4x", name = "demo_003", description = "Command for test.")
public class DemoCommand003  implements Action  {
  
    @Reference
    BundleContext bc; 
    
    @Reference
    PlcGeneralFunction plcGeneralFunction;
    
    @Reference
    PlcItemClientService items_service;
    
    @Reference
    volatile List<PlcDevice> devices;
    
    @Option(name = "-d", aliases = "--did", description = "Device uid.", required = true, multiValued = false)
    String gid; 
    
    @Option(name = "-n", aliases = "--name", description = "Technological name of the group.", required = true, multiValued = false)
    String group_name;     
    
    @Override
    public Object execute() throws Exception {
       
        UUID devUuid = UUID.randomUUID();
        
        Optional<PlcDevice> optPlcDevice = plcGeneralFunction.createDevice(devUuid.toString(),
                                            "simulated", 
                                            "DEMO003",
                                            "simulated://localhost",
                                            "Nombre corto", 
                                            "La descripcion",
                                            "true");
        if (optPlcDevice.isPresent()){
            PlcItem item;
            
            Optional<PlcGroup> optPlcGroup =  plcGeneralFunction.createGroup(UUID.randomUUID().toString(),
                                optPlcDevice.get().getUid().toString(),
                                "GRUPO001",
                                "Descripcion del grupo",
                                "5000",
                                "true");
            
            if (optPlcGroup.isPresent()){
                for (int i= 1; i < 10; i++) {
                    Optional<PlcItem> optPlcItem = plcGeneralFunction.createItem(UUID.randomUUID().toString(), 
                            optPlcGroup.get().getGroupUid().toString(),
                            optPlcDevice.get().getUid().toString(),
                            "ITEM_" + i,
                            "Item description _" + i,
                            "RANDOM/dummy:int[10]",
                            "true");
                    if (optPlcItem.isPresent()){
                        System.out.println("Item create: " + optPlcItem.get().getItemName());
                    }               
                }

           
            }                       
        }
        return null;        
    }
}
