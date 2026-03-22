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
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.epics.gpclient.GPClientConfiguration;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.datasource.CompositeDataSource;
import org.epics.gpclient.datasource.DataSourceProvider;

/**
 *
 * @author cgarcia
 */
public class MerlotGPClientImpl implements MerlotGPClient {
    
    private GPClientInstance gpClient;    
    private CompositeDataSource cds = new CompositeDataSource();
    
    @Override
    public void init() {
        ServiceLoader<DataSourceProvider> ldr = ServiceLoader.load(DataSourceProvider.class);
        for (DataSourceProvider spiObject : ldr) {
            cds.putDataSource(spiObject.getName(), spiObject.createInstance());
        }
       
        this.gpClient = new GPClientConfiguration().defaultMaxRate(Duration.ofMillis(50))
                .notificationExecutor(org.epics.util.concurrent.Executors.localThread())
                .dataSource(cds)
                .dataProcessingThreadPool(Executors.newScheduledThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                org.epics.util.concurrent.Executors.namedPool("MerlotGPClient Worker "))).build();  
    }

    @Override
    public void destroy() {
        gpClient.getDefaultDataSource().getChannels().clear();
        gpClient.close();
    } 
    
    @Override
    public GPClientInstance gpClientFactory(String ThreadsId) {
        return new GPClientConfiguration().defaultMaxRate(Duration.ofMillis(50))
                .notificationExecutor(org.epics.util.concurrent.Executors.localThread())
                .dataSource(cds).dataProcessingThreadPool(Executors.newScheduledThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                org.epics.util.concurrent.Executors.namedPool(ThreadsId))).build(); 
    }

    @Override
    public GPClientInstance gpClientDefaultInstance() {
        return gpClient;
    }    
    
    
    public void bindDataSourceProvider(DataSourceProvider dsp) {
        cds.putDataSource(dsp);
    }    
   
}
