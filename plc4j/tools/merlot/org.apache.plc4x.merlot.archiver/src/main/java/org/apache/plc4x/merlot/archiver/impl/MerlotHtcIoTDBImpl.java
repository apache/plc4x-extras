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
package org.apache.plc4x.merlot.archiver.impl;

import java.util.List;
import java.util.Set;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.epics.vtype.VType;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class MerlotHtcIoTDBImpl implements MerlotHtc {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotHtcIoTDBImpl.class); 
    
    
    private static final String strID = "iotdb";
    private SessionPool sp ;
       

    public MerlotHtcIoTDBImpl(MerlotGPClient gpMerlotClient) {
      
    }
    
    @Override
    public void init() {
       sp = new SessionPool.Builder()
               .host("192.168.0.218")
               .port(6667)
               .user("root")
               .password("root")
               .maxSize(5)
               .build();
       
        if (sp != null) {
            LOGGER.info("Connection sucess");
        }
    }

    @Override
    public void destroy() {
        if (sp != null) {
           sp.close();
        }
    }

    @Override
    public String getID() {
        return strID;
    }    
    
    @Override
    public void addPV(String strPV, Double interval) {
        //
    }

    @Override
    public void removePV(String strPV) {
        //
    }

    @Override
    public Set<String> getPVs() {
       
        return null;
    }

    @Override
    public List<VType> getPVs(String strPV, String init, String end) {
        return null;
    }

    @Override
    public int countPVs(String strPV, String init, String end) {
        return 0;
    }
    
    
    
    
}
