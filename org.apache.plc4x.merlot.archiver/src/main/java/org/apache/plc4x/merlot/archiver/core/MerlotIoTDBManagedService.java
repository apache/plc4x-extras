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
package org.apache.plc4x.merlot.archiver.core;

import java.util.Dictionary;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotIoTDBManagedService implements ManagedService, ConfigurationListener, Job {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotIoTDBManagedService.class);
    
    private final BundleContext ctx;

    public MerlotIoTDBManagedService(BundleContext ctx) {
        this.ctx = ctx;
    }
    
    @Override
    public void execute(JobContext context) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void updated(Dictionary<String, ?> dctnr) throws ConfigurationException {
        System.out.println("adfgadfgasd");
        if (null != dctnr){
            System.out.println("sldkfgapsdkjfalskjdflkasjdflkajsdflkjasldñkfjñalskjdflñkasjdf");
        }
//        dctnr.keys().asIterator().forEachRemaining(System.out::println);
    }

    @Override
    public void configurationEvent(ConfigurationEvent ce) {
        System.out.println(ce.getPid());
    }
    
}
