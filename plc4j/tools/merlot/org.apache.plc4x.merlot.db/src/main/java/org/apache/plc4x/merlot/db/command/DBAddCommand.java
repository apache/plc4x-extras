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
package org.apache.plc4x.merlot.db.command;

import org.apache.plc4x.merlot.db.api.DBControl;
import org.apache.plc4x.merlot.db.api.DBRecordFactory;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.epics.pvdata.pv.PVBoolean;
import org.epics.pvdata.pv.PVInt;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdatabase.PVDatabase;
import org.epics.pvdatabase.PVRecord;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;


@Command(scope = "db", name = "add", description = "Add a Record (tag) to database.")
@Service
public class DBAddCommand  implements Action {
  
    @Reference
    BundleContext bundleContext;
    
    @Reference
    PVDatabase master;
    
//    @Reference
    DBControl dbControl;
    
//    @Option(name = "-n", aliases = "--new", description = "New DBRecord.", required = false, multiValued = false)
//    boolean driver;
//    
//    @Option(name = "-s", aliases = "--start", description = "Stop and delete the S7 device.", required = false, multiValued = false)
//    boolean device;  
//    
//    @Option(name = "-a", aliases = "--array", description = "New DBRecord of the define type.", required = false, multiValued = false)
//    boolean array = false;      
    
    @Argument(index = 0, name = "type", description = "PVType of the record.", required = true, multiValued = false)
    String recordType = null;   
    
    @Argument(index = 1, name = "name", description = "Short name of the record.", required = true, multiValued = false)
    String recordName = null;

    @Argument(index = 2, name = "item", description = "PlcItem name of the record.", required = true, multiValued = false)
    String itemName = null;
    
    @Argument(index = 3, name = "offset", description = "PlcItem data offset.", required = true, multiValued = false)
    int itemOffset = 0;     
    
    @Argument(index = 4, name = "scan", description = "Scan rate for the record.", required = true, multiValued = false)
    String scan = null; 
    
    @Argument(index = 5, name = "enable", description = "PvRecord is enbale to scan PlcItem.", required = true, multiValued = false)
    boolean scanEnable = true;  
    
    @Argument(index = 6, name = "write", description = "PvRecord is enbale to scan PlcItem.", required = true, multiValued = false)
    boolean writeEnable = false;      
    
    @Argument(index = 7, name = "des", description = "Full description of the PvRecord.", required = false, multiValued = false)
    String descriptor = null;    

    
    public Object execute() throws Exception {
        
        boolean isArray = false;
        int lengthArray = 0;
        PVRecord record;
        
        String filter =  "(&(" + Constants.OBJECTCLASS + "=" + DBRecordFactory.class.getName() + ")"+
                           "(db.record.type=" + recordType + "))";
            
        ServiceReference[] references = bundleContext.getServiceReferences((String) null, filter);
        
        if (references != null){
            ServiceReference reference = references[0];
            if (recordType.equalsIgnoreCase((String)reference.getProperty("db.record.type"))){
                DBRecordFactory recordFactory = (DBRecordFactory) bundleContext.getService(reference);

                record = recordFactory.create(recordName);

                PVStructure structure = record.getPVStructure();
                PVString pvDes = structure.getStringField("descriptor");
                pvDes.put(descriptor);
                PVString pvId = structure.getStringField("id");
                pvId.put(itemName);
                PVInt pvOffset = structure.getIntField("offset");
                pvOffset .put(itemOffset);
                PVString pvScan = structure.getStringField("scan_rate");
                pvScan.put(scan);
                PVBoolean pvScanEnable = structure.getBooleanField("scan_enable");
                pvScanEnable.put(scanEnable);
                PVBoolean pvWriteEnable = structure.getBooleanField("write_enable");
                pvWriteEnable.put(writeEnable);                

                master.addRecord(record);  

                System.out.println("Record: \r\n" + record.toString());


            }
        }

        
        return null;
    }
}
