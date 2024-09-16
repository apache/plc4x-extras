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

import org.apache.plc4x.java.api.PlcDriver;
import org.osgi.service.jdbc.DataSourceFactory;

/*
* During the boot process, the sequential order of the boot must be 
* guaranteed based on the available infrastructure.
* The implemented services must monitor the PlcDriver type services and 
* from these deploy the associated PlcDevice, PlcGroup and PlcItem.
* In future implementations, security aspects must be added such as digital 
* signature (MD5, CRC, etc.) of each record in such a way as to minimize 
* the possibility that the data is correct or has been altered.
*/
public interface PlcSecureBoot {
    
    /*
    * Initializes the database connection if the factory is available. 
    * It is initially called by the BluePrint container. 
    * In case the connection is not created, the "init()" is called 
    * when the factory is assigned.
    */
    public void init();
    
    /*
    * Releases all allocated resources and closes the connection to 
    * the database if connected.
    */
    public void destroy();
    
    /*
    * This service checks the availability of each PlcDriver available 
    * in the environment. Upon receiving the availability, 
    * it will try to create the associated groups, items and records.
    */
    public void bindPlcDriver(PlcDriver plcDriver);
    
    /*
    * No action is taken when a PlcDriver is dynamically removed.
    */
    public void unbindPlcDriver(PlcDriver plcDriver);
    
    /*
    * Factory for creating the connection to the database and the 
    * associated tables.
    * This is planned to be SqlLite, however it could be another implementation.
    */
    public void bindDataSourceFactory(DataSourceFactory dsFactory);  
       
    /*
    * Stores all the information in memory related to the PlcDriver, 
    * that is, PlcDevice, PlcGroups, PlcItems and, if the services are 
    * available, the PlcRecords
    */
    public void persist();
    
    /*
    * Stores all resources assigned to the designated PlcDriver. 
    */
    public void store(String plcDriver);    
    
    /*
    * Restores all resources assigned to the designated PlcDriver. 
    */
    public void restore(String plcDriver);
    
}
