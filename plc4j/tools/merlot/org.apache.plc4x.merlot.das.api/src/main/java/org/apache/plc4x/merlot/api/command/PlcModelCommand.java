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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.util.List;
import java.util.Optional;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.plc4x.merlot.api.PlcGeneralFunction;
import org.osgi.framework.BundleContext;
import org.apache.plc4x.merlot.api.PlcGroup;
import org.apache.plc4x.merlot.api.PlcItem;
import org.apache.plc4x.merlot.api.PlcModel;
import org.apache.plc4x.merlot.api.core.PlcItemClientService;


@Command(scope = "plc4x", name = "model", description = "Command to display information about a device's model.")
@Service
public class PlcModelCommand  implements Action {
  
    @Reference
    BundleContext bc;  
    
    @Reference
    PlcGeneralFunction gf;      
    
    @Reference
    PlcItemClientService clients;
    
    @Reference
    volatile List<PlcGroup> groups;
    
    @Option(name = "-d", aliases = "--dump", description = "Group uid.", required = true, multiValued = false)
    Boolean d = false; 
    
    @Argument(index = 0, name = "name", description = "PlcModel dedvice name", required = true, multiValued = false)
    String device_name = null;   
    
    @Argument(index = 1, name = "name", description = "PlcModel area name", required = false, multiValued = false)
    String area_name = null;   

    @Argument(index = 2, name = "name", description = "PlcModel area index", required = false, multiValued = false)
    Integer index = -1;       
   

    @Override
    public Object execute() throws Exception {
        Optional<PlcModel> optPlcModel = gf.getPlcModel(null, device_name);
        if (optPlcModel.isPresent()) {
            final PlcModel plcModel = optPlcModel.get();     
            if ((null != device_name) && (null == area_name) && (index == -1)) {
                PrintMemoryAreas(plcModel);
            } else if ((null != device_name) && (null != area_name) && (index == -1)) {
                PrintMemoryIndex(plcModel, "DB");                
            } else if ((null != device_name) && (null != area_name) && (index != -1)) {
                PrintMemoryByteBuf(plcModel, "DB", 100);                
            } 
        } else {
            System.out.println("PlcModel not present.");
        }
        return null;
    }


    private void PrintMemoryAreas(PlcModel plcModel){
        ShellTable table = new ShellTable();
        table.column("Area");
        table.column("Amount");           
        plcModel.listMemoryAreas().stream().
                forEach(s -> { 
                    table.addRow().addContent(s, plcModel.getMemoryAreaSegmentCount(s).toString());
                });
        
    }

    private void PrintMemoryIndex(PlcModel plcModel, String strMemoryArea){
        ShellTable table = new ShellTable();
        table.column("Area");
        table.column("Amount");          
        plcModel.getMemoryAreaSegmentIds(strMemoryArea).stream().
                forEach(i -> {
                    System.out.println(i);
                    });
        
    }  
    
    private void PrintMemoryByteBuf(PlcModel plcModel, String strMemoryArea, Integer index){
        Optional<PlcItem> optPlcItem = plcModel.getMemoryAreaPlcItem(strMemoryArea, index);
        if (optPlcItem.isPresent()) {
            final PlcItem plcItem = optPlcItem.get();
            final ByteBuf byteBuf = plcItem.getItemByteBuf();
            System.out.println("----");
            System.out.println(ByteBufUtil.prettyHexDump(byteBuf));
        } else {
            System.out.println("Memory area not present.");            
        }
        
    }    
    
    
}
