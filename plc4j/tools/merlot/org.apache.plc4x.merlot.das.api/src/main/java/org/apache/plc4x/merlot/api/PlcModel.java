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
package org.apache.plc4x.merlot.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.plc4x.merlot.db.api.DBRecord;

/*
* PlcModel represents the internal data structure associated with a PLC, 
* RTU, etc. Exception or time-based subscriptions go against this model.
*/
public interface PlcModel {

    /*
    * Lists the different memory areas of this model. 
    * They are usually defined in the driver.
    */
    Set<String> ListMemoryAreas();

    /*
    * Retrieves the ID assigned to a memory area.
    */
    Integer MemoryAreaId(String strMemoryArea);  
    
    /*
    * This procedure is responsible for creating the memory areas 
    * associated with a particular PLC or Device model.
    */
    void CreateMemoryArea(DBRecord dbRecord);
    
    /*
    * This procedure is responsible for creating a scan group 
    * associated with a particular PLC or Device model.
    */
    void CreateScanGroup(DBRecord dbRecord);    
    
    /*
    * Add a listener to a specific memory area within the model.
    */
    void AddMemoryAreaListener(String strMemmoryArea, Integer index, PlcItemListener listener); 
    
    /*
    * Remove a listener.
    */
    void RemoveMemoryAreaListener(String strMemmoryArea, Integer index, PlcItemListener listener);    
    
    /*
    * Returns the number of segments comprising this memory area. 
    * For example, for MODBUS, it will always return 1 for 
    * any type of memory area. For the S7 driver, specifically for DBs, 
    * it will return the number of DB instances required.
    */
    Integer MemoryAreaSegment(String strMemoryArea);      
    
    /*
    * Returns the indices associated with each memory area.
    */
    List<Integer> MemoryAreaSegmentId(String strMemoryArea);

    
    /*
    * Each "model" must create its own scan PlcGroups.
    */
    List<UUID> ModelPlcGroupsUuid(String strMemoryArea);   
    
    /*
    * Returns the PlcItems created for the model update.
    */
    List<UUID> ModelPlcItemsUuid(String strMemoryArea);      
    

}
