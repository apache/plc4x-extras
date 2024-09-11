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


public class PlcItemManagedService implements ManagedService, ConfigurationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlcItemManagedService.class);
    private static final Pattern ITEM_PATTERN =  
            Pattern.compile("(?<itemDevice>.+),(?<itemGroup>.+),(?<itemDescription>.+),(?<itemId>.+)"); 
    
    protected static final String ITEM_DEVICE = "itemDevice";
    protected static final String ITEM_GROUP = "itemGroup";     
    protected static final String ITEM_DESCRIPTION = "itemDescription"; 
    protected static final String ITEM_ID = "itemId";     
    
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
                    if ((matcher = ITEM_PATTERN.matcher(fields)).matches()){  
                        String itemDevice = matcher.group(ITEM_DEVICE);
                        String itemGroup  = matcher.group(ITEM_GROUP);  
                        String itemDescription  = matcher.group(ITEM_DESCRIPTION); 
                        String itemId  = matcher.group(ITEM_ID); 
                        LOGGER.info("{} : {} : {} : {} : {}", key, itemDevice, itemGroup, itemDescription, itemId );
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
