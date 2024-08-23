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
import java.util.stream.Collectors;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.plc4x.merlot.api.PlcDevice;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.osgi.framework.BundleContext;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.impl.PlcGroupImpl;

@Command(scope = "plc4x", name = "group-new", description = "Create group for a device.")
@Service
public class PlcDeviceGroupNewCommand implements Action {

    @Reference
    BundleContext bc;
    
    @Reference
    volatile List<PlcGroup> groups;
    
    @Reference
    volatile List<PlcDevice> devices;          
    
    @Option(name = "-d", aliases = "--uid", description = "Device uid.", required = true, multiValued = false)
    String uid; 
    
    @Option(name = "-p", aliases = "--scantime", description = "Group scan time schedule.", required = true, multiValued = false)
    Long scantime = 1000L; 

    @Argument(index = 0, name = "Name", description = "Name of the group.", required = true, multiValued = false)
    String name;

    @Argument(index = 1, name = "Description", description = "Description of group", required = true, multiValued = false)
    String description;      
    
    @Override
    public Object execute() throws Exception {

        List<PlcGroup> mygroups = groups.stream().
                                filter(g -> g.getGroupName().equalsIgnoreCase(name)).
                                collect(Collectors.toList()); 
        
        if (mygroups.size() == 0) {

            Optional<PlcDevice> plcDevice = devices.stream().
                                            filter(d -> d.getUid().toString().equals(uid)).
                                            findFirst();
            
            if (plcDevice.isEmpty()) {
                System.out.println("Device don't exists.");
                return null;
            }
            
            scantime = (scantime < 100) ? 100 : scantime;
            
            PlcGroup group  = new PlcGroupImpl.PlcGroupBuilder(bc, name).
                                    setGroupPeriod(scantime).build();
            
            plcDevice.get().putGroup(group);
            
            bc.registerService(new String[]{Job.class.getName(), PlcGroup.class.getName()},
                    group, group.getProperties());            
            
        } else {
            System.out.println("A group with that name already exists.");
        }
        
        return null;
    }
    
}
