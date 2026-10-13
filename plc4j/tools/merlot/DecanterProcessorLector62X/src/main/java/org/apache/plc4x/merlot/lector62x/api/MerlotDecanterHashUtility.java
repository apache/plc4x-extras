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
package org.apache.plc4x.merlot.lector62x.api;

import org.osgi.service.event.EventHandler;

public interface MerlotDecanterHashUtility extends EventHandler {

   
    public static final int DESTINY_OFFICE_ARRAY_POSITION = 4;
    public final static String MERLOT_LECTOR62X_EVENT_TOPIC = "decanter/collect/socket";

    /**
     * 
     * @param officeDestiny A 4-digit numeric value, which must be separated digit by digit to apply the hash function
     * @return Hash code that must be written to the corresponding PVA variable
     */
     int applyHash();
    
    /**
     * 
     * @param codeHash Value sent to the PLC indicating the location of the piston to be activated
     */
    void writePV(int codeHash);
    
    
}
