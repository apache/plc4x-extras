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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.epics.gpclient.GPClient;
import org.epics.gpclient.GPClientConfiguration;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PV;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.datasource.DataSourceProvider;
import org.epics.vtype.VType;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cgarcia
 */
public class MerlotHtcRTImpl implements MerlotHtc{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotHtcRTImpl.class); 
    
    private static final Pattern SIM_PATTERN = Pattern.compile("(^ACK:)((,{0,1}(16#[0-9a-fA-F]{8})(;([0-9a-fA-F]{2})))+)");
    
    private Map<String, PVReader<VType>> readerPvs = new ConcurrentHashMap<>();      
    private Map<String, CircularFifoQueue<Pair<LocalDateTime, PV>>> pvs = new ConcurrentHashMap<>();    
    
    static GPClientInstance gpClient;   
    
    private PVReader<VType> pv1;
    private PVReader<VType> pv2;    
    
    @Override
    public void init() {

        gpClient = new GPClientConfiguration().defaultMaxRate(Duration.ofMillis(50))
                .notificationExecutor(org.epics.util.concurrent.Executors.localThread())
                .dataSource(DataSourceProvider.createDataSource())
                .dataProcessingThreadPool(java.util.concurrent.Executors.newScheduledThreadPool(
                        Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                        org.epics.util.concurrent.Executors.namedPool("MerlotHtcRT-Worker ")))
                .build(); 
        
        pv1 = gpClient.read("sim://noise")
                .addReadListener((event, p) ->{
                    System.out.println(event + " <1>  " + p.isConnected() + " " + p.getValue());
                        
                })
                .start(); 
        
        pv2 = gpClient.read("sim://noise")
                .addReadListener((event, p) ->{
                    System.out.println(event + " <2> " + p.isConnected() + " " + p.getValue());
                        
                })
                .start();          
        
        
    }

    @Override
    public void destroy() {
        try {
            pv1.close();
            pv2.close();            
            gpClient.close();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
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
    public String[] getPVs() {
        return null;
    }

    @Override
    public PV[] getPs(String strPV, String init, String end) {
        return null;
    }
    
}
