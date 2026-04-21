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

import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Set;
import org.apache.plc4x.merlot.archiver.api.MerlotGPClient;
import org.apache.plc4x.merlot.scheduler.api.Job;
import org.apache.plc4x.merlot.scheduler.api.JobContext;
import org.epics.gpclient.GPClientInstance;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotDecanterCollectorLogManagedService implements ManagedServiceFactory, ConfigurationListener, Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotDecanterCollectorLogManagedService.class);
    private static final String LOKI_APPENDER_LOG_PATH = "decanter/collect/alarm";

    private final BundleContext ctx;
    private GPClientInstance gpClient;
    private Set<String> pvs = new HashSet();

    public MerlotDecanterCollectorLogManagedService(BundleContext ctx, MerlotGPClient gpMerlotClient) {
        this.ctx = ctx;
        this.gpClient = gpMerlotClient.gpClientFactory("Alarm Log");;
    }

    @Override
    public void execute(JobContext context) {
        //1. Iterar sobre la lista de pvs y monitorear campo de severidad
    }

    @Override
    public void configurationEvent(ConfigurationEvent ce) {
        //TODO
    }

    @Override
    public String getName() {
        return "Merlot-Decanter-Collector-Log";
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        if (pid == null) {
            try {
                throw new Exception("A valid PID must exist");
            } catch (Exception ex) {
                LOGGER.info("A valid PID must exist: {}", ex.getMessage());
            }
        }

        String descriptor = (String) properties.get("descriptor");
        if (descriptor.equalsIgnoreCase(null) && descriptor.equalsIgnoreCase("alarm")) {
            //Leer variables asociadas al descriptor alarma
            Object rawValue = (String) properties.get("pv.list");
            if (rawValue != null) {
                String strPv = rawValue.toString().trim();

                if (!strPv.isEmpty()) {
                    String[] parts = strPv.split("\\s*,\\s*");
                    for (String part : parts) {
                        System.out.println(part);
                    }
                    pvs.addAll(Arrays.asList(parts));
                }
            }
        }

       
        System.out.println("PID: " + pid);
    }

    @Override
    public void deleted(String pid) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
