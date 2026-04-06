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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.epics.gpclient.PV;
import org.epics.vtype.VType;


public interface MerlotHtc {
    
    /*
    * Bundle Initialization
    */    
    public void init();
        
    /*
    * Bundle Destruction
    */    
    public void destroy();        
        
    /*
    * Identify the Bundle's internal ID, required for remote commands 
    * and functions.
    */    
    public String getID();      
    
    
    /*
    * Add a tag to the historian. Only tags using the PVA protocol 
    * are supported.
    * @param strPV
    * @param maxRate 
    */
    void addPV(String strPV, Double maxRate);
    
    /*
    * Removes a tag from the historian. Intended for use only from 
    * the command line.
    * @param strPV
    */
    void removePV(String strPV);
    
    /*
    * A set of tags currently being processed by the historian.
    */
    Set<String> getPVs();
    
    /*
    * It returns the values ​​stored in the historian as pairs of
    * LocalDateTime and VType (value) pairs, for subsequent processing 
    * according to the format required by the client application—for example, 
    * PBRAW, JSON, or XML.
    * @param strPV
    * @param from
    * @param to  
    * @return A List of Pair 
    */
    List<VType> getPVs(String strPV, String init, String end);
    
}
