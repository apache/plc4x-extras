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
package org.apache.plc4x.merlot.kafka.core;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.plc4x.merlot.kafka.api.MerlotDecanterCollector;
import org.apache.plc4x.merlot.kafka.impl.MerlotKafkaDecanterCollectorImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MerlotKafkaManagedServiceFactory implements ManagedServiceFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotKafkaManagedServiceFactory.class);

    private final BundleContext ctx;
    private final EventAdmin dispatcher;
    private  Unmarshaller unmarshaller;
    private Map<String,ServiceRegistration> services = new HashMap<>();

    public MerlotKafkaManagedServiceFactory(BundleContext ctx, EventAdmin dispatcher, Unmarshaller unmarshaller) {
        this.ctx = ctx;
        this.dispatcher = dispatcher;
        this.unmarshaller = unmarshaller;
    }

    @Override
    public String getName() {
        return "Merlot Kafka Managed Service Factory";
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
        LOGGER.info("Registering service: {}", pid);
        deleted(pid);
        MerlotKafkaDecanterCollectorImpl bundle = new MerlotKafkaDecanterCollectorImpl(dispatcher, unmarshaller);

        bundle.activate(pid, (Dictionary<String, Object>) properties);
        bundle.init();
        
        Hashtable<String, String> serviceProperties = new Hashtable<>();
        serviceProperties.put(Constants.SERVICE_PID, pid);
        ServiceRegistration registration = ctx.registerService(MerlotDecanterCollector.class, bundle, serviceProperties);
        services.put(pid, registration);
    }

    @Override
    public void deleted(String pid) {
        LOGGER.info("Removing service: " + pid);
        ServiceRegistration registration = services.remove(pid);
        if (registration != null) {
            try {
                MerlotDecanterCollector collector = (MerlotDecanterCollector) ctx.getService(registration.getReference());
                if (collector != null) {
                    collector.destroy();
                }
                registration.unregister();
            } catch (Exception e) {
                LOGGER.error("Error al eliminar el servicio {}", pid, e);
            }
        }
    }

    public void destroy() {
        LOGGER.info("Destroying MerlotKafkaManagedServiceFactory, cleaning up {} services", services.size());
        for (String pid : services.keySet().toArray(new String[0])) {
            deleted(pid);
        }
    }

}
