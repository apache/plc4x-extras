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

package org.apache.plc4x.merlot.decanter.impl;


import java.util.logging.Level;
import org.apache.plc4x.merlot.decanter.api.BrokerService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BrokerServiceImpl implements BrokerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerServiceImpl.class);    

      private MqttClient client;
    
    @Override
    public void init() {
        try {
            client = new MqttClient("tcp://10.10.1.104:1883", "clientId", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName("merlot");
            options.setPassword("merlot".toCharArray());
            client.connect(options);
            
            MqttMessage message = new MqttMessage();
            
            message.setPayload("esto es una  prueba".getBytes());
            
            for (int i=0; i<100; i++){
                client.publish("prueba", message);
            }
            
            
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
        
    }

    @Override
    public void destroy() {
        System.out.println("Destroy");
    }
    
}
