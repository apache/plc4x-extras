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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.apache.plc4x.merlot.archiver.api.MerlotHtc;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PVEvent;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.datasource.CompositeDataSource;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;
import org.epics.vtype.VType;
import org.slf4j.LoggerFactory;


/**
 *
 * @author cgarcia
 */
public class MerlotHtcRTImpl implements MerlotHtc {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MerlotHtcRTImpl.class);

    private static final Pattern SIM_PATTERN = Pattern.compile("(^noise)");

    private static final String strID = "rt";
    
    private final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_INSTANT)
            .appendLiteral("(0)")
            .toFormatter();

    CompositeDataSource cds = new CompositeDataSource();

    private Map<String, PVReader<VType>> readerPvs = new ConcurrentHashMap<>();
    private Map<String, CircularFifoQueue<VType>> pvs = new ConcurrentHashMap<>();
    
    GPClientInstance gpClient;
    

    public MerlotHtcRTImpl(MerlotGPClient gpMerlotClient) {     
        gpClient = gpMerlotClient.gpClientFactory("HtcRT ");
    }

    @Override
    public void init() {       
        //       
    }

    @Override
    public void destroy() {
        try {
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
    public void addPV(String strPV, Double maxRate) {
        
        PVReader<VType> pv ;
        CircularFifoQueue<VType> queue = new CircularFifoQueue<VType>(5000);        
        pvs.put(strPV, queue);
        String strPVA = "pva://" + strPV.trim();
        
        pv = gpClient.read(strPVA)
                .addReadListener((event, p) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        pvs.get(strPV).add(p.getValue());
                    }
                })
                .start();
        
        readerPvs.put(strPV, pv);          
    }

    @Override
    public void removePV(String strPV) {
        var pv = readerPvs.get(strPV);
        readerPvs.remove(strPV);
        pv.close();
        pvs.remove(strPV);                
    }

    @Override
    public Set<String> getPVs() {
        if (null == readerPvs.keySet()) {
            return new HashSet<>();
        }
        return readerPvs.keySet();
    }

    @Override
    public List<VType> getPVs(String strPV, String init, String end) {
        List<VType> result;
        if (init.indexOf("(") > 0) {
            init = init.substring(0, init.indexOf("("));
        }
        if (end.indexOf("(") > 0) {
            end = end.substring(0, end.indexOf("("));
        }

        Instant inicio = Instant.parse(init);
        Instant fin    = Instant.parse(end);
       
        var queue = pvs.get(strPV);
        List<VType> listPVs = queue.stream()              
            .filter(v -> {
                    Instant fecha = ((VDouble) v).getTime().getTimestamp();
                    return !fecha.isBefore(inicio) && !fecha.isAfter(fin);                        
            })
            .collect(Collectors.toList());   
        if (listPVs.size() == 0) {
            VType lastvalue = queue.get(0);
            VDouble valorOriginal = (VDouble) lastvalue;
            Time tv = Time.of(Instant.parse(init));
            // 3. Re-empaquetar el valor usando ValueFactory
            VDouble valorModificado = VDouble.of(
                    valorOriginal.getValue(),
                    valorOriginal.getAlarm(),
                    tv,
                    valorOriginal.getDisplay());
            listPVs.add(valorModificado);
        }

        return listPVs;             
    }

    @Override
    public int countPVs(String strPV, String init, String end) {
        List<VType> pvs = getPVs(strPV, init, end);        
        return (null == pvs)?0:pvs.size();        
    }
    
    

}
