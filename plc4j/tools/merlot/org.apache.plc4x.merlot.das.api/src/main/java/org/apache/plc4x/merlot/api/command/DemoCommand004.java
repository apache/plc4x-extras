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
import org.osgi.framework.BundleContext;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.core.PlcItemClientService;


@Service
@Command(scope = "plc4x", name = "demo_004", description = "Command for test.")
public class DemoCommand004  implements Action  {
  
    @Reference
    BundleContext bc; 
    
    @Reference
    PlcGeneralFunction plcGeneralFunction;
    
    @Reference
    PlcItemClientService items_service;
    
    @Reference
    volatile List<PlcDevice> devices;
    
    @Option(name = "-d", aliases = "--did", description = "Device uid.", required = false, multiValued = false)
    String gid; 
    
    @Option(name = "-n", aliases = "--name", description = "Technological name of the group.", required = false, multiValued = false)
    String group_name;     
    
    @Override
    public Object execute() throws Exception {
        System.out.println("Version 009");
        UUID devUuid = UUID.randomUUID();
        
        Optional<PlcDevice> optPlcDevice = plcGeneralFunction.createDevice(devUuid.toString(),
                                            "s7", 
                                            "AS02",
                                            "s7://192.168.0.47?remote-rack=0&"
                                            + "remote-slot=3&"
                                            + "controller-type=S7_400&read-timeout=8&"                
                                            + "ping=true&ping-time=2&retry-time=3",
                                            "+C1=AS02.", 
                                            "La descripcion",
                                            "true");
        if (optPlcDevice.isPresent()){
            PlcItem item;
            
            Optional<PlcGroup> optPlcGroup =  plcGeneralFunction.createGroup(UUID.randomUUID().toString(),
                                optPlcDevice.get().getUid().toString(),
                                "GRUPO002",
                                "Descripcion del grupo",
                                "500",
                                "true");
            
            if (optPlcGroup.isPresent()){
                
                for (int i= 1; i < 4; i++) {
                    Optional<PlcItem> optPlcItem = plcGeneralFunction.createItem(UUID.randomUUID().toString(), 
                            optPlcGroup.get().getGroupUid().toString(),
                            optPlcDevice.get().getUid().toString(),
                            "S7ITEM_" + i,
                            "S7Item description _" + i,
                            "%DB100:0:USINT[48]",
                            "true");
                    if (optPlcItem.isPresent()){
                        optPlcItem.get().enable();
                        System.out.println(optPlcItem.get().getItemUid().toString()+ " : " + optPlcItem.get().getItemName());
                    }                                                            
                }           
             
////            
//                for (int i= 1; i < 4; i++) {
//                    Optional<PlcItem> optPlcItem = plcGeneralFunction.createItem(UUID.randomUUID().toString(), 
//                            optPlcGroup.get().getGroupUid().toString(),
//                            optPlcDevice.get().getUid().toString(),
//                            "INPUT_" + i,
//                            "Item description _" + i,
//                            "3x00001:UINT[48]",
//                            "true");
//                    if (optPlcItem.isPresent()){
//                        optPlcItem.get().enable();
//                        System.out.println(optPlcItem.get().getItemUid().toString()+ " : " + optPlcItem.get().getItemName());
//                    }                                                            
//                }           
//                        
                for (int i= 1; i < 10; i++) {
                    Optional<PlcItem> optPlcItem = plcGeneralFunction.createItem(UUID.randomUUID().toString(), 
                            optPlcGroup.get().getGroupUid().toString(),
                            optPlcDevice.get().getUid().toString(),
                            "S7BOOLS_" + i,
                            "Item description _" + i,
                            "%DB100:50.0:BOOL[8]",
                            "true");
                    if (optPlcItem.isPresent()){
                        optPlcItem.get().enable();
                        System.out.println(optPlcItem.get().getItemUid().toString()+ " : " + optPlcItem.get().getItemName());
                    }                                                            
                }           

            }
        }
            optPlcDevice.get().enable();
        return null;        
    }
}
