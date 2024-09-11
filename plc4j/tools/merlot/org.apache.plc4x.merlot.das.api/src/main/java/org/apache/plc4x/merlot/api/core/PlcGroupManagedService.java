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
package org.apache.plc4x.merlot.api.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PlcGroupManagedService implements ManagedService, ConfigurationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlcGroupManagedService.class);      
    private static final Pattern GROUP_PATTERN =  
            Pattern.compile("(?<groupDevice>.+),(?<groupDescription>.+),(?<groupScanTime>[0-9]{3,5})");
    
    protected static final String GROUP_DEVICE = "groupDevice";
    protected static final String GROUP_DESCRIPTION = "groupDescription";  
    protected static final String GROUP_SCANTIME = "groupScanTime";    
    
    
    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        Matcher matcher;
        String fields;
        if (null != properties){
            Enumeration<String> keys = properties.keys();   
            
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                if (properties.get(key) instanceof int[]) {
                } else {
                    fields = (String) properties.get(key);
                    if ((matcher = GROUP_PATTERN.matcher(fields)).matches()) {
                        String groupDevice = matcher.group(GROUP_DEVICE);
                        String groupDescription = matcher.group(GROUP_DESCRIPTION);
                        String groupsCANtIME = matcher.group(GROUP_SCANTIME); 
                        LOGGER.info("{} : {} : {} : {}", key, groupDevice, groupDescription, groupsCANtIME);  
                    }
                }
            }                                  
        }
        
    }

    @Override
    public void configurationEvent(ConfigurationEvent event) {
        LOGGER.info("configurationEvent: " + event.toString());        
    }
    
}
