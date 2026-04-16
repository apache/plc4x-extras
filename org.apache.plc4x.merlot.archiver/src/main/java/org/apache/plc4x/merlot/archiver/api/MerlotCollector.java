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
package org.apache.plc4x.merlot.archiver.api;


public interface MerlotCollector {
    
    public void init();
    
    
    public void destroy();

    /*
    * Start the scan of all groups
    */    
    public void start();    
    
    /*
    * Stop the scan of all groups
    */
    public void stop();

    /*
    * Add un scan group to the collector
    * @param strGroup the group name, must be unique.
    * @param args[0] the scan time in seconds    
    */
    public void addGroup(String strGroup, String... args);
    
    /*
    * Remove the  scan group to the collector
    * @param strGroup the group name, must be unique.  
    */    
    public void removeGroup(String strGroup);    
    
    /*
    * Reschedule the group, inte second specify in scanTime
    * @param strGroup the group name, must be unique.
    * @param scanTime the scan time in seconds      
    */
    public void schedulerGroup(String strGroup, int scanTime);    
    
    /*
    * Reschedule the group, inte second specify in scanTime
    * @param strGroup the group name, must be unique.
    * @param args[0] the scan time in seconds     
    */    
    public void putPvRecord(String strPvName, String... args);
    
    /*
    * Remove the  PvRecord from group.
    * @param strGroup the group name, must be unique.  
    */     
    public void removePvRecord(String strPvName);    
        
}
