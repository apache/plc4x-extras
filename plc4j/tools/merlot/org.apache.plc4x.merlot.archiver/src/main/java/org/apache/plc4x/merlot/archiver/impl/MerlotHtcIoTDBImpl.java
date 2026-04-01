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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PV;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.datasource.CompositeDataSource;
import org.epics.vtype.VType;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class MerlotHtcIoTDBImpl implements MerlotHtc {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotHtcIoTDBImpl.class); 
    
    private static final Pattern SIM_PATTERN = Pattern.compile("(^noise)");
    
    private static final String strID = "iotdb";
    
    CompositeDataSource cds = new  CompositeDataSource();
    
    private Map<String, PVReader<VType>> readerPvs = new ConcurrentHashMap<>();      
    private Map<String, CircularFifoQueue<Pair<LocalDateTime, PV>>> pvs = new ConcurrentHashMap<>();    
    
    final GPClientInstance gpClient;   
    
    private PVReader<VType> pv1;
    private PVReader<VType> pv2;    

    public MerlotHtcIoTDBImpl(MerlotGPClient gpMerlotClient) {
        this.gpClient = gpMerlotClient.gpClientFactory("GPClient HtcRT ");
    }
    
    @Override
    public void init() {
        pv1 = gpClient.read("sim://noise")
                .addReadListener((event, p) ->{
                    pvs.get("").add(new ImmutablePair(LocalDateTime.now(), p));
                })
                .start();  
        readerPvs.put("sim://noise", pv1);
    }

    @Override
    public void destroy() {
        try {
            pv1.close();
            gpClient.close();
        } catch (IllegalStateException e) {
            e.printStackTrace();
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
        if (null == readerPvs.keySet()){
            return new HashSet<>();
        }
        return readerPvs.keySet();
    }

    @Override
    public List<Pair<LocalDateTime, VType>> getPVs(String strPV, String init, String end) {
        return null;
    }
    
}
